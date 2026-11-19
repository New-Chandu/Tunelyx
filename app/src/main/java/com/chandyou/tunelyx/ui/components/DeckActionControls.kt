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
import com.chandyou.tunelyx.ui.animations.CinematicSkipButton
import com.chandyou.tunelyx.ui.animations.ConstructBookmarkButton
import com.chandyou.tunelyx.ui.animations.ElasticLikeButton
import com.chandyou.tunelyx.ui.animations.FluidDislikeButton
import com.chandyou.tunelyx.ui.animations.MorphPlayPauseButton
import com.chandyou.tunelyx.ui.animations.MusicInteractionPalette
import com.chandyou.tunelyx.ui.animations.RewindUndoButton
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
                elevation = if (colors.isDark) 18.dp else 10.dp,
                shape = CircleShape,
                ambientColor = if (colors.isDark) Color(0xA0000000) else Color(0x22000000),
                spotColor = if (colors.isDark) Color(0xFF000000) else Color(0x38000000)
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

        // 0. Undo Button (Far Left) with Rewind Arc & Arrow Reveal
        RewindUndoButton(
            onUndo = {
                if (isHapticEnabled) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                onActionUndo()
            },
            enabled = canUndo && outerEnabled,
            touchSize = undoButtonSizeDp.dp,
            iconSize = (undoButtonSizeDp * 0.52f).dp,
            palette = MusicInteractionPalette(
                active = neutralIconTint ?: colors.textPrimary,
                inactive = resolvedNeutralIcon.copy(alpha = 0.35f),
                ripple = colors.accentGreenGlow
            ),
            modifier = outerModifier
                .clip(CircleShape)
                .background(if (canUndo) (neutralButtonBg ?: Color(0x1AFFFFFF)) else Color.Transparent)
                .let { mod ->
                    if (canUndo && neutralButtonBorder != null) {
                        mod.border(0.8.dp, neutralButtonBorder, CircleShape)
                    } else mod
                }
        )

        // 1. Bookmark / Add to Collection (Constructing Liquid Wave & Sparkles)
        val isBookmarkActive = activeDragDirection == SwipeDirection.BOOKMARK_INAPP
        val bookmarkScale by animateFloatAsState(
            targetValue = if (isBookmarkActive) 1f + 0.18f * dragIntensity else 1.0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "bookmarkScale"
        )
        ConstructBookmarkButton(
            isSaved = isBookmarkActive,
            onToggle = {
                if (isHapticEnabled) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                onActionBookmark()
            },
            enabled = outerEnabled,
            touchSize = secondaryButtonSizeDp.dp,
            iconSize = (secondaryButtonSizeDp * 0.55f).dp,
            palette = MusicInteractionPalette(
                active = colors.bookmarkPurple,
                inactive = resolvedNeutralIcon,
                ripple = colors.bookmarkPurpleGlow,
                sparkle = colors.bookmarkPurple
            ),
            modifier = outerModifier
                .scale(bookmarkScale)
                .shadow(
                    elevation = if (isBookmarkActive) 8.dp else (if (colors.isDark) 6.dp else 4.dp),
                    shape = CircleShape,
                    spotColor = if (isBookmarkActive) colors.bookmarkPurpleGlow else Color(0x20000000)
                )
                .clip(CircleShape)
                .background(if (isBookmarkActive) colors.bookmarkPurple.copy(alpha = 0.22f) else (neutralButtonBg ?: Color(0x1AFFFFFF)))
                .border(
                    width = 1.dp,
                    color = if (isBookmarkActive) colors.bookmarkPurple else (neutralButtonBorder ?: colors.borderSubtle),
                    shape = CircleShape
                )
        )

        // 2. Like / Resume (Center Prominent with Elastic Heart & Particle Sparkles / Morph Play-Pause)
        if (showCenterButton) {
            val isLikeActive = activeDragDirection == SwipeDirection.LIKE
            val dynamicTrackColor = currentTrack?.glowColor ?: colors.accentGreen

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

            val likeScale by animateFloatAsState(
                targetValue = if (isLikeActive) 1f + 0.22f * dragIntensity else 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "likeScale"
            )

            Box(
                modifier = Modifier
                    .scale(likeScale)
                    .size(centerButtonSizeDp.dp)
                    .shadow(
                        elevation = if (isLikeActive) 8.dp else 4.dp,
                        shape = CircleShape,
                        spotColor = colors.accentGreenGlow
                    )
                    .clip(CircleShape)
                    .background(resolvedLikeBg),
                contentAlignment = Alignment.Center
            ) {
                if (isPlaying || currentTrack == null) {
                    ElasticLikeButton(
                        isLiked = isLikeActive,
                        onToggle = {
                            if (currentTrack != null) {
                                if (isHapticEnabled) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                }
                                onActionLike()
                            }
                        },
                        touchSize = centerButtonSizeDp.dp,
                        iconSize = (centerButtonSizeDp * 0.52f).dp,
                        palette = MusicInteractionPalette(
                            active = colors.onAccent,
                            inactive = colors.onAccent,
                            ripple = colors.accentGreenGlow,
                            sparkle = Color.White
                        )
                    )
                } else {
                    MorphPlayPauseButton(
                        isPlaying = false,
                        onToggle = {
                            if (isHapticEnabled) {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            }
                            onResumeAudio()
                        },
                        touchSize = centerButtonSizeDp.dp,
                        iconSize = (centerButtonSizeDp * 0.52f).dp,
                        palette = MusicInteractionPalette(
                            active = colors.textPrimary,
                            inactive = colors.textPrimary,
                            ripple = colors.accentGreenGlow
                        )
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.size(centerButtonSizeDp.dp))
        }

        // 3. Dislike (Fluid Compression Squish & Tilt)
        val isDislikeActive = activeDragDirection == SwipeDirection.DISLIKE
        val dislikeScale by animateFloatAsState(
            targetValue = if (isDislikeActive) 1f + 0.18f * dragIntensity else 1.0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "dislikeScale"
        )
        FluidDislikeButton(
            isDisliked = isDislikeActive,
            onToggle = {
                if (isHapticEnabled) {
                    view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                }
                onActionDislike()
            },
            enabled = outerEnabled,
            touchSize = secondaryButtonSizeDp.dp,
            iconSize = (secondaryButtonSizeDp * 0.55f).dp,
            palette = MusicInteractionPalette(
                active = colors.dislikeRed,
                inactive = resolvedNeutralIcon,
                ripple = colors.dislikeRedGlow
            ),
            modifier = outerModifier
                .scale(dislikeScale)
                .shadow(
                    elevation = if (isDislikeActive) 8.dp else (if (colors.isDark) 6.dp else 4.dp),
                    shape = CircleShape,
                    spotColor = if (isDislikeActive) colors.dislikeRedGlow else Color(0x20000000)
                )
                .clip(CircleShape)
                .background(if (isDislikeActive) colors.dislikeRed.copy(alpha = 0.22f) else (neutralButtonBg ?: Color(0x1AFFFFFF)))
                .border(
                    width = 1.dp,
                    color = if (isDislikeActive) colors.dislikeRed else (neutralButtonBorder ?: colors.borderSubtle),
                    shape = CircleShape
                )
        )

        // 4. Skip (Cinematic Directional Impulse & Echo Wave)
        val isSkipActive = activeDragDirection == SwipeDirection.SKIP_NEUTRAL
        val skipScale by animateFloatAsState(
            targetValue = if (isSkipActive) 1f + 0.18f * dragIntensity else 1.0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "skipScale"
        )
        CinematicSkipButton(
            onSkip = {
                if (isHapticEnabled) {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                onActionSkip()
            },
            enabled = outerEnabled,
            touchSize = secondaryButtonSizeDp.dp,
            iconSize = (secondaryButtonSizeDp * 0.55f).dp,
            palette = MusicInteractionPalette(
                active = if (isSkipActive) colors.skipAmber else resolvedNeutralIcon,
                inactive = resolvedNeutralIcon,
                ripple = colors.skipAmberGlow
            ),
            modifier = outerModifier
                .scale(skipScale)
                .shadow(
                    elevation = if (isSkipActive) 8.dp else (if (colors.isDark) 6.dp else 4.dp),
                    shape = CircleShape,
                    spotColor = if (isSkipActive) colors.skipAmberGlow else Color(0x20000000)
                )
                .clip(CircleShape)
                .background(if (isSkipActive) colors.skipAmber.copy(alpha = 0.22f) else (neutralButtonBg ?: Color(0x1AFFFFFF)))
                .border(
                    width = 1.dp,
                    color = if (isSkipActive) colors.skipAmber else (neutralButtonBorder ?: colors.borderSubtle),
                    shape = CircleShape
                )
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
