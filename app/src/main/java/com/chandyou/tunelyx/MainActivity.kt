package com.chandyou.tunelyx

import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import kotlin.coroutines.cancellation.CancellationException
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.TransformOrigin
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.chandyou.tunelyx.ui.animations.ElasticLikeButton
import com.chandyou.tunelyx.ui.animations.GearSettingsButton
import com.chandyou.tunelyx.ui.animations.LikePlayPauseMode
import com.chandyou.tunelyx.ui.animations.MorphLikePlayPauseButton
import com.chandyou.tunelyx.ui.animations.MorphPlayPauseButton
import com.chandyou.tunelyx.ui.animations.MusicInteractionPalette
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.chandyou.tunelyx.model.SwipeDirection
import com.chandyou.tunelyx.model.Track
import com.chandyou.tunelyx.ui.components.DiscoveryActionButtonRow
import com.chandyou.tunelyx.ui.components.FloatingBottomNav
import com.chandyou.tunelyx.ui.components.ImmersiveDock
import com.chandyou.tunelyx.ui.components.MainTab
import com.chandyou.tunelyx.ui.components.SettingsBottomSheet
import com.chandyou.tunelyx.ui.screens.AboutScreen
import com.chandyou.tunelyx.ui.screens.AccountScreen
import com.chandyou.tunelyx.ui.screens.AppearanceScreen
import com.chandyou.tunelyx.ui.screens.DiscoverDeckScreen
import com.chandyou.tunelyx.ui.screens.FilterListScreen
import com.chandyou.tunelyx.ui.screens.LibraryScreen
import com.chandyou.tunelyx.ui.screens.PlaybackScreen
import com.chandyou.tunelyx.ui.screens.PreferencesScreen
import com.chandyou.tunelyx.ui.theme.TunelyxTheme
import com.chandyou.tunelyx.viewmodel.ActiveSubScreen
import com.chandyou.tunelyx.viewmodel.DiscoverViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: DiscoverViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isDarkTheme by viewModel.isDarkTheme.collectAsState()
            val accentPreset by viewModel.accentPreset.collectAsState()

            TunelyxTheme(
                darkTheme = isDarkTheme,
                accentPreset = accentPreset
            ) {
                // Audio pause on app minimize
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                            viewModel.audioEngine.pause()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                val isFirstBootCompleted by viewModel.isFirstBootCompleted.collectAsState()
                val isEditingPreferences by viewModel.isEditingPreferences.collectAsState()
                val songLanguages by viewModel.songLanguages.collectAsState()
                val appLanguage by viewModel.appLanguage.collectAsState()
                val musicProvider by viewModel.musicProvider.collectAsState()

                if (isEditingPreferences) {
                    BackHandler {
                        viewModel.closePreferencesEditor()
                    }
                }

                AnimatedContent(
                    targetState = when {
                        !isFirstBootCompleted || isEditingPreferences -> "ONBOARDING"
                        else -> "MAIN"
                    },
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "bootNavigation"
                ) { screenState ->
                    when (screenState) {
                        "ONBOARDING" -> {
                            PreferencesScreen(
                                initialSongLanguages = songLanguages,
                                initialAppLanguage = appLanguage,
                                initialGenres = viewModel.prefs.selectedGenres,
                                initialMusicProvider = musicProvider,
                                onComplete = { langs, appLang, genres, provider ->
                                    viewModel.completeOnboarding(langs, appLang, genres, provider)
                                }
                            )
                        }
                        else -> {
                            MainAppScaffold(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MainAppScaffold(viewModel: DiscoverViewModel) {
    val colors = TunelyxTheme.colors
    val view = LocalView.current
    var currentTab by remember { mutableStateOf(MainTab.DISCOVER) }
    var showSettings by remember { mutableStateOf(false) }
    val isPlaying by viewModel.audioEngine.isPlaying.collectAsState()
    val activeSubScreen by viewModel.activeSubScreen.collectAsState()
    val canUndoSwipe by viewModel.canUndoSwipe.collectAsState()

    val isSubScreenActive = activeSubScreen != ActiveSubScreen.NONE
    val scaffoldScale by animateFloatAsState(
        targetValue = 1.0f,
        animationSpec = spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow),
        label = "scaffoldScale"
    )
    val scaffoldAlpha by animateFloatAsState(
        targetValue = 1.0f,
        animationSpec = spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow),
        label = "scaffoldAlpha"
    )

    // Keep screen awake only when a song is actively playing on Discover
    val keepScreenAwake = currentTab == MainTab.DISCOVER && isPlaying
    DisposableEffect(keepScreenAwake) {
        view.keepScreenOn = keepScreenAwake
        onDispose {
            view.keepScreenOn = false
        }
    }

    val isImmersiveDockEnabled by viewModel.isImmersiveDockEnabled.collectAsState()
    val isDockShowLabelsEnabled by viewModel.isDockShowLabelsEnabled.collectAsState()
    val activeDeck by viewModel.activeDeck.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val discoverTrack = activeDeck.getOrNull(currentIndex)
    val playingTrack by viewModel.audioEngine.currentTrack.collectAsState()
    val activeDockTrack = playingTrack ?: discoverTrack

    var predictiveBackProgress by remember { mutableFloatStateOf(0f) }

    // System Back Gesture handling:
    // 1. Predictive Back for Settings Category Sub-screens (Android 14+)
    PredictiveBackHandler(enabled = activeSubScreen != ActiveSubScreen.NONE) { progressFlow ->
        try {
            progressFlow.collect { backEvent ->
                predictiveBackProgress = backEvent.progress
            }
            predictiveBackProgress = 0f
            viewModel.closeSubScreen()
        } catch (e: CancellationException) {
            predictiveBackProgress = 0f
        }
    }

    // 2. Dismiss settings sheet if open
    BackHandler(enabled = activeSubScreen == ActiveSubScreen.NONE && showSettings) {
        showSettings = false
    }

    // 3. Return to Discover tab if on Library or You
    BackHandler(enabled = activeSubScreen == ActiveSubScreen.NONE && !showSettings && currentTab != MainTab.DISCOVER) {
        currentTab = MainTab.DISCOVER
        if (isImmersiveDockEnabled && discoverTrack != null && playingTrack?.id == discoverTrack.id) {
            if (!isPlaying) viewModel.audioEngine.resume()
        } else {
            viewModel.audioEngine.stop()
            discoverTrack?.let {
                viewModel.audioEngine.playTrack(it)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scaffoldScale
                    scaleY = scaffoldScale
                    alpha = scaffoldAlpha
                },
            containerColor = colors.bgBase,
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    // Minimalist Top App Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (currentTab) {
                                MainTab.DISCOVER -> "Discover"
                                MainTab.LIBRARY -> "Library"
                                MainTab.YOU, MainTab.ACCOUNT -> "You"
                            },
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )

                        // Animated Gear Settings icon
                        GearSettingsButton(
                            onClick = { showSettings = true },
                            touchSize = 32.dp,
                            iconSize = 20.dp,
                            palette = MusicInteractionPalette(
                                active = colors.textPrimary,
                                inactive = colors.textTertiary.copy(alpha = 0.85f),
                                ripple = colors.accentGreenGlow
                            )
                        )
                    }

                    // Active Tab View Switching (Instant native tab switching without crossfade)
                    Box(modifier = Modifier.weight(1f)) {
                        when (currentTab) {
                            MainTab.DISCOVER -> DiscoverDeckScreen(viewModel = viewModel)
                            MainTab.LIBRARY -> LibraryScreen(viewModel = viewModel)
                            MainTab.YOU, MainTab.ACCOUNT -> AccountScreen(viewModel = viewModel)
                        }
                    }
                }

                // Anchored Floating Navigation (Classic FloatingBottomNav or Adaptive ImmersiveDock)
                if (isImmersiveDockEnabled) {
                    ImmersiveDock(
                        currentTab = currentTab,
                        onTabSelected = { newTab ->
                            when (newTab) {
                                MainTab.DISCOVER -> {
                                    if (discoverTrack != null && playingTrack?.id == discoverTrack.id) {
                                        if (!isPlaying) viewModel.audioEngine.resume()
                                    } else {
                                        viewModel.audioEngine.stop()
                                        viewModel.currentTrack?.let { cardTrack ->
                                            viewModel.audioEngine.playTrack(cardTrack)
                                        }
                                    }
                                }
                                MainTab.LIBRARY, MainTab.YOU, MainTab.ACCOUNT -> {
                                    // Audio continues playing seamlessly on Library and You
                                }
                            }
                            currentTab = newTab
                        },
                        activeTrack = activeDockTrack,
                        isPlaying = isPlaying,
                        hasActiveAudioSession = playingTrack != null,
                        activeDragDirectionFlow = viewModel.deckDragDirection,
                        dragIntensityFlow = viewModel.deckDragIntensity,
                        canUndo = canUndoSwipe,
                        isHapticEnabled = viewModel.prefs.isHapticEnabled,
                        onActionUndo = { viewModel.undoLastSwipe() },
                        onActionDislike = { viewModel.requestProgrammaticSwipe(SwipeDirection.DISLIKE) },
                        onActionSkip = { viewModel.requestProgrammaticSwipe(SwipeDirection.SKIP_NEUTRAL) },
                        onActionBookmark = { viewModel.requestProgrammaticSwipe(SwipeDirection.BOOKMARK_INAPP) },
                        onActionLike = { viewModel.requestProgrammaticSwipe(SwipeDirection.LIKE) },
                        onResumeAudio = { viewModel.audioEngine.resume() },
                        onMiniPlayerTogglePlayPause = {
                            if (isPlaying) viewModel.audioEngine.pause() else viewModel.audioEngine.resume()
                        },
                        showLabels = isDockShowLabelsEnabled,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                    )
                } else {
                    val shouldShowBarNav = if (currentTab == MainTab.DISCOVER) {
                        discoverTrack != null
                    } else {
                        activeDockTrack != null
                    }
                    val barNavBottomPadding by animateDpAsState(
                        targetValue = if (shouldShowBarNav) 6.dp else 0.dp,
                        animationSpec = spring(dampingRatio = 0.84f, stiffness = Spring.StiffnessMediumLow),
                        label = "barNavBottomPadding"
                    )

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        BarNavigationControl(
                            currentTab = currentTab,
                            activeTrack = if (currentTab == MainTab.DISCOVER) discoverTrack else activeDockTrack,
                            isPlaying = isPlaying,
                            canUndo = canUndoSwipe,
                            dragDirectionFlow = viewModel.deckDragDirection,
                            dragIntensityFlow = viewModel.deckDragIntensity,
                            isHapticEnabled = viewModel.prefs.isHapticEnabled,
                            onActionUndo = { viewModel.undoLastSwipe() },
                            onActionDislike = { viewModel.requestProgrammaticSwipe(SwipeDirection.DISLIKE) },
                            onActionSkip = { viewModel.requestProgrammaticSwipe(SwipeDirection.SKIP_NEUTRAL) },
                            onActionBookmark = { viewModel.requestProgrammaticSwipe(SwipeDirection.BOOKMARK_INAPP) },
                            onActionLike = { viewModel.requestProgrammaticSwipe(SwipeDirection.LIKE) },
                            onResumeAudio = { viewModel.audioEngine.resume() },
                            onMiniPlayerTogglePlayPause = {
                                if (isPlaying) viewModel.audioEngine.pause() else viewModel.audioEngine.resume()
                            },
                            modifier = Modifier.padding(bottom = barNavBottomPadding)
                        )
                        FloatingBottomNav(
                            currentTab = currentTab,
                            showLabels = isDockShowLabelsEnabled,
                            onTabSelected = { newTab ->
                                if (newTab == MainTab.DISCOVER) {
                                    if (discoverTrack != null && playingTrack?.id != discoverTrack.id) {
                                        viewModel.audioEngine.stop()
                                        viewModel.currentTrack?.let { cardTrack ->
                                            viewModel.audioEngine.playTrack(cardTrack)
                                        }
                                    } else if (discoverTrack != null && playingTrack?.id == discoverTrack.id && !isPlaying) {
                                        viewModel.audioEngine.resume()
                                    }
                                }
                                currentTab = newTab
                            },
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }

                // Settings Modal Bottom Sheet
                if (showSettings) {
                    SettingsBottomSheet(
                        onDismissRequest = { showSettings = false },
                        onNavigateToSubScreen = { screen ->
                            showSettings = false
                            viewModel.navigateToSubScreen(screen)
                        },
                        onClearCache = {
                            viewModel.clearCacheOnly()
                            showSettings = false
                        },
                        onResetHistory = {
                            viewModel.resetHistoryOnly()
                            showSettings = false
                        },
                        onResetLiked = {
                            viewModel.resetLikedOnly()
                            showSettings = false
                        },
                        onResetBookmarks = {
                            viewModel.resetBookmarksOnly()
                            showSettings = false
                        },
                        onResetDisliked = {
                            viewModel.resetDislikedOnly()
                            showSettings = false
                        },
                        onResetSkipped = {
                            viewModel.resetSkippedOnly()
                            showSettings = false
                        },
                        onResetAllData = {
                            viewModel.resetAllData()
                            showSettings = false
                        }
                    )
                }
            }
        }

        val predictiveShape = RoundedCornerShape(lerp(0.dp, 28.dp, predictiveBackProgress))
        val predictiveScale = 1f - (predictiveBackProgress * 0.08f)
        val predictiveAlpha = 1f - (predictiveBackProgress * 0.25f)

        // Sub-Screen Transition (Crisp non-clashing native vertical slide + fade + vertical predictive pull)
        AnimatedContent(
            targetState = activeSubScreen,
            transitionSpec = {
                if (targetState != ActiveSubScreen.NONE && initialState == ActiveSubScreen.NONE) {
                    (slideInVertically(
                        initialOffsetY = { (it * 0.22f).toInt() },
                        animationSpec = spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow)
                    ) + fadeIn(tween(180, easing = FastOutSlowInEasing))) togetherWith fadeOut(tween(90))
                } else if (targetState == ActiveSubScreen.NONE) {
                    fadeIn(tween(90)) togetherWith (
                        slideOutVertically(
                            targetOffsetY = { (it * 0.22f).toInt() },
                            animationSpec = spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow)
                        ) + fadeOut(
                            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
                        )
                    )
                } else {
                    fadeIn(tween(140)) togetherWith fadeOut(tween(100))
                }
            },
            label = "subScreenNavigation",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (activeSubScreen != ActiveSubScreen.NONE && predictiveBackProgress > 0f) {
                        scaleX = predictiveScale
                        scaleY = predictiveScale
                        translationY = size.height * (predictiveBackProgress * 0.22f)
                        alpha = predictiveAlpha
                        shape = predictiveShape
                        clip = true
                    }
                }
        ) { subScreen ->
            when (subScreen) {
                ActiveSubScreen.APPEARANCE -> AppearanceScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.closeSubScreen() }
                )
                ActiveSubScreen.PLAYBACK -> PlaybackScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.closeSubScreen() }
                )
                ActiveSubScreen.FILTER_LIST -> FilterListScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.closeSubScreen() }
                )
                ActiveSubScreen.ABOUT -> AboutScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.closeSubScreen() }
                )
                ActiveSubScreen.NONE -> {
                    // Empty placeholder
                }
            }
        }
    }
}

