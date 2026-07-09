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
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
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

private val Up = Color(0xFF34C77B)
private val Down = Color(0xFFE5484D)
private val Neutral = Color(0xFF9BA1A6)

class ManticWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val markets = WatchlistRepository(context).current()
        provideContent {
            GlanceTheme {
                WidgetContent(markets)
            }
        }
    }

    @Composable
    private fun WidgetContent(markets: List<WatchedMarket>) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(12.dp)
        ) {
            Header()
            Spacer(GlanceModifier.height(8.dp))
            if (markets.isEmpty()) {
                EmptyState()
            } else {
                markets.forEach { market ->
                    MarketRow(market)
                    Spacer(GlanceModifier.height(6.dp))
                }
            }
        }
    }

    @Composable
    private fun Header() {
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
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(actionStartActivity<MainActivity>()),
            )
            Text(
                text = "↻",
                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 16.sp),
                modifier = GlanceModifier.clickable(actionRunCallback<RefreshAction>()),
            )
        }
    }

    @Composable
    private fun EmptyState() {
        Text(
            text = "No markets yet — tap to add some.",
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()),
        )
    }

    @Composable
    private fun MarketRow(market: WatchedMarket) {
        val trendColor = when {
            market.delta > 0.0001 -> Up
            market.delta < -0.0001 -> Down
            else -> Neutral
        }
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
                    style = TextStyle(color = ColorProvider(trendColor), fontSize = 10.sp),
                )
            }
            Spacer(GlanceModifier.width(8.dp))
            Image(
                provider = ImageProvider(
                    SparklineRenderer.render(market.history.map { it.p }, color = trendColor.toArgb())
                ),
                contentDescription = "Probability trend",
                modifier = GlanceModifier.size(width = 52.dp, height = 16.dp),
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = market.resolution ?: "${(market.probability * 100).roundToInt()}%",
                style = TextStyle(
                    color = ColorProvider(trendColor),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }

    private fun deltaLabel(market: WatchedMarket): String {
        val trend = when {
            market.isResolved -> "Resolved"
            else -> {
                val points = market.delta * 100
                val span = market.deltaSpanHours.let { if (it >= 22) "24h" else "${it}h" }
                when {
                    market.history.size < 2 -> "no history yet"
                    abs(points) < 0.5 -> "flat · $span"
                    points > 0 -> "▲ ${points.roundToInt()} pts · $span"
                    else -> "▼ ${abs(points).roundToInt()} pts · $span"
                }
            }
        }
        return market.answerText?.let { "$it · $trend" } ?: trend
    }
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt()
)

/** Widget refresh button: re-fetch everything, then re-render all widgets. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WatchlistRepository(context).refreshAll()
        ManticWidget().updateAll(context)
    }
}
