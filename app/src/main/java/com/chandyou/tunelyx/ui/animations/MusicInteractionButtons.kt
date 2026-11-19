package com.chandyou.tunelyx.ui.animations

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected as semanticsSelected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ============================================================================
// SHARED DESIGN TOKENS, PHYSICS, ACCESSIBILITY & DRAWING HELPERS
// ============================================================================

@Immutable
data class MusicInteractionPalette(
    val active: Color = Color(0xFFF3F3ED),
    val inactive: Color = Color(0xFFD6D6D0),
    val ripple: Color = Color(0xFFF3F3ED),
    val sparkle: Color = Color(0xFFFFFFFF),
    val cutoutContrast: Color = Color(0xFF101014),
    val disabledAlpha: Float = 0.34f
)

object MusicInteractionDefaults {
    val TouchTargetSize: Dp = 52.dp
    val IconSize: Dp = 26.dp
    const val VectorViewport: Float = 24f
    const val StrokeWidth24: Float = 1.85f
    const val PressScale: Float = 0.88f

    fun <T> snappySpring() = spring<T>(
        dampingRatio = 0.58f,
        stiffness = 540f
    )

    fun <T> settleSpring() = spring<T>(
        dampingRatio = 0.72f,
        stiffness = 380f
    )
}

@Composable
fun rememberReducedMotionEnabled(override: Boolean? = null): Boolean {
    if (override != null) return override
    val context = LocalContext.current
    return remember(context) {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        } catch (_: Throwable) {
            false
        }
    }
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

private fun clamp01(value: Float): Float = value.coerceIn(0f, 1f)

private fun lerpColor(start: Color, stop: Color, fraction: Float): Color {
    val t = clamp01(fraction)
    return Color(
        red = lerpFloat(start.red, stop.red, t),
        green = lerpFloat(start.green, stop.green, t),
        blue = lerpFloat(start.blue, stop.blue, t),
        alpha = lerpFloat(start.alpha, stop.alpha, t)
    )
}

