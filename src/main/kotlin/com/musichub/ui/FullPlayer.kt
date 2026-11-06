package com.musichub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musichub.playback.RepeatMode

@Composable
fun FullPlayer(onBack: () -> Unit) {
    val graph = LocalGraph.current
    val engine = LocalEngine.current
    val state by engine.state.collectAsState()
    val favorites by graph.favorites.keys.collectAsState()
    val track = state.track
    if (track == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val clickBlocker = remember { MutableInteractionSource() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
            .clickable(interactionSource = clickBlocker, indication = null) {},
    ) {
        val coverSize = minOf(maxWidth * 0.38f, maxHeight * 0.65f, 460.dp)
        Column(Modifier.fillMaxSize().padding(28.dp)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.align(Alignment.CenterStart)) {
                    GlassIconButton(
                        Icons.Rounded.KeyboardArrowDown,
                        "Свернуть плеер",
                        onClick = onBack,
                        size = 44.dp,
                        iconSize = 30.dp,
                    )
                }
                Text("Сейчас играет", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = muted)
            }
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Cover(track.coverUrl, modifier = Modifier.size(coverSize), corner = 24.dp)
                Column(Modifier.widthIn(max = 500.dp).padding(horizontal = 32.dp)) {
                    Text(
                        track.title,
                        fontSize = 32.sp,
                        lineHeight = 38.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        track.artists,
                        fontSize = 18.sp,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SourceBadge(track.source)
                        state.radioTitle?.let { Text(it, fontSize = 12.sp, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    Spacer(Modifier.height(28.dp))
                    ProgressRow(state.positionMs, state.durationMs, onSeek = engine::seekTo)
                    Row(
                        Modifier.fillMaxWidth().padding(top = 18.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlassIconButton(
                            Icons.Rounded.Shuffle,
                            "Перемешать",
                            onClick = engine::toggleShuffle,
                            tint = if (state.shuffle) accent else muted,
                            enabled = !state.waveMode,
                        )
                        GlassIconButton(Icons.Rounded.SkipPrevious, "Предыдущий", onClick = engine::previous, size = 48.dp, iconSize = 32.dp)
                        Box(Modifier.size(68.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface), contentAlignment = Alignment.Center) {
                            if (state.isBuffering && !state.isPlaying) {
                                CircularProgressIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.background, strokeWidth = 3.dp)
                            } else {
                                GlassIconButton(
                                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    if (state.isPlaying) "Пауза" else "Играть",
                                    onClick = engine::togglePlay,
                                    size = 68.dp,
                                    iconSize = 38.dp,
                                    tint = MaterialTheme.colorScheme.background,
                                )
                            }
                        }
                        GlassIconButton(
                            Icons.Rounded.SkipNext,
                            "Следующий",
                            onClick = engine::next,
                            size = 48.dp,
                            iconSize = 32.dp,
                            enabled = state.hasNext || state.repeat != RepeatMode.OFF,
                        )
                        GlassIconButton(
                            if (state.repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            "Повтор",
                            onClick = engine::cycleRepeat,
                            tint = if (state.repeat != RepeatMode.OFF) accent else muted,
                            enabled = !state.waveMode,
                        )
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.Center) {
                        val liked = track.key in favorites
                        GlassIconButton(
                            if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            "Любимое",
                            onClick = { engine.toggleLike() },
                            tint = if (liked) accent else muted,
                            size = 44.dp,
                            iconSize = 25.dp,
                        )
                        if (state.waveMode) {
                            Spacer(Modifier.width(12.dp))
                            GlassIconButton(Icons.Rounded.ThumbDown, "Не нравится", onClick = engine::dislike, tint = muted, size = 44.dp)
                        }
                        Spacer(Modifier.width(12.dp))
                        GlassIconButton(Icons.Rounded.Radio, "Радио по треку", onClick = { engine.startRadio(track) }, tint = muted, size = 44.dp)
                    }
                }
            }
        }
    }
}
