package com.musichub.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musichub.data.model.Track
import com.musichub.ui.GlassIconButton
import com.musichub.ui.Hint
import com.musichub.ui.LocalEngine
import com.musichub.ui.LocalGraph
import com.musichub.ui.PrimaryPill
import com.musichub.ui.TrackRow

/** «Похожие на этот трек»: радио Яндекса по треку + похожие с YouTube, одним списком. */
@Composable
fun SimilarScreen(seed: Track, onBack: () -> Unit) {
    val graph = LocalGraph.current
    val engine = LocalEngine.current
    var tracks by remember(seed.key) { mutableStateOf<List<Track>?>(null) }

    LaunchedEffect(seed.key) {
        tracks = runCatching { graph.search.similar(seed) }.getOrNull() ?: emptyList()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Назад", onClick = onBack)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Похожие", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${seed.artists} — ${seed.title}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        val list = tracks
        when {
            list == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Ищу похожие…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            list.isEmpty() -> Hint("Похожие не нашлись — попробуйте позже")
            else -> {
                PrimaryPill("Слушать всё", Icons.Rounded.PlayArrow, onClick = { engine.playTracks(list) })
                Spacer(Modifier.height(12.dp))
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    itemsIndexed(list, key = { _, t -> t.key }) { i, t ->
                        TrackRow(t, index = i, onPlay = { engine.playTracks(list, i) })
                    }
                }
            }
        }
    }
}
