package com.musichub.ui.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.musichub.ui.glassFill
import com.musichub.ui.theme.LocalPalette
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

/** Включена ли тема Liquid Glass (настройка «Оформление»). */
val LocalLiquidGlass = compositionLocalOf { false }

/** Что лежит под стеклом: запись фона окна, которую панели размывают и преломляют. */
class LiquidBackdrop internal constructor(internal val layer: GraphicsLayer) {
    internal var origin by mutableStateOf(Offset.Zero)
}

val LocalBackdrop = compositionLocalOf<LiquidBackdrop?> { null }

@Composable
fun rememberLiquidBackdrop(): LiquidBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { LiquidBackdrop(layer) }
}

/** Фон окна записывается в [backdrop] и рисуется как обычно. */
fun Modifier.backdropSource(backdrop: LiquidBackdrop): Modifier = this
    .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
    .drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
    }

private val Ink = Color(0xFF0B0B12)

/** Медленно плавающие цветные пятна в цветах палитры: стеклу нужно, что преломлять. Как в Android-версии. */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "ambient")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(17_000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(0f, 1f, infiniteRepeatable(tween(23_000, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    val palette = LocalPalette.current
    Canvas(modifier.fillMaxSize()) {
        drawRect(Ink)
        val w = size.width
        val h = size.height
        fun blob(color: Color, x: Float, y: Float, r: Float) =
            drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), Offset(x, y), r), r, Offset(x, y))
        blob(palette.a.copy(alpha = 0.50f), w * (0.1f + 0.55f * a), h * (0.1f + 0.18f * b), w * 0.6f)
        blob(palette.b.copy(alpha = 0.38f), w * (0.95f - 0.45f * b), h * (0.42f + 0.16f * a), w * 0.5f)
        blob(palette.c.copy(alpha = 0.30f), w * (0.2f + 0.35f * b), h * (0.72f - 0.12f * a), w * 0.48f)
        blob(palette.a.copy(alpha = 0.22f), w * (0.8f - 0.3f * a), h * (0.98f - 0.2f * b), w * 0.45f)
    }
}

/**
 * Преломление у краёв скруглённого прямоугольника: ближе к краю картинка под стеклом сильнее
 * «затягивается» внутрь, как в толстой линзе; каналы смещаются чуть по-разному (хроматическая аберрация).
 */
private val LensEffect: RuntimeEffect by lazy {
    RuntimeEffect.makeForShader(
        """
        uniform shader content;
        uniform float2 size;
        uniform float radius;
        uniform float depth;
        uniform float strength;

        float sdRoundRect(float2 p, float2 hs, float r) {
            float2 q = abs(p) - hs + r;
            return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
        }

        half4 main(float2 coord) {
            float2 hs = size * 0.5;
            float2 p = coord - hs;
            float d = sdRoundRect(p, hs, radius);
            float t = clamp(1.0 + d / depth, 0.0, 1.0);
            if (t <= 0.0) return content.eval(coord);
            float e = 0.5;
            float2 n = normalize(float2(
                sdRoundRect(p + float2(e, 0), hs, radius) - sdRoundRect(p - float2(e, 0), hs, radius),
                sdRoundRect(p + float2(0, e), hs, radius) - sdRoundRect(p - float2(0, e), hs, radius)) + 1e-5);
            float k = t * t * strength;
            half4 c = content.eval(coord - n * k);
            c.r = content.eval(coord - n * k * 1.12).r;
            c.b = content.eval(coord - n * k * 0.88).b;
            return c;
        }
        """.trimIndent(),
    )
}

/**
 * Настоящее стекло для панелей окна: под панелью — размытый и преломлённый у краёв фон,
 * сверху лёгкая тонировка и светлый блик по краю. Без [LocalBackdrop] — обычная полупрозрачная подложка.
 */
fun Modifier.liquidGlass(corner: Dp = 20.dp, tint: Color = Ink.copy(alpha = 0.35f), blur: Dp = 24.dp): Modifier = composed {
    val backdrop = LocalBackdrop.current
    val shape = RoundedCornerShape(corner)
    if (backdrop == null) return@composed this.clip(shape).background(Color(0xFF16161B).copy(alpha = 0.55f)).background(glassFill())
    val density = LocalDensity.current
    val glass = rememberGraphicsLayer()
    var position by remember { mutableStateOf(Offset.Zero) }
    var effectSize by remember { mutableStateOf(IntSize.Zero) }
    val effect = remember(effectSize, density) {
        if (effectSize == IntSize.Zero) return@remember null
        with(density) {
            val builder = RuntimeShaderBuilder(LensEffect).apply {
                uniform("size", effectSize.width.toFloat(), effectSize.height.toFloat())
                uniform("radius", corner.toPx())
                uniform("depth", 18.dp.toPx())
                uniform("strength", 14.dp.toPx())
            }
            val blurred = ImageFilter.makeBlur(blur.toPx() / 2, blur.toPx() / 2, FilterTileMode.CLAMP)
            ImageFilter.makeRuntimeShader(builder, "content", blurred).asComposeRenderEffect()
        }
    }
    this
        .onGloballyPositioned {
            position = it.positionInRoot()
            effectSize = it.size
        }
        .clip(shape)
        .drawBehind {
            glass.renderEffect = effect
            glass.record {
                translate(backdrop.origin.x - position.x, backdrop.origin.y - position.y) { drawLayer(backdrop.layer) }
            }
            drawLayer(glass)
            drawRect(tint)
        }
        .border(
            1.dp,
            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.16f))),
            shape,
        )
}

/** Подложка панели окна (боковая, плеер, текст и очередь): стекло в теме Liquid Glass, иначе полупрозрачная. */
fun Modifier.panelSurface(darkAlpha: Float): Modifier = composed {
    if (LocalBackdrop.current != null) {
        liquidGlass(tint = Ink.copy(alpha = darkAlpha * 0.55f))
    } else {
        clip(RoundedCornerShape(20.dp)).background(Color(0xFF16161B).copy(alpha = darkAlpha)).background(glassFill())
    }
}

/**
 * Переключатель-«капля», как в Android-версии: бегунок вытягивается на ходу и пружинит на месте,
 * а при нажатии раздувается и становится прозрачной линзой, через которую видна дорожка.
 */
@Composable
fun LiquidSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val palette = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pos = remember { Animatable(if (checked) 1f else 0f) }
    LaunchedEffect(checked) { pos.animateTo(if (checked) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 380f)) }
    val press by animateFloatAsState(if (pressed) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "press")
    val p = pos.value.coerceIn(-0.08f, 1.08f)
    val stretch = 1f - abs(p.coerceIn(0f, 1f) * 2 - 1) // 1 — посередине пути
    val trackW = 52.dp
    val trackH = 30.dp
    val thumbW = 24.dp + 12.dp * stretch + 6.dp * press
    val thumbH = 24.dp - 3.dp * stretch + 4.dp * press
    Box(
        Modifier.size(trackW, trackH).clip(CircleShape)
            .background(lerp(Color.White.copy(alpha = 0.14f), palette.b, p.coerceIn(0f, 1f)))
            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            .clickable(interaction, indication = null) { onCheckedChange(!checked) },
    ) {
        Box(
            Modifier
                .offset(x = 3.dp + (trackW - 6.dp - thumbW) * p, y = (trackH - thumbH) / 2)
                .size(thumbW, thumbH)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 1f - 0.6f * press))
                .border(1.dp, Color.White.copy(alpha = 0.3f + 0.5f * press), CircleShape),
        )
    }
}
