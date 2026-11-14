package com.chandyou.tunelyx.ui.screens

import android.content.Context
import android.content.Intent
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.chandyou.tunelyx.model.Genre
import com.chandyou.tunelyx.model.SwipeDirection
import com.chandyou.tunelyx.model.Track
import com.chandyou.tunelyx.model.formatDuration
import com.chandyou.tunelyx.ui.components.DeckActionControls
import com.chandyou.tunelyx.ui.theme.ArtworkAmbientHelper
import com.chandyou.tunelyx.ui.theme.TunelyxTheme
import com.chandyou.tunelyx.viewmodel.DiscoverViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun DiscoverDeckScreen(
    viewModel: DiscoverViewModel,
    modifier: Modifier = Modifier
) {
    val colors = TunelyxTheme.colors
    val view = LocalView.current
    val context = LocalContext.current
    val activeDeck by viewModel.activeDeck.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val currentTrack = activeDeck.getOrNull(currentIndex)
    val nextTrack = activeDeck.getOrNull(currentIndex + 1)
    val thirdTrack = activeDeck.getOrNull(currentIndex + 2)
    val isImmersive by viewModel.isImmersiveMode.collectAsState()
    val availableGenres by viewModel.availableDiscoverGenres.collectAsState()
    val selectedDiscoverGenre by viewModel.selectedDiscoverGenre.collectAsState()
    val deckGeneration by viewModel.deckGeneration.collectAsState()
    val isLoadingDeck by viewModel.isLoadingDeck.collectAsState()
    val apiErrorMessage by viewModel.apiErrorMessage.collectAsState()
    val vmNetworkWarning by viewModel.networkWarning.collectAsState()
    val audioNetworkWarning by viewModel.audioEngine.networkWarning.collectAsState()
    val activeWarning = vmNetworkWarning ?: audioNetworkWarning
    val streamingError by viewModel.audioEngine.streamingError.collectAsState()
    val isAudioReady by viewModel.audioEngine.isAudioReady.collectAsState()
    val canUndoSwipe by viewModel.canUndoSwipe.collectAsState()
    val isPlaying by viewModel.audioEngine.isPlaying.collectAsState()
    val isImmersiveDockEnabled by viewModel.isImmersiveDockEnabled.collectAsState()
    val pendingProgrammaticSwipe by viewModel.pendingProgrammaticSwipe.collectAsState()
    val activeProvider by viewModel.musicProvider.collectAsState()

    val artworkUnlockedIds = remember { mutableStateMapOf<String, Boolean>() }

    // Pre-cache artwork in Coil memory immediately for all active deck tracks
    LaunchedEffect(currentTrack?.id, nextTrack?.id, thirdTrack?.id) {
        val activeIds = listOfNotNull(currentTrack, nextTrack, thirdTrack)
        activeIds.forEach { trackItem ->
            artworkUnlockedIds[trackItem.id] = true
            trackItem.artworkUrl?.let { url ->
                val req = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .build()
                context.imageLoader.enqueue(req)
            }
        }
        // Keep unlocked artwork ID map bounded in RAM to active/recent window
        if (artworkUnlockedIds.size > 25) {
            val activeSet = activeIds.map { it.id }.toSet()
            artworkUnlockedIds.keys.filter { it !in activeSet }.forEach { artworkUnlockedIds.remove(it) }
        }
    }

    var activeDragDirection by remember { mutableStateOf<SwipeDirection?>(null) }
    var dragIntensity by remember { mutableStateOf(0f) }
    var programmaticSwipeDirection by remember { mutableStateOf<SwipeDirection?>(null) }

    LaunchedEffect(pendingProgrammaticSwipe) {
        val requested = pendingProgrammaticSwipe ?: return@LaunchedEffect
        programmaticSwipeDirection = requested
        viewModel.consumeProgrammaticSwipe()
    }

    var showTrackDetailsSheet by remember { mutableStateOf(false) }
    var songToShare by remember { mutableStateOf<Track?>(null) }

    BackHandler(enabled = showTrackDetailsSheet && currentTrack != null) {
        showTrackDetailsSheet = false
    }

    SharedTransitionLayout(modifier = modifier.fillMaxSize()) {
        val sharedScope = this
        AnimatedContent(
            targetState = showTrackDetailsSheet && currentTrack != null,
            transitionSpec = {
                fadeIn(tween(240, easing = FastOutSlowInEasing)) togetherWith
                    fadeOut(tween(190, easing = FastOutSlowInEasing))
            },
            label = "cardToInfoSharedTransition"
        ) { showingInfoCard ->
            val animScope = this
            if (showingInfoCard && currentTrack != null) {
                TrackDetailsCardOverlay(
                    track = currentTrack,
                    viewModel = viewModel,
                    isImmersiveDockEnabled = isImmersiveDockEnabled,
                    sharedTransitionScope = sharedScope,
                    animatedVisibilityScope = animScope,
                    onDismiss = { showTrackDetailsSheet = false }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .let { mod ->
                            if (isImmersiveDockEnabled) {
                                // Dock sits at bottom + 8dp + navigationBarsPadding; slightly taller tray tops at 146.dp
                                mod.navigationBarsPadding().padding(bottom = 146.dp)
                            } else {
                                mod.padding(bottom = 96.dp)
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 1. Provider-Scoped Discover Filter Chips (All + Genres supported by the active platform)
                    DiscoverGenreSelectorRow(
                        genres = availableGenres,
                        selectedGenre = selectedDiscoverGenre,
                        onGenreSelected = { viewModel.selectDiscoverGenre(it) }
                    )

                    // Frontend Error & Warning Banners (Only show when songs are loaded; when searching/deck empty, status is shown on the card itself)
                    if (apiErrorMessage != null && currentTrack != null) {
                        StatusBanner(
                            message = apiErrorMessage!!,
                            accentColor = colors.accentRed,
                            onDismiss = { viewModel.dismissApiError() }
                        )
                    }
                    if (activeWarning != null && currentTrack != null) {
                        StatusBanner(
                            message = activeWarning,
                            accentColor = colors.skipAmber,
                            onDismiss = {
                                viewModel.dismissNetworkWarning()
                                viewModel.audioEngine.clearNetworkWarning()
                            }
                        )
                    }
                    if (streamingError != null && currentTrack != null) {
                        StatusBanner(
                            message = streamingError!!,
                            accentColor = colors.accentRed,
                            onDismiss = { viewModel.audioEngine.clearStreamingError() }
                        )
                    }

                    Spacer(modifier = Modifier.height(if (isImmersiveDockEnabled) 22.dp else 4.dp))

                    // 3. 3-Card Stack with Organic Peeking & Drag Physics
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoadingDeck && currentTrack == null) {
                            val searchingStatus = when {
                                activeWarning != null && activeWarning.contains("slow", ignoreCase = true) ->
                                    "Slow internet, connecting..."
                                activeWarning != null && activeWarning.contains("offline", ignoreCase = true) ->
                                    "No internet, retrying..."
                                selectedDiscoverGenre != null ->
                                    "Retrieving ${selectedDiscoverGenre!!.displayName} from ${activeProvider.displayName}"
                                else ->
                                    "Retrieving songs from ${activeProvider.displayName}"
                            }
                            val loadingTitle = if (selectedDiscoverGenre != null) {
                                selectedDiscoverGenre!!.displayName
                            } else {
                                "Discover"
                            }
                            DiscoverStatusCard(
                                isLoading = true,
                                title = loadingTitle,
                                subtitle = "",
                                accentColor = colors.accentGreen,
                                onRetry = null,
                                statusText = searchingStatus
                            )
                        } else if (apiErrorMessage != null && currentTrack == null) {
                            val (errTitle, errDetail) = parseStatusCardMessage(apiErrorMessage)
                            val statusColor = if (errTitle.contains("rate limit", ignoreCase = true) ||
                                errTitle.contains("slow", ignoreCase = true)
                            ) {
                                colors.skipAmber
                            } else {
                                colors.accentRed
                            }
                            DiscoverStatusCard(
                                isLoading = false,
                                title = errTitle,
                                subtitle = errDetail,
                                accentColor = statusColor,
                                onRetry = { viewModel.rebuildDeck() },
                                statusText = errTitle
                            )
                        } else if (currentTrack != null) {
                            val deckTransitionSpec = spring<Float>(dampingRatio = 0.94f, stiffness = Spring.StiffnessMedium)
                            val deckDpTransitionSpec = spring<androidx.compose.ui.unit.Dp>(dampingRatio = 0.94f, stiffness = Spring.StiffnessMedium)

                            val middleScale by animateFloatAsState(
                                targetValue = 0.96f + (0.04f * dragIntensity),
                                animationSpec = deckTransitionSpec,
                                label = "middleScale"
                            )
                            val middleOffset by animateDpAsState(
                                targetValue = (20f * (1f - dragIntensity)).dp,
                                animationSpec = deckDpTransitionSpec,
                                label = "middleOffset"
                            )
                            val middleRotation by animateFloatAsState(
                                targetValue = -3.8f * (1f - dragIntensity),
                                animationSpec = deckTransitionSpec,
                                label = "middleRotation"
                            )

                            val backScale by animateFloatAsState(
                                targetValue = 0.92f + (0.04f * dragIntensity),
                                animationSpec = deckTransitionSpec,
                                label = "backScale"
                            )
                            val backOffset by animateDpAsState(
                                targetValue = (38f - (18f * dragIntensity)).dp,
                                animationSpec = deckDpTransitionSpec,
                                label = "backOffset"
                            )
                            val backRotation by animateFloatAsState(
                                targetValue = 4.6f - (8.4f * dragIntensity),
                                animationSpec = deckTransitionSpec,
                                label = "backRotation"
                            )

                            // Back card
                            thirdTrack?.let {
                                CardItem(
                                    track = it,
                                    isImmersive = isImmersive,
                                    viewModel = viewModel,
                                    isArtworkUnlocked = true,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .scale(backScale)
                                        .offset(y = backOffset)
                                        .rotate(backRotation),
                                    isFront = false
                                )
                            }

                            // Middle card
                            nextTrack?.let {
                                CardItem(
                                    track = it,
                                    isImmersive = isImmersive,
                                    viewModel = viewModel,
                                    isArtworkUnlocked = true,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .scale(middleScale)
                                        .offset(y = middleOffset)
                                        .rotate(middleRotation),
                                    isFront = false
                                )
                            }

                            // Front active swipeable card (animate entrance only on initial deck load, never shrink/bounce on swipe promotion)
                            key(currentTrack.id) {
                                SwipeableFrontCard(
                                    track = currentTrack,
                                    isImmersive = isImmersive,
                                    viewModel = viewModel,
                                    isArtworkUnlocked = true,
                                    animateOnAppear = (currentIndex == 0),
                                    deckGeneration = deckGeneration,
                                    programmaticSwipe = programmaticSwipeDirection,
                                    onProgrammaticSwipeHandled = { programmaticSwipeDirection = null },
                                    sharedTransitionScope = sharedScope,
                                    animatedVisibilityScope = animScope,
                                    onDragUpdate = { dir, intensity ->
                                        activeDragDirection = dir
                                        dragIntensity = intensity
                                        viewModel.updateDeckDragFeedback(dir, intensity)
                                    },
                                    onSwipeComplete = { dir ->
                                        if (viewModel.prefs.isHapticEnabled) {
                                            when (dir) {
                                                SwipeDirection.LIKE -> view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                                SwipeDirection.DISLIKE -> view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                                                SwipeDirection.BOOKMARK_INAPP -> view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                SwipeDirection.SKIP_NEUTRAL -> view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            }
                                        }
                                        viewModel.onSwipe(dir)
                                        programmaticSwipeDirection = null
                                        activeDragDirection = null
                                        dragIntensity = 0f
                                        viewModel.updateDeckDragFeedback(null, 0f)
                                    },
                                    onOpenTrackDetails = { showTrackDetailsSheet = true },
                                    onShareTrack = { songToShare = currentTrack }
                                )
                            }
                        } else {
                            DiscoverStatusCard(
                                isLoading = false,
                                title = "No songs ready",
                                subtitle = "Switch filter chip or tap retry",
                                accentColor = colors.skipAmber,
                                onRetry = { viewModel.rebuildDeck() },
                                statusText = "No songs ready"
                            )
                        }
                    }

                    if (isImmersiveDockEnabled) {
                        // Equal bottom gap matching top Spacer(22.dp) above the card
                        Spacer(modifier = Modifier.height(22.dp))
                    } else {
                        Spacer(modifier = Modifier.height(14.dp))

                        // 4. Tactile Action Controls (Undo, Bookmark, Like/Resume, Dislike, Skip)
                        DeckActionControls(
                            currentTrack = currentTrack,
                            activeDragDirection = activeDragDirection,
                            dragIntensity = dragIntensity,
                            canUndo = canUndoSwipe,
                            isPlaying = isPlaying,
                            isHapticEnabled = viewModel.prefs.isHapticEnabled,
                            onActionUndo = { viewModel.undoLastSwipe() },
                            onActionDislike = { programmaticSwipeDirection = SwipeDirection.DISLIKE },
                            onActionSkip = { programmaticSwipeDirection = SwipeDirection.SKIP_NEUTRAL },
                            onActionBookmark = { programmaticSwipeDirection = SwipeDirection.BOOKMARK_INAPP },
                            onActionLike = { programmaticSwipeDirection = SwipeDirection.LIKE },
                            onResumeAudio = { viewModel.audioEngine.resume() }
                        )
                    }
                }
            }
        }
    }

    // Simple Popup Dialog for Sharing Platform Song Link
    songToShare?.let { track ->
        SongSharePopupDialog(
            track = track,
            onDismiss = { songToShare = null }
        )
    }
}

