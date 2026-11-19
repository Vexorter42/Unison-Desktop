package com.musichub.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musichub.data.EqualizerConfig
import com.musichub.data.EqualizerPreset
import com.musichub.ui.GlassButton
import com.musichub.ui.GlassChip
import com.musichub.ui.LocalEngine
import com.musichub.ui.LocalGraph
import com.musichub.ui.ThinSlider
import com.musichub.ui.liquid.LiquidSwitch
import com.musichub.ui.liquid.LocalLiquidGlass
import com.musichub.ui.liquid.panelSurface
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Десять полос libVLC с настройкой в реальном времени, пресетами и запасом по уровню. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqualizerScreen() {
    val graph = LocalGraph.current
    val engine = LocalEngine.current
    val settings by graph.settings.flow.collectAsState()
    val eq = settings.equalizer
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    fun change(block: (EqualizerConfig) -> EqualizerConfig) {
        graph.scope.launch { graph.settings.update { it.copy(equalizer = block(it.equalizer)) } }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp)) {
        Text("Эквалайзер", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Настройте звук под наушники и музыку. Изменения слышны сразу.", color = muted,
            modifier = Modifier.padding(top = 5.dp, bottom = 22.dp))

        Column(Modifier.fillMaxWidth().panelSurface(0.6f).padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Обработка звука", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Text(if (eq.enabled) "Включена · ${eq.preset.label}" else "Выключена", color = muted, fontSize = 13.sp)
                }
                if (LocalLiquidGlass.current) LiquidSwitch(eq.enabled) { change { old -> old.copy(enabled = it) } }
                else Switch(checked = eq.enabled, onCheckedChange = { checked -> change { it.copy(enabled = checked) } })
            }
            Spacer(Modifier.height(22.dp))
            Text("Готовые настройки", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            FlowRow(
                Modifier.padding(top = 10.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EqualizerPreset.entries.filterNot { it == EqualizerPreset.CUSTOM }.forEach { preset ->
                    GlassChip(preset.label, eq.preset == preset) { change { it.withPreset(preset).copy(enabled = true) } }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                engine.equalizerFrequencies.forEachIndexed { index, frequency ->
                    BandFader(
                        frequency = frequency,
                        gain = eq.bands.getOrElse(index) { 0f },
                        enabled = eq.enabled,
                        onChange = { gain -> change { old ->
                            old.copy(preset = EqualizerPreset.CUSTOM,
                                bands = List(engine.equalizerFrequencies.size) { i -> if (i == index) gain else old.bands.getOrElse(i) { 0f } })
                        } },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Предусиление", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text("${eq.preamp.signedDb()} дБ", color = muted)
            }
            ThinSlider(
                value = (eq.preamp + 12f) / 24f,
                onValueChange = { fraction -> change { it.copy(preamp = ((fraction * 48f).roundToInt() / 2f) - 12f) } },
                modifier = Modifier.fillMaxWidth(), enabled = eq.enabled,
            )
            Text("Фактически: ${eq.appliedPreamp().signedDb()} дБ", fontSize = 12.sp, color = muted)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Защита от перегрузки", fontWeight = FontWeight.SemiBold)
                    Text("Снижает уровень перед усилением полос, чтобы звук не хрипел", fontSize = 12.sp, color = muted)
                }
                if (LocalLiquidGlass.current) LiquidSwitch(eq.safeHeadroom) { checked -> change { it.copy(safeHeadroom = checked) } }
                else Switch(checked = eq.safeHeadroom, onCheckedChange = { checked -> change { it.copy(safeHeadroom = checked) } })
            }
            Spacer(Modifier.height(16.dp))
            GlassButton("Сбросить настройки", onClick = { change { it.copy(preset = EqualizerPreset.FLAT, bands = EqualizerPreset.FLAT.gains, preamp = 0f) } })
        }
    }
}

private fun Float.signedDb(): String = "${if (this > 0f) "+" else ""}${"%.1f".format(this)}"
private fun gainAt(y: Float, height: Int): Float =
    (((12f - y / height * 24f) * 2f).roundToInt() / 2f).coerceIn(-12f, 12f)

@Composable
private fun BandFader(frequency: Float, gain: Float, enabled: Boolean, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val update by rememberUpdatedState(onChange)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(gain.signedDb(), fontSize = 11.sp, color = if (enabled) accent else muted)
        Canvas(
            Modifier.fillMaxWidth().height(210.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { pos -> update(gainAt(pos.y, size.height)) }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(onDragStart = { pos ->
                        update(gainAt(pos.y, size.height))
                    }) { change, _ ->
                        update(gainAt(change.position.y, size.height))
                    }
                },
        ) {
            val x = size.width / 2f
            val center = size.height / 2f
            val y = (12f - gain.coerceIn(-12f, 12f)) / 24f * size.height
            drawRoundRect(muted.copy(alpha = 0.22f), Offset(x - 3.dp.toPx(), 8.dp.toPx()),
                Size(6.dp.toPx(), size.height - 16.dp.toPx()), CornerRadius(3.dp.toPx()))
            drawLine(muted.copy(alpha = 0.55f), Offset(x - 11.dp.toPx(), center), Offset(x + 11.dp.toPx(), center), 1.dp.toPx())
            if (y != center) drawRoundRect(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.55f))),
                Offset(x - 3.dp.toPx(), minOf(y, center)), Size(6.dp.toPx(), kotlin.math.abs(y - center)), CornerRadius(3.dp.toPx()))
            drawCircle(if (enabled) accent else muted, 9.dp.toPx(), Offset(x, y.coerceIn(9.dp.toPx(), size.height - 9.dp.toPx())))
        }
        Text(if (frequency >= 1000) "${"%.1f".format(frequency / 1000f)}k" else "${frequency.roundToInt()}",
            fontSize = 11.sp, color = muted)
    }
}
