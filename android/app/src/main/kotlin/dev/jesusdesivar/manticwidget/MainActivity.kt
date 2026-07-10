package dev.jesusdesivar.manticwidget

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.jesusdesivar.manticwidget.data.Answer
import dev.jesusdesivar.manticwidget.data.Market
import dev.jesusdesivar.manticwidget.data.WatchedMarket
import dev.jesusdesivar.manticwidget.data.WatchlistRepository
import dev.jesusdesivar.manticwidget.widget.displayValue
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
        val repository = WatchlistRepository(applicationContext)
        setContent {
            ManticAppTheme(repository) {
                WatchlistScreen(repository)
            }
        }
    }
}

/** In-app Material theme following the same preference the widgets use. */
@Composable
fun ManticAppTheme(repository: WatchlistRepository, content: @Composable () -> Unit) {
    val theme by repository.themeFlow.collectAsState(initial = "system")
    val dark = when (theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}

/** Which entry the answer dialog is acting on; a null key means "add as new". */
private data class AnswerPickerRequest(val slug: String, val key: String?, val answers: List<Answer>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(repository: WatchlistRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val markets by repository.watchlist.collectAsState(initial = emptyList())
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<Market>?>(null) }
    var answerPicker by remember { mutableStateOf<AnswerPickerRequest?>(null) }
    var groupPicker by remember { mutableStateOf<String?>(null) }
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
                    text = "Theme",
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

                val active = markets.filterNot { it.isResolved }
                val resolved = markets.filter { it.isResolved }
                val groups = active.groupBy { it.group }
                val showGroupHeaders =
                    groups.size > 1 || groups.keys.singleOrNull()?.let { it != WatchedMarket.DEFAULT_GROUP } == true

                groups.forEach { (groupName, groupMarkets) ->
                    if (showGroupHeaders) {
                        item(key = "group-$groupName") {
                            Text(
                                groupName,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                            )
                        }
                    }
                    items(groupMarkets, key = { it.key }) { market ->
                        ListItem(
                            headlineContent = { Text(market.question) },
                            supportingContent = {
                                Column {
                                    Text(market.answerText ?: market.slug)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        PeriodChip(market, enabled = !busy) { hours ->
                                            run { repository.setPeriod(market.key, hours) }
                                        }
                                        MarketMenu(
                                            market = market,
                                            enabled = !busy,
                                            onSwitchAnswer = {
                                                run(syncWidget = false) {
                                                    answerPicker = AnswerPickerRequest(
                                                        market.slug, market.key, repository.answersFor(market.slug)
                                                    )
                                                }
                                            },
                                            onAddAnswer = {
                                                run(syncWidget = false) {
                                                    answerPicker = AnswerPickerRequest(
                                                        market.slug, null, repository.answersFor(market.slug)
                                                    )
                                                }
                                            },
                                            onSetGroup = { groupPicker = market.key },
                                        )
                                    }
                                }
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = displayValue(market),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    TextButton(
                                        enabled = !busy,
                                        onClick = { run { repository.remove(market.key) } },
                                    ) { Text("✕") }
                                }
                            },
                        )
                    }
                }

                if (resolved.isNotEmpty()) {
                    item(key = "resolved-header") {
                        Text(
                            "Resolved",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(resolved, key = { "resolved-${it.key}" }) { market ->
                        val dim = MaterialTheme.colorScheme.onSurfaceVariant
                        ListItem(
                            headlineContent = { Text(market.question, color = dim) },
                            supportingContent = {
                                Text(
                                    (market.answerText ?: market.slug) +
                                        if (market.isArchived()) "" else " · still on widget",
                                    color = dim,
                                )
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = displayValue(market),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = dim,
                                    )
                                    TextButton(
                                        enabled = !busy,
                                        onClick = { run { repository.remove(market.key) } },
                                    ) { Text("✕") }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    answerPicker?.let { request ->
        AlertDialog(
            onDismissRequest = { answerPicker = null },
            title = { Text(if (request.key == null) "Add which answer?" else "Track which answer?") },
            text = {
                Column {
                    request.answers.forEach { answer ->
                        TextButton(
                            enabled = !busy,
                            onClick = {
                                answerPicker = null
                                run {
                                    if (request.key == null) repository.addAnswer(request.slug, answer.id)
                                    else repository.setAnswer(request.key, answer.id)
                                }
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

    groupPicker?.let { key ->
        var newGroup by remember { mutableStateOf("") }
        val existing = markets.map { it.group }.distinct()
        AlertDialog(
            onDismissRequest = { groupPicker = null },
            title = { Text("Move to group") },
            text = {
                Column {
                    existing.forEach { name ->
                        TextButton(
                            enabled = !busy,
                            onClick = {
                                groupPicker = null
                                run { repository.setGroup(key, name) }
                            },
                        ) { Text(name) }
                    }
                    OutlinedTextField(
                        value = newGroup,
                        onValueChange = { newGroup = it },
                        label = { Text("New group name") },
                        singleLine = true,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && newGroup.isNotBlank(),
                    onClick = {
                        val name = newGroup
                        groupPicker = null
                        run { repository.setGroup(key, name) }
                    },
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { groupPicker = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun MarketMenu(
    market: WatchedMarket,
    enabled: Boolean,
    onSwitchAnswer: () -> Unit,
    onAddAnswer: () -> Unit,
    onSetGroup: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(enabled = enabled, onClick = { expanded = true }) { Text("⋮") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (market.answerId != null) {
                DropdownMenuItem(
                    text = { Text("Switch answer…") },
                    onClick = { expanded = false; onSwitchAnswer() },
                )
                DropdownMenuItem(
                    text = { Text("Add another answer…") },
                    onClick = { expanded = false; onAddAnswer() },
                )
            }
            DropdownMenuItem(
                text = { Text("Move to group…") },
                onClick = { expanded = false; onSetGroup() },
            )
        }
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