@Composable
private fun StatusBanner(
    message: String,
    accentColor: Color,
    onDismiss: () -> Unit
) {
    val colors = TunelyxTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(accentColor.copy(alpha = 0.14f))
            .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = message,
                fontSize = 12.sp,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss",
                tint = colors.textSecondary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

private fun shareTrack(context: Context, track: Track) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        action = Intent.ACTION_SEND
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "${track.title} - ${track.artist}")
        putExtra(
            Intent.EXTRA_TEXT,
            "Listen to \"${track.title}\" by ${track.artist} on Tunelyx!\n${track.previewUrl ?: ""}"
        )
    }
    context.startActivity(Intent.createChooser(sendIntent, "Share Track"))
}

private fun parseStatusCardMessage(rawMessage: String?): Pair<String, String> {
    val msg = rawMessage?.trim().orEmpty()
    if (msg.isEmpty()) return "Unable to load songs" to "Tap Retry to reload"
    val title = when {
        msg.contains("rate limit", ignoreCase = true) || msg.contains("cooling down", ignoreCase = true) ->
            "Rate limited"
        msg.contains("no internet", ignoreCase = true) || msg.contains("wi-fi", ignoreCase = true) || msg.contains("offline", ignoreCase = true) ->
            "No internet"
        msg.contains("timed out", ignoreCase = true) || msg.contains("not responding", ignoreCase = true) ->
            "Server not responding"
        msg.contains("slow", ignoreCase = true) ->
            "Slow internet"
        msg.contains("no fresh", ignoreCase = true) || msg.contains("no new", ignoreCase = true) ->
            "No new songs"
        msg.contains("internal", ignoreCase = true) ->
            "Catalog error"
        else -> "Unable to load songs"
    }
    val detail = when {
        msg.contains("no internet", ignoreCase = true) || msg.contains("offline", ignoreCase = true) ->
            "Check connection and tap retry"
        msg.contains("slow", ignoreCase = true) ->
            "Connecting took longer than usual"
        msg.contains("rate limit", ignoreCase = true) ->
            "Cooling down briefly"
        else -> "Tap Retry to reload deck"
    }
    return title to detail
}

