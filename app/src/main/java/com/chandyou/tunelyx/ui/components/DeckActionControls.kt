package com.chandyou.tunelyx.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.chandyou.tunelyx.model.SwipeDirection
import com.chandyou.tunelyx.model.Track
import com.chandyou.tunelyx.ui.theme.TunelyxTheme
import kotlinx.coroutines.launch

@Composable
fun DeckActionControls(
    currentTrack: Track?,
    activeDragDirection: SwipeDirection?,
    dragIntensity: Float, // 0f to 1f
    canUndo: Boolean = false,
    isPlaying: Boolean = true,
    isHapticEnabled: Boolean = true,
    onActionUndo: () -> Unit = {},
    onActionDislike: () -> Unit,
    onActionSkip: () -> Unit,
    onActionBookmark: () -> Unit,
    onActionLike: () -> Unit,
    onResumeAudio: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = TunelyxTheme.colors

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .shadow(
                elevation = if (colors.isDark) 0.dp else 6.dp,
                shape = CircleShape,
                spotColor = Color(0x24000000)
            )
            .clip(CircleShape)
            .background(colors.bgGlass)
            .border(
                width = 1.dp,
                color = colors.borderSubtle,
                shape = CircleShape
            )
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        DiscoveryActionButtonRow(
            currentTrack = currentTrack,
            activeDragDirection = activeDragDirection,
            dragIntensity = dragIntensity,
            canUndo = canUndo,
            isPlaying = isPlaying,
            isHapticEnabled = isHapticEnabled,
            onActionUndo = onActionUndo,
            onActionDislike = onActionDislike,
            onActionSkip = onActionSkip,
            onActionBookmark = onActionBookmark,
            onActionLike = onActionLike,
            onResumeAudio = onResumeAudio
        )
    }
}

/**
 * Shared reusable row of action buttons (Undo, Bookmark, Like/Resume, Dislike, Skip).
 * Used directly by both DeckActionControls and ImmersiveDock to guarantee perfectly identical
 * button silhouettes, colors, and animations.
 */
