package com.musichub.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musichub.ui.LocalEngine
import com.musichub.ui.LocalGraph
import com.musichub.ui.PrimaryPill
import com.musichub.ui.SectionTitle
import com.musichub.ui.TrackRow
import com.musichub.ui.glassFill
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private class Mood(val value: String, val label: String, val icon: ImageVector, val from: Color, val to: Color)

/** Настроения и цвета — те же, что у Моей волны в Android-версии. */
private val moods = listOf(
    Mood("all", "Любое", Icons.Rounded.AllInclusive, Color(0xFF7B4DFF), Color(0xFFE040FB)),
    Mood("active", "Бодрое", Icons.Rounded.Bolt, Color(0xFFFF6D00), Color(0xFFFFC400)),
    Mood("fun", "Весёлое", Icons.Rounded.WbSunny, Color(0xFFFF4081), Color(0xFFFFD740)),
    Mood("calm", "Спокойное", Icons.Rounded.Spa, Color(0xFF00BFA5), Color(0xFF448AFF)),
    Mood("sad", "Грустное", Icons.Rounded.WaterDrop, Color(0xFF3D5AFE), Color(0xFF7C4DFF)),
)

private class Diversity(val value: String, val label: String, val icon: ImageVector)

private val diversities = listOf(
    Diversity("default", "Обычно", Icons.Rounded.Tune),
    Diversity("favorite", "Любимое", Icons.Rounded.Favorite),
    Diversity("discover", "Незнакомое", Icons.Rounded.Explore),
    Diversity("popular", "Популярное", Icons.Rounded.Whatshot),
)

/**
 * Главная, как в Android-версии: Моя волна на весь экран — живой фон в цветах настроения, большая кнопка
 * с пульсом, пока играет, ниже — настроения карточками с иконками и характер волны.
 */
@Composable
fun HomeScreen(openSettings: () -> Unit) {
    val graph = LocalGraph.current
    val engine = LocalEngine.current
    val settings by graph.settings.flow.collectAsState()
    val state by engine.state.collectAsState()
    val waveOn = state.waveMode && state.radioTitle == "Моя волна"

    fun applyWaveSettings(mood: String, diversity: String) = graph.scope.launch {
        graph.settings.update { it.copy(waveMood = mood, waveDiversity = diversity) }
        val r = runCatching { graph.yandex.setWaveSettings(mood, diversity) }
        // Новые настройки волны действуют на следующие треки — перезапускаем, если волна уже играет
        if (r.isSuccess && waveOn) engine.startWave()
        r.exceptionOrNull()?.let { engine.notify("Настройки волны: ${it.message}") }
    }

    val moodIndex = moods.indexOfFirst { it.value == settings.waveMood }.coerceAtLeast(0)
    val mood = moods[moodIndex]
    val diversity = diversities.firstOrNull { it.value == settings.waveDiversity } ?: diversities[0]

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp)) {
        WaveHero(
            mood = mood,
            subtitle = "${mood.label} · ${diversity.label}",
            playing = waveOn && state.isPlaying,
            nowPlaying = state.track?.takeIf { waveOn }?.let { "${it.artists} — ${it.title}" },
            loggedIn = settings.yandexLoggedIn,
            onPlay = { if (waveOn) engine.togglePlay() else engine.startWave() },
            onLogin = openSettings,
        ) {
            if (settings.yandexLoggedIn) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                ) {
                    moods.forEachIndexed { i, m ->
                        MoodCard(m, selected = i == moodIndex) { applyWaveSettings(m.value, settings.waveDiversity) }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    diversities.forEach { d ->
                        DiversityChip(d, selected = d.value == settings.waveDiversity) { applyWaveSettings(settings.waveMood, d.value) }
                    }
                }
            }
        }

        if (state.waveMode && state.queue.size > state.index + 1) {
            Spacer(Modifier.height(24.dp))
            SectionTitle("Дальше в «${state.radioTitle}»")
            state.queue.drop(state.index + 1).take(8).forEachIndexed { i, t ->
                TrackRow(t, index = null, onPlay = { engine.skipTo(state.index + 1 + i) })
            }
        }
    }
}