/**
 * Song-card-shaped status card used for loading, errors, and warnings on the Discover deck.
 */
@Composable
private fun DiscoverStatusCard(
    isLoading: Boolean,
    title: String,
    subtitle: String,
    accentColor: Color,
    onRetry: (() -> Unit)?,
    statusText: String = "",
    modifier: Modifier = Modifier
) {
    val colors = TunelyxTheme.colors
    val infiniteTransition = rememberInfiniteTransition(label = "statusCardProgress")
    val travelFraction by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "loadingTravel"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(26.dp),
                spotColor = if (colors.isDark) Color.Black else Color(0x33000000)
            )
            .clip(RoundedCornerShape(26.dp))
            .background(colors.bgSurfaceElevated)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(26.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            // Artwork area slot: contextual short status when loading/searching, or warning icon
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                accentColor.copy(alpha = if (colors.isDark) 0.16f else 0.10f),
                                colors.cardBg
                            )
                        )
                    )
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    Text(
                        text = statusText.ifBlank { "Retrieving songs..." },
                        color = colors.textPrimary.copy(alpha = 0.95f),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            // Bottom metadata slot mimicking CardItem's typography & progress bar silhouette
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 2.dp, start = 2.dp, end = 2.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = title,
                            color = colors.textPrimary,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!isLoading && subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                color = colors.textSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (!isLoading && onRetry != null) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (colors.isDark) Color(0x1EFFFFFF) else Color(0x10000000))
                                .border(0.8.dp, accentColor.copy(alpha = 0.55f), CircleShape)
                                .clickable { onRetry() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry",
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Bottom progress bar: starts cleanly from 0% on left when loading, halted static bar on error/warning (no seconds text)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(if (colors.isDark) Color(0x28FFFFFF) else Color(0x1A000000))
                ) {
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = travelFraction.coerceIn(0f, 1f)
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                }
                                .clip(CircleShape)
                                .background(colors.accentGreen)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(0.35f)
                                .clip(CircleShape)
                                .background(accentColor)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverGenreSelectorRow(
    genres: List<Genre>,
    selectedGenre: Genre?,
    onGenreSelected: (Genre?) -> Unit
) {
    val colors = TunelyxTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val chipOptions: List<Pair<Genre?, String>> = listOf(null to "All") + genres.map { it to it.displayName }
        chipOptions.forEach { (genreOption, label) ->
            val isSelected = genreOption == selectedGenre
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()

            val chipScale by animateFloatAsState(
                targetValue = when {
                    isPressed -> 0.94f
                    isSelected -> 1.03f
                    else -> 1.0f
                },
                animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
                label = "chipScale"
            )
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) colors.accentGreen else colors.bgSurfaceElevated,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                label = "chipBg"
            )
            val borderColor by animateColorAsState(
                targetValue = if (isSelected) colors.accentGreen else colors.borderSubtle,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                label = "chipBorder"
            )
            val labelColor by animateColorAsState(
                targetValue = if (isSelected) colors.onAccent else colors.textPrimary,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                label = "chipText"
            )

            Box(
                modifier = Modifier
                    .scale(chipScale)
                    .clip(CircleShape)
                    .background(bgColor)
                    .border(1.dp, borderColor, CircleShape)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) { onGenreSelected(genreOption) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = labelColor
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SwipeableFrontCard(
    track: Track,
    isImmersive: Boolean,
    viewModel: DiscoverViewModel,
    isArtworkUnlocked: Boolean = true,
    animateOnAppear: Boolean = false,
    deckGeneration: Long = 0L,
    programmaticSwipe: SwipeDirection? = null,
    onProgrammaticSwipeHandled: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    onDragUpdate: (SwipeDirection?, Float) -> Unit,
    onSwipeComplete: (SwipeDirection) -> Unit,
    onOpenTrackDetails: () -> Unit,
    onShareTrack: () -> Unit
) {
    val offsetX = remember(track.id) { Animatable(0f) }
    val offsetY = remember(track.id) { Animatable(0f) }
    val rotation = remember(track.id) { Animatable(0f) }
    val cardScale = remember(track.id) { Animatable(1f) }
    val flashAlpha = remember(track.id) { Animatable(0f) }
    var flashColor by remember(track.id) { mutableStateOf<Color?>(null) }
    var showSongInfo by remember(track.id) { mutableStateOf(false) }
    var isSwipingOut by remember(track.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val swipeThresholdPx = with(LocalDensity.current) { 115.dp.toPx() }

    // Crisp entrance ONLY on initial deck load; zero shrink/bounce when promoted smoothly after swipe
    LaunchedEffect(track.id, deckGeneration) {
        if (animateOnAppear) {
            cardScale.snapTo(0.96f)
            cardScale.animateTo(1f, tween(160, easing = FastOutSlowInEasing))
        } else {
            cardScale.snapTo(1f)
        }
    }

    // Programmatic swipe with smooth rotation, gradual exit, and dragIntensity updates so next card smoothly glides up
    LaunchedEffect(programmaticSwipe) {
        val dir = programmaticSwipe ?: return@LaunchedEffect
        if (isSwipingOut) return@LaunchedEffect
        isSwipingOut = true
        flashColor = when (dir) {
            SwipeDirection.LIKE -> Color(0x661DB954)
            SwipeDirection.DISLIKE -> Color(0x66E53935)
            SwipeDirection.BOOKMARK_INAPP -> Color(0x668A2BE2)
            SwipeDirection.SKIP_NEUTRAL -> Color(0x40FFFFFF)
        }
        val exitSpring = spring<Float>(
            dampingRatio = 0.85f,
            stiffness = Spring.StiffnessMediumLow
        )
        scope.launch { flashAlpha.animateTo(1f, tween(100)) }

        val progressJob = scope.launch {
            val steps = 10
            for (i in 1..steps) {
                kotlinx.coroutines.delay(16)
                onDragUpdate(dir, (i.toFloat() / steps))
            }
        }

        when (dir) {
            SwipeDirection.LIKE -> {
                launch { rotation.animateTo(14f, exitSpring) }
                offsetX.animateTo(1200f, exitSpring)
            }
            SwipeDirection.DISLIKE -> {
                launch { rotation.animateTo(-14f, exitSpring) }
                offsetX.animateTo(-1200f, exitSpring)
            }
            SwipeDirection.BOOKMARK_INAPP -> {
                offsetY.animateTo(1400f, exitSpring)
            }
            SwipeDirection.SKIP_NEUTRAL -> {
                offsetY.animateTo(-1400f, exitSpring)
            }
        }
        progressJob.cancel()
        onProgrammaticSwipeHandled()
        onSwipeComplete(dir)
    }

    // Directional tint during drag gesture
    val dragTint = when {
        offsetX.value > 60f -> Color(0x1DB954).copy(alpha = ((offsetX.value - 60f) / 300f).coerceIn(0f, 0.35f))
        offsetX.value < -60f -> Color(0xE53935).copy(alpha = ((-offsetX.value - 60f) / 300f).coerceIn(0f, 0.35f))
        offsetY.value > 60f -> Color(0x8A2BE2).copy(alpha = ((offsetY.value - 60f) / 300f).coerceIn(0f, 0.35f))
        offsetY.value < -60f -> Color(0xFFFFFF).copy(alpha = ((-offsetY.value - 60f) / 300f).coerceIn(0f, 0.20f))
        else -> null
    }

    val activeCardTint = if (flashColor != null && flashAlpha.value > 0f) {
        flashColor!!.copy(alpha = flashColor!!.alpha * flashAlpha.value)
    } else {
        dragTint
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
            .rotate(rotation.value)
            .scale(cardScale.value)
            .graphicsLayer {
                rotationY = (offsetX.value * 0.025f).coerceIn(-10f, 10f)
                cameraDistance = 16f * density
            }
            .pointerInput(track.id) {
                detectTapGestures(
                    onTap = {
                        if (!isSwipingOut) {
                            showSongInfo = !showSongInfo
                        }
                    }
                )
            }
            .pointerInput(track.id) {
                val resetCard: () -> Unit = {
                    scope.launch {
                        val rubberBandSpring = spring<Float>(
                            dampingRatio = 0.68f,
                            stiffness = Spring.StiffnessMediumLow
                        )
                        launch { offsetX.animateTo(0f, rubberBandSpring) }
                        launch { offsetY.animateTo(0f, rubberBandSpring) }
                        launch { rotation.animateTo(0f, rubberBandSpring) }
                        onDragUpdate(null, 0f)
                    }
                }
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        if (isSwipingOut) return@detectDragGestures
                        change.consume()
                        scope.launch {
                            offsetX.snapTo(offsetX.value + dragAmount.x)
                            offsetY.snapTo(offsetY.value + dragAmount.y)
                            rotation.snapTo(offsetX.value * 0.08f)
                        }

                        val distance = sqrt(offsetX.value * offsetX.value + offsetY.value * offsetY.value)
                        val intensity = (distance / swipeThresholdPx).coerceIn(0f, 1f)

                        val dir = if (abs(offsetX.value) > abs(offsetY.value)) {
                            if (offsetX.value > 60f) SwipeDirection.LIKE
                            else if (offsetX.value < -60f) SwipeDirection.DISLIKE
                            else null
                        } else {
                            if (offsetY.value > 60f) SwipeDirection.BOOKMARK_INAPP
                            else if (offsetY.value < -60f) SwipeDirection.SKIP_NEUTRAL
                            else null
                        }
                        onDragUpdate(dir, intensity)
                    },
                    onDragCancel = {
                        if (!isSwipingOut) resetCard()
                    },
                    onDragEnd = {
                        if (isSwipingOut) return@detectDragGestures
                        val decidedDirection = when {
                            abs(offsetX.value) > abs(offsetY.value) -> {
                                if (offsetX.value > swipeThresholdPx) SwipeDirection.LIKE
                                else if (offsetX.value < -swipeThresholdPx) SwipeDirection.DISLIKE
                                else null
                            }
                            else -> {
                                if (offsetY.value > swipeThresholdPx) SwipeDirection.BOOKMARK_INAPP
                                else if (offsetY.value < -swipeThresholdPx) SwipeDirection.SKIP_NEUTRAL
                                else null
                            }
                        }

                        if (decidedDirection != null) {
                            isSwipingOut = true
                            scope.launch {
                                flashColor = when (decidedDirection) {
                                    SwipeDirection.LIKE -> Color(0x661DB954)
                                    SwipeDirection.DISLIKE -> Color(0x66E53935)
                                    SwipeDirection.BOOKMARK_INAPP -> Color(0x668A2BE2)
                                    SwipeDirection.SKIP_NEUTRAL -> Color(0x40FFFFFF)
                                }
                                launch { flashAlpha.animateTo(1f, tween(100)) }
                                val exitSpring = spring<Float>(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                                when (decidedDirection) {
                                    SwipeDirection.LIKE -> offsetX.animateTo(1200f, exitSpring)
                                    SwipeDirection.DISLIKE -> offsetX.animateTo(-1200f, exitSpring)
                                    SwipeDirection.BOOKMARK_INAPP -> offsetY.animateTo(1400f, exitSpring)
                                    SwipeDirection.SKIP_NEUTRAL -> offsetY.animateTo(-1400f, exitSpring)
                                }
                                onDragUpdate(null, 0f)
                                onSwipeComplete(decidedDirection)
                            }
                        } else {
                            resetCard()
                        }
                    }
                )
            }
    ) {
        CardItem(
            track = track,
            isImmersive = isImmersive,
            viewModel = viewModel,
            isArtworkUnlocked = isArtworkUnlocked,
            isFront = true,
            showSongInfo = showSongInfo,
            cardColorOverlay = activeCardTint,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            onOpenDetails = onOpenTrackDetails,
            onShare = onShareTrack,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun buildDurationLabel(fullDurationSec: Int, previewDurationMs: Long): String {
    val fullStr = formatDuration(fullDurationSec)
    val previewSec = ((previewDurationMs / 1000L).toInt()).coerceIn(0, 30)
    val previewStr = if (previewSec > 0) "${previewSec}s preview" else "30s preview"
    return listOf(fullStr, previewStr).filter { it.isNotEmpty() }.joinToString(" • ")
}

/**
 * Shared interactive progress bar supporting both tap-to-seek and smooth horizontal dragging
 * without letting card-swipe gesture detectors hijack horizontal scrubbing.
 */
@Composable
private fun SeekablePreviewProgressBar(
    trackKey: String,
    isInteractive: Boolean,
    progress: Float,
    barHeightDp: Int = 3,
    trackColor: Color,
    fillColor: Color,
    onSeekFraction: (Float) -> Unit,
    onScrubFractionChange: (Float?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isScrubbing by remember(trackKey) { mutableStateOf(false) }
    var scrubFraction by remember(trackKey) { mutableFloatStateOf(0f) }
    var barWidthPx by remember { mutableIntStateOf(1) }

    val displayedFraction = when {
        !isInteractive -> 0f
        isScrubbing -> scrubFraction.coerceIn(0f, 1f)
        else -> progress.coerceIn(0f, 1f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(20.dp)
            .onSizeChanged { barWidthPx = it.width.coerceAtLeast(1) }
            .then(
                if (isInteractive) {
                    Modifier
                        .pointerInput(trackKey) {
                            detectTapGestures { offset ->
                                val fraction = (offset.x / barWidthPx.toFloat()).coerceIn(0f, 1f)
                                onSeekFraction(fraction)
                                onScrubFractionChange(null)
                            }
                        }
                        .pointerInput(trackKey) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    isScrubbing = true
                                    val fraction = (offset.x / barWidthPx.toFloat()).coerceIn(0f, 1f)
                                    scrubFraction = fraction
                                    onScrubFractionChange(fraction)
                                },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    val fraction = (change.position.x / barWidthPx.toFloat()).coerceIn(0f, 1f)
                                    scrubFraction = fraction
                                    onScrubFractionChange(fraction)
                                },
                                onDragEnd = {
                                    onSeekFraction(scrubFraction)
                                    isScrubbing = false
                                    onScrubFractionChange(null)
                                },
                                onDragCancel = {
                                    isScrubbing = false
                                    onScrubFractionChange(null)
                                }
                            )
                        }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        val visualHeight = if (isScrubbing) (barHeightDp + 2).dp else barHeightDp.dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(visualHeight)
                .clip(CircleShape)
                .background(trackColor)
        ) {
            if (displayedFraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(displayedFraction)
                        .clip(CircleShape)
                        .background(fillColor)
                )
            }
        }
    }
}

@Composable
private fun CardTagPill(
    text: String,
    textColor: Color
) {
    val colors = TunelyxTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (colors.isDark) Color(0x14FFFFFF) else Color(0x0D000000))
            .border(0.8.dp, colors.borderSubtle, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun Modifier.sharedKeyBounds(
    key: String,
    enabled: Boolean,
    sharedScope: SharedTransitionScope?,
    animScope: AnimatedVisibilityScope?
): Modifier {
    if (!enabled || sharedScope == null || animScope == null) return this
    return with(sharedScope) {
        this@sharedKeyBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = animScope,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun Modifier.sharedKeyElement(
    key: String,
    enabled: Boolean,
    sharedScope: SharedTransitionScope?,
    animScope: AnimatedVisibilityScope?
): Modifier {
    if (!enabled || sharedScope == null || animScope == null) return this
    return with(sharedScope) {
        this@sharedKeyElement.sharedElement(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = animScope
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CardItem(
    track: Track,
    isImmersive: Boolean,
    viewModel: DiscoverViewModel,
    modifier: Modifier = Modifier,
    isArtworkUnlocked: Boolean = true,
    isFront: Boolean,
    showSongInfo: Boolean = false,
    cardColorOverlay: Color? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    onOpenDetails: () -> Unit = {},
    onShare: () -> Unit = {}
) {
    val colors = TunelyxTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isPlaying by viewModel.audioEngine.isPlaying.collectAsState()
    val previewDurationMs by viewModel.audioEngine.previewDurationMs.collectAsState()
    val currentPositionMs by viewModel.audioEngine.currentPositionMs.collectAsState()
    val playbackProgress by viewModel.audioEngine.playbackProgress.collectAsState()
    val separatedArtists = remember(track.id, track.artist, track.artists) { track.displayArtists() }
    val displayArtistLine = remember(separatedArtists) { separatedArtists.joinToString(", ") }
    var activeScrubFraction by remember(track.id) { mutableStateOf<Float?>(null) }
    var sampledAmbientColor by remember(track.id) {
        mutableStateOf(ArtworkAmbientHelper.getCachedColor(track.artworkUrl))
    }

    val ambientColor by animateColorAsState(
        targetValue = sampledAmbientColor ?: track.coverGradient.first(),
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "ambientColor"
    )

    val artworkRequest = remember(track.id, track.artworkUrl, context) {
        track.artworkUrl?.let { url ->
            ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .crossfade(false)
                .build()
        }
    }

    val durationLabel = buildDurationLabel(track.durationSeconds, if (isFront) previewDurationMs else 0L)
    val infoOverlayAlpha by animateFloatAsState(
        targetValue = if (showSongInfo && isFront) 0.58f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "infoOverlayAlpha"
    )

    val ambientCardBorder = if (isFront) {
        ambientColor.copy(alpha = if (colors.isDark) 0.28f else 0.18f)
    } else {
        colors.borderSubtle
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .sharedKeyBounds("card_${track.id}", isFront, sharedTransitionScope, animatedVisibilityScope)
            .shadow(
                elevation = if (isFront) 18.dp else 6.dp,
                shape = RoundedCornerShape(26.dp),
                ambientColor = if (isFront) ambientColor.copy(alpha = 0.45f) else Color.Black,
                spotColor = if (isFront) ambientColor.copy(alpha = 0.55f) else if (colors.isDark) Color.Black else Color(0x33000000)
            )
            .clip(RoundedCornerShape(26.dp))
            .background(colors.bgSurfaceElevated)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        ambientColor.copy(alpha = if (colors.isDark) 0.18f else 0.09f),
                        ambientColor.copy(alpha = if (colors.isDark) 0.05f else 0.02f),
                        Color.Transparent
                    )
                )
            )
            .border(1.dp, ambientCardBorder, RoundedCornerShape(26.dp))
    ) {
        // Color flash / drag glow overlay on card
        if (cardColorOverlay != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(cardColorOverlay)
            )
        }

        if (isImmersive) {
            // Unified Elevated Card with Inset Rounded Artwork, Ambient Wash, Metadata, Pills & Draggable Progress Bar
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // Top Inset Rounded Album Artwork with Subtle Top-Right Share & Info Controls + Integrated Liner Details
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .sharedKeyElement("art_${track.id}", isFront, sharedTransitionScope, animatedVisibilityScope)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Brush.linearGradient(track.coverGradient))
                ) {
                    if (artworkRequest != null) {
                        AsyncImage(
                            model = artworkRequest,
                            contentDescription = track.title,
                            contentScale = ContentScale.Crop,
                            onSuccess = { state ->
                                scope.launch {
                                    val sampled = ArtworkAmbientHelper.extractDominantColor(
                                        drawable = state.result.drawable,
                                        cacheKey = track.artworkUrl
                                    )
                                    if (sampled != null) sampledAmbientColor = sampled
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Smooth bottom-up artwork scrim blending card details organically into the cover itself
                    if (infoOverlayAlpha > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0f to Color.Black.copy(alpha = infoOverlayAlpha * 0.25f),
                                        0.45f to Color.Black.copy(alpha = infoOverlayAlpha * 0.65f),
                                        1f to Color.Black.copy(alpha = (infoOverlayAlpha * 1.48f).coerceIn(0f, 0.90f))
                                    )
                                )
                        )
                    }

                    // Integrated liner notes revealed smoothly at bottom of artwork (zero detached floating box)
                    this@Column.AnimatedVisibility(
                        visible = showSongInfo && isFront,
                        enter = fadeIn(tween(180, easing = FastOutSlowInEasing)) +
                            slideInVertically(tween(190, easing = FastOutSlowInEasing)) { it / 5 },
                        exit = fadeOut(tween(130, easing = FastOutSlowInEasing)) +
                            slideOutVertically(tween(140, easing = FastOutSlowInEasing)) { it / 5 },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(0.6.dp)
                                    .background(Color(0x40FFFFFF))
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${track.genre.displayName} • ${track.provider.displayName}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.accentGreen
                                )
                                val meta = listOfNotNull(track.musicalKey, track.bpm?.let { "$it BPM" }).joinToString(" • ")
                                if (meta.isNotEmpty()) {
                                    Text(
                                        text = meta,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xE0FFFFFF)
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Album: ${track.album ?: "Single"}",
                                    fontSize = 11.sp,
                                    color = Color(0xE0FFFFFF),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Text(
                                    text = durationLabel,
                                    fontSize = 11.sp,
                                    color = Color(0xE0FFFFFF)
                                )
                            }
                            if (!track.language.isNullOrBlank()) {
                                Text(
                                    text = "Language: ${track.language}",
                                    fontSize = 10.sp,
                                    color = Color(0xB3FFFFFF)
                                )
                            }
                        }
                    }

                    // Top-Right Action Bar on Card (Share & Info)
                    if (isFront) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 12.dp, end = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x66000000))
                                    .border(0.5.dp, Color(0x38FFFFFF), CircleShape)
                                    .clickable { onShare() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x66000000))
                                    .border(0.5.dp, Color(0x38FFFFFF), CircleShape)
                                    .clickable { onOpenDetails() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Track Details",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }

                // Bottom Metadata Section (Fixed height stability; zero bounce or resizing flicker)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 2.dp, start = 2.dp, end = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {

                    // 1. Title + Artist stacked on Left, Circular Mute Button aligned on Right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = track.title,
                                color = colors.textPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.sharedKeyBounds("title_${track.id}", isFront, sharedTransitionScope, animatedVisibilityScope)
                            )
                            Text(
                                text = displayArtistLine,
                                color = colors.textSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.sharedKeyBounds("artist_${track.id}", isFront, sharedTransitionScope, animatedVisibilityScope)
                            )
                        }

                        val isCardPlaying = isPlaying && isFront
                        val playPauseScale by animateFloatAsState(
                            targetValue = if (isCardPlaying) 1.0f else 0.90f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                            label = "playPauseScale"
                        )
                        Box(
                            modifier = Modifier
                                .scale(playPauseScale)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (colors.isDark) Color(0x1EFFFFFF) else Color(0x10000000))
                                .border(0.8.dp, colors.borderSubtle, CircleShape)
                                .clickable { if (isFront) viewModel.audioEngine.togglePlayPause() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isCardPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isCardPlaying) "Pause" else "Play",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // 2. Subtle Track-Specific Genre & Detected Language Pills (Provider shown inside tap info)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CardTagPill(text = track.genre.displayName, textColor = colors.textSecondary)
                        if (!track.language.isNullOrBlank()) {
                            CardTagPill(text = track.language, textColor = colors.textSecondary)
                        }
                    }

                    // 3. Draggable Linear Song Progress Bar + 30s-Capped Live Elapsed & Total Timestamps
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val cappedTrackSec = if (track.durationSeconds in 1..30) track.durationSeconds else 30
                        val previewTotalSec = if (isFront) {
                            val engineSec = (previewDurationMs / 1000L).toInt()
                            if (engineSec > 0) engineSec.coerceAtMost(30) else cappedTrackSec
                        } else {
                            cappedTrackSec
                        }
                        val totalLabel = formatDuration(previewTotalSec)

                        SeekablePreviewProgressBar(
                            trackKey = track.id,
                            isInteractive = isFront,
                            progress = playbackProgress,
                            barHeightDp = 3,
                            trackColor = if (colors.isDark) Color(0x28FFFFFF) else Color(0x1A000000),
                            fillColor = colors.accentGreen,
                            onSeekFraction = { fraction -> viewModel.audioEngine.seekToFraction(fraction) },
                            onScrubFractionChange = { activeScrubFraction = it }
                        )

                        val elapsedSec = when {
                            !isFront -> 0
                            activeScrubFraction != null -> (activeScrubFraction!! * previewTotalSec).toInt().coerceIn(0, previewTotalSec)
                            else -> (currentPositionMs / 1000L).toInt().coerceIn(0, previewTotalSec)
                        }
                        val elapsedLabel = if (elapsedSec <= 0) "0:00" else formatDuration(elapsedSec)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = elapsedLabel,
                                fontSize = 11.sp,
                                color = colors.textTertiary,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = totalLabel,
                                fontSize = 11.sp,
                                color = colors.textTertiary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        } else {
            // Minimalist Mode - Rich Animated Vinyl Turntable with outer Ambient Wash
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top placeholder to balance spacing
                Spacer(modifier = Modifier.height(28.dp))

                // Animated Turntable Stage (Vinyl + Tonearm + Grooves + Spindle Ripples)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "vinylSpin")
                    val vinylAngle by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(tween(7500, easing = LinearEasing), RepeatMode.Restart),
                        label = "vinylRotation"
                    )

                    val tonearmAngle by animateFloatAsState(
                        targetValue = if (isPlaying && isFront) 0f else -26f,
                        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow),
                        label = "tonearmAngle"
                    )

                    val vinylOffset by animateDpAsState(
                        targetValue = if (isPlaying && isFront) 54.dp else 24.dp,
                        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
                        label = "vinylOffset"
                    )

                    val rippleWave by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Restart),
                        label = "spindleRipple"
                    )

                    // Spindle Acoustic Ripple Rings
                    if (isPlaying && isFront) {
                        Box(
                            modifier = Modifier
                                .offset(x = vinylOffset)
                                .size(150.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val maxRadius = size.width / 2f
                                val currentRadius = maxRadius * (0.35f + 0.65f * rippleWave)
                                val alpha = (1f - rippleWave).coerceIn(0f, 0.45f)
                                drawCircle(
                                    color = Color(0x331DB954).copy(alpha = alpha),
                                    radius = currentRadius,
                                    center = center,
                                    style = Stroke(width = 1.5.dp.toPx())
                                )
                            }
                        }
                    }

                    // Sliding Spinning Vinyl Disc with Etched Grooves & Spindle Label
                    Box(
                        modifier = Modifier
                            .offset(x = vinylOffset)
                            .size(154.dp)
                            .rotate(if (isPlaying && isFront) vinylAngle else 0f)
                            .clip(CircleShape)
                            .background(Color(0xFF0F0F13))
                            .border(1.dp, Color(0x33FFFFFF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        // Etched concentric groove rings
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val grooveRadii = listOf(26.dp.toPx(), 38.dp.toPx(), 50.dp.toPx(), 62.dp.toPx(), 70.dp.toPx())
                            grooveRadii.forEach { r ->
                                drawCircle(
                                    color = Color(0x18FFFFFF),
                                    radius = r,
                                    center = center,
                                    style = Stroke(width = 0.8.dp.toPx())
                                )
                            }
                        }

                        // Center Vinyl Label with Mini Album Thumbnail
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(track.coverGradient))
                                .border(1.dp, Color(0x40FFFFFF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (track.artworkUrl != null) {
                                AsyncImage(
                                    model = track.artworkUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            // Center Silver Spindle Hole
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFDDDDDD))
                                    .border(1.dp, Color(0xFF555555), CircleShape)
                            )
                        }
                    }

                    // Squircle Album Jacket Sleeve (Layered above the sliding vinyl record)
                    val sleeveScale by animateFloatAsState(
                        targetValue = if (isPlaying && isFront) 1.02f else 1.0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        label = "sleeveScale"
                    )
                    Box(
                        modifier = Modifier
                            .scale(sleeveScale)
                            .size(160.dp)
                            .sharedKeyElement("art_${track.id}", isFront, sharedTransitionScope, animatedVisibilityScope)
                            .shadow(elevation = 14.dp, shape = RoundedCornerShape(22.dp), spotColor = Color(0x40000000))
                            .clip(RoundedCornerShape(22.dp))
                            .background(Brush.linearGradient(track.coverGradient))
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(22.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (artworkRequest != null) {
                            AsyncImage(
                                model = artworkRequest,
                                contentDescription = track.title,
                                contentScale = ContentScale.Crop,
                                onSuccess = { state ->
                                    scope.launch {
                                        val sampled = ArtworkAmbientHelper.extractDominantColor(
                                            drawable = state.result.drawable,
                                            cacheKey = track.artworkUrl
                                        )
                                        if (sampled != null) sampledAmbientColor = sampled
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        // Sleeve right-edge opening shadow
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(8.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color.Transparent, Color(0x33000000))
                                    )
                                )
                        )
                    }

                    // Animated Vintage Metallic Tonearm with Stylus Needle Drop
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp, end = 12.dp)
                            .size(width = 64.dp, height = 120.dp)
                            .graphicsLayer {
                                rotationZ = tonearmAngle
                                transformOrigin = TransformOrigin(0.85f, 0.15f)
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val pivot = Offset(size.width * 0.85f, size.height * 0.15f)
                            val head = Offset(size.width * 0.25f, size.height * 0.88f)

                            // Silver pivot base
                            drawCircle(
                                color = Color(0xFFCCCCCC),
                                radius = 7.dp.toPx(),
                                center = pivot
                            )
                            drawCircle(
                                color = Color(0xFF666666),
                                radius = 4.dp.toPx(),
                                center = pivot
                            )

                            // Chrome tonearm bar
                            drawLine(
                                color = Color(0xFFDDDDDD),
                                start = pivot,
                                end = head,
                                strokeWidth = 2.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )

                            // Stylus cartridge head
                            drawRoundRect(
                                color = Color(0xFF222222),
                                topLeft = Offset(head.x - 5.dp.toPx(), head.y - 4.dp.toPx()),
                                size = Size(10.dp.toPx(), 14.dp.toPx()),
                                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                            )
                        }
                    }
                }

                // Minimalist Mode Bottom Metadata & Playback HUD
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Smooth expandable detailed info integrated directly onto Minimalist card surface
                    AnimatedVisibility(
                        visible = showSongInfo && isFront,
                        enter = fadeIn(tween(170, easing = FastOutSlowInEasing)) + expandVertically(tween(170, easing = FastOutSlowInEasing)),
                        exit = fadeOut(tween(130, easing = FastOutSlowInEasing)) + shrinkVertically(tween(140, easing = FastOutSlowInEasing))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${track.genre.displayName} • ${track.provider.displayName}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.accentGreen
                                )
                                val meta = listOfNotNull(track.musicalKey, track.bpm?.let { "$it BPM" }).joinToString(" • ")
                                if (meta.isNotEmpty()) {
                                    Text(
                                        text = meta,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Album: ${track.album ?: "Single"}",
                                    fontSize = 11.sp,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Text(
                                    text = durationLabel,
                                    fontSize = 11.sp,
                                    color = colors.textSecondary
                                )
                            }
                            if (!track.language.isNullOrBlank()) {
                                Text(
                                    text = "Language: ${track.language}",
                                    fontSize = 10.sp,
                                    color = colors.textTertiary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                                    .height(0.6.dp)
                                    .background(colors.borderSubtle)
                            )
                        }
                    }

                    // Song Name
                    Text(
                        text = track.title,
                        color = colors.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.sharedKeyBounds("title_${track.id}", isFront, sharedTransitionScope, animatedVisibilityScope)
                    )

                    // Artist Name (Left) + Mute Button (Right)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = displayArtistLine,
                            color = colors.textSecondary,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .sharedKeyBounds("artist_${track.id}", isFront, sharedTransitionScope, animatedVisibilityScope)
                        )

                        val isCardPlayingMin = isPlaying && isFront
                        val playPauseScaleMin by animateFloatAsState(
                            targetValue = if (isCardPlayingMin) 1.0f else 0.90f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                            label = "playPauseScaleMin"
                        )
                        Box(
                            modifier = Modifier
                                .scale(playPauseScaleMin)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(colors.bgGlass)
                                .border(0.5.dp, colors.borderSubtle, CircleShape)
                                .clickable { if (isFront) viewModel.audioEngine.togglePlayPause() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isCardPlayingMin) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isCardPlayingMin) "Pause" else "Play",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Track-specific Genre Pill
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colors.bgGlass)
                                .border(0.5.dp, colors.borderSubtle, CircleShape)
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = track.genre.displayName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                        }
                        if (!track.language.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(colors.bgGlass)
                                    .border(0.5.dp, colors.borderSubtle, CircleShape)
                                    .padding(horizontal = 9.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = track.language,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun TrackDetailsCardOverlay(
    track: Track,
    viewModel: DiscoverViewModel,
    isImmersiveDockEnabled: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onDismiss: () -> Unit
) {
    val colors = TunelyxTheme.colors
    val separatedArtists = remember(track.id, track.artist, track.artists) { track.displayArtists() }
    val cachedAmbient = remember(track.artworkUrl) { ArtworkAmbientHelper.getCachedColor(track.artworkUrl) } ?: track.coverGradient.first()

    var isTitleExpanded by remember { mutableStateOf(false) }
    var isArtistExpanded by remember { mutableStateOf(false) }
    var isAlbumExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .let { mod ->
                if (isImmersiveDockEnabled) {
                    mod.navigationBarsPadding().padding(bottom = 148.dp)
                } else {
                    mod.padding(bottom = 96.dp)
                }
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .sharedKeyBounds("card_${track.id}", true, sharedTransitionScope, animatedVisibilityScope)
                .shadow(
                    elevation = 18.dp,
                    shape = RoundedCornerShape(26.dp),
                    ambientColor = cachedAmbient.copy(alpha = 0.35f),
                    spotColor = if (colors.isDark) Color.Black else Color(0x33000000)
                )
                .clip(RoundedCornerShape(26.dp))
                .background(colors.bgSurfaceElevated)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            cachedAmbient.copy(alpha = if (colors.isDark) 0.14f else 0.08f),
                            Color.Transparent
                        )
                    )
                )
                .border(1.dp, cachedAmbient.copy(alpha = if (colors.isDark) 0.28f else 0.18f), RoundedCornerShape(26.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (colors.isDark) Color(0x1EFFFFFF) else Color(0x10000000))
                            .border(0.6.dp, colors.borderSubtle, CircleShape)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Details",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Shared Artwork Cover
                Box(
                    modifier = Modifier
                        .size(172.dp)
                        .sharedKeyElement("art_${track.id}", true, sharedTransitionScope, animatedVisibilityScope)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.linearGradient(track.coverGradient))
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(20.dp))
                ) {
                    if (!track.artworkUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = track.artworkUrl,
                            contentDescription = track.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Expandable Song Title
                Text(
                    text = track.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = if (isTitleExpanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .sharedKeyBounds("title_${track.id}", true, sharedTransitionScope, animatedVisibilityScope)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { isTitleExpanded = !isTitleExpanded }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Expandable Artist Names
                Text(
                    text = separatedArtists.joinToString(" • "),
                    fontSize = 15.sp,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = if (isArtistExpanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .sharedKeyBounds("artist_${track.id}", true, sharedTransitionScope, animatedVisibilityScope)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { isArtistExpanded = !isArtistExpanded }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Details metadata pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.bgGlass)
                            .border(1.dp, colors.borderSubtle, CircleShape)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = track.genre.displayName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.accentGreen
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.bgGlass)
                            .border(1.dp, colors.borderSubtle, CircleShape)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = track.provider.displayName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                    }
                }

                if (!track.album.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Album: ${track.album}",
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                        maxLines = if (isAlbumExpanded) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { isAlbumExpanded = !isAlbumExpanded }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Compact Block Artist Chips (fit multiple artists cleanly with block icon on left)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    separatedArtists.forEach { individualArtist ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (colors.isDark) Color(0x18FFFFFF) else Color(0x0C000000))
                                .border(0.8.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
                                .padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(colors.accentRed.copy(alpha = 0.16f))
                                    .clickable {
                                        viewModel.blockArtist(individualArtist)
                                        onDismiss()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Block $individualArtist",
                                    tint = colors.accentRed,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = individualArtist,
                                fontSize = 12.sp,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

/**
 * Lightweight popup dialog for sharing direct platform song link to external apps.
 */
@Composable
private fun SongSharePopupDialog(
    track: Track,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = TunelyxTheme.colors
    val shareUrl = remember(track.id) { track.platformShareUrl() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.bgSurfaceElevated,
        shape = RoundedCornerShape(22.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Share Song",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (colors.isDark) Color(0x18FFFFFF) else Color(0x0C000000))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = track.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${track.artist} • ${track.provider.displayName}",
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (colors.isDark) Color(0x12FFFFFF) else Color(0x08000000))
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = shareUrl,
                        fontSize = 12.sp,
                        color = colors.accentGreen,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        action = Intent.ACTION_SEND
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "${track.title} - ${track.artist}")
                        putExtra(Intent.EXTRA_TEXT, "${track.title} by ${track.artist}\n$shareUrl")
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share Link"))
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentGreen),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    tint = colors.onAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share Link", color = colors.onAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Song Link", shareUrl)
                    clipboard?.setPrimaryClip(clip)
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle)
            ) {
                Text("Copy Link", color = colors.textPrimary, fontSize = 13.sp)
            }
        }
    )
}
