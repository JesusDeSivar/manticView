package dev.jesusdesivar.manticwidget.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** One probability observation, so deltas can be computed over real time windows. */
@Serializable
data class ProbPoint(val t: Long, val p: Double)

/**
 * One tracked market plus a rolling probability history for sparklines.
 * For multiple-choice markets, one answer is tracked (the top one at add
 * time) and [probability]/[history] refer to that answer.
 */
@Serializable
data class WatchedMarket(
    val slug: String,
    val question: String,
    val url: String,
    val probability: Double,
    val answerId: String? = null,
    val answerText: String? = null,
    val isResolved: Boolean = false,
    val resolution: String? = null,
    /** Chronological; capped at [WatchlistRepository.HISTORY_SIZE] points. */
    val history: List<ProbPoint> = emptyList(),
    val lastUpdatedMillis: Long = 0L,
) {
    /** Probability change over the last ~24h (or the full history if shorter). */
    val delta: Double
        get() = deltaBaseline()?.let { history.last().p - it.p } ?: 0.0

    /** Hours the delta actually spans, for labeling. */
    val deltaSpanHours: Long
        get() = deltaBaseline()?.let {
            ((history.last().t - it.t) / 3_600_000L).coerceAtLeast(1)
        } ?: 0

    private fun deltaBaseline(): ProbPoint? {
        if (history.size < 2) return null
        val cutoff = history.last().t - DAY_MILLIS
        return history.lastOrNull { it.t <= cutoff } ?: history.first()
    }

    companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}

private val Context.dataStore by preferencesDataStore(name = "watchlist")

class WatchlistRepository(private val context: Context) {

    val watchlist: Flow<List<WatchedMarket>> = context.dataStore.data.map { prefs ->
        prefs[KEY]?.let { decode(it) } ?: emptyList()
    }

    suspend fun current(): List<WatchedMarket> = watchlist.first()

    /**
     * Fetches the market to validate it, seeds the sparkline from recent bet
     * history, and adds it to the watchlist. Binary markets are tracked
     * directly; multiple-choice markets track their current top answer.
     */
    suspend fun add(slugOrUrl: String) {
        val market = ManifoldApi.fetchMarket(slugOrUrl)
        val topAnswer = market.answers.maxByOrNull { it.probability ?: -1.0 }
        val (probability, answer) = when {
            market.probability != null -> market.probability to null
            topAnswer?.probability != null -> topAnswer.probability!! to topAnswer
            else -> throw IllegalArgumentException(
                "\"${market.question}\" (${market.outcomeType}) isn't supported yet — only YES/NO and multiple-choice markets are."
            )
        }
        val now = System.currentTimeMillis()
        val entry = WatchedMarket(
            slug = market.slug,
            question = market.question,
            url = market.url,
            probability = probability,
            answerId = answer?.id,
            answerText = answer?.text,
            isResolved = market.isResolved,
            resolution = market.resolution,
            history = seedHistory(market.slug, answer?.id) + ProbPoint(now, probability),
            lastUpdatedMillis = now,
        )
        mutate { list -> list.filterNot { it.slug == entry.slug } + entry }
    }

    suspend fun remove(slug: String) {
        mutate { list -> list.filterNot { it.slug == slug } }
    }

    /** Re-fetches every watched market, appending to each probability history. */
    suspend fun refreshAll() {
        val updated = current().map { watched ->
            runCatching { ManifoldApi.fetchMarket(watched.slug) }.fold(
                onSuccess = { market ->
                    val answer = watched.answerId?.let { id -> market.answers.find { it.id == id } }
                    val probability = answer?.probability ?: market.probability ?: watched.probability
                    watched.copy(
                        question = market.question,
                        probability = probability,
                        answerText = answer?.text ?: watched.answerText,
                        isResolved = market.isResolved,
                        resolution = market.resolution,
                        history = (watched.history + ProbPoint(System.currentTimeMillis(), probability))
                            .takeLast(HISTORY_SIZE),
                        lastUpdatedMillis = System.currentTimeMillis(),
                    )
                },
                // Keep stale data on transient failures; the widget shows last-updated time.
                onFailure = { watched },
            )
        }
        context.dataStore.edit { prefs -> prefs[KEY] = encode(updated) }
    }

    /**
     * Builds an initial history from recent bets so the sparkline and delta
     * are meaningful immediately instead of waiting hours for refreshes.
     * Prefers the last 7 days of activity; falls back to whatever exists.
     */
    private suspend fun seedHistory(slug: String, answerId: String?): List<ProbPoint> {
        val bets = runCatching { ManifoldApi.fetchBets(slug) }.getOrDefault(emptyList())
        val points = bets
            .filter { answerId == null || it.answerId == answerId }
            .mapNotNull { bet -> bet.probAfter?.let { ProbPoint(bet.createdTime, it) } }
            .sortedBy { it.t }
        val recent = points.filter { it.t >= System.currentTimeMillis() - 7 * WatchedMarket.DAY_MILLIS }
        return downsample(if (recent.size >= 2) recent else points, HISTORY_SIZE - 1)
    }

    private fun downsample(points: List<ProbPoint>, target: Int): List<ProbPoint> {
        if (points.size <= target) return points
        return List(target) { i -> points[i * (points.size - 1) / (target - 1)] }
    }

    private suspend fun mutate(transform: (List<WatchedMarket>) -> List<WatchedMarket>) {
        context.dataStore.edit { prefs ->
            val list = prefs[KEY]?.let { decode(it) } ?: emptyList()
            prefs[KEY] = encode(transform(list))
        }
    }

    private fun encode(list: List<WatchedMarket>): String =
        json.encodeToString(ListSerializer(WatchedMarket.serializer()), list)

    private fun decode(raw: String): List<WatchedMarket> =
        runCatching { json.decodeFromString(ListSerializer(WatchedMarket.serializer()), raw) }
            .getOrDefault(emptyList())

    companion object {
        const val HISTORY_SIZE = 48
        private val KEY = stringPreferencesKey("markets")
        private val json = Json { ignoreUnknownKeys = true }
    }
}
