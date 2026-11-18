package com.musichub.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musichub.data.model.Track
import com.musichub.ui.GlassButton
import com.musichub.ui.LocalEngine
import com.musichub.ui.LocalGraph
import com.musichub.ui.TrackRow
import com.musichub.util.formatTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Все прослушивания, в том числе повторы одного трека; порции по 100 без общего лимита. */
@Composable
fun HistoryScreen() {
    val graph = LocalGraph.current
    val engine = LocalEngine.current
    val events by graph.stats.events.collectAsState()
    var limit by remember { mutableIntStateOf(100) }
    val ordered = remember(events) { events.sortedByDescending { it.startedAt } }
    val shown = remember(ordered, limit) {
        ordered.take(limit).mapNotNull { event -> runCatching { event to Track.fromJson(event.trackJson) }.getOrNull() }
    }
    val tracks = remember(shown) { shown.map { it.second } }
    val formatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.forLanguageTag("ru")) }

    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp)) {
        Text("История прослушивания", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("${events.size} прослушиваний · последние сверху", color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
        LazyColumn {
            itemsIndexed(shown, key = { index, _ -> index }) { index, (event, track) ->
                Row(Modifier.fillMaxWidth()) {
                    Text(Instant.ofEpochMilli(event.startedAt).atZone(ZoneId.systemDefault()).format(formatter),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 18.dp).weight(0.22f))
                    Column(Modifier.weight(0.78f)) {
                        TrackRow(track, index = null, onPlay = { engine.playTracks(tracks, index) })
                        Text("Слушали ${formatTime(event.playedMs)}${if (event.fromWave) " · Моя волна" else ""}",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 72.dp, bottom = 5.dp))
                    }
                }
            }
            if (ordered.size > limit) item {
                Row {
                    GlassButton("Показать ещё 100", onClick = { limit += 100 })
                    GlassButton("Показать все", onClick = { limit = Int.MAX_VALUE })
                }
                Spacer(Modifier.padding(bottom = 12.dp))
            }
        }
    }
}