@Composable
private fun TactileActionContainer(
    onClick: () -> Unit,
    enabled: Boolean,
    selected: Boolean?,
    contentDesc: String,
    touchSize: Dp,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(pressScale: Float) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale = remember { Animatable(1f) }

    LaunchedEffect(isPressed, enabled, reducedMotion) {
        val target = if (isPressed && enabled) MusicInteractionDefaults.PressScale else 1f
        if (reducedMotion) {
            pressScale.snapTo(target)
        } else {
            pressScale.animateTo(
                targetValue = target,
                animationSpec = if (isPressed) {
                    tween(durationMillis = 75, easing = FastOutSlowInEasing)
                } else {
                    MusicInteractionDefaults.snappySpring()
                }
            )
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(touchSize)
            .semantics {
                contentDescription = contentDesc
                if (selected != null) {
                    semanticsSelected = selected
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = if (selected != null) Role.Tab else Role.Button,
                onClick = onClick
            )
            .graphicsLayer { clip = false }
    ) {
        content(pressScale.value)
    }
}

private inline fun DrawScope.inIconViewport(
    iconSizePx: Float,
    block: DrawScope.() -> Unit
) {
    val scale = iconSizePx / MusicInteractionDefaults.VectorViewport
    val offsetX = (size.width - iconSizePx) * 0.5f
    val offsetY = (size.height - iconSizePx) * 0.5f
    withTransform({
        translate(left = offsetX, top = offsetY)
        scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
    }) {
        block()
    }
}

private fun DrawScope.drawCircularRipple(
    progress: Float,
    color: Color,
    maxRadius: Float,
    strokeWidth: Float
) {
    if (progress <= 0.001f || progress >= 0.999f) return
    val eased = 1f - (1f - progress) * (1f - progress)
    val radius = lerpFloat(maxRadius * 0.28f, maxRadius, eased)
    val alpha = (1f - progress) * 0.40f
    drawCircle(
        color = color.copy(alpha = alpha),
        radius = radius,
        center = center,
        style = Stroke(width = strokeWidth * (1f - progress * 0.45f))
    )
}

private fun DrawScope.drawFourPointSpark(
    progress: Float,
    cx: Float,
    cy: Float,
    maxArm: Float,
    color: Color,
    alphaFactor: Float
) {
    if (progress <= 0.001f || progress >= 0.999f) return
    val pulse = sin(progress * PI).toFloat()
    val arm = maxArm * pulse * 1.15f
    val alpha = pulse * alphaFactor
    drawLine(
        color = color.copy(alpha = alpha),
        start = Offset(cx - arm, cy),
        end = Offset(cx + arm, cy),
        strokeWidth = 1.25f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = color.copy(alpha = alpha),
        start = Offset(cx, cy - arm),
        end = Offset(cx, cy + arm),
        strokeWidth = 1.25f,
        cap = StrokeCap.Round
    )
}

// ============================================================================
// 1. LIKE BUTTON â€” ELASTIC HEART
// ============================================================================

@Composable
fun ElasticLikeButton(
    isLiked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    touchSize: Dp = MusicInteractionDefaults.TouchTargetSize,
    iconSize: Dp = MusicInteractionDefaults.IconSize,
    palette: MusicInteractionPalette = MusicInteractionPalette(),
    reducedMotionOverride: Boolean? = null,
    contentDescription: String = if (isLiked) "Unlike song" else "Like song"
) {
    val reducedMotion = rememberReducedMotionEnabled(reducedMotionOverride)
    var hasInitialized by remember { mutableStateOf(false) }

    val scale = remember { Animatable(1f) }
    val fillMorph = remember { Animatable(if (isLiked) 1f else 0f) }
    val rippleProgress = remember { Animatable(0f) }
    val particleProgress = remember { Animatable(0f) }

    val heartPath = remember { buildHeartPath() }
    val particleAngles = remember { floatArrayOf(-90f, -38f, 18f, 68f, 118f, 168f, 222f) }

    LaunchedEffect(isLiked, reducedMotion) {
        if (!hasInitialized) {
            hasInitialized = true
            fillMorph.snapTo(if (isLiked) 1f else 0f)
            scale.snapTo(1f)
            rippleProgress.snapTo(0f)
            particleProgress.snapTo(0f)
            return@LaunchedEffect
        }

        if (reducedMotion) {
            scale.snapTo(1f)
            rippleProgress.snapTo(0f)
            particleProgress.snapTo(0f)
            fillMorph.animateTo(if (isLiked) 1f else 0f, tween(120))
            return@LaunchedEffect
        }

        if (isLiked) {
            rippleProgress.snapTo(0f)
            particleProgress.snapTo(0f)

            coroutineScope {
                launch {
                    scale.animateTo(1.26f, spring(dampingRatio = 0.52f, stiffness = 620f))
                    scale.animateTo(0.96f, tween(85, easing = FastOutSlowInEasing))
                    scale.animateTo(1.09f, tween(95, easing = FastOutSlowInEasing))
                    scale.animateTo(1f, MusicInteractionDefaults.settleSpring())
                }
                launch {
                    fillMorph.animateTo(1f, tween(210, easing = FastOutSlowInEasing))
                }
                launch {
                    delay(25)
                    rippleProgress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
                }
                launch {
                    delay(35)
                    particleProgress.animateTo(1f, tween(460, easing = FastOutSlowInEasing))
                }
            }
        } else {
            particleProgress.snapTo(0f)
            rippleProgress.snapTo(0f)
            coroutineScope {
                launch {
                    scale.animateTo(0.86f, tween(80, easing = FastOutSlowInEasing))
                    scale.animateTo(1f, MusicInteractionDefaults.settleSpring())
                }
                launch {
                    fillMorph.animateTo(0f, tween(180, easing = FastOutSlowInEasing))
                }
            }
        }
    }

    TactileActionContainer(
        onClick = onToggle,
        enabled = enabled,
        selected = null,
        contentDesc = contentDescription,
        touchSize = touchSize,
        reducedMotion = reducedMotion,
        modifier = modifier
    ) { pressScale ->
        val alphaFactor = if (enabled) 1f else palette.disabledAlpha

        Canvas(
            modifier = Modifier
                .size(touchSize)
                .graphicsLayer { clip = false }
        ) {
            val iconPx = iconSize.toPx()

            drawCircularRipple(
                progress = rippleProgress.value,
                color = palette.ripple.copy(alpha = alphaFactor),
                maxRadius = size.minDimension * 0.56f,
                strokeWidth = 1.6.dp.toPx()
            )

            val p = particleProgress.value
            if (p > 0.001f && p < 0.999f) {
                val pEase = 1f - (1f - p) * (1f - p)
                val pAlpha = if (p < 0.3f) p / 0.3f else 1f - ((p - 0.3f) / 0.7f)
                val minR = iconPx * 0.42f
                val maxR = iconPx * 0.96f
                particleAngles.forEachIndexed { idx, deg ->
                    val rad = (deg * PI / 180.0).toFloat()
                    val distJitter = if (idx % 2 == 0) 1f else 0.84f
                    val dist = lerpFloat(minR, maxR * distJitter, pEase)
                    val dotRadius = (if (idx % 2 == 0) 2.2.dp.toPx() else 1.6.dp.toPx()) *
                        (1f - p * 0.75f)
                    drawCircle(
                        color = palette.active.copy(alpha = pAlpha * 0.9f * alphaFactor),
                        radius = dotRadius.coerceAtLeast(0.4f),
                        center = Offset(
                            x = center.x + cos(rad) * dist,
                            y = center.y + sin(rad) * dist
                        )
                    )
                }
            }

            val totalScale = pressScale * scale.value
            val morph = fillMorph.value
            val tint = lerpColor(palette.inactive, palette.active, morph)
                .copy(alpha = alphaFactor)

            inIconViewport(iconPx) {
                withTransform({
                    scale(scaleX = totalScale, scaleY = totalScale, pivot = Offset(12f, 12f))
                }) {
                    if (morph > 0.01f) {
                        clipPath(heartPath) {
                            drawCircle(
                                color = tint.copy(alpha = tint.alpha * clamp01(morph * 1.15f)),
                                radius = lerpFloat(1.5f, 13.5f, morph),
                                center = Offset(12f, 11.8f)
                            )
                        }
                    }
                    drawPath(
                        path = heartPath,
                        color = tint,
                        style = Stroke(
                            width = MusicInteractionDefaults.StrokeWidth24,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }
    }
}

// ============================================================================
// 2. DISLIKE BUTTON â€” FLUID COMPRESSION
// ============================================================================

@Composable
fun FluidDislikeButton(
    isDisliked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    touchSize: Dp = MusicInteractionDefaults.TouchTargetSize,
    iconSize: Dp = MusicInteractionDefaults.IconSize,
    palette: MusicInteractionPalette = MusicInteractionPalette(),
    reducedMotionOverride: Boolean? = null,
    contentDescription: String = if (isDisliked) "Remove dislike" else "Dislike song"
) {
    val reducedMotion = rememberReducedMotionEnabled(reducedMotionOverride)
    var hasInitialized by remember { mutableStateOf(false) }

    val scaleXAnim = remember { Animatable(1f) }
    val scaleYAnim = remember { Animatable(1f) }
    val translateY = remember { Animatable(0f) }
    val rotation = remember { Animatable(0f) }
    val fillProgress = remember { Animatable(if (isDisliked) 1f else 0f) }
    val rippleProgress = remember { Animatable(0f) }

    val cuffPath = remember { buildThumbCuffPath(isDown = true) }
    val handPath = remember { buildThumbHandPath(isDown = true) }

    LaunchedEffect(isDisliked, reducedMotion) {
        if (!hasInitialized) {
            hasInitialized = true
            fillProgress.snapTo(if (isDisliked) 1f else 0f)
            return@LaunchedEffect
        }

        if (reducedMotion) {
            scaleXAnim.snapTo(1f)
            scaleYAnim.snapTo(1f)
            translateY.snapTo(0f)
            rotation.snapTo(0f)
            rippleProgress.snapTo(0f)
            fillProgress.animateTo(if (isDisliked) 1f else 0f, tween(120))
            return@LaunchedEffect
        }

        if (isDisliked) {
            rippleProgress.snapTo(0f)
            coroutineScope {
                launch {
                    scaleXAnim.animateTo(0.80f, tween(85, easing = FastOutSlowInEasing))
                    scaleXAnim.animateTo(1f, MusicInteractionDefaults.settleSpring())
                }
                launch {
                    scaleYAnim.animateTo(1.09f, tween(85, easing = FastOutSlowInEasing))
                    scaleYAnim.animateTo(1f, MusicInteractionDefaults.settleSpring())
                }
                launch {
                    translateY.animateTo(3.4f, tween(135, easing = FastOutSlowInEasing))
                    translateY.animateTo(0f, MusicInteractionDefaults.settleSpring())
                }
                launch {
                    rotation.animateTo(-11f, tween(135, easing = FastOutSlowInEasing))
                    rotation.animateTo(0f, MusicInteractionDefaults.settleSpring())
                }
                launch {
                    delay(45)
                    fillProgress.animateTo(1f, tween(190, easing = FastOutSlowInEasing))
                }
                launch {
                    delay(35)
                    rippleProgress.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
                }
            }
        } else {
            rippleProgress.snapTo(0f)
            coroutineScope {
                launch {
                    translateY.animateTo(-1.8f, tween(85, easing = FastOutSlowInEasing))
                    translateY.animateTo(0f, MusicInteractionDefaults.settleSpring())
                }
                launch {
                    rotation.animateTo(5f, tween(85, easing = FastOutSlowInEasing))
                    rotation.animateTo(0f, MusicInteractionDefaults.settleSpring())
                }
                launch {
                    scaleXAnim.animateTo(1f, MusicInteractionDefaults.settleSpring())
                    scaleYAnim.animateTo(1f, MusicInteractionDefaults.settleSpring())
                }
                launch {
                    fillProgress.animateTo(0f, tween(160, easing = FastOutSlowInEasing))
                }
            }
        }
    }

    TactileActionContainer(
        onClick = onToggle,
        enabled = enabled,
        selected = null,
        contentDesc = contentDescription,
        touchSize = touchSize,
        reducedMotion = reducedMotion,
        modifier = modifier
    ) { pressScale ->
        val alphaFactor = if (enabled) 1f else palette.disabledAlpha

        Canvas(
            modifier = Modifier
                .size(touchSize)
                .graphicsLayer { clip = false }
        ) {
            val iconPx = iconSize.toPx()

            drawCircularRipple(
                progress = rippleProgress.value,
                color = palette.ripple.copy(alpha = 0.72f * alphaFactor),
                maxRadius = size.minDimension * 0.50f,
                strokeWidth = 1.4.dp.toPx()
            )

            val fill = fillProgress.value
            val strokeColor = lerpColor(palette.inactive, palette.active, fill)
                .copy(alpha = alphaFactor)

            inIconViewport(iconPx) {
                withTransform({
                    translate(left = 0f, top = translateY.value)
                    rotate(degrees = rotation.value, pivot = Offset(12f, 12f))
                    scale(
                        scaleX = pressScale * scaleXAnim.value,
                        scaleY = pressScale * scaleYAnim.value,
                        pivot = Offset(12f, 12f)
                    )
                }) {
                    if (fill > 0.01f) {
                        drawPath(
                            path = cuffPath,
                            color = palette.active.copy(alpha = clamp01(fill * 0.9f) * alphaFactor)
                        )
                    }
                    drawPath(
                        path = cuffPath,
                        color = strokeColor,
                        style = Stroke(
                            width = MusicInteractionDefaults.StrokeWidth24,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    if (fill > 0.01f) {
                        clipPath(handPath) {
                            val fillBottom = lerpFloat(3f, 22f, fill)
                            drawRect(
                                color = palette.active.copy(alpha = alphaFactor),
                                topLeft = Offset(0f, 0f),
                                size = Size(24f, fillBottom)
                            )
                        }
                    }
                    drawPath(
                        path = handPath,
                        color = strokeColor,
                        style = Stroke(
                            width = MusicInteractionDefaults.StrokeWidth24,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }
    }
}

// ============================================================================
// 3. SAVE BUTTON â€” ANIMATED BOOKMARK CONSTRUCTION
// ============================================================================

@Composable
fun ConstructBookmarkButton(
    isSaved: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    touchSize: Dp = MusicInteractionDefaults.TouchTargetSize,
    iconSize: Dp = MusicInteractionDefaults.IconSize,
    palette: MusicInteractionPalette = MusicInteractionPalette(),
    reducedMotionOverride: Boolean? = null,
    contentDescription: String = if (isSaved) "Remove from saved" else "Save song"
) {
    val reducedMotion = rememberReducedMotionEnabled(reducedMotionOverride)
    var hasInitialized by remember { mutableStateOf(false) }

    val outlineDraw = remember { Animatable(1f) }
    val liquidLevel = remember { Animatable(if (isSaved) 1f else 0f) }
    val wavePhase = remember { Animatable(0f) }
    val scaleXAnim = remember { Animatable(1f) }
    val scaleYAnim = remember { Animatable(1f) }
    val sparkleProgress = remember { Animatable(0f) }

    val bookmarkBody = remember { buildBookmarkBodyPath() }
    val bodyMeasure = remember { PathMeasure().apply { setPath(bookmarkBody, false) } }
    val partialBodyPath = remember { Path() }
    val liquidWavePath = remember { Path() }

    LaunchedEffect(isSaved, reducedMotion) {
        if (!hasInitialized) {
            hasInitialized = true
            outlineDraw.snapTo(1f)
            liquidLevel.snapTo(if (isSaved) 1f else 0f)
            sparkleProgress.snapTo(0f)
            return@LaunchedEffect
        }

        if (reducedMotion) {
            outlineDraw.snapTo(1f)
            scaleXAnim.snapTo(1f)
            scaleYAnim.snapTo(1f)
            sparkleProgress.snapTo(0f)
            liquidLevel.animateTo(if (isSaved) 1f else 0f, tween(120))
            return@LaunchedEffect
        }

        if (isSaved) {
            outlineDraw.snapTo(0.18f)
            sparkleProgress.snapTo(0f)
            wavePhase.snapTo(0f)

            coroutineScope {
                launch {
                    outlineDraw.animateTo(1f, tween(210, easing = FastOutSlowInEasing))
                }
                launch {
                    delay(85)
                    liquidLevel.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
                }
                launch {
                    delay(85)
                    wavePhase.animateTo(PI.toFloat() * 2.5f, tween(340, easing = LinearEasing))
                }
                launch {
                    delay(75)
                    scaleYAnim.animateTo(1.13f, tween(150, easing = FastOutSlowInEasing))
                    scaleYAnim.animateTo(1f, MusicInteractionDefaults.snappySpring())
                }
                launch {
                    delay(75)
                    scaleXAnim.animateTo(0.93f, tween(150, easing = FastOutSlowInEasing))
                    scaleXAnim.animateTo(1f, MusicInteractionDefaults.snappySpring())
                }
                launch {
                    delay(355)
                    sparkleProgress.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
                }
            }
        } else {
            outlineDraw.snapTo(1f)
            sparkleProgress.snapTo(0f)
            wavePhase.snapTo(0f)

            coroutineScope {
                launch {
                    liquidLevel.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
                }
                launch {
                    wavePhase.animateTo(-PI.toFloat() * 1.8f, tween(260, easing = LinearEasing))
                }
                launch {
                    scaleYAnim.animateTo(0.92f, tween(110, easing = FastOutSlowInEasing))
                    scaleYAnim.animateTo(1f, MusicInteractionDefaults.settleSpring())
                }
                launch {
                    scaleXAnim.animateTo(1.05f, tween(110, easing = FastOutSlowInEasing))
                    scaleXAnim.animateTo(1f, MusicInteractionDefaults.settleSpring())
                }
            }
        }
    }

    TactileActionContainer(
        onClick = onToggle,
        enabled = enabled,
        selected = null,
        contentDesc = contentDescription,
        touchSize = touchSize,
        reducedMotion = reducedMotion,
        modifier = modifier
    ) { pressScale ->
        val alphaFactor = if (enabled) 1f else palette.disabledAlpha

        Canvas(
            modifier = Modifier
                .size(touchSize)
                .graphicsLayer { clip = false }
        ) {
            val iconPx = iconSize.toPx()
            val fill = liquidLevel.value
            val strokeColor = lerpColor(palette.inactive, palette.active, fill)
                .copy(alpha = alphaFactor)

            inIconViewport(iconPx) {
                withTransform({
                    scale(
                        scaleX = pressScale * scaleXAnim.value,
                        scaleY = pressScale * scaleYAnim.value,
                        pivot = Offset(12f, 13f)
                    )
                }) {
                    if (fill > 0.01f) {
                        val surfaceY = lerpFloat(21.2f, 2.8f, fill)
                        val waveAmp = sin(fill * PI).toFloat() * 1.35f
                        val phase = wavePhase.value

                        liquidWavePath.reset()
                        liquidWavePath.moveTo(4f, 22f)
                        liquidWavePath.lineTo(4f, surfaceY)
                        val steps = 8
                        for (i in 0..steps) {
                            val x = lerpFloat(4f, 20f, i / steps.toFloat())
                            val y = surfaceY + sin((x * 0.65f) + phase) * waveAmp
                            liquidWavePath.lineTo(x, y)
                        }
                        liquidWavePath.lineTo(20f, 22f)
                        liquidWavePath.close()

                        clipPath(bookmarkBody) {
                            drawPath(
                                path = liquidWavePath,
                                color = palette.active.copy(alpha = alphaFactor)
                            )
                            if (fill > 0.72f) {
                                val capAlpha = ((fill - 0.72f) / 0.28f) * 0.22f * alphaFactor
                                drawRect(
                                    color = Color.Black.copy(alpha = capAlpha),
                                    topLeft = Offset(5f, 7.6f),
                                    size = Size(14f, 13.5f)
                                )
                            }
                        }
                    }

                    val drawFraction = clamp01(outlineDraw.value)
                    if (drawFraction >= 0.995f) {
                        drawPath(
                            path = bookmarkBody,
                            color = strokeColor,
                            style = Stroke(
                                width = MusicInteractionDefaults.StrokeWidth24,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    } else {
                        partialBodyPath.reset()
                        bodyMeasure.getSegment(
                            0f,
                            bodyMeasure.length * drawFraction,
                            partialBodyPath,
                            true
                        )
                        drawPath(
                            path = partialBodyPath,
                            color = strokeColor,
                            style = Stroke(
                                width = MusicInteractionDefaults.StrokeWidth24,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    val capProgress = clamp01((drawFraction - 0.35f) / 0.65f)
                    if (capProgress > 0.01f && fill < 0.95f) {
                        val halfSpan = 6.6f * capProgress
                        drawLine(
                            color = strokeColor.copy(alpha = strokeColor.alpha * (1f - fill * 0.7f)),
                            start = Offset(12f - halfSpan, 7.6f),
                            end = Offset(12f + halfSpan, 7.6f),
                            strokeWidth = MusicInteractionDefaults.StrokeWidth24 * 0.85f,
                            cap = StrokeCap.Round
                        )
                    }

                    drawFourPointSpark(
                        progress = sparkleProgress.value,
                        cx = 19.6f,
                        cy = 3.6f,
                        maxArm = 2.6f,
                        color = palette.sparkle,
                        alphaFactor = alphaFactor
                    )
                }
            }
        }
    }
}

// ============================================================================
// 4. SKIP BUTTON â€” DIRECTIONAL FORWARD MICRO-INTERACTION
// ============================================================================

@Composable
fun CinematicSkipButton(
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    externalTrigger: Int = 0,
    touchSize: Dp = MusicInteractionDefaults.TouchTargetSize,
    iconSize: Dp = MusicInteractionDefaults.IconSize,
    palette: MusicInteractionPalette = MusicInteractionPalette(),
    reducedMotionOverride: Boolean? = null,
    contentDescription: String = "Skip song"
) {
    val reducedMotion = rememberReducedMotionEnabled(reducedMotionOverride)
    var localTrigger by remember { mutableIntStateOf(0) }
    val combinedTrigger = localTrigger + externalTrigger

    val triangleShift = remember { Animatable(0f) }
    val barShift = remember { Animatable(0f) }
    val echoProgress = remember { Animatable(0f) }

    val trianglePath = remember { buildSkipTrianglePath() }
    val barPath = remember { buildSkipBarPath() }

    LaunchedEffect(combinedTrigger) {
        if (combinedTrigger == 0) return@LaunchedEffect
        if (reducedMotion) {
            triangleShift.snapTo(0f)
            barShift.snapTo(0f)
            echoProgress.snapTo(0f)
            return@LaunchedEffect
        }

        echoProgress.snapTo(0f)
        coroutineScope {
            launch {
                triangleShift.animateTo(3.1f, tween(110, easing = FastOutSlowInEasing))
                triangleShift.animateTo(0f, MusicInteractionDefaults.snappySpring())
            }
            launch {
                delay(35)
                barShift.animateTo(1.7f, tween(105, easing = FastOutSlowInEasing))
                barShift.animateTo(0f, MusicInteractionDefaults.settleSpring())
            }
            launch {
                echoProgress.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
            }
        }
    }

    TactileActionContainer(
        onClick = {
            localTrigger++
            onSkip()
        },
        enabled = enabled,
        selected = null,
        contentDesc = contentDescription,
        touchSize = touchSize,
        reducedMotion = reducedMotion,
        modifier = modifier
    ) { pressScale ->
        val alphaFactor = if (enabled) 1f else palette.disabledAlpha

        Canvas(
            modifier = Modifier
                .size(touchSize)
                .graphicsLayer { clip = false }
        ) {
            val iconPx = iconSize.toPx()
            val iconColor = palette.active.copy(alpha = alphaFactor)

            inIconViewport(iconPx) {
                withTransform({
                    scale(scaleX = pressScale, scaleY = pressScale, pivot = Offset(12f, 12f))
                }) {
                    val echo = echoProgress.value
                    if (echo > 0.01f && echo < 0.99f) {
                        withTransform({
                            translate(left = lerpFloat(-2.2f, 2.2f, echo), top = 0f)
                        }) {
                            drawPath(
                                path = trianglePath,
                                color = iconColor.copy(alpha = (1f - echo) * 0.35f * alphaFactor),
                                style = Stroke(
                                    width = MusicInteractionDefaults.StrokeWidth24 * 0.85f,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }

                    withTransform({
                        translate(left = triangleShift.value, top = 0f)
                    }) {
                        drawPath(path = trianglePath, color = iconColor)
                        drawPath(
                            path = trianglePath,
                            color = iconColor,
                            style = Stroke(
                                width = 0.8f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    withTransform({
                        translate(left = barShift.value, top = 0f)
                    }) {
                        drawPath(path = barPath, color = iconColor)
                    }
                }
            }
        }
    }
}

// ============================================================================
// 5. UNDO BUTTON â€” COUNTER-CLOCKWISE REWIND SWEEP
// ============================================================================

@Composable
fun RewindUndoButton(
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    externalTrigger: Int = 0,
    touchSize: Dp = MusicInteractionDefaults.TouchTargetSize,
    iconSize: Dp = MusicInteractionDefaults.IconSize,
    palette: MusicInteractionPalette = MusicInteractionPalette(),
    reducedMotionOverride: Boolean? = null,
    contentDescription: String = "Undo last action"
) {
    val reducedMotion = rememberReducedMotionEnabled(reducedMotionOverride)
    var localTrigger by remember { mutableIntStateOf(0) }
    val combinedTrigger = localTrigger + externalTrigger

    val rotation = remember { Animatable(0f) }
    val arcReveal = remember { Animatable(1f) }
    val rippleProgress = remember { Animatable(0f) }

    val undoArcPath = remember { buildUndoArcPath() }
    val undoHeadPath = remember { buildUndoArrowHeadPath() }
    val arcMeasure = remember { PathMeasure().apply { setPath(undoArcPath, false) } }
    val partialArcPath = remember { Path() }

    LaunchedEffect(combinedTrigger) {
        if (combinedTrigger == 0) return@LaunchedEffect
        if (reducedMotion) {
            rotation.snapTo(0f)
            arcReveal.snapTo(1f)
            rippleProgress.snapTo(0f)
            return@LaunchedEffect
        }

        rippleProgress.snapTo(0f)
        arcReveal.snapTo(0.25f)

        coroutineScope {
            launch {
                rotation.animateTo(-42f, tween(135, easing = FastOutSlowInEasing))
                rotation.animateTo(0f, spring(dampingRatio = 0.54f, stiffness = 420f))
            }
            launch {
                arcReveal.animateTo(1f, tween(270, easing = FastOutSlowInEasing))
            }
            launch {
                rippleProgress.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
            }
        }
    }

    TactileActionContainer(
        onClick = {
            localTrigger++
            onUndo()
        },
        enabled = enabled,
        selected = null,
        contentDesc = contentDescription,
        touchSize = touchSize,
        reducedMotion = reducedMotion,
        modifier = modifier
    ) { pressScale ->
        val alphaFactor = if (enabled) 1f else palette.disabledAlpha

        Canvas(
            modifier = Modifier
                .size(touchSize)
                .graphicsLayer { clip = false }
        ) {
            val iconPx = iconSize.toPx()
            val iconColor = palette.active.copy(alpha = alphaFactor)

            drawCircularRipple(
                progress = rippleProgress.value,
                color = palette.ripple.copy(alpha = 0.72f * alphaFactor),
                maxRadius = size.minDimension * 0.48f,
                strokeWidth = 1.4.dp.toPx()
            )

            inIconViewport(iconPx) {
                withTransform({
                    scale(scaleX = pressScale, scaleY = pressScale, pivot = Offset(12f, 12.2f))
                    rotate(degrees = rotation.value, pivot = Offset(12.2f, 12.6f))
                }) {
                    val reveal = clamp01(arcReveal.value)
                    if (reveal >= 0.995f) {
                        drawPath(
                            path = undoArcPath,
                            color = iconColor,
                            style = Stroke(
                                width = MusicInteractionDefaults.StrokeWidth24,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    } else {
                        partialArcPath.reset()
                        val totalLen = arcMeasure.length
                        arcMeasure.getSegment(
                            totalLen * (1f - reveal),
                            totalLen,
                            partialArcPath,
                            true
                        )
                        drawPath(
                            path = partialArcPath,
                            color = iconColor,
                            style = Stroke(
                                width = MusicInteractionDefaults.StrokeWidth24,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    drawPath(
                        path = undoHeadPath,
                        color = iconColor,
                        style = Stroke(
                            width = MusicInteractionDefaults.StrokeWidth24,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }
    }
}

// ============================================================================
// 6. DISCOVER TAB â€” STACKED SWIPE CARDS + SPARK
// ============================================================================

@Composable
fun DiscoverTabButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    touchSize: Dp = MusicInteractionDefaults.TouchTargetSize,
    iconSize: Dp = MusicInteractionDefaults.IconSize,
    palette: MusicInteractionPalette = MusicInteractionPalette(),
    reducedMotionOverride: Boolean? = null,
    contentDescription: String = "Discover"
) {
    val reducedMotion = rememberReducedMotionEnabled(reducedMotionOverride)
    var hasInitialized by remember { mutableStateOf(false) }

    val frontShiftX = remember { Animatable(0f) }
    val frontTilt = remember { Animatable(0f) }
    val backFan = remember { Animatable(0f) }
    val fillProgress = remember { Animatable(if (selected) 1f else 0f) }
    val sparkProgress = remember { Animatable(0f) }
    val rippleProgress = remember { Animatable(0f) }

    val frontCardPath = remember { buildDiscoverFrontCardPath() }
    val backCardPath = remember { buildDiscoverBackCardPath() }

    LaunchedEffect(selected, reducedMotion) {
        if (!hasInitialized) {
            hasInitialized = true
            fillProgress.snapTo(if (selected) 1f else 0f)
            return@LaunchedEffect
        }

        if (reducedMotion) {
            frontShiftX.snapTo(0f)
            frontTilt.snapTo(0f)
            backFan.snapTo(0f)
            sparkProgress.snapTo(0f)
            rippleProgress.snapTo(0f)
            fillProgress.animateTo(if (selected) 1f else 0f, tween(120))
            return@LaunchedEffect
        }

        if (selected) {
            sparkProgress.snapTo(0f)
            rippleProgress.snapTo(0f)
            coroutineScope {
                launch {
                    frontShiftX.animateTo(-2.5f, tween(115, easing = FastOutSlowInEasing))
                    frontShiftX.animateTo(0f, MusicInteractionDefaults.snappySpring())
                }
                launch {
                    frontTilt.animateTo(-9.5f, tween(115, easing = FastOutSlowInEasing))
                    frontTilt.animateTo(0f, MusicInteractionDefaults.snappySpring())
                }
                launch {
                    backFan.animateTo(1f, tween(135, easing = FastOutSlowInEasing))
                    backFan.animateTo(0f, MusicInteractionDefaults.settleSpring())
                }
                launch {
                    delay(40)
                    fillProgress.animateTo(1f, tween(210, easing = FastOutSlowInEasing))
                }
                launch {
                    delay(175)
                    sparkProgress.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
                }
                launch {
                    delay(30)
                    rippleProgress.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
                }
            }
        } else {
            sparkProgress.snapTo(0f)
            rippleProgress.snapTo(0f)
            coroutineScope {
                launch {
                    frontTilt.animateTo(3.5f, tween(80, easing = FastOutSlowInEasing))
                    frontTilt.animateTo(0f, MusicInteractionDefaults.settleSpring())
                }
                launch {
                    fillProgress.animateTo(0f, tween(170, easing = FastOutSlowInEasing))
                }
            }
        }
    }

    TactileActionContainer(
        onClick = onClick,
        enabled = enabled,
        selected = selected,
        contentDesc = contentDescription,
        touchSize = touchSize,
        reducedMotion = reducedMotion,
        modifier = modifier
    ) { pressScale ->
        val alphaFactor = if (enabled) 1f else palette.disabledAlpha

        Canvas(
            modifier = Modifier
                .size(touchSize)
                .graphicsLayer { clip = false }
        ) {
            val iconPx = iconSize.toPx()
            val fill = fillProgress.value
            val tint = lerpColor(palette.inactive, palette.active, fill).copy(alpha = alphaFactor)

            drawCircularRipple(
                progress = rippleProgress.value,
                color = palette.ripple.copy(alpha = alphaFactor),
                maxRadius = size.minDimension * 0.52f,
                strokeWidth = 1.4.dp.toPx()
            )

            inIconViewport(iconPx) {
                withTransform({
                    scale(scaleX = pressScale, scaleY = pressScale, pivot = Offset(12f, 12f))
                }) {
                    val fan = backFan.value
                    withTransform({
                        translate(left = fan * 1.9f, top = -fan * 0.8f)
                        rotate(degrees = 7.5f + fan * 6.5f, pivot = Offset(13.5f, 11.5f))
                    }) {
                        drawPath(
                            path = backCardPath,
                            color = tint.copy(alpha = tint.alpha * lerpFloat(0.62f, 0.85f, fill)),
                            style = Stroke(
                                width = MusicInteractionDefaults.StrokeWidth24 * 0.9f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    withTransform({
                        translate(left = frontShiftX.value, top = 0f)
                        rotate(degrees = frontTilt.value, pivot = Offset(10f, 15f))
                    }) {
                        if (fill > 0.01f) {
                            drawPath(
                                path = frontCardPath,
                                color = palette.cutoutContrast.copy(alpha = clamp01(fill * 1.2f))
                            )
                            clipPath(frontCardPath) {
                                val topY = lerpFloat(21f, 5.5f, fill)
                                drawRect(
                                    color = palette.active.copy(alpha = alphaFactor),
                                    topLeft = Offset(3.5f, topY),
                                    size = Size(14f, 22f - topY)
                                )
                            }
                        }

                        drawPath(
                            path = frontCardPath,
                            color = tint,
                            style = Stroke(
                                width = MusicInteractionDefaults.StrokeWidth24,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )

                        val detailColor = lerpColor(
                            tint.copy(alpha = tint.alpha * 0.85f),
                            palette.cutoutContrast.copy(alpha = alphaFactor),
                            fill
                        )
                        drawCircle(
                            color = detailColor,
                            radius = 2.0f,
                            center = Offset(10.0f, 11.4f),
                            style = if (fill > 0.55f) Fill else Stroke(width = 1.45f)
                        )
                        drawLine(
                            color = detailColor,
                            start = Offset(7.5f, 16.0f),
                            end = Offset(12.5f, 16.0f),
                            strokeWidth = 1.55f,
                            cap = StrokeCap.Round
                        )
                    }

                    drawFourPointSpark(
                        progress = sparkProgress.value,
                        cx = 19.8f,
                        cy = 3.8f,
                        maxArm = 2.3f,
                        color = palette.sparkle,
                        alphaFactor = alphaFactor
                    )
                }
            }
        }
    }
}

// ============================================================================
// 7. LIBRARY TAB â€” HARMONIC COLLECTION SPINES
// ============================================================================

@Composable
fun LibraryTabButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    touchSize: Dp = MusicInteractionDefaults.TouchTargetSize,
    iconSize: Dp = MusicInteractionDefaults.IconSize,
    palette: MusicInteractionPalette = MusicInteractionPalette(),
    reducedMotionOverride: Boolean? = null,
    contentDescription: String = "Library"
) {
    val reducedMotion = rememberReducedMotionEnabled(reducedMotionOverride)
    var hasInitialized by remember { mutableStateOf(false) }

    val lift1 = remember { Animatable(0f) }
    val lift2 = remember { Animatable(0f) }
    val lift3 = remember { Animatable(0f) }
    val leanKick = remember { Animatable(0f) }
    val fillProgress = remember { Animatable(if (selected) 1f else 0f) }
    val rippleProgress = remember { Animatable(0f) }

    val spine1 = remember { buildLibrarySpinePath(left = 3.6f, top = 5.6f, right = 7.2f, bottom = 19.2f) }
    val spine2 = remember { buildLibrarySpinePath(left = 8.9f, top = 4.2f, right = 12.5f, bottom = 19.2f) }
    val spine3 = remember { buildLibrarySpinePath(left = 14.4f, top = 5.6f, right = 18.0f, bottom = 19.2f) }

    LaunchedEffect(selected, reducedMotion) {
        if (!hasInitialized) {
            hasInitialized = true
            fillProgress.snapTo(if (selected) 1f else 0f)
            return@LaunchedEffect
        }

        if (reducedMotion) {
            lift1.snapTo(0f); lift2.snapTo(0f); lift3.snapTo(0f)
            leanKick.snapTo(0f); rippleProgress.snapTo(0f)
            fillProgress.animateTo(if (selected) 1f else 0f, tween(120))
            return@LaunchedEffect
        }

        if (selected) {
            rippleProgress.snapTo(0f)
            coroutineScope {
                launch {
                    lift1.animateTo(-2.4f, tween(95, easing = FastOutSlowInEasing))
                    lift1.animateTo(0f, MusicInteractionDefaults.snappySpring())
                }
                launch {
                    delay(45)
                    lift2.animateTo(-2.8f, tween(95, easing = FastOutSlowInEasing))
                    lift2.animateTo(0f, MusicInteractionDefaults.snappySpring())
                }
                launch {
                    delay(90)
                    lift3.animateTo(-2.2f, tween(95, easing = FastOutSlowInEasing))
                    lift3.animateTo(0f, MusicInteractionDefaults.snappySpring())
                }
                launch {
                    delay(65)
                    leanKick.animateTo(-7.5f, tween(115, easing = FastOutSlowInEasing))
                    leanKick.animateTo(0f, MusicInteractionDefaults.snappySpring())
                }
                launch {
                    delay(35)
                    fillProgress.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
                }
                launch {
                    delay(25)
                    rippleProgress.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
                }
            }
        } else {
            rippleProgress.snapTo(0f)
            coroutineScope {
                launch {
                    lift2.animateTo(1.2f, tween(80, easing = FastOutSlowInEasing))
                    lift2.animateTo(0f, MusicInteractionDefaults.settleSpring())
                }
                launch {
                    fillProgress.animateTo(0f, tween(175, easing = FastOutSlowInEasing))
                }
            }
        }
    }

    TactileActionContainer(
        onClick = onClick,
        enabled = enabled,
        selected = selected,
        contentDesc = contentDescription,
        touchSize = touchSize,
        reducedMotion = reducedMotion,
        modifier = modifier
    ) { pressScale ->
        val alphaFactor = if (enabled) 1f else palette.disabledAlpha

        Canvas(
            modifier = Modifier
                .size(touchSize)
                .graphicsLayer { clip = false }
        ) {
            val iconPx = iconSize.toPx()
            val fill = fillProgress.value
            val tint = lerpColor(palette.inactive, palette.active, fill).copy(alpha = alphaFactor)

            drawCircularRipple(
                progress = rippleProgress.value,
                color = palette.ripple.copy(alpha = alphaFactor),
                maxRadius = size.minDimension * 0.52f,
                strokeWidth = 1.4.dp.toPx()
            )

            inIconViewport(iconPx) {
                withTransform({
                    scale(scaleX = pressScale, scaleY = pressScale, pivot = Offset(12f, 12f))
                }) {
                    drawLibrarySpine(
                        path = spine1,
                        liftY = lift1.value,
                        capY = 8.4f,
                        leftX = 3.6f,
                        rightX = 7.2f,
                        fill = fill,
                        tint = tint,
                        activeColor = palette.active.copy(alpha = alphaFactor)
                    )
                    drawLibrarySpine(
                        path = spine2,
                        liftY = lift2.value,
                        capY = 7.2f,
                        leftX = 8.9f,
                        rightX = 12.5f,
                        fill = fill,
                        tint = tint,
                        activeColor = palette.active.copy(alpha = alphaFactor)
                    )
                    withTransform({
                        rotate(
                            degrees = -12f + leanKick.value,
                            pivot = Offset(14.6f, 19.0f)
                        )
                    }) {
                        drawLibrarySpine(
                            path = spine3,
                            liftY = lift3.value,
                            capY = 8.4f,
                            leftX = 14.4f,
                            rightX = 18.0f,
                            fill = fill,
                            tint = tint,
                            activeColor = palette.active.copy(alpha = alphaFactor)
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawLibrarySpine(
    path: Path,
    liftY: Float,
    capY: Float,
    leftX: Float,
    rightX: Float,
    fill: Float,
    tint: Color,
    activeColor: Color
) {
    withTransform({
        translate(left = 0f, top = liftY)
    }) {
        if (fill > 0.01f) {
            clipPath(path) {
                val fillTop = lerpFloat(20.5f, 3.5f, fill)
                drawRect(
                    color = activeColor,
                    topLeft = Offset(leftX - 1f, fillTop),
                    size = Size(rightX - leftX + 2f, 21f - fillTop)
                )
            }
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(
                width = MusicInteractionDefaults.StrokeWidth24 * 0.9f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        if (fill < 0.85f) {
            drawLine(
                color = tint.copy(alpha = tint.alpha * (1f - fill)),
                start = Offset(leftX + 0.3f, capY),
                end = Offset(rightX - 0.3f, capY),
                strokeWidth = 1.35f,
                cap = StrokeCap.Round
            )
        }
    }
}

// ============================================================================
// 8. YOU TAB â€” TASTE PROFILE & ORBITING AURA
// ============================================================================

@Composable
fun YouTabButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    touchSize: Dp = MusicInteractionDefaults.TouchTargetSize,
    iconSize: Dp = MusicInteractionDefaults.IconSize,
    palette: MusicInteractionPalette = MusicInteractionPalette(),
    reducedMotionOverride: Boolean? = null,
    contentDescription: String = "You"
) {
    val reducedMotion = rememberReducedMotionEnabled(reducedMotionOverride)
    var hasInitialized by remember { mutableStateOf(false) }

    val headLift = remember { Animatable(0f) }
    val shoulderScaleX = remember { Animatable(1f) }
    val fillProgress = remember { Animatable(if (selected) 1f else 0f) }
    val orbitProgress = remember { Animatable(0f) }
    val sparkProgress = remember { Animatable(0f) }

    val shouldersPath = remember { buildYouShouldersPath() }
    val orbitArcPath = remember { buildYouOrbitArcPath() }
    val orbitMeasure = remember { PathMeasure().apply { setPath(orbitArcPath, false) } }
    val partialOrbitPath = remember { Path() }

    LaunchedEffect(selected, reducedMotion) {
        if (!hasInitialized) {
            hasInitialized = true
            fillProgress.snapTo(if (selected) 1f else 0f)
            return@LaunchedEffect
        }

        if (reducedMotion) {
            headLift.snapTo(0f)
            shoulderScaleX.snapTo(1f)
            orbitProgress.snapTo(0f)
            sparkProgress.snapTo(0f)
            fillProgress.animateTo(if (selected) 1f else 0f, tween(120))
            return@LaunchedEffect
        }

        if (selected) {
            orbitProgress.snapTo(0f)
            sparkProgress.snapTo(0f)
            coroutineScope {
                launch {
                    headLift.animateTo(-1.8f, tween(105, easing = FastOutSlowInEasing))
                    headLift.animateTo(0f, MusicInteractionDefaults.snappySpring())
                }
                launch {
                    shoulderScaleX.animateTo(1.10f, tween(115, easing = FastOutSlowInEasing))
                    shoulderScaleX.animateTo(1f, MusicInteractionDefaults.settleSpring())
                }
                launch {
                    fillProgress.animateTo(1f, tween(210, easing = FastOutSlowInEasing))
                }
                launch {
                    delay(40)
                    orbitProgress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
                }
                launch {
                    delay(190)
                    sparkProgress.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
                }
            }
        } else {
            orbitProgress.snapTo(0f)
            sparkProgress.snapTo(0f)
            coroutineScope {
                launch {
                    headLift.animateTo(1.0f, tween(80, easing = FastOutSlowInEasing))
                    headLift.animateTo(0f, MusicInteractionDefaults.settleSpring())
                }
                launch {
                    fillProgress.animateTo(0f, tween(175, easing = FastOutSlowInEasing))
                }
            }
        }
    }

    TactileActionContainer(
        onClick = onClick,
        enabled = enabled,
        selected = selected,
        contentDesc = contentDescription,
        touchSize = touchSize,
        reducedMotion = reducedMotion,
        modifier = modifier
    ) { pressScale ->
        val alphaFactor = if (enabled) 1f else palette.disabledAlpha

        Canvas(
            modifier = Modifier
                .size(touchSize)
                .graphicsLayer { clip = false }
        ) {
            val iconPx = iconSize.toPx()
            val fill = fillProgress.value
            val tint = lerpColor(palette.inactive, palette.active, fill).copy(alpha = alphaFactor)

            inIconViewport(iconPx) {
                withTransform({
                    scale(scaleX = pressScale, scaleY = pressScale, pivot = Offset(12f, 12f))
                }) {
                    val orb = orbitProgress.value
                    if (orb > 0.01f && orb < 0.99f) {
                        val segStart = clamp01((orb - 0.45f) / 0.55f) * orbitMeasure.length
                        val segEnd = clamp01(orb / 0.72f) * orbitMeasure.length
                        if (segEnd > segStart) {
                            partialOrbitPath.reset()
                            orbitMeasure.getSegment(segStart, segEnd, partialOrbitPath, true)
                            val orbAlpha = sin(orb * PI).toFloat() * 0.7f * alphaFactor
                            drawPath(
                                path = partialOrbitPath,
                                color = palette.active.copy(alpha = orbAlpha),
                                style = Stroke(width = 1.4f, cap = StrokeCap.Round)
                            )
                        }
                    }

                    withTransform({
                        translate(left = 0f, top = headLift.value)
                    }) {
                        if (fill > 0.01f) {
                            drawCircle(
                                color = palette.active.copy(alpha = fill * alphaFactor),
                                radius = lerpFloat(0.6f, 3.35f, fill),
                                center = Offset(12f, 7.6f)
                            )
                        }
                        drawCircle(
                            color = tint,
                            radius = 3.35f,
                            center = Offset(12f, 7.6f),
                            style = Stroke(width = MusicInteractionDefaults.StrokeWidth24)
                        )
                    }

                    withTransform({
                        scale(
                            scaleX = shoulderScaleX.value,
                            scaleY = 1f,
                            pivot = Offset(12f, 17f)
                        )
                    }) {
                        if (fill > 0.01f) {
                            clipPath(shouldersPath) {
                                val topY = lerpFloat(21.0f, 12.6f, fill)
                                drawRect(
                                    color = palette.active.copy(alpha = alphaFactor),
                                    topLeft = Offset(4f, topY),
                                    size = Size(16f, 21.5f - topY)
                                )
                            }
                        }
                        drawPath(
                            path = shouldersPath,
                            color = tint,
                            style = Stroke(
                                width = MusicInteractionDefaults.StrokeWidth24,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    drawFourPointSpark(
                        progress = sparkProgress.value,
                        cx = 18.8f,
                        cy = 5.2f,
                        maxArm = 2.1f,
                        color = palette.sparkle,
                        alphaFactor = alphaFactor
                    )
                }
            }
        }
    }
}

// ============================================================================
// 9. PAUSE / RESUME BUTTON â€” TRUE GEOMETRIC PLAY â†” PAUSE MORPH
// ============================================================================

@Composable
fun MorphPlayPauseButton(
    isPlaying: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    touchSize: Dp = MusicInteractionDefaults.TouchTargetSize,
    iconSize: Dp = MusicInteractionDefaults.IconSize,
    palette: MusicInteractionPalette = MusicInteractionPalette(),
    reducedMotionOverride: Boolean? = null,
    contentDescription: String = if (isPlaying) "Pause" else "Play"
) {
    val reducedMotion = rememberReducedMotionEnabled(reducedMotionOverride)
    var hasInitialized by remember { mutableStateOf(false) }

    val morph = remember { Animatable(if (isPlaying) 1f else 0f) }
    val tilt = remember { Animatable(0f) }
    val nudgeX = remember { Animatable(0f) }
    val rippleProgress = remember { Animatable(0f) }

    val leftShapePath = remember { Path() }
    val rightShapePath = remember { Path() }

    LaunchedEffect(isPlaying, reducedMotion) {
        if (!hasInitialized) {
            hasInitialized = true
            morph.snapTo(if (isPlaying) 1f else 0f)
            return@LaunchedEffect
        }

        if (reducedMotion) {
            tilt.snapTo(0f)
            nudgeX.snapTo(0f)
            rippleProgress.snapTo(0f)
            morph.animateTo(if (isPlaying) 1f else 0f, tween(110))
            return@LaunchedEffect
        }

        rippleProgress.snapTo(0f)
        coroutineScope {
            if (isPlaying) {
                launch {
                    morph.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(dampingRatio = 0.62f, stiffness = 460f)
                    )
                }
                launch {
                    tilt.animateTo(-10f, tween(95, easing = FastOutSlowInEasing))
                    tilt.animateTo(0f, MusicInteractionDefaults.snappySpring())
                }
                launch {
                    nudgeX.animateTo(0f, MusicInteractionDefaults.settleSpring())
                }
            } else {
                launch {
                    morph.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(dampingRatio = 0.72f, stiffness = 480f)
                    )
                }
                launch {
                    tilt.animateTo(8f, tween(95, easing = FastOutSlowInEasing))
                    tilt.animateTo(0f, MusicInteractionDefaults.snappySpring())
                }
                launch {
                    nudgeX.animateTo(1.5f, tween(110, easing = FastOutSlowInEasing))
                    nudgeX.animateTo(0f, MusicInteractionDefaults.snappySpring())
                }
            }
            launch {
                rippleProgress.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
            }
        }
    }

    TactileActionContainer(
        onClick = onToggle,
        enabled = enabled,
        selected = null,
        contentDesc = contentDescription,
        touchSize = touchSize,
        reducedMotion = reducedMotion,
        modifier = modifier
    ) { pressScale ->
        val alphaFactor = if (enabled) 1f else palette.disabledAlpha

        Canvas(
            modifier = Modifier
                .size(touchSize)
                .graphicsLayer { clip = false }
        ) {
            val iconPx = iconSize.toPx()
            val iconColor = palette.active.copy(alpha = alphaFactor)

            drawCircularRipple(
                progress = rippleProgress.value,
                color = palette.ripple.copy(alpha = alphaFactor),
                maxRadius = size.minDimension * 0.54f,
                strokeWidth = 1.5.dp.toPx()
            )

            val m = morph.value
            val clampedM = clamp01(m)

            inIconViewport(iconPx) {
                withTransform({
                    translate(left = nudgeX.value, top = 0f)
                    rotate(degrees = tilt.value, pivot = Offset(12f, 12f))
                    scale(scaleX = pressScale, scaleY = pressScale, pivot = Offset(12f, 12f))
                }) {
                    leftShapePath.reset()
                    leftShapePath.moveTo(lerpFloat(7.0f, 6.4f, m), lerpFloat(5.2f, 5.0f, clampedM))
                    leftShapePath.lineTo(lerpFloat(13.15f, 9.6f, m), lerpFloat(8.6f, 5.0f, clampedM))
                    leftShapePath.lineTo(lerpFloat(13.15f, 9.6f, m), lerpFloat(15.4f, 19.0f, clampedM))
                    leftShapePath.lineTo(lerpFloat(7.0f, 6.4f, m), lerpFloat(18.8f, 19.0f, clampedM))
                    leftShapePath.close()

                    rightShapePath.reset()
                    rightShapePath.moveTo(lerpFloat(12.85f, 14.4f, m), lerpFloat(8.6f, 5.0f, clampedM))
                    rightShapePath.lineTo(lerpFloat(19.0f, 17.6f, m), lerpFloat(11.9f, 5.0f, clampedM))
                    rightShapePath.lineTo(lerpFloat(19.0f, 17.6f, m), lerpFloat(12.1f, 19.0f, clampedM))
                    rightShapePath.lineTo(lerpFloat(12.85f, 14.4f, m), lerpFloat(15.4f, 19.0f, clampedM))
                    rightShapePath.close()

                    val roundJoinStroke = Stroke(
                        width = 1.65f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )

                    drawPath(path = leftShapePath, color = iconColor, style = Fill)
                    drawPath(path = leftShapePath, color = iconColor, style = roundJoinStroke)

                    drawPath(path = rightShapePath, color = iconColor, style = Fill)
                    drawPath(path = rightShapePath, color = iconColor, style = roundJoinStroke)
                }
            }
        }
    }
}

// ============================================================================
// CUSTOM ROUNDED VECTOR PATHS (24x24 NORMALIZED VIEWPORT)
// ============================================================================

private fun buildHeartPath(): Path = Path().apply {
    moveTo(12f, 20.1f)
    cubicTo(11.6f, 19.75f, 3.2f, 13.8f, 3.2f, 8.6f)
    cubicTo(3.2f, 5.45f, 5.65f, 3.6f, 8.3f, 3.6f)
    cubicTo(10.0f, 3.6f, 11.35f, 4.45f, 12.0f, 5.55f)
    cubicTo(12.65f, 4.45f, 14.0f, 3.6f, 15.7f, 3.6f)
    cubicTo(18.35f, 3.6f, 20.8f, 5.45f, 20.8f, 8.6f)
    cubicTo(20.8f, 13.8f, 12.4f, 19.75f, 12f, 20.1f)
    close()
}

private fun buildThumbCuffPath(isDown: Boolean): Path = Path().apply {
    val top = if (isDown) 4.2f else 10.6f
    val bottom = if (isDown) 13.4f else 19.8f
    addRoundRect(
        RoundRect(
            left = 3.2f,
            top = top,
            right = 6.4f,
            bottom = bottom,
            cornerRadius = CornerRadius(1.6f, 1.6f)
        )
    )
}

private fun buildThumbHandPath(isDown: Boolean): Path = Path().apply {
    fun y(upY: Float): Float = if (isDown) 24f - upY else upY

    moveTo(8.6f, y(10.6f))
    lineTo(11.7f, y(4.7f))
    cubicTo(12.2f, y(3.75f), 13.5f, y(3.6f), 14.2f, y(4.3f))
    cubicTo(14.7f, y(4.8f), 14.7f, y(5.6f), 14.4f, y(6.5f))
    lineTo(13.5f, y(9.4f))
    lineTo(18.0f, y(9.4f))
    cubicTo(19.6f, y(9.4f), 20.7f, y(10.8f), 20.3f, y(12.4f))
    lineTo(19.0f, y(17.6f))
    cubicTo(18.65f, y(18.95f), 17.45f, y(19.8f), 16.05f, y(19.8f))
    lineTo(10.2f, y(19.8f))
    cubicTo(9.2f, y(19.8f), 8.6f, y(19.1f), 8.6f, y(18.1f))
    close()
}

private fun buildBookmarkBodyPath(): Path = Path().apply {
    moveTo(5.4f, 6.2f)
    cubicTo(5.4f, 4.65f, 6.65f, 3.6f, 8.2f, 3.6f)
    lineTo(15.8f, 3.6f)
    cubicTo(17.35f, 3.6f, 18.6f, 4.65f, 18.6f, 6.2f)
    lineTo(18.6f, 19.1f)
    cubicTo(18.6f, 20.15f, 17.45f, 20.75f, 16.6f, 20.15f)
    lineTo(12.8f, 17.45f)
    cubicTo(12.32f, 17.1f, 11.68f, 17.1f, 11.2f, 17.45f)
    lineTo(7.4f, 20.15f)
    cubicTo(6.55f, 20.75f, 5.4f, 20.15f, 5.4f, 19.1f)
    close()
}

private fun buildSkipTrianglePath(): Path = Path().apply {
    moveTo(5.6f, 6.6f)
    cubicTo(5.6f, 5.55f, 6.75f, 4.9f, 7.65f, 5.45f)
    lineTo(14.55f, 10.85f)
    cubicTo(15.35f, 11.4f, 15.35f, 12.6f, 14.55f, 13.15f)
    lineTo(7.65f, 18.55f)
    cubicTo(6.75f, 19.1f, 5.6f, 18.45f, 5.6f, 17.4f)
    close()
}

private fun buildSkipBarPath(): Path = Path().apply {
    addRoundRect(
        RoundRect(
            left = 16.6f,
            top = 5.5f,
            right = 18.8f,
            bottom = 18.5f,
            cornerRadius = CornerRadius(1.1f, 1.1f)
        )
    )
}

private fun buildUndoArcPath(): Path = Path().apply {
    moveTo(16.6f, 17.4f)
    cubicTo(19.2f, 15.1f, 19.6f, 10.9f, 17.4f, 8.0f)
    cubicTo(15.1f, 5.1f, 10.8f, 4.7f, 7.9f, 7.0f)
    cubicTo(6.9f, 7.8f, 6.2f, 8.8f, 5.8f, 10.0f)
}

private fun buildUndoArrowHeadPath(): Path = Path().apply {
    moveTo(4.1f, 6.8f)
    lineTo(5.6f, 10.3f)
    lineTo(9.2f, 9.3f)
}

private fun buildDiscoverFrontCardPath(): Path = Path().apply {
    addRoundRect(
        RoundRect(
            left = 4.6f,
            top = 6.4f,
            right = 15.6f,
            bottom = 19.8f,
            cornerRadius = CornerRadius(2.2f, 2.2f)
        )
    )
}

private fun buildDiscoverBackCardPath(): Path = Path().apply {
    addRoundRect(
        RoundRect(
            left = 8.4f,
            top = 4.2f,
            right = 18.8f,
            bottom = 17.0f,
            cornerRadius = CornerRadius(2.1f, 2.1f)
        )
    )
}

private fun buildLibrarySpinePath(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float
): Path = Path().apply {
    addRoundRect(
        RoundRect(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            cornerRadius = CornerRadius(1.35f, 1.35f)
        )
    )
}

private fun buildYouShouldersPath(): Path = Path().apply {
    moveTo(5.2f, 18.4f)
    cubicTo(5.2f, 14.9f, 8.1f, 13.2f, 12.0f, 13.2f)
    cubicTo(15.9f, 13.2f, 18.8f, 14.9f, 18.8f, 18.4f)
    cubicTo(18.8f, 19.55f, 17.95f, 20.2f, 16.8f, 20.2f)
    lineTo(7.2f, 20.2f)
    cubicTo(6.05f, 20.2f, 5.2f, 19.55f, 5.2f, 18.4f)
    close()
}

private fun buildYouOrbitArcPath(): Path = Path().apply {
    moveTo(4.0f, 14.0f)
    cubicTo(3.2f, 8.4f, 7.2f, 3.4f, 12.6f, 3.2f)
    cubicTo(16.2f, 3.1f, 19.4f, 5.2f, 20.6f, 8.8f)
}


// ============================================================================
// SETTINGS AND MORPH LIKE PLAY PAUSE COMPONENTS
// ============================================================================

// ============================================================================
// INTERNAL HELPERS
// ============================================================================

private fun lerpVal(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

private fun clamp01F(value: Float): Float = value.coerceIn(0f, 1f)

@Composable
private fun SettingsTactileContainer(
    onClick: () -> Unit,
    enabled: Boolean,
    contentDesc: String,
    touchSize: Dp,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(pressScale: Float) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale = remember { Animatable(1f) }

    LaunchedEffect(isPressed, enabled, reducedMotion) {
        val target = if (isPressed && enabled) MusicInteractionDefaults.PressScale else 1f
        if (reducedMotion) {
            pressScale.snapTo(target)
        } else {
            pressScale.animateTo(
                targetValue = target,
                animationSpec = if (isPressed) {
                    tween(durationMillis = 75, easing = FastOutSlowInEasing)
                } else {
                    MusicInteractionDefaults.snappySpring()
                }
            )
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(touchSize)
            .semantics { contentDescription = contentDesc }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .graphicsLayer { clip = false }
    ) {
        content(pressScale.value)
    }
}

private inline fun DrawScope.inViewport24(
    iconSizePx: Float,
    block: DrawScope.() -> Unit
) {
    val scale = iconSizePx / MusicInteractionDefaults.VectorViewport
    val offsetX = (size.width - iconSizePx) * 0.5f
    val offsetY = (size.height - iconSizePx) * 0.5f
    withTransform({
        translate(left = offsetX, top = offsetY)
        scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
    }) {
        block()
    }
}

private fun DrawScope.drawRingPulse(
    progress: Float,
    color: Color,
    maxRadius: Float,
    strokeWidth: Float
) {
    if (progress <= 0.001f || progress >= 0.999f) return
    val eased = 1f - (1f - progress) * (1f - progress)
    val radius = lerpVal(maxRadius * 0.28f, maxRadius, eased)
    val alpha = (1f - progress) * 0.38f
    drawCircle(
        color = color.copy(alpha = alpha),
        radius = radius,
        center = center,
        style = Stroke(width = strokeWidth * (1f - progress * 0.45f))
    )
}

// ============================================================================
// 1. APPEARANCE ICON â€” THEME CONTRAST ORB & RAYS
// ============================================================================

/**
 * Appearance Settings Icon:
 * - Split-contrast theme orb surrounded by 4 rounded capsule rays.
 * - On tap: rays expand outward & rotate 45Â° while the contrast core rotates 180Â°
 *   with spring overshoot, evoking theme / appearance switching.
 */
@Composable
fun AppearanceSettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    externalTrigger: Int = 0,
    touchSize: Dp = MusicInteractionDefaults.TouchTargetSize,
    iconSize: Dp = MusicInteractionDefaults.IconSize,
    palette: MusicInteractionPalette = MusicInteractionPalette(),
    reducedMotionOverride: Boolean? = null,
    contentDescription: String = "Appearance settings"
) {
    val reducedMotion = rememberReducedMotionEnabled(reducedMotionOverride)
    var localTrigger by remember { mutableIntStateOf(0) }
    val combinedTrigger = localTrigger + externalTrigger

    val coreRotation = remember { Animatable(0f) }
    var targetCoreAngle by remember { mutableFloatStateOf(0f) }
    val rayExpansion = remember { Animatable(0f) }
    val raySpin = remember { Animatable(0f) }
    val rippleProgress = remember { Animatable(0f) }

    val innerOrbPath = remember {
        Path().apply { addOval(Rect(7.4f, 7.4f, 16.6f, 16.6f)) }
    }

    LaunchedEffect(combinedTrigger) {
        if (combinedTrigger == 0) return@LaunchedEffect
        targetCoreAngle += 180f
        if (reducedMotion) {
            coreRotation.snapTo(targetCoreAngle)
            rayExpansion.snapTo(0f)
            raySpin.snapTo(0f)
            rippleProgress.snapTo(0f)
            return@LaunchedEffect
        }

        rippleProgress.snapTo(0f)
        raySpin.snapTo(0f)
        coroutineScope {
            launch {
                coreRotation.animateTo(
                    targetValue = targetCoreAngle,
                    animationSpec = spring(dampingRatio = 0.62f, stiffness = 420f)
                )
            }
            launch {
                rayExpansion.animateTo(1.7f, tween(120, easing = FastOutSlowInEasing))
                rayExpansion.animateTo(0f, MusicInteractionDefaults.snappySpring())
            }
            launch {
                raySpin.animateTo(90f, tween(340, easing = FastOutSlowInEasing))
            }
            launch {
                rippleProgress.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
            }
        }
    }

    SettingsTactileContainer(
        onClick = {
            localTrigger++
            onClick()
        },
        enabled = enabled,
        contentDesc = contentDescription,
        touchSize = touchSize,
        reducedMotion = reducedMotion,
        modifier = modifier
    ) { pressScale ->
        val alphaFactor = if (enabled) 1f else palette.disabledAlpha

        Canvas(
            modifier = Modifier
                .size(touchSize)
                .graphicsLayer { clip = false }
        ) {
            val iconPx = iconSize.toPx()
            val iconColor = palette.active.copy(alpha = alphaFactor)

            drawRingPulse(
                progress = rippleProgress.value,
                color = palette.ripple.copy(alpha = alphaFactor),
                maxRadius = size.minDimension * 0.52f,
                strokeWidth = 1.4.dp.toPx()
            )

            inViewport24(iconPx) {
                withTransform({
                    scale(scaleX = pressScale, scaleY = pressScale, pivot = Offset(12f, 12f))
                }) {
                    // 4 rounded capsule rays
                    val rayOut = rayExpansion.value
                    withTransform({
                        rotate(degrees = raySpin.value, pivot = Offset(12f, 12f))
                    }) {
                        val rayStart = 7.4f + rayOut
                        val rayEnd = 9.0f + rayOut
                        for (i in 0 until 4) {
                            withTransform({
                                rotate(degrees = i * 90f, pivot = Offset(12f, 12f))
                            }) {
                                drawLine(
                                    color = iconColor,
                                    start = Offset(12f, 12f - rayStart),
                                    end = Offset(12f, 12f - rayEnd),
                                    strokeWidth = MusicInteractionDefaults.StrokeWidth24 * 0.9f,
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                    }

                    // Dual-tone contrast core
                    withTransform({
                        rotate(degrees = coreRotation.value, pivot = Offset(12f, 12f))
                    }) {
                        clipPath(innerOrbPath) {
                            drawRect(
                                color = iconColor,
                                topLeft = Offset(12f, 7f),
                                size = Size(6f, 10f)
                            )
                        }
                        drawCircle(
                            color = iconColor,
                            radius = 4.6f,
                            center = Offset(12f, 12f),
                            style = Stroke(width = MusicInteractionDefaults.StrokeWidth24)
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// 2. PLAYBACK ICON â€” ACOUSTIC SPEAKER & STAGGERED WAVES
// ============================================================================

/**
 * Playback Settings Icon:
 * - Rounded speaker housing paired with two curved acoustic wave arcs.
 * - On tap: speaker subtly recoils while inner & outer wave arcs emit sequentially
 *   with PathMeasure arc tracing and spring settle.
 */
@Composable
fun PlaybackSettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    externalTrigger: Int = 0,
    touchSize: Dp = MusicInteractionDefaults.TouchTargetSize,
    iconSize: Dp = MusicInteractionDefaults.IconSize,
    palette: MusicInteractionPalette = MusicInteractionPalette(),
    reducedMotionOverride: Boolean? = null,
    contentDescription: String = "Playback settings"
) {
    val reducedMotion = rememberReducedMotionEnabled(reducedMotionOverride)
    var localTrigger by remember { mutableIntStateOf(0) }
    val combinedTrigger = localTrigger + externalTrigger

    val speakerRecoilX = remember { Animatable(0f) }
    val wave1Pulse = remember { Animatable(0f) }
    val wave2Pulse = remember { Animatable(0f) }
    val speakerFillFlash = remember { Animatable(0f) }

    val speakerPath = remember { buildSpeakerPath() }
    val waveInnerPath = remember { buildWaveArcPath(radius = 3.4f) }
    val waveOuterPath = remember { buildWaveArcPath(radius = 6.2f) }

    LaunchedEffect(combinedTrigger) {
        if (combinedTrigger == 0) return@LaunchedEffect
        if (reducedMotion) {
            speakerRecoilX.snapTo(0f)
            wave1Pulse.snapTo(0f)
            wave2Pulse.snapTo(0f)
            speakerFillFlash.snapTo(0f)
            return@LaunchedEffect
        }

        coroutineScope {
            launch {
                speakerRecoilX.animateTo(-1.3f, tween(85, easing = FastOutSlowInEasing))
                speakerRecoilX.animateTo(0f, MusicInteractionDefaults.snappySpring())
            }
            launch {
                speakerFillFlash.animateTo(1f, tween(110, easing = FastOutSlowInEasing))
                speakerFillFlash.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
            }
            launch {
                wave1Pulse.animateTo(1f, tween(130, easing = FastOutSlowInEasing))
                wave1Pulse.animateTo(0f, MusicInteractionDefaults.settleSpring())
            }
            launch {
                delay(55)
                wave2Pulse.animateTo(1f, tween(145, easing = FastOutSlowInEasing))
                wave2Pulse.animateTo(0f, MusicInteractionDefaults.settleSpring())
            }
        }
    }

    SettingsTactileContainer(
        onClick = {
            localTrigger++
            onClick()
        },
        enabled = enabled,
        contentDesc = contentDescription,
        touchSize = touchSize,
        reducedMotion = reducedMotion,
        modifier = modifier
    ) { pressScale ->
        val alphaFactor = if (enabled) 1f else palette.disabledAlpha

        Canvas(
            modifier = Modifier
                .size(touchSize)
                .graphicsLayer { clip = false }
        ) {
            val iconPx = iconSize.toPx()
            val iconColor = palette.active.copy(alpha = alphaFactor)

            inViewport24(iconPx) {
                withTransform({
                    scale(scaleX = pressScale, scaleY = pressScale, pivot = Offset(12f, 12f))
                }) {
                    // Speaker body
                    withTransform({
                        translate(left = speakerRecoilX.value, top = 0f)
                    }) {
                        val flash = speakerFillFlash.value
                        if (flash > 0.01f) {
                            drawPath(
                                path = speakerPath,
                                color = iconColor.copy(alpha = flash * 0.85f * alphaFactor)
                            )
                        }
                        drawPath(
                            path = speakerPath,
                            color = iconColor,
                            style = Stroke(
                                width = MusicInteractionDefaults.StrokeWidth24,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    // Inner wave arc
                    val w1 = wave1Pulse.value
                    withTransform({
                        translate(left = w1 * 1.1f, top = 0f)
                        scale(scaleX = 1f + w1 * 0.12f, scaleY = 1f + w1 * 0.12f, pivot = Offset(13.5f, 12f))
                    }) {
                        drawPath(
                            path = waveInnerPath,
                            color = iconColor.copy(alpha = (0.88f + w1 * 0.12f) * alphaFactor),
                            style = Stroke(
                                width = MusicInteractionDefaults.StrokeWidth24,
                                cap = StrokeCap.Round
                            )
                        )
                    }

                    // Outer wave arc
                    val w2 = wave2Pulse.value
                    withTransform({
                        translate(left = w2 * 1.6f, top = 0f)
                        scale(scaleX = 1f + w2 * 0.14f, scaleY = 1f + w2 * 0.14f, pivot = Offset(13.5f, 12f))
                    }) {
                        drawPath(
                            path = waveOuterPath,
                            color = iconColor.copy(alpha = (0.72f + w2 * 0.28f) * alphaFactor),
                            style = Stroke(
                                width = MusicInteractionDefaults.StrokeWidth24,
                                cap = StrokeCap.Round
                            )
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// 3. FILTER LIST ICON â€” STAGGERED CASCADE BARS
// ============================================================================

/**
 * Filter List Settings Icon:
 * - Three centered, progressively shorter rounded filter bars with subtle pill nodes.
 * - On tap: bars perform a crisp opposing horizontal glide & spring settle.
 */
@Composable
fun FilterListSettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    externalTrigger: Int = 0,
    touchSize: Dp = MusicInteractionDefaults.TouchTargetSize,
    iconSize: Dp = MusicInteractionDefaults.IconSize,
    palette: MusicInteractionPalette = MusicInteractionPalette(),
    reducedMotionOverride: Boolean? = null,
    contentDescription: String = "Filter list"
) {
    val reducedMotion = rememberReducedMotionEnabled(reducedMotionOverride)
    var localTrigger by remember { mutableIntStateOf(0) }
    val combinedTrigger = localTrigger + externalTrigger

    val row1Shift = remember { Animatable(0f) }
    val row2Shift = remember { Animatable(0f) }
    val row3Shift = remember { Animatable(0f) }
    val nodePop = remember { Animatable(0f) }
    val rippleProgress = remember { Animatable(0f) }

    LaunchedEffect(combinedTrigger) {
        if (combinedTrigger == 0) return@LaunchedEffect
        if (reducedMotion) {
            row1Shift.snapTo(0f); row2Shift.snapTo(0f); row3Shift.snapTo(0f)
            nodePop.snapTo(0f); rippleProgress.snapTo(0f)
            return@LaunchedEffect
        }

        rippleProgress.snapTo(0f)
        coroutineScope {
            launch {
                row1Shift.animateTo(-2.6f, tween(95, easing = FastOutSlowInEasing))
                row1Shift.animateTo(0f, MusicInteractionDefaults.snappySpring())
            }
            launch {
                delay(40)
                row2Shift.animateTo(2.6f, tween(95, easing = FastOutSlowInEasing))
                row2Shift.animateTo(0f, MusicInteractionDefaults.snappySpring())
            }
            launch {
                delay(80)
                row3Shift.animateTo(-1.8f, tween(95, easing = FastOutSlowInEasing))
                row3Shift.animateTo(0f, MusicInteractionDefaults.snappySpring())
            }
            launch {
                nodePop.animateTo(1f, tween(130, easing = FastOutSlowInEasing))
                nodePop.animateTo(0f, MusicInteractionDefaults.settleSpring())
            }
            launch {
                rippleProgress.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
            }
        }
    }

    SettingsTactileContainer(
        onClick = {
            localTrigger++
            onClick()
        },
        enabled = enabled,
        contentDesc = contentDescription,
        touchSize = touchSize,
        reducedMotion = reducedMotion,
        modifier = modifier
    ) { pressScale ->
        val alphaFactor = if (enabled) 1f else palette.disabledAlpha

        Canvas(
            modifier = Modifier
                .size(touchSize)
                .graphicsLayer { clip = false }
        ) {
            val iconPx = iconSize.toPx()
            val iconColor = palette.active.copy(alpha = alphaFactor)

            drawRingPulse(
                progress = rippleProgress.value,
                color = palette.ripple.copy(alpha = alphaFactor),
                maxRadius = size.minDimension * 0.50f,
                strokeWidth = 1.4.dp.toPx()
            )

            inViewport24(iconPx) {
                withTransform({
                    scale(scaleX = pressScale, scaleY = pressScale, pivot = Offset(12f, 12f))
                }) {
                    val pop = nodePop.value
                    // Top bar (wide)
                    withTransform({ translate(left = row1Shift.value, top = 0f) }) {
                        drawLine(
                            color = iconColor,
                            start = Offset(4.2f, 7.2f),
                            end = Offset(19.8f, 7.2f),
                            strokeWidth = MusicInteractionDefaults.StrokeWidth24,
                            cap = StrokeCap.Round
                        )
                        drawCircle(
                            color = palette.cutoutContrast.copy(alpha = alphaFactor),
                            radius = 0.7f * pop,
                            center = Offset(15.6f, 7.2f)
                        )
                    }
                    // Middle bar (medium)
                    withTransform({ translate(left = row2Shift.value, top = 0f) }) {
                        drawLine(
                            color = iconColor,
                            start = Offset(6.8f, 12.0f),
                            end = Offset(17.2f, 12.0f),
                            strokeWidth = MusicInteractionDefaults.StrokeWidth24,
                            cap = StrokeCap.Round
                        )
                        drawCircle(
                            color = palette.cutoutContrast.copy(alpha = alphaFactor),
                            radius = 0.7f * pop,
                            center = Offset(9.2f, 12.0f)
                        )
                    }
                    // Bottom bar (short)
                    withTransform({ translate(left = row3Shift.value, top = 0f) }) {
                        drawLine(
                            color = iconColor,
                            start = Offset(9.6f, 16.8f),
                            end = Offset(14.4f, 16.8f),
                            strokeWidth = MusicInteractionDefaults.StrokeWidth24,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// 4. LIKE BUTTON BECOMING PAUSE / RESUME â€” TRUE 3-WAY CUBIC PATH MORPH
// ============================================================================

enum class LikePlayPauseMode {
    HEART,
    PLAY,
    PAUSE
}

/**
 * Morphs seamlessly between a Heart (outlined or solid via [isLiked]),
 * Play triangle, and Pause bars using continuous 2-loop cubic Bezier interpolation.
 * Rapid mode switching redirects smoothly from the exact in-flight geometry.
 */
@Composable
fun MorphLikePlayPauseButton(
    mode: LikePlayPauseMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLiked: Boolean = true,
    enabled: Boolean = true,
    touchSize: Dp = MusicInteractionDefaults.TouchTargetSize,
    iconSize: Dp = MusicInteractionDefaults.IconSize,
    palette: MusicInteractionPalette = MusicInteractionPalette(),
    reducedMotionOverride: Boolean? = null,
    contentDescription: String = when (mode) {
        LikePlayPauseMode.HEART -> if (isLiked) "Liked" else "Like"
        LikePlayPauseMode.PLAY -> "Play"
        LikePlayPauseMode.PAUSE -> "Pause"
    }
) {
    val reducedMotion = rememberReducedMotionEnabled(reducedMotionOverride)

    val heartLeftPts = remember { buildHeartLeftLoopPoints() }
    val heartRightPts = remember { buildHeartRightLoopPoints() }
    val playLeftPts = remember { buildPlayLeftLoopPoints() }
    val playRightPts = remember { buildPlayRightLoopPoints() }
    val pauseLeftPts = remember { buildPauseLeftLoopPoints() }
    val pauseRightPts = remember { buildPauseRightLoopPoints() }

    fun leftTargetFor(m: LikePlayPauseMode) = when (m) {
        LikePlayPauseMode.HEART -> heartLeftPts
        LikePlayPauseMode.PLAY -> playLeftPts
        LikePlayPauseMode.PAUSE -> pauseLeftPts
    }

    fun rightTargetFor(m: LikePlayPauseMode) = when (m) {
        LikePlayPauseMode.HEART -> heartRightPts
        LikePlayPauseMode.PLAY -> playRightPts
        LikePlayPauseMode.PAUSE -> pauseRightPts
    }

    fun targetFillFor(m: LikePlayPauseMode, liked: Boolean): Float =
        if (m == LikePlayPauseMode.HEART && !liked) 0f else 1f

    var hasInitialized by remember { mutableStateOf(false) }
    val fromLeft = remember { leftTargetFor(mode).copyOf() }
    val toLeft = remember { leftTargetFor(mode).copyOf() }
    val fromRight = remember { rightTargetFor(mode).copyOf() }
    val toRight = remember { rightTargetFor(mode).copyOf() }

    var fromFill by remember { mutableFloatStateOf(targetFillFor(mode, isLiked)) }
    var toFill by remember { mutableFloatStateOf(targetFillFor(mode, isLiked)) }
    var fromOutlineMix by remember { mutableFloatStateOf(if (mode == LikePlayPauseMode.HEART && !isLiked) 1f else 0f) }
    var toOutlineMix by remember { mutableFloatStateOf(fromOutlineMix) }

    val morphProgress = remember { Animatable(1f) }
    val popScale = remember { Animatable(1f) }
    val rippleProgress = remember { Animatable(0f) }

    val leftPath = remember { Path() }
    val rightPath = remember { Path() }
    val seamlessHeartPath = remember { buildSeamlessHeartPath() }

    LaunchedEffect(mode, isLiked, reducedMotion) {
        if (!hasInitialized) {
            hasInitialized = true
            leftTargetFor(mode).copyInto(fromLeft)
            leftTargetFor(mode).copyInto(toLeft)
            rightTargetFor(mode).copyInto(fromRight)
            rightTargetFor(mode).copyInto(toRight)
            val initFill = targetFillFor(mode, isLiked)
            fromFill = initFill
            toFill = initFill
            val initOut = if (mode == LikePlayPauseMode.HEART && !isLiked) 1f else 0f
            fromOutlineMix = initOut
            toOutlineMix = initOut
            morphProgress.snapTo(1f)
            return@LaunchedEffect
        }

        val currentT = morphProgress.value
        for (i in 0 until 24) {
            fromLeft[i] = lerpVal(fromLeft[i], toLeft[i], currentT)
            fromRight[i] = lerpVal(fromRight[i], toRight[i], currentT)
        }
        fromFill = lerpVal(fromFill, toFill, clamp01F(currentT))
        fromOutlineMix = lerpVal(fromOutlineMix, toOutlineMix, clamp01F(currentT))

        leftTargetFor(mode).copyInto(toLeft)
        rightTargetFor(mode).copyInto(toRight)
        toFill = targetFillFor(mode, isLiked)
        toOutlineMix = if (mode == LikePlayPauseMode.HEART && !isLiked) 1f else 0f

        morphProgress.snapTo(0f)

        if (reducedMotion) {
            popScale.snapTo(1f)
            rippleProgress.snapTo(0f)
            morphProgress.animateTo(1f, tween(130))
            return@LaunchedEffect
        }

        rippleProgress.snapTo(0f)
        coroutineScope {
            launch {
                morphProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.66f, stiffness = 430f)
                )
            }
            launch {
                popScale.animateTo(1.14f, tween(105, easing = FastOutSlowInEasing))
                popScale.animateTo(1f, MusicInteractionDefaults.snappySpring())
            }
            launch {
                rippleProgress.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
            }
        }
    }

    SettingsTactileContainer(
        onClick = onClick,
        enabled = enabled,
        contentDesc = contentDescription,
        touchSize = touchSize,
        reducedMotion = reducedMotion,
        modifier = modifier
    ) { pressScale ->
        val alphaFactor = if (enabled) 1f else palette.disabledAlpha

        Canvas(
            modifier = Modifier
                .size(touchSize)
                .graphicsLayer { clip = false }
        ) {
            val iconPx = iconSize.toPx()
            val t = morphProgress.value
            val ct = clamp01F(t)

            drawRingPulse(
                progress = rippleProgress.value,
                color = palette.ripple.copy(alpha = alphaFactor),
                maxRadius = size.minDimension * 0.54f,
                strokeWidth = 1.5.dp.toPx()
            )

            val fillAlpha = lerpVal(fromFill, toFill, ct) * alphaFactor
            val outlineMix = lerpVal(fromOutlineMix, toOutlineMix, ct)
            val tint = palette.active.copy(alpha = alphaFactor)

            inViewport24(iconPx) {
                withTransform({
                    val totalScale = pressScale * popScale.value
                    scale(scaleX = totalScale, scaleY = totalScale, pivot = Offset(12f, 12f))
                }) {
                    writeInterpolatedCubicLoop(fromLeft, toLeft, t, leftPath)
                    writeInterpolatedCubicLoop(fromRight, toRight, t, rightPath)

                    if (fillAlpha > 0.01f) {
                        val roundStroke = Stroke(
                            width = 1.45f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                        val filledColor = tint.copy(alpha = fillAlpha)
                        drawPath(path = leftPath, color = filledColor, style = Fill)
                        drawPath(path = leftPath, color = filledColor, style = roundStroke)
                        drawPath(path = rightPath, color = filledColor, style = Fill)
                        drawPath(path = rightPath, color = filledColor, style = roundStroke)
                    }

                    if (outlineMix > 0.01f) {
                        drawPath(
                            path = seamlessHeartPath,
                            color = palette.inactive.copy(alpha = outlineMix * alphaFactor),
                            style = Stroke(
                                width = MusicInteractionDefaults.StrokeWidth24,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// 5. ABOUT ICON â€” DRAWING INFO STEM, BOUNCING DOT & RING
// ============================================================================

/**
 * About Settings Icon:
 * - Rounded geometric circle with vertical `i` stem and floating dot.
 * - On tap: stem retraces upward, dot hops with elastic spring bounce, and ring pulses.
 */
@Composable
fun AboutSettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    externalTrigger: Int = 0,
    touchSize: Dp = MusicInteractionDefaults.TouchTargetSize,
    iconSize: Dp = MusicInteractionDefaults.IconSize,
    palette: MusicInteractionPalette = MusicInteractionPalette(),
    reducedMotionOverride: Boolean? = null,
    contentDescription: String = "About"
) {
    val reducedMotion = rememberReducedMotionEnabled(reducedMotionOverride)
    var localTrigger by remember { mutableIntStateOf(0) }
    val combinedTrigger = localTrigger + externalTrigger

    val dotHopY = remember { Animatable(0f) }
    val stemDraw = remember { Animatable(1f) }
    val ringScale = remember { Animatable(1f) }
    val rippleProgress = remember { Animatable(0f) }

    LaunchedEffect(combinedTrigger) {
        if (combinedTrigger == 0) return@LaunchedEffect
        if (reducedMotion) {
            dotHopY.snapTo(0f); stemDraw.snapTo(1f)
            ringScale.snapTo(1f); rippleProgress.snapTo(0f)
            return@LaunchedEffect
        }

        stemDraw.snapTo(0.15f)
        rippleProgress.snapTo(0f)
        coroutineScope {
            launch {
                stemDraw.animateTo(1f, tween(210, easing = FastOutSlowInEasing))
            }
            launch {
                delay(45)
                dotHopY.animateTo(-2.1f, tween(110, easing = FastOutSlowInEasing))
                dotHopY.animateTo(0f, spring(dampingRatio = 0.48f, stiffness = 520f))
            }
            launch {
                ringScale.animateTo(1.09f, tween(110, easing = FastOutSlowInEasing))
                ringScale.animateTo(1f, MusicInteractionDefaults.settleSpring())
            }
            launch {
                rippleProgress.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
            }
        }
    }

    SettingsTactileContainer(
        onClick = {
            localTrigger++
            onClick()
        },
        enabled = enabled,
        contentDesc = contentDescription,
        touchSize = touchSize,
        reducedMotion = reducedMotion,
        modifier = modifier
    ) { pressScale ->
        val alphaFactor = if (enabled) 1f else palette.disabledAlpha

        Canvas(
            modifier = Modifier
                .size(touchSize)
                .graphicsLayer { clip = false }
        ) {
            val iconPx = iconSize.toPx()
            val iconColor = palette.active.copy(alpha = alphaFactor)

            drawRingPulse(
                progress = rippleProgress.value,
                color = palette.ripple.copy(alpha = alphaFactor),
                maxRadius = size.minDimension * 0.52f,
                strokeWidth = 1.4.dp.toPx()
            )

            inViewport24(iconPx) {
                val rScale = pressScale * ringScale.value
                withTransform({
                    scale(scaleX = rScale, scaleY = rScale, pivot = Offset(12f, 12f))
                }) {
                    drawCircle(
                        color = iconColor,
                        radius = 8.4f,
                        center = Offset(12f, 12f),
                        style = Stroke(width = MusicInteractionDefaults.StrokeWidth24)
                    )
                }

                withTransform({
                    scale(scaleX = pressScale, scaleY = pressScale, pivot = Offset(12f, 12f))
                }) {
                    // Stem draws upward from bottom
                    val sFrac = clamp01F(stemDraw.value)
                    drawLine(
                        color = iconColor,
                        start = Offset(12f, 16.0f),
                        end = Offset(12f, lerpVal(15.6f, 11.4f, sFrac)),
                        strokeWidth = MusicInteractionDefaults.StrokeWidth24,
                        cap = StrokeCap.Round
                    )

                    // Top dot hops cleanly
                    drawCircle(
                        color = iconColor,
                        radius = 1.15f,
                        center = Offset(12f, 8.1f + dotHopY.value)
                    )
                }
            }
        }
    }
}

// ============================================================================
// 6. SETTINGS ICON â€” ROUNDED 6-TOOTH GEAR STEP ROTATION
// ============================================================================

/**
 * Settings Gear Icon:
 * - Soft rounded 6-tooth geometric gear with clean inner ring.
 * - On tap: advances continuously by +60Â° (1 tooth pitch) with spring overshoot
 *   while the center ring briefly pulses.
 */
@Composable
fun GearSettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    externalTrigger: Int = 0,
    touchSize: Dp = MusicInteractionDefaults.TouchTargetSize,
    iconSize: Dp = MusicInteractionDefaults.IconSize,
    palette: MusicInteractionPalette = MusicInteractionPalette(),
    reducedMotionOverride: Boolean? = null,
    contentDescription: String = "Settings"
) {
    val reducedMotion = rememberReducedMotionEnabled(reducedMotionOverride)
    var localTrigger by remember { mutableIntStateOf(0) }
    val combinedTrigger = localTrigger + externalTrigger

    val gearAngle = remember { Animatable(0f) }
    var targetAngle by remember { mutableFloatStateOf(0f) }
    val innerPulse = remember { Animatable(0f) }
    val rippleProgress = remember { Animatable(0f) }

    val gearPath = remember { buildRoundedGearPath() }

    LaunchedEffect(combinedTrigger) {
        if (combinedTrigger == 0) return@LaunchedEffect
        targetAngle += 60f
        if (reducedMotion) {
            gearAngle.snapTo(targetAngle)
            innerPulse.snapTo(0f)
            rippleProgress.snapTo(0f)
            return@LaunchedEffect
        }

        rippleProgress.snapTo(0f)
        coroutineScope {
            launch {
                gearAngle.animateTo(
                    targetValue = targetAngle,
                    animationSpec = spring(dampingRatio = 0.56f, stiffness = 420f)
                )
            }
            launch {
                innerPulse.animateTo(1f, tween(115, easing = FastOutSlowInEasing))
                innerPulse.animateTo(0f, MusicInteractionDefaults.settleSpring())
            }
            launch {
                rippleProgress.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
            }
        }
    }

    SettingsTactileContainer(
        onClick = {
            localTrigger++
            onClick()
        },
        enabled = enabled,
        contentDesc = contentDescription,
        touchSize = touchSize,
        reducedMotion = reducedMotion,
        modifier = modifier
    ) { pressScale ->
        val alphaFactor = if (enabled) 1f else palette.disabledAlpha

        Canvas(
            modifier = Modifier
                .size(touchSize)
                .graphicsLayer { clip = false }
        ) {
            val iconPx = iconSize.toPx()
            val iconColor = palette.active.copy(alpha = alphaFactor)

            drawRingPulse(
                progress = rippleProgress.value,
                color = palette.ripple.copy(alpha = alphaFactor),
                maxRadius = size.minDimension * 0.54f,
                strokeWidth = 1.4.dp.toPx()
            )

            inViewport24(iconPx) {
                withTransform({
                    scale(scaleX = pressScale, scaleY = pressScale, pivot = Offset(12f, 12f))
                    rotate(degrees = gearAngle.value, pivot = Offset(12f, 12f))
                }) {
                    drawPath(
                        path = gearPath,
                        color = iconColor,
                        style = Stroke(
                            width = MusicInteractionDefaults.StrokeWidth24,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    val pulse = innerPulse.value
                    if (pulse > 0.01f) {
                        drawCircle(
                            color = iconColor.copy(alpha = pulse * 0.85f * alphaFactor),
                            radius = lerpVal(1.2f, 2.75f, pulse),
                            center = Offset(12f, 12f)
                        )
                    }
                    drawCircle(
                        color = iconColor,
                        radius = 2.75f * (1f + pulse * 0.14f),
                        center = Offset(12f, 12f),
                        style = Stroke(width = MusicInteractionDefaults.StrokeWidth24 * 0.92f)
                    )
                }
            }
        }
    }
}

// ============================================================================
// VECTOR PATHS & CUBIC MORPH GEOMETRY (24x24 VIEWPORT)
// ============================================================================

private fun buildSpeakerPath(): Path = Path().apply {
    moveTo(4.6f, 10.2f)
    cubicTo(4.6f, 9.5f, 5.15f, 9.0f, 5.85f, 9.0f)
    lineTo(8.1f, 9.0f)
    lineTo(11.9f, 5.8f)
    cubicTo(12.65f, 5.2f, 13.6f, 5.7f, 13.6f, 6.65f)
    lineTo(13.6f, 17.35f)
    cubicTo(13.6f, 18.3f, 12.65f, 18.8f, 11.9f, 18.2f)
    lineTo(8.1f, 15.0f)
    lineTo(5.85f, 15.0f)
    cubicTo(5.15f, 15.0f, 4.6f, 14.5f, 4.6f, 13.8f)
    close()
}

private fun buildWaveArcPath(radius: Float): Path = Path().apply {
    val cx = 13.4f
    val cy = 12.0f
    addArc(
        oval = Rect(cx - radius, cy - radius, cx + radius, cy + radius),
        startAngleDegrees = -42f,
        sweepAngleDegrees = 84f
    )
}

private fun buildRoundedGearPath(): Path = Path().apply {
    val cx = 12f
    val cy = 12f
    val rValley = 6.25f
    val rShoulder = 8.65f
    val rCrest = 8.95f

    fun px(deg: Float, r: Float): Float = cx + cos(deg * PI / 180.0).toFloat() * r
    fun py(deg: Float, r: Float): Float = cy + sin(deg * PI / 180.0).toFloat() * r

    for (i in 0 until 6) {
        val c = i * 60f - 90f
        val vStart = c - 30f
        val rBase = c - 17f
        val lShoulder = c - 11f
        val crest = c
        val rShoulderDeg = c + 11f
        val fBase = c + 17f

        if (i == 0) {
            moveTo(px(vStart, rValley), py(vStart, rValley))
        } else {
            lineTo(px(vStart, rValley), py(vStart, rValley))
        }
        lineTo(px(rBase, rValley + 0.25f), py(rBase, rValley + 0.25f))
        quadraticTo(
            px(lShoulder, rShoulder), py(lShoulder, rShoulder),
            px(crest, rCrest), py(crest, rCrest)
        )
        quadraticTo(
            px(rShoulderDeg, rShoulder), py(rShoulderDeg, rShoulder),
            px(fBase, rValley + 0.25f), py(fBase, rValley + 0.25f)
        )
    }
    close()
}

private fun buildSeamlessHeartPath(): Path = Path().apply {
    moveTo(12f, 20.0f)
    cubicTo(9.0f, 17.6f, 3.3f, 13.4f, 3.3f, 8.8f)
    cubicTo(3.3f, 5.6f, 5.7f, 3.7f, 8.3f, 3.7f)
    cubicTo(10.1f, 3.7f, 11.4f, 4.5f, 12.0f, 5.6f)
    cubicTo(12.6f, 4.5f, 13.9f, 3.7f, 15.7f, 3.7f)
    cubicTo(18.3f, 3.7f, 20.7f, 5.6f, 20.7f, 8.8f)
    cubicTo(20.7f, 13.4f, 15.0f, 17.6f, 12f, 20.0f)
    close()
}

private fun linearQuadLoop(
    ax0: Float, ay0: Float,
    ax1: Float, ay1: Float,
    ax2: Float, ay2: Float,
    ax3: Float, ay3: Float
): FloatArray = floatArrayOf(
    ax0, ay0, lerpVal(ax0, ax1, 1f / 3f), lerpVal(ay0, ay1, 1f / 3f), lerpVal(ax0, ax1, 2f / 3f), lerpVal(ay0, ay1, 2f / 3f),
    ax1, ay1, lerpVal(ax1, ax2, 1f / 3f), lerpVal(ay1, ay2, 1f / 3f), lerpVal(ax1, ax2, 2f / 3f), lerpVal(ay1, ay2, 2f / 3f),
    ax2, ay2, lerpVal(ax2, ax3, 1f / 3f), lerpVal(ay2, ay3, 1f / 3f), lerpVal(ax2, ax3, 2f / 3f), lerpVal(ay2, ay3, 2f / 3f),
    ax3, ay3, lerpVal(ax3, ax0, 1f / 3f), lerpVal(ay3, ay0, 1f / 3f), lerpVal(ax3, ax0, 2f / 3f), lerpVal(ay3, ay0, 2f / 3f)
)

private fun buildHeartLeftLoopPoints(): FloatArray = floatArrayOf(
    12.05f, 5.6f, 12.05f, 10.4f, 12.05f, 15.2f,
    12.05f, 20.0f, 9.0f, 17.6f, 3.3f, 13.4f,
    3.3f, 8.8f, 3.3f, 5.6f, 5.7f, 3.7f,
    8.3f, 3.7f, 10.1f, 3.7f, 11.4f, 4.5f
)

private fun buildHeartRightLoopPoints(): FloatArray = floatArrayOf(
    11.95f, 5.6f, 12.6f, 4.5f, 13.9f, 3.7f,
    15.7f, 3.7f, 18.3f, 3.7f, 20.7f, 5.6f,
    20.7f, 8.8f, 20.7f, 13.4f, 15.0f, 17.6f,
    11.95f, 20.0f, 11.95f, 15.2f, 11.95f, 10.4f
)

private fun buildPlayLeftLoopPoints(): FloatArray = linearQuadLoop(
    13.15f, 8.6f,
    13.15f, 15.4f,
    7.0f, 18.8f,
    7.0f, 5.2f
)

private fun buildPlayRightLoopPoints(): FloatArray = linearQuadLoop(
    12.85f, 8.6f,
    19.0f, 11.9f,
    19.0f, 12.1f,
    12.85f, 15.4f
)

private fun buildPauseLeftLoopPoints(): FloatArray = linearQuadLoop(
    9.6f, 5.0f,
    9.6f, 19.0f,
    6.4f, 19.0f,
    6.4f, 5.0f
)

private fun buildPauseRightLoopPoints(): FloatArray = linearQuadLoop(
    14.4f, 5.0f,
    17.6f, 5.0f,
    17.6f, 19.0f,
    14.4f, 19.0f
)

private fun writeInterpolatedCubicLoop(
    from: FloatArray,
    to: FloatArray,
    t: Float,
    outPath: Path
) {
    fun p(idx: Int): Float = lerpVal(from[idx], to[idx], t)
    outPath.reset()
    outPath.moveTo(p(0), p(1))
    outPath.cubicTo(p(2), p(3), p(4), p(5), p(6), p(7))
    outPath.cubicTo(p(8), p(9), p(10), p(11), p(12), p(13))
    outPath.cubicTo(p(14), p(15), p(16), p(17), p(18), p(19))
    outPath.cubicTo(p(20), p(21), p(22), p(23), p(0), p(1))
    outPath.close()
}
