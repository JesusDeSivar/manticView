package dev.jesusdesivar.manticwidget.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import dev.jesusdesivar.manticwidget.data.WatchlistRepository
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Shown by the launcher when a single-market widget is placed: pick which
 * watched market this widget instance should display.
 */
class SingleMarketConfigActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setResult(RESULT_CANCELED, resultIntent(appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val repository = WatchlistRepository(applicationContext)
        setContent {
            MaterialTheme {
                val markets by repository.watchlist.collectAsState(initial = emptyList())
                Scaffold(
                    topBar = { TopAppBar(title = { Text("Choose a market") }) },
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                        if (markets.isEmpty()) {
                            Text(
                                "Your watchlist is empty. Open Mantic View and add a market first, then place this widget again.",
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                        LazyColumn {
                            items(markets, key = { it.slug }) { market ->
                                ListItem(
                                    headlineContent = { Text(market.question) },
                                    supportingContent = { Text(market.answerText ?: market.slug) },
                                    trailingContent = {
                                        Text("${(market.probability * 100).roundToInt()}%")
                                    },
                                    modifier = Modifier.clickable { select(appWidgetId, market.slug) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun select(appWidgetId: Int, slug: String) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@SingleMarketConfigActivity)
                .getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@SingleMarketConfigActivity, glanceId) { prefs ->
                prefs[SingleMarketWidget.SLUG_KEY] = slug
            }
            SingleMarketWidget().update(this@SingleMarketConfigActivity, glanceId)
            setResult(RESULT_OK, resultIntent(appWidgetId))
            finish()
        }
    }

    private fun resultIntent(appWidgetId: Int): Intent =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}
