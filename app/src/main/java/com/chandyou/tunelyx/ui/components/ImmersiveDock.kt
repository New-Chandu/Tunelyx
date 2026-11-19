package com.chandyou.tunelyx.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.chandyou.tunelyx.model.SwipeDirection
import com.chandyou.tunelyx.model.Track
import com.chandyou.tunelyx.ui.animations.DiscoverTabButton
import com.chandyou.tunelyx.ui.animations.ElasticLikeButton
import com.chandyou.tunelyx.ui.animations.LibraryTabButton
import com.chandyou.tunelyx.ui.animations.LikePlayPauseMode
import com.chandyou.tunelyx.ui.animations.MorphLikePlayPauseButton
import com.chandyou.tunelyx.ui.animations.MorphPlayPauseButton
import com.chandyou.tunelyx.ui.animations.MusicInteractionPalette
import com.chandyou.tunelyx.ui.animations.YouTabButton
import com.chandyou.tunelyx.ui.theme.ArtworkAmbientHelper
import com.chandyou.tunelyx.ui.theme.SpotifyGreen
import com.chandyou.tunelyx.ui.theme.TunelyxTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class DockUpperMode {
    DISCOVER_ACTIONS,
    MINI_PLAYER,
    COLLAPSED
}

/**
 * Unified adaptive floating dock integrating the discovery/mini-player tray (extending cleanly
 * underneath the nav bar corners), the 66.dp white rounded navigation capsule in front,
 * shared-element Discover <-> Library Like <-> Play/Pause coordination, and bottom-sheet slide for Account.
 */
