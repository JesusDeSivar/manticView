package dev.jesusdesivar.manticwidget.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import dev.jesusdesivar.manticwidget.ManticAppTheme
import dev.jesusdesivar.manticwidget.data.WatchedMarket
import dev.jesusdesivar.manticwidget.data.WatchlistRepository
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Shown by the launcher when a single-market widget is placed: pick which
 * group ("watchlist") the widget follows and which market it starts on.
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
            ManticAppTheme(repository) {
                val markets by repository.watchlist.collectAsState(initial = emptyList())
                // null = follow all markets
                var selectedGroup by remember { mutableStateOf<String?>(null) }
                val groups = markets.map { it.group }.distinct()
                val pool = markets.filter { selectedGroup == null || it.group == selectedGroup }

                Scaffold(
                    topBar = { TopAppBar(title = { Text("Choose what to follow") }) },
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                        if (markets.isEmpty()) {
                            Text(
                                "Your watchlist is empty. Open Mantic View and add a market first, then place this widget again.",
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        } else {
                            if (groups.size > 1) {
                                Text(
                                    "Watchlist",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                                Row {
                                    (listOf<String?>(null) + groups).forEach { group ->
                                        TextButton(onClick = { selectedGroup = group }) {
                                            Text(
                                                group ?: "All",
                                                fontWeight = if (selectedGroup == group) FontWeight.Bold
                                                else FontWeight.Normal,
                                            )
                                        }
                                    }
                                }
                            }
                            Text(
                                "Start on",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        LazyColumn {
                            items(pool, key = { it.key }) { market ->
                                ListItem(
                                    headlineContent = { Text(market.question) },
                                    supportingContent = { Text(market.answerText ?: market.slug) },
                                    trailingContent = {
                                        Text("${(market.probability * 100).roundToInt()}%")
                                    },
                                    modifier = Modifier.clickable {
                                        select(appWidgetId, selectedGroup, market)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun select(appWidgetId: Int, group: String?, market: WatchedMarket) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@SingleMarketConfigActivity)
                .getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@SingleMarketConfigActivity, glanceId) { prefs ->
                prefs[SingleMarketWidget.ENTRY_KEY] = market.key
                if (group == null) prefs.remove(SingleMarketWidget.GROUP_KEY)
                else prefs[SingleMarketWidget.GROUP_KEY] = group
            }
            SingleMarketWidget().update(this@SingleMarketConfigActivity, glanceId)
            setResult(RESULT_OK, resultIntent(appWidgetId))
            finish()
        }
    }

    private fun resultIntent(appWidgetId: Int): Intent =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}
