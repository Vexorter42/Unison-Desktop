package com.musichub.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

/**
 * Светящаяся сфера из «лент» за надписью Моей волны: ленты медленно текут, а от басов сфера
 * раздувается и ярче светится. Без движения — неподвижная картинка того же вида.
 */
private val SphereEffect: RuntimeEffect by lazy {
    RuntimeEffect.makeForShader(
        """
        uniform float2 size;
        uniform float2 center;
        uniform float time;
        uniform float bass;
        uniform float3 colA;
        uniform float3 colB;

        float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
        float noise(float2 p) {
            float2 i = floor(p);
            float2 f = fract(p);
            f = f * f * (3.0 - 2.0 * f);
            float a = hash(i);
            float b = hash(i + float2(1.0, 0.0));
            float c = hash(i + float2(0.0, 1.0));
            float d = hash(i + float2(1.0, 1.0));
            return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
        }

        half4 main(float2 fc) {
            float2 uv = (fc - center) / min(size.x, size.y);
            float r = length(uv);
            float a = atan(uv.y, uv.x);
            float2 dir = float2(cos(a), sin(a));
            float R = 0.30 * (1.0 + 0.18 * bass);
            float lines = 0.0;
            for (int i = 0; i < 7; i++) {
                float fi = float(i);
                // Шум по окружности — лента извивается; время сдвигает шум, и ленты «текут»
                float n = noise(dir * 1.7 + float2(fi * 3.1, time * 0.22 + fi * 1.3));
                float ri = R * (0.55 + 0.09 * fi) + (n - 0.5) * 0.24 * (1.0 + 1.4 * bass);
                float d = abs(r - ri);
                lines += 0.004 / (d + 0.004) * (0.7 - 0.06 * fi);
            }
            float core = exp(-pow(r / (R * 1.05), 2.0) * 1.7);
            float halo = exp(-pow(r / (R * 2.3), 2.0) * 1.3);
            float t = 0.5 + 0.5 * sin(a + time * 0.15);
            float3 col = mix(colA, colB, t);
            // Ленты гаснут к краю сферы — получается шар, а не кольца на весь экран
            float fade = exp(-pow(r / (R * 1.6), 2.0) * 1.4);
            float3 c = col * (core * 0.6 + halo * 0.35) * (0.75 + 0.55 * bass)
                + mix(col, float3(1.0), 0.6) * lines * fade * 0.2 * (0.8 + 0.8 * bass);
            float al = clamp(max(c.r, max(c.g, c.b)), 0.0, 1.0);
            return half4(half3(min(c, float3(al))), half(al));
        }
        """.trimIndent(),
    )
}

/**
 * @param motion сфера двигается и слушает басы; иначе — неподвижна.
 * @param bass текущая сила басов 0..1 (читается каждый кадр).
 * @param centerY где по высоте центр сферы (доля высоты).
 */
@Composable
fun WaveSphere(colorA: Color, colorB: Color, motion: Boolean, bass: () -> Float, modifier: Modifier = Modifier, centerY: Float = 0.5f) {
    var time by remember { mutableFloatStateOf(7f) }
    var level by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(motion) {
        if (!motion) {
            level = 0f
            return@LaunchedEffect
        }
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) time += (now - last) / 1e9f
                last = now
                level = bass()
            }
        }
    }
    val builder = remember { RuntimeShaderBuilder(SphereEffect) }
    Canvas(modifier) {
        builder.uniform("size", size.width, size.height)
        builder.uniform("center", size.width / 2, size.height * centerY)
        builder.uniform("time", time)
        builder.uniform("bass", level)
        builder.uniform("colA", colorA.red, colorA.green, colorA.blue)
        builder.uniform("colB", colorB.red, colorB.green, colorB.blue)
        val paint = org.jetbrains.skia.Paint().apply { shader = builder.makeShader() }
        drawIntoCanvas { it.nativeCanvas.drawRect(org.jetbrains.skia.Rect.makeWH(size.width, size.height), paint) }
    }
}
