package com.chandyou.tunelyx.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    COOLDOWN("Cooldown"),
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

    val history by viewModel.history.collectAsState()
    val inAppSaved by viewModel.inAppSaved.collectAsState()
    val playingTrack by viewModel.audioEngine.currentTrack.collectAsState()
    val isPlaying by viewModel.audioEngine.isPlaying.collectAsState()
    val canUndoRemoval by viewModel.canUndoLibraryRemoval.collectAsState()
    val isImmersiveDockEnabled by viewModel.isImmersiveDockEnabled.collectAsState()

    var searchQuery by remember { mutableStateOf("") }

    // Offload active-tab track mapping, sorting & filtering off the Main Thread so switching to Library/sub-tabs stays 0-lag
    val activeTabTracks by produceState<List<Track>>(
        initialValue = emptyList(),
        selectedTab, history, inAppSaved, selectedPlatformFilter, searchQuery
    ) {
        value = withContext(Dispatchers.Default) {
            val baseList = when (selectedTab) {
                LibraryTab.LIKED -> history.values
                    .filter { it.action == SwipeDirection.LIKE || it.action == SwipeDirection.BOOKMARK_INAPP }
                    .sortedByDescending { it.timestamp }
                    .map { it.toTrack() }
                LibraryTab.BOOKMARKS -> inAppSaved
                LibraryTab.COOLDOWN -> {
                    val now = System.currentTimeMillis()
                    val sevenDaysMs = 7 * 24 * 60 * 60 * 1000L
                    history.values
                        .filter { it.action == SwipeDirection.SKIP_NEUTRAL && (now - it.timestamp) < sevenDaysMs }
                        .sortedByDescending { it.timestamp }
                        .map { it.toTrack() }
                }
                LibraryTab.DISLIKED -> history.values
                    .filter { it.action == SwipeDirection.DISLIKE }
                    .sortedByDescending { it.timestamp }
                    .map { it.toTrack() }
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
            playingTrack != null -> 170.dp
            else -> 98.dp
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp)
                .padding(bottom = listBottomPadding)
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
                            .clickable { showPlatformMenu = true }
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

                    DropdownMenu(
                        expanded = showPlatformMenu,
                        onDismissRequest = { showPlatformMenu = false },
                        modifier = Modifier.background(colors.bgSurfaceElevated)
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Platforms", color = colors.textPrimary, fontSize = 13.sp) },
                            onClick = {
                                selectedPlatformFilter = null
                                showPlatformMenu = false
                            }
                        )
                        MusicProvider.entries.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(provider.displayName, color = colors.textPrimary, fontSize = 13.sp) },
                                onClick = {
                                    selectedPlatformFilter = provider
                                    showPlatformMenu = false
                                }
                            )
                        }
                    }
                }
            }

        // Filter Tabs Row
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(LibraryTab.entries) { tab ->
                val isSelected = tab == selectedTab

                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) colors.accentGreen else colors.bgGlass,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "tabBg"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) colors.onAccent else colors.textSecondary,
                    label = "tabText"
                )
                val borderColor by animateColorAsState(
                    targetValue = if (isSelected) colors.accentGreen else colors.borderSubtle,
                    label = "tabBorder"
                )

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(bgColor)
                        .border(
                            width = 1.dp,
                            color = borderColor,
                            shape = CircleShape
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { selectedTab = tab }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab.title,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = textColor
                    )
                }
            }
        }

        // Lightweight crossfade with paged lazy loading (25 items per page) to keep tab switching silky smooth
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                fadeIn(animationSpec = tween(160, easing = LinearOutSlowInEasing)) togetherWith
                    fadeOut(animationSpec = tween(120))
            },
            label = "libraryContent"
        ) { tab ->
            val trackList = if (tab == selectedTab) activeTabTracks else emptyList()

            if (trackList.isEmpty()) {
                val hasActiveFilter = selectedPlatformFilter != null || searchQuery.isNotBlank()
                val emptyMessage = when (tab) {
                    LibraryTab.LIKED -> if (!hasActiveFilter) "No liked songs." else "No liked songs match active filters."
                    LibraryTab.BOOKMARKS -> if (!hasActiveFilter) "No bookmarked songs." else "No bookmarks match active filters."
                    LibraryTab.COOLDOWN -> if (!hasActiveFilter) "No cooldown songs." else "No cooldown songs match active filters."
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
                var visibleLimit by remember(tab, selectedPlatformFilter, searchQuery) { mutableIntStateOf(25) }
                val pagedTracks = remember(trackList, visibleLimit) { trackList.take(visibleLimit) }

                LaunchedEffect(listState, trackList.size) {
                    snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                        .collect { lastVisible ->
                            if (visibleLimit < trackList.size && lastVisible >= (visibleLimit - 6)) {
                                visibleLimit = (visibleLimit + 25).coerceAtMost(trackList.size)
                            }
                        }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Use stable item key per tab+trackId so removing/undoing updates ONLY that row without refreshing the whole list
                    itemsIndexed(pagedTracks, key = { _, it -> "${tab.name}_${it.id}" }) { index, track ->
                        val isCurrentPlaying = playingTrack?.id == track.id && isPlaying
                        var maxDragFraction by remember(track.id) { androidx.compose.runtime.mutableFloatStateOf(0f) }

                        @OptIn(ExperimentalMaterial3Api::class)
                        val dismissState = rememberSwipeToDismissBoxState(
                            positionalThreshold = { totalDistance -> totalDistance * 0.62f }
                        )

                        // Reset settled state cleanly when an undone item returns with the same stable key
                        LaunchedEffect(track.id) {
                            maxDragFraction = 0f
                            if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                                dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                            }
                        }

                        LaunchedEffect(dismissState.progress, dismissState.targetValue) {
                            if (dismissState.targetValue != SwipeToDismissBoxValue.Settled) {
                                maxDragFraction = maxOf(maxDragFraction, dismissState.progress)
                            } else if (dismissState.currentValue == SwipeToDismissBoxValue.Settled) {
                                maxDragFraction = 0f
                            }
                        }

                        // Reject short-distance accidental scroll flicks; only delete on deliberate horizontal swipe
                        LaunchedEffect(dismissState.currentValue) {
                            if (dismissState.currentValue == SwipeToDismissBoxValue.StartToEnd ||
                                dismissState.currentValue == SwipeToDismissBoxValue.EndToStart
                            ) {
                                if (maxDragFraction >= 0.42f) {
                                    maxDragFraction = 0f
                                    viewModel.removeTrackFromLibrary(track)
                                } else {
                                    maxDragFraction = 0f
                                    dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                                }
                            }
                        }

                        Column(modifier = Modifier.animateItem()) {
                            @OptIn(ExperimentalMaterial3Api::class)
                            SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = {
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
                            if (index < pagedTracks.lastIndex) {
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

    // Floating In-App Mini Preview Player Bar for Library Auditioning (hidden when Immersive Dock handles Library mini-player)
    AnimatedVisibility(
        visible = !isImmersiveDockEnabled && playingTrack != null,
        enter = fadeIn(spring()) + slideInVertically(initialOffsetY = { it }),
        exit = fadeOut(tween(160)) + slideOutVertically(targetOffsetY = { it }),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 16.dp)
            .padding(bottom = 98.dp)
    ) {
        playingTrack?.let { track ->
            val playInteractionSource = remember { MutableInteractionSource() }
            val isPlayPressed by playInteractionSource.collectIsPressedAsState()
            val playScale by animateFloatAsState(
                targetValue = if (isPlayPressed) 0.88f else 1.0f,
                animationSpec = spring(),
                label = "playScale"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 12.dp, shape = RoundedCornerShape(16.dp), spotColor = Color(0x33000000))
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.cardBg)
                    .border(
                        1.dp,
                        Color(0x33888888),
                        RoundedCornerShape(16.dp)
                    )
                .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = track.artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${track.displayArtists().joinToString(", ")} • ${if (isPlaying) "Previewing" else "Paused"}",
                        fontSize = 11.sp,
                        color = if (isPlaying) colors.accentGreen else colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .scale(playScale)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(colors.accentGreen)
                        .clickable(
                            interactionSource = playInteractionSource,
                            indication = null
                        ) { viewModel.audioEngine.togglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = colors.onAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .clickable { viewModel.audioEngine.stop() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Stop",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
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
