package dev.jesusdesivar.manticwidget

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import dev.jesusdesivar.manticwidget.data.WatchlistRepository
import dev.jesusdesivar.manticwidget.widget.ManticWidget
import dev.jesusdesivar.manticwidget.work.RefreshWorker
import kotlinx.coroutines.launch
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

    fun runAndSyncWidget(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            try {
                block()
                ManticWidget().updateAll(context)
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
                .padding(16.dp)
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Market slug or URL") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = !busy && input.isNotBlank(),
                    onClick = {
                        val value = input
                        input = ""
                        runAndSyncWidget { repository.add(value) }
                    },
                ) { Text("Add") }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    enabled = !busy && markets.isNotEmpty(),
                    onClick = { runAndSyncWidget { repository.refreshAll() } },
                ) { Text("Refresh all") }
            }

            LazyColumn {
                items(markets, key = { it.slug }) { market ->
                    ListItem(
                        headlineContent = { Text(market.question) },
                        supportingContent = {
                            Text(
                                market.resolution?.let { "Resolved: $it" }
                                    ?: market.answerText
                                    ?: market.slug
                            )
                        },
                        trailingContent = {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(
                                    text = "${(market.probability * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                TextButton(
                                    onClick = { runAndSyncWidget { repository.remove(market.slug) } },
                                ) { Text("✕") }
                            }
                        },
                    )
                }
            }
        }
    }
}
