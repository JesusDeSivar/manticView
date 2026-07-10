package dev.jesusdesivar.manticwidget.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** One probability observation, so deltas can be computed over real time windows. */
@Serializable
data class ProbPoint(val t: Long, val p: Double)

/**
 * One tracked entry: a market, or one specific answer of a multiple-choice
 * market — the same market can be watched several times with different
 * answers, so identity is [key], not [slug]. Entries belong to a [group]
 * ("watchlist"/"category"), used for widget filtering and list sections.
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
    /**
     * Human-readable outcome: "YES"/"NO" for binary markets, "Won"/"Lost"
     * for the tracked answer of a multiple-choice market, "N/A" for
     * cancelled. Raw [resolution] holds an answer ID for multi-choice
     * markets, which is meaningless on screen.
     */
    val resolutionLabel: String? = null,
    /** When the app first observed this market as resolved. */
    val resolvedSeenMillis: Long? = null,
    /** Chronological; capped at [WatchlistRepository.HISTORY_SIZE] points. */
    val history: List<ProbPoint> = emptyList(),
    val lastUpdatedMillis: Long = 0L,
    /** Comparison window for delta and sparkline; 0 = all history. */
    val periodHours: Int = 24,
    val group: String = DEFAULT_GROUP,
) {
    /** Watchlist identity: market plus tracked answer. */
    val key: String
        get() = answerId?.let { "$slug:$it" } ?: slug

    /**
     * Resolved markets linger on the watchlist widget for a grace period so
     * the outcome is seen, then drop off; they stay in the app until removed.
     */
    fun isArchived(now: Long = System.currentTimeMillis()): Boolean =
        isResolved && resolvedSeenMillis != null && now - resolvedSeenMillis > ARCHIVE_AFTER_MILLIS

    /** Probability change over the chosen period (or the full history if shorter). */
    val delta: Double
        get() = baseline()?.let { history.last().p - it.p } ?: 0.0

    /** Hours the delta actually spans, for honest labeling. */
    val deltaSpanHours: Long
        get() = baseline()?.let { base ->
            Math.round((history.last().t - base.t) / 3_600_000.0).coerceAtLeast(1)
        } ?: 0

    /**
     * History points within the chosen period, for the sparkline. The window
     * boundary is an interpolated point, so changing the period visibly
     * changes the chart even with sparse history.
     */
    fun sparkPoints(): List<ProbPoint> {
        if (history.size < 2) return history
        val cutoff = cutoff() ?: return history
        val base = interpolatedAt(cutoff) ?: return history
        return listOf(base) + history.filter { it.t > cutoff }
    }

    /**
     * The comparison point: the probability exactly [periodHours] ago,
     * linearly interpolated between the surrounding observations. Stored
     * points are sparse (seeded bets, 30-minute refreshes), so snapping to
     * the nearest stored point would silently stretch a "1H" comparison to
     * whatever gap the history happens to have.
     */
    private fun baseline(): ProbPoint? {
        if (history.size < 2) return null
        val cutoff = cutoff() ?: return history.first()
        return interpolatedAt(cutoff) ?: history.first()
    }

    private fun cutoff(): Long? =
        if (periodHours == 0) null else history.last().t - periodHours * 3_600_000L

    private fun interpolatedAt(time: Long): ProbPoint? {
        val older = history.lastOrNull { it.t <= time } ?: return null
        val newer = history.firstOrNull { it.t >= time } ?: return older
        if (newer.t == older.t) return older
        val fraction = (time - older.t).toDouble() / (newer.t - older.t)
        return ProbPoint(time, older.p + (newer.p - older.p) * fraction)
    }

    companion object {
        const val DAY_MILLIS = 86_400_000L
        const val ARCHIVE_AFTER_MILLIS = 2 * DAY_MILLIS
        const val DEFAULT_GROUP = "General"
        val PERIOD_OPTIONS = listOf(1 to "1H", 6 to "6H", 24 to "1D", 168 to "1W", 720 to "1M", 0 to "ALL")
        fun periodLabel(hours: Int): String =
            PERIOD_OPTIONS.firstOrNull { it.first == hours }?.second ?: "${hours}H"
    }
}

private val Context.dataStore by preferencesDataStore(name = "watchlist")

class WatchlistRepository(private val context: Context) {

    val watchlist: Flow<List<WatchedMarket>> = context.dataStore.data.map { prefs ->
        prefs[KEY]?.let { decode(it) } ?: emptyList()
    }

    suspend fun current(): List<WatchedMarket> = watchlist.first()

    /** Group names in watchlist order, for pickers. */
    suspend fun groups(): List<String> = current().map { it.group }.distinct()