@Composable
fun ImmersiveDock(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    activeTrack: Track?,
    isPlaying: Boolean,
    hasActiveAudioSession: Boolean,
    activeDragDirection: SwipeDirection? = null,
    dragIntensity: Float = 0f,
    activeDragDirectionFlow: StateFlow<SwipeDirection?>? = null,
    dragIntensityFlow: StateFlow<Float>? = null,
    canUndo: Boolean,
    isHapticEnabled: Boolean,
    onActionUndo: () -> Unit,
    onActionDislike: () -> Unit,
    onActionSkip: () -> Unit,
    onActionBookmark: () -> Unit,
    onActionLike: () -> Unit,
    onResumeAudio: () -> Unit,
    onMiniPlayerTogglePlayPause: () -> Unit,
    showLabels: Boolean = true,
    modifier: Modifier = Modifier
) {
    val currentDragDirection = activeDragDirectionFlow?.collectAsState()?.value ?: activeDragDirection
    val currentDragIntensity = dragIntensityFlow?.collectAsState()?.value ?: dragIntensity
    val colors = TunelyxTheme.colors
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val reducedMotion = remember(context) { ArtworkAmbientHelper.isReducedMotionEnabled(context) }

    val upperMode = when {
        currentTab == MainTab.DISCOVER -> DockUpperMode.DISCOVER_ACTIONS
        (currentTab == MainTab.LIBRARY || currentTab == MainTab.YOU) && activeTrack != null && isPlaying -> DockUpperMode.MINI_PLAYER
        else -> DockUpperMode.COLLAPSED
    }

    // Track last non-collapsed content mode so exiting/entering You slides cleanly as an intact sheet
    var lastExpandedMode by remember { mutableStateOf(DockUpperMode.DISCOVER_ACTIONS) }
    if (upperMode != DockUpperMode.COLLAPSED) {
        lastExpandedMode = upperMode
    }

    // Dynamic cached ambient color extraction from current active album artwork
    var extractedAmbientColor by remember(activeTrack?.id) {
        mutableStateOf(ArtworkAmbientHelper.getCachedColor(activeTrack?.artworkUrl))
    }

    LaunchedEffect(activeTrack?.id, activeTrack?.artworkUrl) {
        val url = activeTrack?.artworkUrl
        if (url.isNullOrBlank()) {
            extractedAmbientColor = null
            return@LaunchedEffect
        }
        ArtworkAmbientHelper.getCachedColor(url)?.let { cached ->
            extractedAmbientColor = cached
            return@LaunchedEffect
        }
        val req = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(req)
        if (result is SuccessResult) {
            val sampled = ArtworkAmbientHelper.extractDominantColor(result.drawable, cacheKey = url)
            if (sampled != null) {
                extractedAmbientColor = sampled
            }
        }
    }

    val hasLoadedTrack = activeTrack != null
    val targetAmbient = if (hasLoadedTrack) {
        extractedAmbientColor ?: activeTrack?.coverGradient?.firstOrNull() ?: colors.accentGreen
    } else {
        colors.accentGreen
    }
    val ambientColor by animateColorAsState(
        targetValue = targetAmbient,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "dockAmbientColor"
    )

    val upperSurfaceTarget = ArtworkAmbientHelper.computeDockUpperSurfaceColor(
        ambientColor = ambientColor,
        isDark = colors.isDark,
        paletteAccent = colors.accentGreen,
        hasLoadedTrack = hasLoadedTrack
    )
    val upperSurfaceColor by animateColorAsState(
        targetValue = upperSurfaceTarget,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "dockUpperSurface"
    )

    val navHeight = 66.dp
    val navCornerRadius = 30.dp
    val trayBottomExtension = 16.dp // Keeps bottom corners hidden well behind the opaque 66.dp white nav pill

    // 1. You bottom-sheet vertical slide progress (1f = expanded sheet above nav, 0f = tucked beneath nav)
    val traySheetProgress by animateFloatAsState(
        targetValue = if (upperMode == DockUpperMode.COLLAPSED) 0f else 1f,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 330, easing = FastOutSlowInEasing),
        label = "traySheetProgress"
    )

    // 2. Discover <-> Library bi-directional coordinated morph progress (0f = Discover Actions, 1f = Library Mini-Player)
    val playerMorph by animateFloatAsState(
        targetValue = if (lastExpandedMode == DockUpperMode.MINI_PLAYER) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "sharedPlayerMorph"
    )

    val trayHeight by animateDpAsState(
        targetValue = if (lastExpandedMode == DockUpperMode.MINI_PLAYER) 114.dp else 122.dp,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "trayHeight"
    )

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // A. CLIPPED SHEET HOST FOR UPPER TRAY (Sits behind zIndex 2f nav capsule; bottom clipped cleanly at 16.dp)
        if (traySheetProgress > 0.005f) {
            val trayShape = RoundedCornerShape(
                topStart = navCornerRadius,
                topEnd = navCornerRadius,
                bottomStart = 18.dp,
                bottomEnd = 18.dp
            )
            val trayBorderColor = if (colors.isDark) {
                ambientColor.copy(alpha = 0.35f)
            } else {
                Color(0x60FFFFFF)
            }

            Box(
                modifier = Modifier
                    .zIndex(1f)
                    .align(Alignment.BottomCenter)
                    .padding(bottom = trayBottomExtension)
                    .padding(top = 24.dp, start = 16.dp, end = 16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 396.dp)
                        .fillMaxWidth()
                        .height(trayHeight)
                        .graphicsLayer {
                            // Slide straight down under the white nav capsule like a bottom sheet
                            val fullSlidePx = (trayHeight + trayBottomExtension).toPx()
                            translationY = fullSlidePx * (1f - traySheetProgress)
                            alpha = (traySheetProgress * 1.15f).coerceIn(0f, 1f)
                        }
                        .shadow(
                            elevation = if (colors.isDark) 24.dp else 16.dp,
                            shape = trayShape,
                            ambientColor = if (colors.isDark) Color(0xA0000000) else Color(0x250F172A),
                            spotColor = if (colors.isDark) Color(0xFF000000) else Color(0x400F172A)
                        )
                        .clip(trayShape)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    upperSurfaceColor,
                                    upperSurfaceColor.copy(alpha = (upperSurfaceColor.alpha * 0.99f).coerceIn(0f, 1f))
                                )
                            )
                        )
                        .border(1.dp, trayBorderColor, trayShape)
                        // Bottom 50.dp extends behind the overlapping 66.dp white nav capsule so outer sides merge cleanly
                        .padding(top = 6.dp, bottom = 50.dp, start = 14.dp, end = 14.dp)
                ) {
                    val neutralBtnBg = if (colors.isDark) Color(0x24FFFFFF) else Color(0x140E2A1F)
                    val neutralTint = if (colors.isDark) Color(0xFFF1F5F9) else Color(0xFF1F2D36)
                    val likeBg = if (!colors.isDark && colors.accentGreen == SpotifyGreen) {
                        Color(0xFF14DC6E)
                    } else {
                        colors.accentGreen
                    }
                    val likeContent = if (!colors.isDark && colors.accentGreen == SpotifyGreen) {
                        Color(0xFF0E1D17)
                    } else {
                        colors.onAccent
                    }

                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        // 1. Outer 4 Discover Action Buttons (fade + shrink cleanly away as Like glides right, reverse on return)
                        val outerAlpha = (1f - playerMorph * 2.1f).coerceIn(0f, 1f)
                        val outerScale = 1f - 0.24f * (1f - outerAlpha)
                        if (outerAlpha > 0.01f) {
                            DiscoveryActionButtonRow(
                                currentTrack = activeTrack,
                                activeDragDirection = currentDragDirection,
                                dragIntensity = currentDragIntensity,
                                canUndo = canUndo,
                                isPlaying = isPlaying,
                                isHapticEnabled = isHapticEnabled,
                                neutralButtonBg = neutralBtnBg,
                                neutralButtonBorder = Color.Transparent,
                                neutralIconTint = neutralTint,
                                likeFillColor = likeBg,
                                likeContentTint = likeContent,
                                centerButtonSizeDp = 48,
                                secondaryButtonSizeDp = 42,
                                undoButtonSizeDp = 42,
                                bookmarkIcon = Icons.Rounded.BookmarkAdd,
                                showCenterButton = false,
                                outerButtonsAlpha = outerAlpha,
                                outerButtonsScale = outerScale,
                                onActionUndo = onActionUndo,
                                onActionDislike = onActionDislike,
                                onActionSkip = onActionSkip,
                                onActionBookmark = onActionBookmark,
                                onActionLike = onActionLike,
                                onResumeAudio = onResumeAudio
                            )
                        }

                        // 2. Mini-Player Metadata (Artwork, Title, Artist sliding smoothly in from left / back out left on return)
                        val metaProgress = ((playerMorph - 0.12f) / 0.88f).coerceIn(0f, 1f)
                        if (metaProgress > 0.01f && activeTrack != null) {
                            val slideOffsetPx = with(density) { (-40.dp).toPx() * (1f - metaProgress) }
                            ImmersiveMiniPlayerMeta(
                                track = activeTrack,
                                isDark = colors.isDark,
                                textPrimary = if (colors.isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A),
                                textSecondary = if (colors.isDark) Color(0xFFB6C2CF) else Color(0xFF475569),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 46.dp)
                                    .graphicsLayer {
                                        alpha = metaProgress
                                        translationX = slideOffsetPx
                                    }
                            )
                        }

                        // 3. Shared-Element Primary Green Circle (Glides smoothly Center 48.dp Like <-> Right 36.dp Play/Pause)
                        val buttonSize = lerp(48.dp, 36.dp, playerMorph)
                        val iconSize = lerp(22.dp, 18.dp, playerMorph)
                        val isPlayerMode = playerMorph >= 0.5f

                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()

                        val isLikeDragActive = !isPlayerMode && currentDragDirection == SwipeDirection.LIKE
                        val dragScale by animateFloatAsState(
                            targetValue = if (isLikeDragActive) 1f + 0.22f * currentDragIntensity else 1.0f,
                            animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
                            label = "sharedCircleDragScale"
                        )
                        val pressScale by animateFloatAsState(
                            targetValue = if (isPressed) 0.90f else 1.0f,
                            animationSpec = if (reducedMotion) snap() else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
                            label = "sharedCirclePressScale"
                        )

                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .layout { measurable, constraints ->
                                    val placeable = measurable.measure(constraints)
                                    val containerWidth = constraints.maxWidth
                                    val centerLeftX = (containerWidth - placeable.width) / 2f
                                    val rightLeftX = (containerWidth - placeable.width).toFloat()
                                    val targetX = (centerLeftX * (1f - playerMorph) + rightLeftX * playerMorph).roundToInt()
                                    layout(placeable.width, placeable.height) {
                                        placeable.placeRelative(targetX, 0)
                                    }
                                }
                                .scale(dragScale * pressScale)
                                .size(buttonSize)
                                .shadow(
                                    elevation = if (isLikeDragActive) 14.dp else 5.dp,
                                    shape = CircleShape,
                                    spotColor = colors.accentGreenGlow
                                )
                                .clip(CircleShape)
                                .background(likeBg),
                            contentAlignment = Alignment.Center
                        ) {
                            val currentMode = if (isPlayerMode) {
                                if (isPlaying && hasActiveAudioSession) LikePlayPauseMode.PAUSE else LikePlayPauseMode.PLAY
                            } else {
                                LikePlayPauseMode.HEART
                            }

                            MorphLikePlayPauseButton(
                                mode = currentMode,
                                isLiked = isLikeDragActive,
                                onClick = {
                                    if (isPlayerMode) {
                                        if (isHapticEnabled) {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        }
                                        onMiniPlayerTogglePlayPause()
                                    } else if (isPlaying || activeTrack == null) {
                                        if (activeTrack != null) {
                                            if (isHapticEnabled) {
                                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                            }
                                            onActionLike()
                                        }
                                    } else {
                                        if (isHapticEnabled) {
                                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        }
                                        onResumeAudio()
                                    }
                                },
                                touchSize = buttonSize,
                                iconSize = iconSize,
                                palette = MusicInteractionPalette(
                                    active = likeContent,
                                    inactive = likeContent,
                                    ripple = colors.accentGreenGlow,
                                    cutoutContrast = colors.bgBase
                                )
                            )
                        }
                    }
                }
            }
        }

        // B. LOWER NAVIGATION CAPSULE (Adapts cleanly to Light white or Dark elevated charcoal surfaces)
        Box(
            modifier = Modifier
                .zIndex(2f)
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            val lowerShape = RoundedCornerShape(navCornerRadius)
            val lowerSurfaceColor = if (colors.isDark) Color(0xF5111614) else Color(0xFFFCFEFD)
            val lowerBorderColor = if (colors.isDark) Color(0x24FFFFFF) else Color(0x140F172A)
            val isMonochromeLight = !colors.isDark && colors.accentGreen == Color(0xFF18181B)
            val selectionPillBg = when {
                colors.isDark -> colors.accentGreen.copy(alpha = if (colors.accentGreen == Color.White) 0.18f else 0.22f)
                isMonochromeLight -> Color(0x1618181B)
                else -> lerp(Color.White, colors.accentGreen, 0.18f)
            }
            val selectionPillBorder = if (colors.isDark) colors.accentGreen.copy(alpha = 0.38f) else Color.Transparent
            val activeNavIconTint = when {
                colors.isDark -> if (colors.accentGreen == Color.White) Color.White else Color(0xFFECFDF5)
                isMonochromeLight -> Color(0xFF18181B)
                else -> Color(0xFF0E1D17)
            }
            val activeNavLabelTint = when {
                colors.isDark -> colors.accentGreen
                isMonochromeLight -> Color(0xFF18181B)
                else -> lerp(colors.accentGreen, Color(0xFF0F172A), 0.36f)
            }

            BoxWithConstraints(
                modifier = Modifier
                    .widthIn(max = 396.dp)
                    .fillMaxWidth()
                    .height(navHeight)
                    .shadow(
                        elevation = if (colors.isDark) 20.dp else 14.dp,
                        shape = lowerShape,
                        ambientColor = if (colors.isDark) Color(0xA0000000) else Color(0x25000000),
                        spotColor = if (colors.isDark) Color(0xFF000000) else Color(0x40000000)
                    )
                    .clip(lowerShape)
                    .background(lowerSurfaceColor)
                    .border(0.8.dp, lowerBorderColor, lowerShape)
                    .padding(5.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val tabWidth = maxWidth / 3
                val targetTabIndex = when (currentTab) {
                    MainTab.DISCOVER -> 0f
                    MainTab.LIBRARY -> 1f
                    MainTab.YOU -> 2f
                }
                val animatedTabIndex by animateFloatAsState(
                    targetValue = targetTabIndex,
                    animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.84f, stiffness = 360f),
                    label = "immersiveNavPillIndex"
                )

                // Soft mint concentric active destination capsule
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset((animatedTabIndex * tabWidth.toPx()).roundToInt(), 0)
                        }
                        .width(tabWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(25.dp))
                        .background(selectionPillBg)
                        .border(0.8.dp, selectionPillBorder, RoundedCornerShape(25.dp))
                )

                Row(modifier = Modifier.fillMaxSize()) {
                    ImmersiveNavItem(
                        label = "Discover",
                        tab = MainTab.DISCOVER,
                        isSelected = currentTab == MainTab.DISCOVER,
                        isDark = colors.isDark,
                        showLabels = showLabels,
                        activeIconTint = activeNavIconTint,
                        activeLabelTint = activeNavLabelTint,
                        modifier = Modifier.weight(1f),
                        onClick = { onTabSelected(MainTab.DISCOVER) }
                    )
                    ImmersiveNavItem(
                        label = "Library",
                        tab = MainTab.LIBRARY,
                        isSelected = currentTab == MainTab.LIBRARY,
                        isDark = colors.isDark,
                        showLabels = showLabels,
                        activeIconTint = activeNavIconTint,
                        activeLabelTint = activeNavLabelTint,
                        modifier = Modifier.weight(1f),
                        onClick = { onTabSelected(MainTab.LIBRARY) }
                    )
                    ImmersiveNavItem(
                        label = "You",
                        tab = MainTab.YOU,
                        isSelected = currentTab == MainTab.YOU,
                        isDark = colors.isDark,
                        showLabels = showLabels,
                        activeIconTint = activeNavIconTint,
                        activeLabelTint = activeNavLabelTint,
                        modifier = Modifier.weight(1f),
                        onClick = { onTabSelected(MainTab.YOU) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ImmersiveMiniPlayerMeta(
    track: Track,
    isDark: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val artworkRequest = remember(track.id, track.artworkUrl, context) {
        track.artworkUrl?.let { url ->
            ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .crossfade(false)
                .build()
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Artwork thumbnail emerging smoothly from the left
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Brush.linearGradient(track.coverGradient))
                .border(0.6.dp, if (isDark) Color(0x30FFFFFF) else Color(0x18000000), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (artworkRequest != null) {
                AsyncImage(
                    model = artworkRequest,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Strictly Title + Artist only (zero extra metadata or progress bar)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = track.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.displayArtists().joinToString(", "),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ImmersiveNavItem(
    label: String,
    tab: MainTab,
    isSelected: Boolean,
    isDark: Boolean,
    showLabels: Boolean = true,
    activeIconTint: Color,
    activeLabelTint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val inactiveTint = if (isDark) Color(0xFF9CA3AF) else Color(0xFF3E4A59)
    val navIconSize = if (showLabels) 23.dp else 25.dp
    val navTouchSize = if (showLabels) 25.dp else 30.dp

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(25.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (tab) {
                MainTab.DISCOVER -> {
                    DiscoverTabButton(
                        selected = isSelected,
                        onClick = onClick,
                        touchSize = navTouchSize,
                        iconSize = navIconSize,
                        palette = MusicInteractionPalette(
                            active = activeIconTint,
                            inactive = inactiveTint,
                            ripple = activeIconTint.copy(alpha = 0.25f),
                            sparkle = activeIconTint
                        )
                    )
                }
                MainTab.LIBRARY -> {
                    LibraryTabButton(
                        selected = isSelected,
                        onClick = onClick,
                        touchSize = navTouchSize,
                        iconSize = navIconSize,
                        palette = MusicInteractionPalette(
                            active = activeIconTint,
                            inactive = inactiveTint,
                            ripple = activeIconTint.copy(alpha = 0.25f)
                        )
                    )
                }
                MainTab.YOU -> {
                    YouTabButton(
                        selected = isSelected,
                        onClick = onClick,
                        touchSize = navTouchSize,
                        iconSize = navIconSize,
                        palette = MusicInteractionPalette(
                            active = activeIconTint,
                            inactive = inactiveTint,
                            ripple = activeIconTint.copy(alpha = 0.25f),
                            sparkle = activeIconTint
                        )
                    )
                }
            }
            if (showLabels) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) activeLabelTint else inactiveTint,
                    maxLines = 1
                )
            }
        }
    }
}