@Composable
fun DiscoveryActionButtonRow(
    currentTrack: Track?,
    activeDragDirection: SwipeDirection?,
    dragIntensity: Float,
    canUndo: Boolean,
    isPlaying: Boolean,
    isHapticEnabled: Boolean,
    neutralButtonBg: Color? = null,
    neutralButtonBorder: Color? = null,
    neutralIconTint: Color? = null,
    likeFillColor: Color? = null,
    likeContentTint: Color? = null,
    centerButtonSizeDp: Int = 48,
    secondaryButtonSizeDp: Int = 42,
    undoButtonSizeDp: Int = 42,
    bookmarkIcon: ImageVector = Icons.Rounded.Bookmark,
    showCenterButton: Boolean = true,
    outerButtonsAlpha: Float = 1.0f,
    outerButtonsScale: Float = 1.0f,
    onActionUndo: () -> Unit,
    onActionDislike: () -> Unit,
    onActionSkip: () -> Unit,
    onActionBookmark: () -> Unit,
    onActionLike: () -> Unit,
    onResumeAudio: () -> Unit
) {
    val colors = TunelyxTheme.colors
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val resolvedNeutralIcon = neutralIconTint ?: colors.textPrimary

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val outerModifier = Modifier
            .scale(outerButtonsScale)
            .graphicsLayer { alpha = outerButtonsAlpha }
        val outerEnabled = outerButtonsAlpha > 0.5f

        // 0. Undo Button (Far Left) with Full 360 Spin on Icon
        val undoInteractionSource = remember { MutableInteractionSource() }
        val isUndoPressed by undoInteractionSource.collectIsPressedAsState()
        var undoSpinCount by remember { mutableIntStateOf(0) }
        val undoScale by animateFloatAsState(
            targetValue = if (isUndoPressed) 0.88f else 1.0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
            label = "undoScale"
        )
        val undoRotation by animateFloatAsState(
            targetValue = (undoSpinCount * -360f) + (if (isUndoPressed) -20f else 0f),
            animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
            label = "undoIconRotation"
        )
        Box(
            modifier = outerModifier
                .scale(undoScale)
                .size(undoButtonSizeDp.dp)
                .clip(CircleShape)
                .background(
                    if (canUndo) {
                        neutralButtonBg ?: Color(0x1AFFFFFF)
                    } else {
                        Color.Transparent
                    }
                )
                .let { mod ->
                    if (canUndo && neutralButtonBorder != null) {
                        mod.border(0.8.dp, neutralButtonBorder, CircleShape)
                    } else {
                        mod
                    }
                }
                .clickable(
                    enabled = canUndo && outerEnabled,
                    interactionSource = undoInteractionSource,
                    indication = null,
                    onClick = {
                        if (isHapticEnabled) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                        undoSpinCount++
                        onActionUndo()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Undo,
                contentDescription = "Undo",
                tint = if (canUndo) (neutralIconTint ?: colors.textPrimary) else resolvedNeutralIcon.copy(alpha = 0.35f),
                modifier = Modifier
                    .size(18.dp)
                    .rotate(undoRotation)
            )
        }

        // 1. Bookmark / Add to Collection (Elastic Drop & Scale on Icon)
        val isBookmarkActive = activeDragDirection == SwipeDirection.BOOKMARK_INAPP
        val bookmarkScale by animateFloatAsState(
            targetValue = if (isBookmarkActive) 1f + 0.18f * dragIntensity else 1.0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "bookmarkScale"
        )
        ActionButton(
            icon = bookmarkIcon,
            contentDescription = "Bookmark",
            scale = bookmarkScale,
            sizeDp = secondaryButtonSizeDp,
            isDragActive = isBookmarkActive,
            dragIntensity = dragIntensity,
            activeIconScale = 1.24f,
            activeIconOffsetY = 3f,
            tint = if (isBookmarkActive) colors.bookmarkPurple else resolvedNeutralIcon,
            glowColor = if (isBookmarkActive) colors.bookmarkPurpleGlow else Color.Transparent,
            neutralBg = neutralButtonBg,
            neutralBorder = neutralButtonBorder,
            enabled = outerEnabled,
            modifier = outerModifier,
            onClick = {
                if (isHapticEnabled) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                onActionBookmark()
            }
        )

        // 2. Like / Resume (Center Prominent with Dynamic Double-Pulse Heartbeat & Tilt on Icon)
        if (showCenterButton) {
            val isLikeActive = activeDragDirection == SwipeDirection.LIKE
            var isHeartBeating by remember { mutableStateOf(false) }

            // Dynamic color sync with track gradient
            val dynamicTrackColor = currentTrack?.glowColor ?: colors.accentGreen
            val dynamicTrackContent = colors.onAccent

            val resolvedLikeBg by animateColorAsState(
                targetValue = if (isLikeActive) {
                    dynamicTrackColor
                } else if (!isPlaying && currentTrack != null) {
                    colors.bgSurfaceElevated
                } else {
                    likeFillColor ?: dynamicTrackColor
                },
                animationSpec = tween(180),
                label = "likeBg"
            )

            val resolvedLikeContent by animateColorAsState(
                targetValue = if (!isPlaying && currentTrack != null) {
                    colors.textPrimary
                } else {
                    likeContentTint ?: dynamicTrackContent
                },
                animationSpec = tween(180),
                label = "likeContent"
            )

            val likeScale by animateFloatAsState(
                targetValue = if (isLikeActive) 1f + 0.22f * dragIntensity else 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "likeScale"
            )

            val likeInteractionSource = remember { MutableInteractionSource() }
            val isLikePressed by likeInteractionSource.collectIsPressedAsState()
            val likePressScale by animateFloatAsState(
                targetValue = if (isLikePressed) 0.88f else 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
                label = "likePressScale"
            )

            val heartBeatPulse = remember { Animatable(1f) }
            val heartTilt = remember { Animatable(0f) }

            val heartScale by animateFloatAsState(
                targetValue = if (isLikeActive || isLikePressed || isHeartBeating) 1.28f else 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "heartScale"
            )

            Box(
                modifier = Modifier
                    .scale(likeScale * likePressScale),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(centerButtonSizeDp.dp)
                        .shadow(
                            elevation = if (isLikeActive) 8.dp else 4.dp,
                            shape = CircleShape,
                            spotColor = colors.accentGreenGlow
                        )
                        .clip(CircleShape)
                        .background(resolvedLikeBg)
                        .clickable(
                            interactionSource = likeInteractionSource,
                            indication = null,
                            onClick = {
                                if (isPlaying || currentTrack == null) {
                                    if (currentTrack != null) {
                                        if (isHapticEnabled) {
                                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                        }
                                        scope.launch {
                                            isHeartBeating = true
                                            // Tactile double heartbeat pulse with playful tilt on the heart icon
                                            launch {
                                                heartTilt.animateTo(-8f, tween(70))
                                                heartTilt.animateTo(8f, tween(90))
                                                heartTilt.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                            }
                                            heartBeatPulse.animateTo(1.35f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh))
                                            heartBeatPulse.animateTo(1.08f, tween(70))
                                            heartBeatPulse.animateTo(1.24f, tween(80))
                                            heartBeatPulse.animateTo(1.0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                            isHeartBeating = false
                                            onActionLike()
                                        }
                                    }
                                } else {
                                    if (isHapticEnabled) {
                                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    }
                                    onResumeAudio()
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = isPlaying,
                        transitionSpec = {
                            (fadeIn(tween(150)) + scaleIn(initialScale = 0.72f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)))
                                .togetherWith(fadeOut(tween(110)) + scaleOut(targetScale = 0.72f))
                        },
                        label = "centerIconMorph"
                    ) { playing ->
                        Icon(
                            imageVector = if (playing) Icons.Rounded.Favorite else Icons.Rounded.PlayArrow,
                            contentDescription = if (playing) "Like Track" else "Resume Track",
                            tint = resolvedLikeContent,
                            modifier = Modifier
                                .size(23.dp)
                                .scale(heartScale * heartBeatPulse.value)
                                .rotate(heartTilt.value)
                        )
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.size(centerButtonSizeDp.dp))
        }

        // 3. Dislike (Snappy Shake & Tilt on Icon)
        val isDislikeActive = activeDragDirection == SwipeDirection.DISLIKE
        val dislikeScale by animateFloatAsState(
            targetValue = if (isDislikeActive) 1f + 0.18f * dragIntensity else 1.0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "dislikeScale"
        )
        ActionButton(
            icon = Icons.Rounded.Close,
            contentDescription = "Dislike",
            scale = dislikeScale,
            sizeDp = secondaryButtonSizeDp,
            isDragActive = isDislikeActive,
            dragIntensity = dragIntensity,
            activeIconScale = 1.22f,
            activeIconRotation = -24f,
            activeIconOffsetX = -2f,
            tint = if (isDislikeActive) colors.dislikeRed else resolvedNeutralIcon,
            glowColor = if (isDislikeActive) colors.dislikeRedGlow else Color.Transparent,
            neutralBg = neutralButtonBg,
            neutralBorder = neutralButtonBorder,
            enabled = outerEnabled,
            modifier = outerModifier,
            onClick = {
                if (isHapticEnabled) {
                    view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                }
                onActionDislike()
            }
        )

        // 4. Skip (Forward Slide Nudge on Icon)
        val isSkipActive = activeDragDirection == SwipeDirection.SKIP_NEUTRAL
        val skipScale by animateFloatAsState(
            targetValue = if (isSkipActive) 1f + 0.18f * dragIntensity else 1.0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "skipScale"
        )
        ActionButton(
            icon = Icons.Rounded.SkipNext,
            contentDescription = "Skip",
            scale = skipScale,
            sizeDp = secondaryButtonSizeDp,
            isDragActive = isSkipActive,
            dragIntensity = dragIntensity,
            activeIconScale = 1.22f,
            activeIconOffsetX = 4f,
            tint = if (isSkipActive) colors.skipAmber else resolvedNeutralIcon,
            glowColor = if (isSkipActive) colors.skipAmberGlow else Color.Transparent,
            neutralBg = neutralButtonBg,
            neutralBorder = neutralButtonBorder,
            enabled = outerEnabled,
            modifier = outerModifier,
            onClick = {
                if (isHapticEnabled) {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                onActionSkip()
            }
        )
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    contentDescription: String,
    scale: Float,
    sizeDp: Int = 44,
    isDragActive: Boolean = false,
    dragIntensity: Float = 0f,
    activeIconScale: Float = 1.22f,
    activeIconRotation: Float = 0f,
    activeIconOffsetX: Float = 0f,
    activeIconOffsetY: Float = 0f,
    tint: Color,
    glowColor: Color,
    neutralBg: Color? = null,
    neutralBorder: Color? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = TunelyxTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isExcited = isPressed || isDragActive

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "btnPressScale"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (isExcited) activeIconScale else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "btnIconScale"
    )
    val iconRotation by animateFloatAsState(
        targetValue = if (isExcited) activeIconRotation else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "btnIconRotation"
    )
    val iconOffsetX by animateFloatAsState(
        targetValue = if (isExcited) activeIconOffsetX else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "btnIconOffsetX"
    )
    val iconOffsetY by animateFloatAsState(
        targetValue = if (isExcited) activeIconOffsetY else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "btnIconOffsetY"
    )

    val restingBg = neutralBg ?: if (colors.isDark) Color(0x14FFFFFF) else Color(0x0C000000)
    val restingBorder = neutralBorder ?: colors.borderSubtle

    Box(
        modifier = modifier
            .scale(scale * pressScale),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(sizeDp.dp)
                .shadow(
                    elevation = if (glowColor != Color.Transparent) 6.dp else 0.dp,
                    shape = CircleShape,
                    spotColor = glowColor
                )
                .clip(CircleShape)
                .background(if (glowColor != Color.Transparent) glowColor else restingBg)
                .border(1.dp, if (glowColor != Color.Transparent) tint else restingBorder, CircleShape)
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier
                    .size(19.dp)
                    .scale(iconScale)
                    .rotate(iconRotation)
                    .offset(x = iconOffsetX.dp, y = iconOffsetY.dp)
            )
        }
    }
}
