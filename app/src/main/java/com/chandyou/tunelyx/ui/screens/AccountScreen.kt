package com.chandyou.tunelyx.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chandyou.tunelyx.model.GenreTrajectory
import com.chandyou.tunelyx.model.GenreAffinityStat
import com.chandyou.tunelyx.model.TopArtistStat
import com.chandyou.tunelyx.model.ActiveDayStat
import com.chandyou.tunelyx.model.UserTasteProfile
import com.chandyou.tunelyx.ui.theme.TunelyxTheme
import com.chandyou.tunelyx.viewmodel.DiscoverViewModel
import com.chandyou.tunelyx.viewmodel.ImportMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(
    viewModel: DiscoverViewModel,
    modifier: Modifier = Modifier
) {
    YouScreen(viewModel = viewModel, modifier = modifier)
}

@Composable
fun YouScreen(
    viewModel: DiscoverViewModel,
    modifier: Modifier = Modifier
) {
    val colors = TunelyxTheme.colors
    val tasteProfile by viewModel.tasteProfile.collectAsState()
    val isImmersiveDockEnabled by viewModel.isImmersiveDockEnabled.collectAsState()
    val playingTrack by viewModel.audioEngine.currentTrack.collectAsState()
    val bottomPad = when {
        isImmersiveDockEnabled -> if (playingTrack != null || viewModel.currentTrack != null) 156.dp else 98.dp
        playingTrack != null -> 160.dp
        else -> 98.dp
    }

    var isGenreExpanded by remember { mutableStateOf(false) }
    var isArtistsExpanded by remember { mutableStateOf(false) }
    var isTrajectoryExpanded by remember { mutableStateOf(true) }
    var isSessionsExpanded by remember { mutableStateOf(false) }
    var isBackupExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var pendingImportContent by remember { mutableStateOf<String?>(null) }
    var selectedImportMode by remember { mutableStateOf(ImportMode.MERGE) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val json = viewModel.exportBackupJson()
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    }
                    backupMessage = "Data exported successfully."
                } catch (e: Exception) {
                    backupMessage = "Export failed: ${e.localizedMessage ?: "disk error"}"
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val text = context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader().readText()
                    }
                    if (!text.isNullOrBlank()) {
                        pendingImportContent = text
                    }
                } catch (e: Exception) {
                    backupMessage = "Import failed: ${e.localizedMessage ?: "file read error"}"
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 10.dp, bottom = bottomPad)
    ) {
        // 1. Taste Profile Primary Card (Main Summary Metrics)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.cardBg)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "Taste Profile",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (tasteProfile.totalSongsExplored < 3) {
                Text(
                    text = "Explore a few songs in Discover to generate your taste profile.",
                    fontSize = 13.sp,
                    color = colors.textSecondary
                )
            } else {
                // Overview metrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TasteMetricPill(
                        value = "${tasteProfile.totalSongsExplored}",
                        label = "EXPLORED",
                        modifier = Modifier.weight(1f)
                    )
                    TasteMetricPill(
                        value = "${tasteProfile.totalSongsSaved}",
                        label = "SAVED",
                        modifier = Modifier.weight(1f)
                    )
                    TasteMetricPill(
                        value = "${tasteProfile.discoveryRatePercentage}%",
                        label = "DISCOVERY",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (tasteProfile.totalSongsExplored >= 3) {
            // Affinity Progression Dropdown (Chart below number stats, kept expanded by default)
            if (tasteProfile.trajectories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                CollapsibleTasteSection(
                    title = "Affinity Progression",
                    badgeText = "Trajectories",
                    isExpanded = isTrajectoryExpanded,
                    onToggle = { isTrajectoryExpanded = !isTrajectoryExpanded }
                ) {
                    TasteTrajectoryChart(
                        trajectories = tasteProfile.trajectories,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Chart Legend
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tasteProfile.trajectories.forEach { traj ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(traj.genre.baseColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = traj.genre.displayName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Genre Breakdown Dropdown
            if (tasteProfile.topGenres.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                CollapsibleTasteSection(
                    title = "Genre Breakdown",
                    badgeText = "${tasteProfile.topGenres.size} genres",
                    isExpanded = isGenreExpanded,
                    onToggle = { isGenreExpanded = !isGenreExpanded }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        tasteProfile.topGenres.forEach { stat ->
                            GenreAffinityBar(stat = stat)
                        }
                    }
                }
            }

            // Top Artists Dropdown
            if (tasteProfile.topArtists.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                CollapsibleTasteSection(
                    title = "Top Artists",
                    badgeText = "${tasteProfile.topArtists.size} artists",
                    isExpanded = isArtistsExpanded,
                    onToggle = { isArtistsExpanded = !isArtistsExpanded }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        tasteProfile.topArtists.forEachIndexed { index, artistStat ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textTertiary,
                                    modifier = Modifier.width(22.dp)
                                )
                                Text(
                                    text = artistStat.artistName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1
                                )
                                Text(
                                    text = "${artistStat.likesCount} saved",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Active Sessions Dropdown
            if (tasteProfile.activeDays.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                CollapsibleTasteSection(
                    title = "Active Sessions",
                    badgeText = "${tasteProfile.activeDays.size} days",
                    isExpanded = isSessionsExpanded,
                    onToggle = { isSessionsExpanded = !isSessionsExpanded }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        tasteProfile.activeDays.take(6).forEach { day ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = day.dateLabel,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = "${day.totalSwipes} explored • ${day.savedCount} saved",
                                    fontSize = 12.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Data Backup & Restore Dropdown
        CollapsibleTasteSection(
            title = "Data Backup & Restore",
            badgeText = "Storage",
            isExpanded = isBackupExpanded,
            onToggle = { isBackupExpanded = !isBackupExpanded }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US).format(java.util.Date())
                        exportLauncher.launch("tunelyx_backup_$timestamp.json")
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.bgSurfaceElevated,
                        contentColor = colors.textPrimary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
                    modifier = Modifier.weight(1f).height(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FileUpload,
                        contentDescription = "Export Data",
                        tint = colors.accentGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Export",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = {
                        importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.bgSurfaceElevated,
                        contentColor = colors.textPrimary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
                    modifier = Modifier.weight(1f).height(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "Import Data",
                        tint = colors.accentGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Import",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (!backupMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = backupMessage!!,
                    fontSize = 12.sp,
                    color = colors.accentGreen,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Import Confirmation Dialog with Non-Destructive Merge Choice
        if (pendingImportContent != null) {
            AlertDialog(
                onDismissRequest = { pendingImportContent = null },
                title = {
                    Text(
                        text = "Import Songs & Data",
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Merge Option (Default)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selectedImportMode == ImportMode.MERGE) colors.bgSurfaceElevated else Color.Transparent)
                                .border(
                                    width = if (selectedImportMode == ImportMode.MERGE) 1.5.dp else 1.dp,
                                    color = if (selectedImportMode == ImportMode.MERGE) colors.accentGreen else colors.borderSubtle,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedImportMode = ImportMode.MERGE }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Merge with existing library",
                                fontSize = 14.sp,
                                fontWeight = if (selectedImportMode == ImportMode.MERGE) FontWeight.Bold else FontWeight.Medium,
                                color = colors.textPrimary
                            )
                            if (selectedImportMode == ImportMode.MERGE) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = colors.accentGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Replace Option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selectedImportMode == ImportMode.REPLACE) colors.bgSurfaceElevated else Color.Transparent)
                                .border(
                                    width = if (selectedImportMode == ImportMode.REPLACE) 1.5.dp else 1.dp,
                                    color = if (selectedImportMode == ImportMode.REPLACE) colors.accentRed else colors.borderSubtle,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedImportMode = ImportMode.REPLACE }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Replace existing library",
                                fontSize = 14.sp,
                                fontWeight = if (selectedImportMode == ImportMode.REPLACE) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedImportMode == ImportMode.REPLACE) colors.accentRed else colors.textPrimary
                            )
                            if (selectedImportMode == ImportMode.REPLACE) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = colors.accentRed,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val content = pendingImportContent
                            val mode = selectedImportMode
                            pendingImportContent = null
                            if (!content.isNullOrBlank()) {
                                coroutineScope.launch(Dispatchers.IO) {
                                    val result = viewModel.importFromText(content, mode)
                                    backupMessage = result.getOrElse { "Import error: ${it.localizedMessage}" }
                                }
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentGreen,
                            contentColor = colors.onAccent
                        )
                    ) {
                        Text("Import", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { pendingImportContent = null },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = colors.textSecondary
                        )
                    ) {
                        Text("Cancel")
                    }
                },
                containerColor = colors.bgSurfaceElevated,
                shape = RoundedCornerShape(18.dp)
            )
        }
    }
}

@Composable
fun TasteTrajectoryChart(
    trajectories: List<GenreTrajectory>,
    modifier: Modifier = Modifier
) {
    val colors = TunelyxTheme.colors
    val days = trajectories.firstOrNull()?.points?.map { it.dayLabel }
        ?: listOf("Start", "Mid", "Now")
    val numPoints = trajectories.firstOrNull()?.points?.size ?: 0

    // Progressive path draw-on animation (left-to-right progressive curve unveiling)
    val drawProgress = remember { Animatable(0f) }
    val trajectoryKey = remember(trajectories) {
        trajectories.joinToString { "${it.genre.name}:${it.points.size}" }
    }
    LaunchedEffect(trajectoryKey) {
        drawProgress.snapTo(0f)
        drawProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing)
        )
    }

    Column(modifier = modifier) {
        // Canvas chart area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val chartWidth = size.width
                val chartHeight = size.height

                // 1. Draw horizontal reference grid lines (25%, 50%, 75%, 100%)
                val gridLevels = listOf(0.25f, 0.5f, 0.7f, 0.95f)
                val gridDashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

                gridLevels.forEach { level ->
                    val y = chartHeight * (1f - level)
                    drawLine(
                        color = colors.borderSubtle.copy(alpha = 0.5f),
                        start = Offset(0f, y),
                        end = Offset(chartWidth, y),
                        strokeWidth = 1f,
                        pathEffect = gridDashEffect
                    )
                }

                // 2. Draw each trajectory's smooth cubic bezier curve with animated progressive clipping
                if (numPoints >= 2) {
                    val stepX = chartWidth / (numPoints - 1)
                    val currentMaxX = chartWidth * drawProgress.value

                    clipRect(left = 0f, top = 0f, right = currentMaxX, bottom = chartHeight) {
                        trajectories.forEach { traj ->
                            if (traj.points.size >= numPoints) {
                                val strokePath = Path()
                                val fillPath = Path()

                                val coords = traj.points.mapIndexed { idx, pt ->
                                    val x = idx * stepX
                                    val normalized = (pt.score / 100f).coerceIn(0.05f, 0.95f)
                                    val y = chartHeight * (1f - normalized)
                                    Offset(x, y)
                                }

                                // Build smooth bezier path
                                strokePath.moveTo(coords[0].x, coords[0].y)
                                fillPath.moveTo(coords[0].x, coords[0].y)

                                for (i in 0 until coords.size - 1) {
                                    val p0 = coords[i]
                                    val p1 = coords[i + 1]
                                    val midX = (p0.x + p1.x) / 2f

                                    strokePath.cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                                    fillPath.cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                                }

                                // Complete fill path down to chart baseline
                                fillPath.lineTo(coords.last().x, chartHeight)
                                fillPath.lineTo(coords.first().x, chartHeight)
                                fillPath.close()

                                // Draw fill gradient
                                drawPath(
                                    path = fillPath,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            traj.genre.baseColor.copy(alpha = 0.18f),
                                            Color.Transparent
                                        ),
                                        startY = 0f,
                                        endY = chartHeight
                                    )
                                )

                                // Draw stroke
                                drawPath(
                                    path = strokePath,
                                    color = traj.genre.baseColor,
                                    style = Stroke(
                                        width = 3.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                )
                            }
                        }
                    }

                    // Glowing points on "Now" fade & pop in at completion of progressive draw
                    if (drawProgress.value > 0.85f) {
                        val dotScale = ((drawProgress.value - 0.85f) / 0.15f).coerceIn(0f, 1f)
                        trajectories.forEach { traj ->
                            if (traj.points.size >= numPoints) {
                                val lastNormalized = (traj.points.last().score / 100f).coerceIn(0.05f, 0.95f)
                                val lastPoint = Offset(chartWidth, chartHeight * (1f - lastNormalized))

                                drawCircle(
                                    color = traj.genre.baseColor.copy(alpha = 0.35f * dotScale),
                                    radius = 7.dp.toPx() * dotScale,
                                    center = lastPoint
                                )
                                drawCircle(
                                    color = traj.genre.baseColor.copy(alpha = dotScale),
                                    radius = 4.dp.toPx() * dotScale,
                                    center = lastPoint
                                )
                                drawCircle(
                                    color = Color.White.copy(alpha = dotScale),
                                    radius = 2.dp.toPx() * dotScale,
                                    center = lastPoint
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // X-axis Day Labels
        if (days.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                days.forEach { day ->
                    Text(
                        text = day,
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun TasteMetricPill(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    val colors = TunelyxTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.bgSurfaceElevated)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textSecondary,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun GenreAffinityBar(stat: GenreAffinityStat) {
    val colors = TunelyxTheme.colors
    val percentageInt = stat.percentage.toInt().coerceIn(0, 100)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(stat.genre.baseColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stat.genre.displayName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary
                )
            }
            Text(
                text = "$percentageInt%",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textSecondary
            )
        }

        Spacer(modifier = Modifier.height(5.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(colors.bgSurfaceElevated)
        ) {
            val fillFraction = (stat.percentage / 100f).coerceIn(0.02f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth(fillFraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(stat.genre.baseColor)
            )
        }
    }
}

@Composable
private fun CollapsibleTasteSection(
    title: String,
    badgeText: String? = null,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = TunelyxTheme.colors
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "chevronRotation"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.cardBg)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(18.dp))
            .clickable { onToggle() }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                if (badgeText != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (colors.isDark) Color(0x14FFFFFF) else Color(0x0D000000))
                            .border(0.6.dp, colors.borderSubtle, RoundedCornerShape(8.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textSecondary
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = colors.textSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(rotation)
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(160)),
            exit = shrinkVertically(animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)) + fadeOut(tween(120))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
            ) {
                content()
            }
        }
    }
}
