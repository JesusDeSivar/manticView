package dev.jesusdesivar.manticwidget

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import dev.jesusdesivar.manticwidget.data.Answer
import dev.jesusdesivar.manticwidget.data.Market
import dev.jesusdesivar.manticwidget.data.WatchedMarket
import dev.jesusdesivar.manticwidget.data.WatchlistRepository
import dev.jesusdesivar.manticwidget.widget.updateAllWidgets
import dev.jesusdesivar.manticwidget.work.RefreshWorker
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RefreshWorker.schedule(this)
        setContent {
            MaterialTheme {
                WatchlistScreen(WatchlistRepository(applicationContext))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(repository: WatchlistRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val markets by repository.watchlist.collectAsState(initial = emptyList())
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<Market>?>(null) }
    var answerPicker by remember { mutableStateOf<Pair<String, List<Answer>>?>(null) }
    val theme by repository.themeFlow.collectAsState(initial = "system")

    fun run(syncWidget: Boolean = true, block: suspend () -> Unit) {
        scope.launch {
            busy = true
            try {
                block()
                if (syncWidget) updateAllWidgets(context)
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "Something went wrong", Toast.LENGTH_LONG).show()
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Mantic View") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Market slug, URL, or search words") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.padding(top = 8.dp)) {
                Button(
                    enabled = !busy && input.isNotBlank(),
                    onClick = {
                        val value = input
                        run { repository.add(value); input = ""; searchResults = null }
                    },
                ) { Text("Add") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    enabled = !busy && input.isNotBlank(),
                    onClick = {
                        val term = input
                        run(syncWidget = false) { searchResults = repository.search(term) }
                    },
                ) { Text("Search") }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = lastUpdatedLabel(markets),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    enabled = !busy && markets.isNotEmpty(),
                    onClick = { run { repository.refreshAll() } },
                ) { Text("Refresh all") }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Widget theme",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                listOf("system", "light", "dark").forEach { option ->
                    TextButton(
                        enabled = !busy,
                        onClick = { run { repository.setTheme(option) } },
                    ) {
                        Text(
                            option.replaceFirstChar { it.uppercase() },
                            fontWeight = if (theme == option) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }

            LazyColumn {
                val results = searchResults
                if (results != null) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Search results",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { searchResults = null }) { Text("Clear") }
                        }
                    }
                    if (results.isEmpty()) {
                        item { Text("No markets found.", style = MaterialTheme.typography.bodySmall) }
                    }
                    items(results, key = { "search-${it.id}" }) { market ->
                        ListItem(
                            headlineContent = { Text(market.question) },
                            supportingContent = {
                                Text(
                                    market.probability?.let { "${(it * 100).roundToInt()}%" }
                                        ?: market.outcomeType.lowercase().replace('_', ' ')
                                )
                            },
                            trailingContent = {
                                TextButton(
                                    enabled = !busy,
                                    onClick = {
                                        run { repository.add(market.slug); searchResults = null; input = "" }
                                    },
                                ) { Text("Add") }
                            },
                        )
                    }
                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                }

                items(markets, key = { it.slug }) { market ->
                    ListItem(
                        headlineContent = { Text(market.question) },
                        supportingContent = {
                            Column {
                                Text(
                                    market.resolution?.let { "Resolved: $it" }
                                        ?: market.answerText
                                        ?: market.slug
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    PeriodChip(market, enabled = !busy) { hours ->
                                        run { repository.setPeriod(market.slug, hours) }
                                    }
                                    if (market.answerId != null) {
                                        TextButton(
                                            enabled = !busy,
                                            onClick = {
                                                run(syncWidget = false) {
                                                    answerPicker = market.slug to repository.answersFor(market.slug)
                                                }
                                            },
                                        ) { Text("Answer…") }
                                    }
                                }
                            }
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${(market.probability * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                TextButton(
                                    enabled = !busy,
                                    onClick = { run { repository.remove(market.slug) } },
                                ) { Text("✕") }
                            }
                        },
                    )
                }
            }
        }
    }

    answerPicker?.let { (slug, answers) ->
        AlertDialog(
            onDismissRequest = { answerPicker = null },
            title = { Text("Track which answer?") },
            text = {
                Column {
                    answers.forEach { answer ->
                        TextButton(
                            enabled = !busy,
                            onClick = {
                                answerPicker = null
                                run { repository.setAnswer(slug, answer.id) }
                            },
                        ) {
                            Text(
                                "${answer.text} — ${answer.probability?.let { "${(it * 100).roundToInt()}%" } ?: "?"}"
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { answerPicker = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PeriodChip(market: WatchedMarket, enabled: Boolean, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(enabled = enabled, onClick = { expanded = true }) {
            Text("Δ ${WatchedMarket.periodLabel(market.periodHours)} ▾")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            WatchedMarket.PERIOD_OPTIONS.forEach { (hours, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        onSelect(hours)
                    },
                )
            }
        }
    }
}

private fun lastUpdatedLabel(markets: List<WatchedMarket>): String {
    val newest = markets.maxOfOrNull { it.lastUpdatedMillis } ?: 0L
    if (newest == 0L) return "Not updated yet"
    return "Last updated: " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(newest))
}
