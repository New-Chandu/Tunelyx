package com.chandyou.tunelyx.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.chandyou.tunelyx.model.Genre
import com.chandyou.tunelyx.model.MusicProvider
import com.chandyou.tunelyx.model.SwipeDirection
import com.chandyou.tunelyx.model.Track
import com.chandyou.tunelyx.ui.theme.TunelyxTheme
import com.chandyou.tunelyx.viewmodel.DiscoverViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

enum class LibraryTab(val title: String) {
    LIKED("Liked"),
    BOOKMARKS("Bookmarks"),
    COOLDOWN("Skipped"),
    DISLIKED("Disliked")
}

@Composable
fun LibraryScreen(
    viewModel: DiscoverViewModel,
    modifier: Modifier = Modifier
) {
    val colors = TunelyxTheme.colors
    var selectedTab by remember { mutableStateOf(LibraryTab.LIKED) }
    var selectedPlatformFilter by remember { mutableStateOf<MusicProvider?>(null) }
    var showPlatformMenu by remember { mutableStateOf(false) }

    val likedTracks by viewModel.libraryLiked.collectAsState()
    val inAppSaved by viewModel.inAppSaved.collectAsState()
    val cooldownTracks by viewModel.libraryCooldown.collectAsState()
    val dislikedTracks by viewModel.libraryDisliked.collectAsState()
    val playingTrack by viewModel.audioEngine.currentTrack.collectAsState()
    val isPlaying by viewModel.audioEngine.isPlaying.collectAsState()
    val canUndoRemoval by viewModel.canUndoLibraryRemoval.collectAsState()
    val isImmersiveDockEnabled by viewModel.isImmersiveDockEnabled.collectAsState()

    var searchQuery by remember { mutableStateOf("") }

    // Instant O(1) tab list resolution from pre-sorted background StateFlows
    val activeTabTracks = remember(selectedTab, likedTracks, inAppSaved, cooldownTracks, dislikedTracks, selectedPlatformFilter, searchQuery) {
        val baseList = when (selectedTab) {
            LibraryTab.LIKED -> likedTracks
            LibraryTab.BOOKMARKS -> inAppSaved
            LibraryTab.COOLDOWN -> cooldownTracks
            LibraryTab.DISLIKED -> dislikedTracks
        }
        if (selectedPlatformFilter == null && searchQuery.isBlank()) {
            baseList
        } else {
            baseList.filter { track ->
                val matchesPlatform = selectedPlatformFilter == null || track.provider == selectedPlatformFilter
                val matchesSearch = searchQuery.isBlank() ||
                    track.title.contains(searchQuery, ignoreCase = true) ||
                    track.artist.contains(searchQuery, ignoreCase = true)
                matchesPlatform && matchesSearch
            }
        }
    }

    LaunchedEffect(canUndoRemoval) {
        if (canUndoRemoval) {
            kotlinx.coroutines.delay(4500L)
            viewModel.dismissLibraryUndo()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        val listBottomPadding = when {
            isImmersiveDockEnabled -> if (playingTrack != null || viewModel.currentTrack != null) 156.dp else 98.dp
            playingTrack != null -> 164.dp
            else -> 98.dp
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp)
        ) {
            // Real-Time Search Filter Bar with Platform Dropdown Button (matching Image 2)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Search Input Box
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.bgSurfaceElevated)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search saved songs or artists...",
                                fontSize = 13.sp,
                                color = colors.textTertiary
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = colors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(colors.accentGreen),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = colors.textSecondary,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { searchQuery = "" }
                        )
                    }
                }

                // Platform Filter Dropdown Menu Button (matching Image 2)
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selectedPlatformFilter != null) colors.accentGreen else colors.bgSurfaceElevated)
                            .border(1.dp, if (selectedPlatformFilter != null) colors.accentGreen else colors.borderSubtle, RoundedCornerShape(12.dp))
                            .clickable { showPlatformMenu = !showPlatformMenu }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter Platform",
                            tint = if (selectedPlatformFilter != null) colors.onAccent else colors.textSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = selectedPlatformFilter?.displayName ?: "All Platforms",
                            fontSize = 11.sp,
                            fontWeight = if (selectedPlatformFilter != null) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedPlatformFilter != null) colors.onAccent else colors.textPrimary
                        )
                    }

                    // Decoupled Minimal Platform Selector Popup: does not alter parent layout bounds or crush search bar
                    if (showPlatformMenu) {
                        Popup(
                            alignment = Alignment.TopEnd,
                            offset = with(LocalDensity.current) { IntOffset(0, 42.dp.roundToPx()) },
                            onDismissRequest = { showPlatformMenu = false },
                            properties = PopupProperties(focusable = true)
                        ) {
                            var isPopupVisible by remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) { isPopupVisible = true }

                            val popupScale by animateFloatAsState(
                                targetValue = if (isPopupVisible) 1.0f else 0.85f,
                                animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
                                label = "popupScale"
                            )
                            val popupAlpha by animateFloatAsState(
                                targetValue = if (isPopupVisible) 1.0f else 0.0f,
                                animationSpec = tween(140),
                                label = "popupAlpha"
                            )

                            Column(
                                modifier = Modifier
                                    .width(184.dp)
                                    .graphicsLayer {
                                        scaleX = popupScale
                                        scaleY = popupScale
                                        alpha = popupAlpha
                                        transformOrigin = TransformOrigin(1f, 0f)
                                    }
                                    .shadow(
                                        elevation = if (colors.isDark) 16.dp else 10.dp,
                                        shape = RoundedCornerShape(16.dp),
                                        spotColor = Color(0x38000000)
                                    )
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (colors.isDark) Color(0xFF161920) else Color(0xFFFFFFFF))
                                    .border(
                                        width = 1.dp,
                                        color = colors.borderSubtle.copy(alpha = if (colors.isDark) 0.55f else 0.75f),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .padding(vertical = 5.dp)
                            ) {
                                val options: List<MusicProvider?> = listOf(null) + MusicProvider.entries
                                options.forEach { provider ->
                                    val isSelected = selectedPlatformFilter == provider
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedPlatformFilter = provider
                                                showPlatformMenu = false
                                            }
                                            .background(if (isSelected) colors.accentGreen.copy(alpha = 0.12f) else Color.Transparent)
                                            .padding(horizontal = 14.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = provider?.displayName ?: "All Platforms",
                                            fontSize = 12.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) colors.accentGreen else colors.textPrimary
                                        )
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = colors.accentGreen,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Filter Tabs Row (Minimal dot-separated typography with active pill matching Discover screen)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LibraryTab.entries.forEachIndexed { index, tab ->
                    if (index > 0) {
                        // Subtle dot separator design between items
                        Box(
                            modifier = Modifier
                                .size(3.5.dp)
                                .clip(CircleShape)
                                .background(colors.textTertiary.copy(alpha = 0.40f))
                        )
                    }

                    val isSelected = tab == selectedTab
                    val tabInteractionSource = remember { MutableInteractionSource() }
                    val isTabPressed by tabInteractionSource.collectIsPressedAsState()

                    val tabScale by animateFloatAsState(
                        targetValue = if (isTabPressed) 0.94f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "tabScale"
                    )

                    if (isSelected) {
                        // Clean minimal solid pill ONLY for the currently selected tab
                        Box(
                            modifier = Modifier
                                .scale(tabScale)
                                .clip(CircleShape)
                                .background(if (colors.isDark) Color(0xFFF1F5F9) else Color(0xFF0F172A))
                                .clickable(
                                    interactionSource = tabInteractionSource,
                                    indication = null
                                ) { selectedTab = tab }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (colors.isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC)
                            )
                        }
                    } else {
                        // Clean borderless muted text for inactive tabs
                        Box(
                            modifier = Modifier
                                .scale(tabScale)
                                .clickable(
                                    interactionSource = tabInteractionSource,
                                    indication = null
                                ) { selectedTab = tab }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textSecondary.copy(alpha = 0.70f)
                            )
                        }
                    }
                }
            }

        // Instant tab content rendering without crossfade
        val tab = selectedTab
        val trackList = activeTabTracks

            if (trackList.isEmpty()) {
                val hasActiveFilter = selectedPlatformFilter != null || searchQuery.isNotBlank()
                val emptyMessage = when (tab) {
                    LibraryTab.LIKED -> if (!hasActiveFilter) "No liked songs." else "No liked songs match active filters."
                    LibraryTab.BOOKMARKS -> if (!hasActiveFilter) "No bookmarked songs." else "No bookmarks match active filters."
                    LibraryTab.COOLDOWN -> if (!hasActiveFilter) "No skipped songs." else "No skipped songs match active filters."
                    LibraryTab.DISLIKED -> if (!hasActiveFilter) "No disliked songs." else "No disliked songs match active filters."
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 64.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        text = emptyMessage,
                        fontSize = 14.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            } else {
                val listState = rememberLazyListState()

                LazyColumn(
                    state = listState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = listBottomPadding),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // True lazy loading: LazyColumn composes and renders only items currently visible on viewport
                    itemsIndexed(trackList, key = { _, it -> "${tab.name}_${it.id}" }) { index, track ->
                        val isCurrentPlaying = playingTrack?.id == track.id && isPlaying

                        @Suppress("DEPRECATION")
                        @OptIn(ExperimentalMaterial3Api::class)
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { targetValue ->
                                if (targetValue == SwipeToDismissBoxValue.StartToEnd || targetValue == SwipeToDismissBoxValue.EndToStart) {
                                    viewModel.removeTrackFromLibrary(track)
                                    true
                                } else {
                                    false
                                }
                            },
                            positionalThreshold = { totalDistance -> totalDistance * 0.75f }
                        )

                        Column {
                            @OptIn(ExperimentalMaterial3Api::class)
                            SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = {
                                    val isDismissing = dismissState.targetValue != SwipeToDismissBoxValue.Settled ||
                                        dismissState.currentValue != SwipeToDismissBoxValue.Settled
                                    if (isDismissing) {
                                        val color by animateColorAsState(
                                            targetValue = when (dismissState.targetValue) {
                                                SwipeToDismissBoxValue.Settled -> Color.Transparent
                                                else -> colors.accentRed.copy(alpha = 0.85f)
                                            },
                                            label = "dismissColor"
                                        )
                                        val alignment = when (dismissState.targetValue) {
                                            SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                            else -> Alignment.CenterEnd
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(color)
                                                .padding(horizontal = 20.dp),
                                            contentAlignment = alignment
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Remove",
                                                tint = Color.White
                                            )
                                        }
                                    }
                                },
                                content = {
                                    TrackRowItem(
                                        track = track,
                                        isPlaying = isCurrentPlaying,
                                        tab = tab,
                                        onPlayToggle = {
                                            if (isCurrentPlaying) {
                                                viewModel.audioEngine.pause()
                                            } else {
                                                viewModel.audioEngine.playTrack(track)
                                            }
                                        },
                                        onRediscover = { viewModel.rediscoverTrack(track.id) },
                                        onUnban = { viewModel.unbanTrack(track.id) }
                                    )
                                }
                            )

                            // Subtle divider between list items matching real music app styling
                            if (index < trackList.lastIndex) {
                                HorizontalDivider(
                                    color = colors.borderSubtle.copy(alpha = if (colors.isDark) 0.35f else 0.50f),
                                    thickness = 0.6.dp,
                                    modifier = Modifier.padding(start = 66.dp, end = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

    // Floating Undo Removal Bar with Horizontal Swipe-to-Dismiss
    AnimatedVisibility(
        visible = canUndoRemoval,
        enter = fadeIn(spring()) + slideInVertically(initialOffsetY = { it }),
        exit = fadeOut(tween(140)) + slideOutVertically(targetOffsetY = { it }),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 20.dp)
            .padding(bottom = listBottomPadding)
    ) {
        val dismissOffsetX = remember { Animatable(0f) }
        val undoScope = rememberCoroutineScope()

        Row(
            modifier = Modifier
                .offset { IntOffset(dismissOffsetX.value.roundToInt(), 0) }
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(colors.cardBg)
                .border(1.dp, colors.accentGreen.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (abs(dismissOffsetX.value) > 120f) {
                                undoScope.launch {
                                    val target = if (dismissOffsetX.value > 0) 650f else -650f
                                    dismissOffsetX.animateTo(target, tween(150))
                                    viewModel.dismissLibraryUndo()
                                    dismissOffsetX.snapTo(0f)
                                }
                            } else {
                                undoScope.launch {
                                    dismissOffsetX.animateTo(0f, spring())
                                }
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            undoScope.launch {
                                dismissOffsetX.snapTo(dismissOffsetX.value + dragAmount)
                            }
                        }
                    )
                }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Track removed from library",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary
            )
            Text(
                text = "UNDO",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accentGreen,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { viewModel.undoRemoveFromLibrary() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
    }
}

@Composable
fun TrackRowItem(
    track: Track,
    isPlaying: Boolean,
    tab: LibraryTab,
    modifier: Modifier = Modifier,
    onPlayToggle: () -> Unit,
    onRediscover: () -> Unit = {},
    onUnban: () -> Unit = {}
) {
    val colors = TunelyxTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isPlaying) colors.accentGreen.copy(alpha = if (colors.isDark) 0.12f else 0.08f) else Color.Transparent)
            .clickable { onPlayToggle() }
            .padding(horizontal = 6.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Clean Album Artwork (48dp rounded, downsampled for crisp low-memory lazy scrolling)
        val thumbContext = LocalContext.current
        val thumbRequest = remember(track.id, track.artworkUrl, thumbContext) {
            track.artworkUrl?.let { url ->
                ImageRequest.Builder(thumbContext)
                    .data(url)
                    .size(140)
                    .crossfade(false)
                    .build()
            }
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.linearGradient(track.coverGradient)),
            contentAlignment = Alignment.Center
        ) {
            if (thumbRequest != null) {
                AsyncImage(
                    model = thumbRequest,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x66000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = "Pause",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Title & Artist info (clean typography and spacious layout)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = track.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isPlaying) colors.accentGreen else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = track.displayArtists().joinToString(", "),
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = "•",
                    fontSize = 10.sp,
                    color = colors.textTertiary
                )
                Text(
                    text = track.genre.displayName,
                    fontSize = 11.sp,
                    color = colors.textSecondary.copy(alpha = 0.8f),
                    maxLines = 1
                )
            }
        }

        // Action controls / Play affordance
        when (tab) {
            LibraryTab.LIKED, LibraryTab.BOOKMARKS -> {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (isPlaying) colors.accentGreen else colors.bgSurfaceElevated)
                        .border(1.dp, if (isPlaying) colors.accentGreen else colors.borderSubtle, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = if (isPlaying) colors.onAccent else colors.textPrimary,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
            LibraryTab.COOLDOWN -> {
                OutlinedButton(
                    onClick = onRediscover,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp),
                    shape = CircleShape,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.accentGreen)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = colors.accentGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Rediscover",
                        fontSize = 11.sp,
                        color = colors.accentGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            LibraryTab.DISLIKED -> {
                Button(
                    onClick = onUnban,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentRed)
                ) {
                    Text(
                        text = "Unban",
                        fontSize = 11.sp,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