    /** User-chosen group display order; groups not listed follow at the end. */
    val groupOrderFlow: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[GROUP_ORDER_KEY]?.let { decodeStrings(it) } ?: emptyList()
    }

    /** Groups hidden from the watchlist widget (still visible in the app). */
    val hiddenGroupsFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[HIDDEN_GROUPS_KEY]?.let { decodeStrings(it).toSet() } ?: emptySet()
    }

    /** Moves a group up (-1) or down (+1) in the display order. */
    suspend fun moveGroup(name: String, direction: Int) {
        val present = current().map { it.group }.distinct()
        val order = orderedGroups(groupOrderFlow.first(), present).toMutableList()
        val from = order.indexOf(name)
        val to = from + direction
        if (from < 0 || to < 0 || to >= order.size) return
        order[from] = order[to].also { order[to] = name }
        context.dataStore.edit { it[GROUP_ORDER_KEY] = encodeStrings(order) }
    }

    suspend fun setGroupHidden(name: String, hidden: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[HIDDEN_GROUPS_KEY]?.let { decodeStrings(it).toMutableSet() } ?: mutableSetOf()
            if (hidden) current.add(name) else current.remove(name)
            prefs[HIDDEN_GROUPS_KEY] = encodeStrings(current.toList())
        }
    }

    /** Swaps an entry with its nearest same-group neighbor above (-1) or below (+1). */
    suspend fun move(key: String, direction: Int) {
        mutate { list ->
            val index = list.indexOfFirst { it.key == key }
            if (index < 0) return@mutate list
            val group = list[index].group
            var other = index + direction
            while (other in list.indices && list[other].group != group) other += direction
            if (other !in list.indices) return@mutate list
            val mutable = list.toMutableList()
            mutable[index] = mutable[other].also { mutable[other] = mutable[index] }
            mutable
        }
    }

    /** Widget theme preference: "system", "light", or "dark". */
    val themeFlow: Flow<String> = context.dataStore.data.map { it[THEME_KEY] ?: "system" }

    suspend fun theme(): String = themeFlow.first()

    suspend fun setTheme(value: String) {
        context.dataStore.edit { it[THEME_KEY] = value }
    }

    /** True while a manual refresh is in flight, so widgets can show a spinner. */
    val refreshingFlow: Flow<Boolean> = context.dataStore.data.map { it[REFRESHING_KEY] ?: false }

    suspend fun setRefreshing(value: Boolean) {
        context.dataStore.edit { it[REFRESHING_KEY] = value }
    }

    /**
     * Fetches the market to validate it, seeds the sparkline from recent bet
     * history, and adds it to the watchlist. Binary markets are tracked
     * directly; multiple-choice markets track their current top answer.
     */
    suspend fun add(slugOrUrl: String) {
        val market = ManifoldApi.fetchMarket(slugOrUrl)
        val topAnswer = market.answers.maxByOrNull { it.probability ?: -1.0 }
        val answer = when {
            market.probability != null -> null
            topAnswer?.probability != null -> topAnswer
            else -> throw IllegalArgumentException(
                "\"${market.question}\" (${market.outcomeType}) isn't supported yet — only YES/NO and multiple-choice markets are."
            )
        }
        val entry = buildEntry(market, answer, groupFor(market.slug))
        mutate { list -> list.filterNot { it.key == entry.key } + entry }
    }

    /** Adds another entry for the same market, tracking a different answer. */
    suspend fun addAnswer(slug: String, answerId: String) {
        val market = ManifoldApi.fetchMarket(slug)
        val answer = market.answers.find { it.id == answerId }
            ?: throw IllegalArgumentException("Answer not found on \"${market.question}\"")
        val entry = buildEntry(market, answer, groupFor(slug))
        mutate { list -> list.filterNot { it.key == entry.key } + entry }
    }

    /** Switches an existing entry to track a different answer, re-seeding history. */
    suspend fun setAnswer(key: String, answerId: String) {
        val existing = current().find { it.key == key } ?: return
        val market = ManifoldApi.fetchMarket(existing.slug)
        val answer = market.answers.find { it.id == answerId }
            ?: throw IllegalArgumentException("Answer not found on \"${market.question}\"")
        val updated = buildEntry(market, answer, existing.group, existing.periodHours)
        mutate { list ->
            list.filterNot { it.key == updated.key && it.key != key }
                .map { if (it.key == key) updated else it }
        }
    }

    suspend fun remove(key: String) {
        mutate { list -> list.filterNot { it.key == key } }
    }

    /** Changes the comparison window (delta + sparkline) for one entry. */
    suspend fun setPeriod(key: String, hours: Int) {
        mutate { list -> list.map { if (it.key == key) it.copy(periodHours = hours) else it } }
    }

    /** Moves an entry to a (possibly new) group. */
    suspend fun setGroup(key: String, group: String) {
        val name = group.trim().ifEmpty { WatchedMarket.DEFAULT_GROUP }
        mutate { list -> list.map { if (it.key == key) it.copy(group = name) else it } }
    }

    /** Answers available on a market, for the answer pickers. */
    suspend fun answersFor(slug: String): List<Answer> = ManifoldApi.fetchMarket(slug).answers

    suspend fun search(term: String, limit: Int = 15): List<Market> = ManifoldApi.searchMarkets(term, limit)

    /** Re-fetches every watched market in parallel, appending to each history. */
    suspend fun refreshAll() = coroutineScope {
        val updated = current()
            .map { watched -> async { refreshOne(watched) } }
            .awaitAll()
        context.dataStore.edit { prefs -> prefs[KEY] = encode(updated) }
    }

    private suspend fun refreshOne(watched: WatchedMarket): WatchedMarket =
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
                    resolutionLabel = resolutionLabel(market, watched.answerId),
                    resolvedSeenMillis = watched.resolvedSeenMillis
                        ?: if (market.isResolved) System.currentTimeMillis() else null,
                    history = (watched.history + ProbPoint(System.currentTimeMillis(), probability))
                        .takeLast(HISTORY_SIZE),
                    lastUpdatedMillis = System.currentTimeMillis(),
                )
            },
            // Keep stale data on transient failures; the widget shows last-updated time.
            onFailure = { watched },
        )

    /** Builds a fully seeded entry for a market (or one answer of it). */
    private suspend fun buildEntry(
        market: Market,
        answer: Answer?,
        group: String,
        periodHours: Int = 24,
    ): WatchedMarket {
        val probability = answer?.probability ?: market.probability
            ?: throw IllegalArgumentException("\"${answer?.text ?: market.question}\" has no probability")
        val now = System.currentTimeMillis()
        return WatchedMarket(
            slug = market.slug,
            question = market.question,
            url = market.url,
            probability = probability,
            answerId = answer?.id,
            answerText = answer?.text,
            isResolved = market.isResolved,
            resolution = market.resolution,
            resolutionLabel = resolutionLabel(market, answer?.id),
            resolvedSeenMillis = if (market.isResolved) now else null,
            history = seedHistory(market.slug, answer?.id) + ProbPoint(now, probability),
            lastUpdatedMillis = now,
            periodHours = periodHours,
            group = group,
        )
    }

    /** New entries for a market inherit the group of its existing entries. */
    private suspend fun groupFor(slug: String): String =
        current().find { it.slug == slug }?.group ?: WatchedMarket.DEFAULT_GROUP

    /**
     * Maps the API's resolution to something displayable. Binary markets
     * resolve to "YES"/"NO"/"MKT"/"CANCEL"; multiple-choice markets resolve
     * to the winning answer's ID, so translate relative to the tracked answer.
     */
    private fun resolutionLabel(market: Market, answerId: String?): String? {
        val resolution = market.resolution ?: return null
        return when {
            resolution == "CANCEL" -> "N/A"
            resolution == "MKT" -> "MKT"
            answerId == null -> resolution
            resolution == answerId -> "Won"
            else -> "Lost"
        }
    }

    /**
     * Builds an initial history from recent bets so the sparkline and delta
     * are meaningful immediately instead of waiting hours for refreshes.
     */
    private suspend fun seedHistory(slug: String, answerId: String?): List<ProbPoint> {
        val bets = runCatching { ManifoldApi.fetchBets(slug) }.getOrDefault(emptyList())
        val points = bets
            .filter { answerId == null || it.answerId == answerId }
            .mapNotNull { bet -> bet.probAfter?.let { ProbPoint(bet.createdTime, it) } }
            .sortedBy { it.t }
        return downsample(points, HISTORY_SIZE - 1)
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

    private fun encodeStrings(list: List<String>): String =
        json.encodeToString(ListSerializer(String.serializer()), list)

    private fun decodeStrings(raw: String): List<String> =
        runCatching { json.decodeFromString(ListSerializer(String.serializer()), raw) }
            .getOrDefault(emptyList())

    companion object {
        const val HISTORY_SIZE = 288
        private val KEY = stringPreferencesKey("markets")
        private val THEME_KEY = stringPreferencesKey("theme")
        private val REFRESHING_KEY = booleanPreferencesKey("refreshing")
        private val GROUP_ORDER_KEY = stringPreferencesKey("group_order")
        private val HIDDEN_GROUPS_KEY = stringPreferencesKey("hidden_groups")
        private val json = Json { ignoreUnknownKeys = true }

        /** Stored order first (existing groups only), then any new groups. */
        fun orderedGroups(stored: List<String>, present: List<String>): List<String> =
            stored.filter { it in present } + present.filterNot { it in stored }
    }
}
