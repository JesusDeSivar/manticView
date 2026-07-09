package dev.jesusdesivar.manticwidget.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.jesusdesivar.manticwidget.MainActivity
import dev.jesusdesivar.manticwidget.data.WatchedMarket
import dev.jesusdesivar.manticwidget.data.WatchlistRepository
import kotlin.math.abs
import kotlin.math.roundToInt

internal val Up = Color(0xFF34C77B)
internal val Down = Color(0xFFE5484D)
internal val Neutral = Color(0xFF9BA1A6)

internal fun trendColor(market: WatchedMarket): Color = when {
    market.delta > 0.0001 -> Up
    market.delta < -0.0001 -> Down
    else -> Neutral
}

internal fun deltaLabel(market: WatchedMarket, withAnswer: Boolean = true): String {
    val trend = when {
        market.isResolved -> "Resolved"
        market.history.size < 2 -> "no history yet"
        else -> {
            val points = market.delta * 100
            val span = spanLabel(market.deltaSpanHours)
            when {
                abs(points) < 0.5 -> "flat · $span"
                points > 0 -> "▲ ${points.roundToInt()} pts · $span"
                else -> "▼ ${abs(points).roundToInt()} pts · $span"
            }
        }
    }
    return if (withAnswer) market.answerText?.let { "$it · $trend" } ?: trend else trend
}

internal fun spanLabel(hours: Long): String =
    if (hours >= 48) "${hours / 24}d" else "${hours}h"

internal fun lastUpdatedLabel(markets: List<WatchedMarket>): String? {
    val newest = markets.maxOfOrNull { it.lastUpdatedMillis } ?: return null
    if (newest == 0L) return null
    return java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
        .format(java.util.Date(newest))
}

internal fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt()
)

/** 4×2 watchlist widget: one compact row per market, TradingView-style. */
class ManticWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = WatchlistRepository(context)
        val markets = repository.current()
        val theme = repository.theme()
        val refreshing = repository.isRefreshing()
        provideContent {
            ManticGlanceTheme(theme) {
                WidgetContent(markets, refreshing)
            }
        }
    }

    @Composable
    private fun WidgetContent(markets: List<WatchedMarket>, refreshing: Boolean) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(12.dp)
        ) {
            Header(lastUpdatedLabel(markets), refreshing)
            Spacer(GlanceModifier.height(8.dp))
            if (markets.isEmpty()) {
                Text(
                    text = "No markets yet — tap to add some.",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                    modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()),
                )
            } else {
                markets.forEach { market ->
                    MarketRow(market)
                    Spacer(GlanceModifier.height(6.dp))
                }
            }
        }
    }

    @Composable
    private fun Header(lastUpdated: String?, refreshing: Boolean) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Mantic View",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()),
            )
            if (lastUpdated != null) {
                Text(
                    text = "  · $lastUpdated",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                )
            }
            Spacer(GlanceModifier.defaultWeight())
            RefreshButton(refreshing)
        }
    }

    @Composable
    private fun MarketRow(market: WatchedMarket) {
        val color = trendColor(market)
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(
                    actionStartActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(market.url))
                    )
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = market.question,
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp),
                )
                Text(
                    text = deltaLabel(market),
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(color), fontSize = 10.sp),
                )
            }
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = market.resolution ?: "${(market.probability * 100).roundToInt()}%",
                style = TextStyle(
                    color = ColorProvider(color),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

/** Shared refresh affordance: ↻ normally, a spinner while refreshing. */
@Composable
internal fun RefreshButton(refreshing: Boolean) {
    if (refreshing) {
        CircularProgressIndicator(
            modifier = GlanceModifier.size(16.dp),
            color = GlanceTheme.colors.primary,
        )
    } else {
        Text(
            text = "↻",
            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 16.sp),
            modifier = GlanceModifier.clickable(actionRunCallback<RefreshAction>()),
        )
    }
}

/**
 * Manual refresh from any widget: flip the spinner on, re-fetch everything,
 * then re-render both widget types.
 */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val repository = WatchlistRepository(context)
        repository.setRefreshing(true)
        updateAllWidgets(context)
        try {
            repository.refreshAll()
        } finally {
            repository.setRefreshing(false)
            updateAllWidgets(context)
        }
    }
}

internal suspend fun updateAllWidgets(context: Context) {
    ManticWidget().updateAll(context)
    SingleMarketWidget().updateAll(context)
}
