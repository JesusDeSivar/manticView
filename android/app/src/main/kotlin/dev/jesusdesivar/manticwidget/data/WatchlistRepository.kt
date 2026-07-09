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

/** One tracked market plus a rolling probability history for sparklines. */
@Serializable
data class WatchedMarket(
    val slug: String,
    val question: String,
    val url: String,
    val probability: Double,
    val isResolved: Boolean = false,
    val resolution: String? = null,
    /** Most recent last; capped at [WatchlistRepository.HISTORY_SIZE] points. */
    val history: List<Double> = emptyList(),
    val lastUpdatedMillis: Long = 0L,
) {
    val delta: Double
        get() = if (history.size >= 2) history.last() - history.first() else 0.0
}

private val Context.dataStore by preferencesDataStore(name = "watchlist")

class WatchlistRepository(private val context: Context) {

    val watchlist: Flow<List<WatchedMarket>> = context.dataStore.data.map { prefs ->
        prefs[KEY]?.let { decode(it) } ?: emptyList()
    }

    suspend fun current(): List<WatchedMarket> = watchlist.first()

    /** Fetches the market to validate it, then adds it to the watchlist. */
    suspend fun add(slugOrUrl: String) {
        val market = ManifoldApi.fetchMarket(slugOrUrl)
        val probability = market.probability
            ?: throw IllegalArgumentException("\"${market.question}\" is not a YES/NO market")
        val entry = WatchedMarket(
            slug = market.slug,
            question = market.question,
            url = market.url,
            probability = probability,
            isResolved = market.isResolved,
            resolution = market.resolution,
            history = listOf(probability),
            lastUpdatedMillis = System.currentTimeMillis(),
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
                    val probability = market.probability ?: watched.probability
                    watched.copy(
                        question = market.question,
                        probability = probability,
                        isResolved = market.isResolved,
                        resolution = market.resolution,
                        history = (watched.history + probability).takeLast(HISTORY_SIZE),
                        lastUpdatedMillis = System.currentTimeMillis(),
                    )
                },
                // Keep stale data on transient failures; the widget shows last-updated time.
                onFailure = { watched },
            )
        }
        context.dataStore.edit { prefs -> prefs[KEY] = encode(updated) }
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
