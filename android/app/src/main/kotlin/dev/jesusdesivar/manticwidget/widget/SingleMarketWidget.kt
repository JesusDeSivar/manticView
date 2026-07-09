package dev.jesusdesivar.manticwidget.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.jesusdesivar.manticwidget.MainActivity
import dev.jesusdesivar.manticwidget.data.WatchedMarket
import dev.jesusdesivar.manticwidget.data.WatchlistRepository
import kotlin.math.roundToInt

/**
 * 2×2 widget focused on a single market, with a large sparkline. Which
 * market it shows is chosen in [SingleMarketConfigActivity] when the widget
 * is placed, and stored in per-widget Glance state.
 */
class SingleMarketWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = WatchlistRepository(context)
        // Snapshot for first paint; the flows below keep the composition live.
        val initialMarkets = repository.current()
        val initialTheme = repository.theme()
        provideContent {
            // Collected inside the composition so DataStore changes recompose
            // the widget instead of leaving a stale session on screen.
            val markets by repository.watchlist.collectAsState(initial = initialMarkets)
            val theme by repository.themeFlow.collectAsState(initial = initialTheme)
            val refreshing by repository.refreshingFlow.collectAsState(initial = false)
            val slug = currentState<Preferences>()[SLUG_KEY]
            val index = markets.indexOfFirst { it.slug == slug }
            val market = markets.getOrNull(index)
            ManticGlanceTheme(theme) {
                if (market == null) {
                    Unconfigured()
                } else {
                    MarketPanel(market, refreshing, position = "${index + 1}/${markets.size}")
                }
            }
        }
    }

    @Composable
    private fun Unconfigured() {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "No market selected — remove and re-add this widget, or add markets in the app first.",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
        }
    }

    @Composable
    private fun MarketPanel(market: WatchedMarket, refreshing: Boolean, position: String) {
        val color = displayColor(market)
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(12.dp)
                .clickable(
                    actionStartActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(market.url))
                    )
                ),
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = market.question,
                    maxLines = 2,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp),
                    modifier = GlanceModifier.defaultWeight(),
                )
                RefreshButton(refreshing)
            }
            Spacer(GlanceModifier.height(4.dp))
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayValue(market),
                    style = TextStyle(
                        color = ColorProvider(color),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    text = deltaLabel(market, withAnswer = false),
                    style = TextStyle(color = ColorProvider(color), fontSize = 11.sp),
                )
            }
            market.answerText?.let {
                Text(
                    text = it,
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
                )
            }
            Spacer(GlanceModifier.height(6.dp))
            Image(
                provider = ImageProvider(
                    SparklineRenderer.render(
                        market.sparkPoints().map { it.p },
                        widthPx = 480,
                        heightPx = 140,
                        color = color.toArgb(),
                    )
                ),
                contentDescription = "Probability history",
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            )
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CycleButton("‹", direction = -1)
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    text = listOfNotNull(
                        lastUpdatedLabel(listOf(market)),
                        WatchedMarket.periodLabel(market.periodHours),
                        position,
                    ).joinToString(" · "),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 9.sp),
                )
                Spacer(GlanceModifier.defaultWeight())
                CycleButton("›", direction = 1)
            }
        }
    }

    @Composable
    private fun CycleButton(label: String, direction: Int) {
        Box(
            modifier = GlanceModifier.clickable(
                actionRunCallback<CycleMarketAction>(
                    actionParametersOf(CycleMarketAction.DIRECTION to direction)
                )
            )
        ) {
            Text(
                text = label,
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = GlanceModifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }

    companion object {
        val SLUG_KEY = stringPreferencesKey("market_slug")
    }
}

class SingleMarketWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SingleMarketWidget()
}

/** ‹ › on the widget: step to the previous/next market in the watchlist. */
class CycleMarketAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val direction = parameters[DIRECTION] ?: 1
        val markets = WatchlistRepository(context).current()
        if (markets.isEmpty()) return
        updateAppWidgetState(context, glanceId) { prefs ->
            val current = prefs[SingleMarketWidget.SLUG_KEY]
            val index = markets.indexOfFirst { it.slug == current }
            val next = if (index < 0) 0 else (index + direction + markets.size) % markets.size
            prefs[SingleMarketWidget.SLUG_KEY] = markets[next].slug
        }
        SingleMarketWidget().update(context, glanceId)
    }

    companion object {
        val DIRECTION = ActionParameters.Key<Int>("direction")
    }
}