/** Большая карточка Моей волны: «дышащий» градиент в цветах настроения, кнопка по центру, внизу — выбор настроения. */
@Composable
private fun WaveHero(
    mood: Mood,
    subtitle: String,
    playing: Boolean,
    nowPlaying: String?,
    loggedIn: Boolean,
    onPlay: () -> Unit,
    onLogin: () -> Unit,
    controls: @Composable () -> Unit,
) {
    val from by animateColorAsState(mood.from, tween(700), label = "from")
    val to by animateColorAsState(mood.to, tween(700), label = "to")
    val t = rememberInfiniteTransition(label = "hero")
    val phase by t.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "phase")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1_800, easing = LinearEasing), RepeatMode.Restart), label = "pulse")

    Box(Modifier.fillMaxWidth().height(600.dp).clip(RoundedCornerShape(32.dp)).background(Color(0xFF0E0E14))) {
        // Плавающие пятна цвета — фон «дышит» под музыку
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            fun blob(color: Color, cx: Float, cy: Float, r: Float) =
                drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), Offset(cx, cy), r), r, Offset(cx, cy))
            blob(from.copy(alpha = 0.85f), w * (0.3f + 0.15f * cos(phase)), h * (0.28f + 0.08f * sin(phase)), w * 0.6f)
            blob(to.copy(alpha = 0.7f), w * (0.75f + 0.12f * sin(phase * 0.8f)), h * (0.55f + 0.1f * cos(phase * 0.8f)), w * 0.55f)
            blob(from.copy(alpha = 0.45f), w * (0.5f + 0.2f * sin(phase * 1.3f)), h * (0.95f + 0.05f * cos(phase)), w * 0.5f)
        }
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Моя волна", fontSize = 46.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
                Text(subtitle, color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(30.dp))
                if (loggedIn) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
                        // Пульсирующее кольцо, пока волна играет
                        if (playing) {
                            Canvas(Modifier.fillMaxSize()) {
                                drawCircle(
                                    Color.White.copy(alpha = (1f - pulse) * 0.5f),
                                    radius = size.minDimension / 2 * (0.62f + 0.38f * pulse),
                                    style = Stroke(width = 3.dp.toPx()),
                                )
                            }
                        }
                        val interaction = remember { MutableInteractionSource() }
                        val pressed by interaction.collectIsPressedAsState()
                        val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "press")
                        Box(
                            Modifier.size(96.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape).background(Color.White)
                                .clickable(interaction, indication = null, onClick = onPlay),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Моя волна", tint = from, modifier = Modifier.size(52.dp))
                        }
                    }
                    Text(
                        nowPlaying ?: " ",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 520.dp).padding(top = 8.dp),
                    )
                } else {
                    Text(
                        "Войдите в Яндекс, чтобы слушать Мою волну",
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    PrimaryPill("Войти в Яндекс", null, onClick = onLogin)
                }
            }
            controls()
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** Карточка настроения: выбранная крупнее и ярче, соседние — приглушены (как карусель на телефоне). */
@Composable
private fun MoodCard(m: Mood, selected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1f else 0.86f, spring(dampingRatio = 0.6f, stiffness = 380f), label = "scale")
    val alpha by animateFloatAsState(if (selected) 1f else 0.6f, tween(250), label = "alpha")
    Column(
        Modifier
            .size(width = 120.dp, height = 116.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (selected) 0.22f else 0.12f), Color.White.copy(alpha = 0.05f))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.4f), Color.White.copy(alpha = 0.06f))), RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(Brush.linearGradient(listOf(m.from, m.to))), contentAlignment = Alignment.Center) {
            Icon(m.icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Text(m.label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun DiversityChip(d: Diversity, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color.White.copy(alpha = 0.9f) else glassFill(strong = true))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (selected) Color(0xFF0D0D10) else Color.White
        Icon(d.icon, null, tint = color, modifier = Modifier.size(18.dp))
        Text(d.label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
    }
}
