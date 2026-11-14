package com.chandyou.tunelyx.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chandyou.tunelyx.ui.theme.TunelyxTheme
import com.chandyou.tunelyx.viewmodel.ActiveSubScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    onDismissRequest: () -> Unit,
    onNavigateToSubScreen: (ActiveSubScreen) -> Unit,
    onClearCache: () -> Unit = {},
    onResetHistory: () -> Unit = {},
    onResetLiked: () -> Unit = {},
    onResetBookmarks: () -> Unit = {},
    onResetDisliked: () -> Unit = {},
    onResetSkipped: () -> Unit = {},
    onResetAllData: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = TunelyxTheme.colors
    var showDataDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = colors.bgSurfaceElevated,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(colors.borderFocus)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Settings",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Navigation Categories
            SettingsCategoryTile(
                title = "Appearance",
                icon = Icons.Rounded.Palette,
                onClick = {
                    onDismissRequest()
                    onNavigateToSubScreen(ActiveSubScreen.APPEARANCE)
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            SettingsCategoryTile(
                title = "Playback",
                icon = Icons.Rounded.PlayCircle,
                onClick = {
                    onDismissRequest()
                    onNavigateToSubScreen(ActiveSubScreen.PLAYBACK)
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            SettingsCategoryTile(
                title = "Services",
                icon = Icons.Rounded.Cloud,
                onClick = {
                    onDismissRequest()
                    onNavigateToSubScreen(ActiveSubScreen.SERVICES)
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            SettingsCategoryTile(
                title = "Filter List",
                icon = Icons.Rounded.Tune,
                onClick = {
                    onDismissRequest()
                    onNavigateToSubScreen(ActiveSubScreen.FILTER_LIST)
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            SettingsCategoryTile(
                title = "About",
                icon = Icons.Rounded.Info,
                onClick = {
                    onDismissRequest()
                    onNavigateToSubScreen(ActiveSubScreen.ABOUT)
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Storage & Data Management Button
            val storageInteraction = remember { MutableInteractionSource() }
            val isStoragePressed by storageInteraction.collectIsPressedAsState()
            val storageScale by animateFloatAsState(
                targetValue = if (isStoragePressed) 0.96f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "storageScale"
            )

            OutlinedButton(
                onClick = { showDataDialog = true },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = colors.dislikeRed
                ),
                border = BorderStroke(1.dp, colors.accentGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(storageScale)
                    .height(48.dp),
                interactionSource = storageInteraction
            ) {
                Text(
                    text = "Storage & Data Management",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.dislikeRed
                )
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    // Granular Storage & Data Management Dialog
    if (showDataDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDataDialog = false },
            title = {
                Text(
                    text = "Data Management",
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DataActionRow(
                        title = "Clear Cached Songs",
                        actionLabel = "Clear",
                        isDestructive = false,
                        onClick = {
                            onClearCache()
                            showDataDialog = false
                        }
                    )
                    DataActionRow(
                        title = "Reset Liked Songs",
                        actionLabel = "Reset",
                        isDestructive = true,
                        onClick = {
                            onResetLiked()
                            showDataDialog = false
                        }
                    )
                    DataActionRow(
                        title = "Reset Disliked Songs",
                        actionLabel = "Reset",
                        isDestructive = true,
                        onClick = {
                            onResetDisliked()
                            showDataDialog = false
                        }
                    )
                    DataActionRow(
                        title = "Reset Library Bookmarks",
                        actionLabel = "Reset",
                        isDestructive = true,
                        onClick = {
                            onResetBookmarks()
                            showDataDialog = false
                        }
                    )
                    DataActionRow(
                        title = "Reset Skipped Songs",
                        actionLabel = "Reset",
                        isDestructive = false,
                        onClick = {
                            onResetSkipped()
                            showDataDialog = false
                        }
                    )
                    DataActionRow(
                        title = "Reset Complete History",
                        actionLabel = "Reset",
                        isDestructive = true,
                        onClick = {
                            onResetHistory()
                            showDataDialog = false
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    DataActionRow(
                        title = "Reset All App Data",
                        actionLabel = "Erase All",
                        isDestructive = true,
                        onClick = {
                            onResetAllData()
                            showDataDialog = false
                        }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showDataDialog = false },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.bgSurfaceElevated,
                        contentColor = colors.textPrimary
                    )
                ) {
                    Text("Close", fontWeight = FontWeight.SemiBold)
                }
            },
            containerColor = colors.bgSurfaceElevated,
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@Composable
private fun SettingsCategoryTile(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val colors = TunelyxTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "categoryTileScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.cardBg)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.accentGreenGlow),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.accentGreen,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun DataActionRow(
    title: String,
    actionLabel: String,
    isDestructive: Boolean,
    onClick: () -> Unit
) {
    val colors = TunelyxTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "dataRowScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            color = colors.textPrimary,
            fontWeight = FontWeight.Medium
        )
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isDestructive) colors.dislikeRed.copy(alpha = 0.15f) else colors.bgBase,
                contentColor = if (isDestructive) colors.dislikeRed else colors.textPrimary
            ),
            modifier = Modifier
                .scale(scale)
                .height(34.dp),
            interactionSource = interaction
        ) {
            Text(actionLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