@Composable
private fun BarNavigationControl(
    currentTab: MainTab,
    activeTrack: Track?,
    isPlaying: Boolean,
    canUndo: Boolean,
    dragDirectionFlow: StateFlow<SwipeDirection?>,
    dragIntensityFlow: StateFlow<Float>,
    isHapticEnabled: Boolean,
    onActionUndo: () -> Unit,
    onActionDislike: () -> Unit,
    onActionSkip: () -> Unit,
    onActionBookmark: () -> Unit,
    onActionLike: () -> Unit,
    onResumeAudio: () -> Unit,
    onMiniPlayerTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeDragDirection by dragDirectionFlow.collectAsState()
    val dragIntensity by dragIntensityFlow.collectAsState()
    val colors = TunelyxTheme.colors
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val isDiscover = currentTab == MainTab.DISCOVER
    val shouldShowBar = activeTrack != null

    val morphProgress by animateFloatAsState(
        targetValue = if (isDiscover) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow),
        label = "barNavMorphProgress"
    )

    androidx.compose.animation.AnimatedVisibility(
        visible = shouldShowBar,
        enter = fadeIn(spring()) + expandVertically(spring()),
        exit = fadeOut(tween(140)) + shrinkVertically(spring()),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        val barShape = RoundedCornerShape(18.dp)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .shadow(
                    elevation = if (colors.isDark) 18.dp else 10.dp,
                    shape = barShape,
                    ambientColor = if (colors.isDark) Color(0xA0000000) else Color(0x22000000),
                    spotColor = if (colors.isDark) Color(0xFF000000) else Color(0x38000000)
                )
                .clip(barShape)
                .background(colors.cardBg)
                .border(1.dp, colors.borderSubtle, barShape)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // 1. Discover Action Buttons (Undo, Bookmark, Dislike, Skip) fade & scale away smoothly
                val outerAlpha = (1f - morphProgress * 2.2f).coerceIn(0f, 1f)
                val outerScale = 1f - 0.25f * (1f - outerAlpha)
                if (outerAlpha > 0.01f) {
                    DiscoveryActionButtonRow(
                        currentTrack = activeTrack,
                        activeDragDirection = activeDragDirection,
                        dragIntensity = dragIntensity,
                        canUndo = canUndo,
                        isPlaying = isPlaying,
                        isHapticEnabled = isHapticEnabled,
                        showCenterButton = false,
                        outerButtonsAlpha = outerAlpha,
                        outerButtonsScale = outerScale,
                        centerButtonSizeDp = 44,
                        secondaryButtonSizeDp = 38,
                        undoButtonSizeDp = 38,
                        onActionUndo = onActionUndo,
                        onActionDislike = onActionDislike,
                        onActionSkip = onActionSkip,
                        onActionBookmark = onActionBookmark,
                        onActionLike = onActionLike,
                        onResumeAudio = onResumeAudio
                    )
                }

                // 2. Mini-Player Metadata (Artwork, Title, Artist) sliding in smoothly from left
                val metaProgress = ((morphProgress - 0.15f) / 0.85f).coerceIn(0f, 1f)
                if (metaProgress > 0.01f && activeTrack != null) {
                    val slideOffsetPx = with(density) { (-32.dp).toPx() * (1f - metaProgress) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 46.dp)
                            .graphicsLayer {
                                alpha = metaProgress
                                translationX = slideOffsetPx
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onMiniPlayerTogglePlayPause
                            )
                    ) {
                        AsyncImage(
                            model = activeTrack.artworkUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Color(0x18FFFFFF))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(verticalArrangement = Arrangement.Center) {
                            Text(
                                text = activeTrack.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = activeTrack.artist,
                                fontSize = 11.sp,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // 3. Shared Element: Center Like Button <-> Right Play/Pause Button
                val isPlayerMode = morphProgress >= 0.5f
                val buttonSize = lerp(44.dp, 36.dp, morphProgress)

                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                var isHeartBeating by remember { mutableStateOf(false) }

                val isLikeDragActive = !isPlayerMode && activeDragDirection == SwipeDirection.LIKE
                val dragScale by animateFloatAsState(
                    targetValue = if (isLikeDragActive) 1f + 0.20f * dragIntensity else 1.0f,
                    animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
                    label = "barSharedDragScale"
                )
                val pressScale by animateFloatAsState(
                    targetValue = if (isPressed) 0.90f else 1.0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
                    label = "barSharedPressScale"
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val containerWidth = constraints.maxWidth
                            val centerLeftX = (containerWidth - placeable.width) / 2f
                            val rightLeftX = (containerWidth - placeable.width).toFloat()
                            val targetX = (centerLeftX * (1f - morphProgress) + rightLeftX * morphProgress).roundToInt()
                            layout(placeable.width, placeable.height) {
                                placeable.placeRelative(targetX, 0)
                            }
                        }
                        .scale(dragScale * pressScale)
                        .size(buttonSize)
                        .shadow(
                            elevation = if (isLikeDragActive) 10.dp else 4.dp,
                            shape = CircleShape,
                            spotColor = colors.accentGreenGlow
                        )
                        .clip(CircleShape)
                        .background(colors.accentGreen),
                    contentAlignment = Alignment.Center
                ) {
                    val currentMode = if (isPlayerMode) {
                        if (isPlaying) LikePlayPauseMode.PAUSE else LikePlayPauseMode.PLAY
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
                        iconSize = lerp(22.dp, 18.dp, morphProgress),
                        palette = MusicInteractionPalette(
                            active = colors.onAccent,
                            inactive = colors.onAccent,
                            ripple = colors.accentGreenGlow,
                            cutoutContrast = colors.accentGreen
                        )
                    )
                }
            }
        }
    }
}
