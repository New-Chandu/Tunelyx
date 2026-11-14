package com.chandyou.tunelyx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.chandyou.tunelyx.model.SwipeDirection
import com.chandyou.tunelyx.model.Track
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
import com.chandyou.tunelyx.ui.screens.ServicesScreen
import com.chandyou.tunelyx.ui.theme.TunelyxTheme
import com.chandyou.tunelyx.viewmodel.ActiveSubScreen
import com.chandyou.tunelyx.viewmodel.DiscoverViewModel

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

    // Keep screen awake only when a song is actively playing on Discover
    val keepScreenAwake = currentTab == MainTab.DISCOVER && isPlaying
    DisposableEffect(keepScreenAwake) {
        view.keepScreenOn = keepScreenAwake
        onDispose {
            view.keepScreenOn = false
        }
    }

    val isImmersiveDockEnabled by viewModel.isImmersiveDockEnabled.collectAsState()
    val activeDeck by viewModel.activeDeck.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val discoverTrack = activeDeck.getOrNull(currentIndex)
    val playingTrack by viewModel.audioEngine.currentTrack.collectAsState()
    val activeDockTrack = playingTrack ?: discoverTrack

    // System Back Gesture handling:
    // 1. Close sub-screen if active
    BackHandler(enabled = activeSubScreen != ActiveSubScreen.NONE) {
        viewModel.closeSubScreen()
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
            modifier = Modifier.fillMaxSize(),
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

                        // Minimal understated Settings icon
                        IconButton(
                            onClick = { showSettings = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Settings",
                                tint = colors.textTertiary.copy(alpha = 0.78f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Active Tab View Switching
                    AnimatedContent(
                        targetState = currentTab,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        modifier = Modifier.weight(1f),
                        label = "tabSwitching"
                    ) { tab ->
                        when (tab) {
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
                        activeDragDirection = null,
                        dragIntensity = 0f,
                        canUndo = false,
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
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (activeDockTrack != null && (currentTab == MainTab.LIBRARY || currentTab == MainTab.YOU || currentTab == MainTab.ACCOUNT)) {
                            StandaloneMiniPlayer(
                                track = activeDockTrack,
                                isPlaying = isPlaying,
                                onTogglePlayPause = {
                                    if (isPlaying) {
                                        viewModel.audioEngine.pause()
                                    } else {
                                        viewModel.audioEngine.resume()
                                    }
                                },
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                        FloatingBottomNav(
                            currentTab = currentTab,
                            onTabSelected = { newTab ->
                                if (newTab == MainTab.DISCOVER) {
                                    if (discoverTrack != null && playingTrack?.id != discoverTrack.id) {
                                        viewModel.audioEngine.stop()
                                        viewModel.currentTrack?.let { cardTrack ->
                                            viewModel.audioEngine.playTrack(cardTrack)
                                        }
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

        // Sub-Screen Sliding Transition (Item 10)
        AnimatedContent(
            targetState = activeSubScreen,
            transitionSpec = {
                if (targetState != ActiveSubScreen.NONE && initialState == ActiveSubScreen.NONE) {
                    (slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow)
                    ) + fadeIn(tween(140))) togetherWith fadeOut(tween(90))
                } else if (targetState == ActiveSubScreen.NONE) {
                    fadeIn(tween(90)) togetherWith (slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow)
                    ) + fadeOut(tween(140)))
                } else {
                    (slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow)
                    ) + fadeIn(tween(140))) togetherWith (slideOutHorizontally(
                        targetOffsetX = { -it },
                        animationSpec = spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow)
                    ) + fadeOut(tween(140)))
                }
            },
            label = "subScreenNavigation",
            modifier = Modifier.fillMaxSize()
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
                ActiveSubScreen.SERVICES -> ServicesScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.closeSubScreen() },
                    onEditPreferences = {
                        viewModel.closeSubScreen()
                        viewModel.openPreferencesEditor()
                    }
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
private fun StandaloneMiniPlayer(
    track: Track,
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = TunelyxTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(52.dp)
            .shadow(
                elevation = if (colors.isDark) 0.dp else 6.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = Color(0x28000000)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(colors.cardBg)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(16.dp))
            .clickable(onClick = onTogglePlayPause)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).padding(end = 12.dp)
        ) {
            AsyncImage(
                model = track.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x18FFFFFF))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = track.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(colors.accentGreen)
                .clickable(onClick = onTogglePlayPause),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = colors.onAccent,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
