package dev.jesusdesivar.manticwidget.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class Market(
    val id: String,
    val question: String,
    val slug: String,
    val url: String,
    val probability: Double? = null,
    val outcomeType: String = "BINARY",
    val isResolved: Boolean = false,
    val resolution: String? = null,
    val closeTime: Long? = null,
    val volume24Hours: Double = 0.0,
    val answers: List<Answer> = emptyList(),
)

@Serializable
data class Answer(
    val id: String,
    val text: String,
    val probability: Double? = null,
)

@Serializable
data class Bet(
    val createdTime: Long,
    val probAfter: Double? = null,
    val answerId: String? = null,
)

/**
 * Thin client for the public Manifold API (https://docs.manifold.markets/api).
 * No API key required; only public market data is read.
 */
object ManifoldApi {
    private const val BASE_URL = "https://api.manifold.markets/v0"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /** Accepts a bare slug or a full manifold.markets market URL. */
    fun parseSlug(input: String): String {
        val trimmed = input.trim()
        val match = Regex("""manifold\.markets/[^/]+/([^/?#\s]+)""").find(trimmed)
        return match?.groupValues?.get(1)
            ?: trimmed.substringBefore('?').substringBefore('#').trim('/')
    }

    suspend fun fetchMarket(slugOrUrl: String): Market = withContext(Dispatchers.IO) {
        val slug = parseSlug(slugOrUrl)
        get("$BASE_URL/slug/$slug").let { json.decodeFromString(Market.serializer(), it) }
    }

    /** Recent bets for a market, newest first. Used to seed sparkline history. */
    suspend fun fetchBets(slug: String, limit: Int = 100): List<Bet> = withContext(Dispatchers.IO) {
        get("$BASE_URL/bets?contractSlug=$slug&limit=$limit").let {
            json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Bet.serializer()), it)
        }
    }

    suspend fun searchMarkets(term: String, limit: Int = 10): List<Market> = withContext(Dispatchers.IO) {
        val encoded = java.net.URLEncoder.encode(term, "UTF-8")
        get("$BASE_URL/search-markets?term=$encoded&limit=$limit&contractType=BINARY").let {
            json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Market.serializer()), it)
        }
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) throw IOException("Market not found")
            if (!response.isSuccessful) throw IOException("Manifold API error: HTTP ${response.code}")
            return response.body?.string() ?: throw IOException("Empty response")
        }
    }
}
