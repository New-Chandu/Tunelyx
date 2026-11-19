package com.chandyou.tunelyx.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chandyou.tunelyx.ui.animations.AboutSettingsButton
import com.chandyou.tunelyx.ui.animations.AppearanceSettingsButton
import com.chandyou.tunelyx.ui.animations.FilterListSettingsButton
import com.chandyou.tunelyx.ui.animations.MusicInteractionPalette
import com.chandyou.tunelyx.ui.animations.PlaybackSettingsButton
import com.chandyou.tunelyx.ui.theme.TunelyxTheme
import com.chandyou.tunelyx.viewmodel.ActiveSubScreen

private data class PendingDataAction(
    val title: String,
    val description: String,
    val confirmLabel: String,
    val isDestructive: Boolean,
    val onConfirm: () -> Unit
)

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

    var pendingAction by remember { mutableStateOf<PendingDataAction?>(null) }
    var isDataExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = colors.bgSurfaceElevated,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(36.dp)
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
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .navigationBarsPadding()
        ) {
            // Header Row: Title & Close Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Settings",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    letterSpacing = (-0.5).sp
                )
                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(colors.cardBg)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Section 1 Header: Preferences
            Text(
                text = "PREFERENCES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textSecondary,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            // Inset Grouped Card: Navigation Categories
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.cardBg)
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(20.dp))
            ) {
                SettingsGroupRow(
                    title = "Appearance",
                    subtitle = "Themes, card mode & dock style",
                    onClick = { onNavigateToSubScreen(ActiveSubScreen.APPEARANCE) },
                    iconSlot = {
                        AppearanceSettingsButton(
                            onClick = { onNavigateToSubScreen(ActiveSubScreen.APPEARANCE) },
                            touchSize = 36.dp,
                            iconSize = 24.dp,
                            palette = MusicInteractionPalette(
                                active = colors.accentGreen,
                                inactive = colors.accentGreen,
                                ripple = colors.accentGreenGlow
                            )
                        )
                    }
                )

                HorizontalDivider(
                    color = colors.borderSubtle.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 66.dp, end = 16.dp)
                )

                SettingsGroupRow(
                    title = "Playback",
                    subtitle = "Streaming provider & tactile haptics",
                    onClick = { onNavigateToSubScreen(ActiveSubScreen.PLAYBACK) },
                    iconSlot = {
                        PlaybackSettingsButton(
                            onClick = { onNavigateToSubScreen(ActiveSubScreen.PLAYBACK) },
                            touchSize = 36.dp,
                            iconSize = 24.dp,
                            palette = MusicInteractionPalette(
                                active = colors.accentGreen,
                                inactive = colors.accentGreen,
                                ripple = colors.accentGreenGlow
                            )
                        )
                    }
                )

                HorizontalDivider(
                    color = colors.borderSubtle.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 66.dp, end = 16.dp)
                )

                SettingsGroupRow(
                    title = "Filter List",
                    subtitle = "Blocked artists & music filters",
                    onClick = { onNavigateToSubScreen(ActiveSubScreen.FILTER_LIST) },
                    iconSlot = {
                        FilterListSettingsButton(
                            onClick = { onNavigateToSubScreen(ActiveSubScreen.FILTER_LIST) },
                            touchSize = 36.dp,
                            iconSize = 24.dp,
                            palette = MusicInteractionPalette(
                                active = colors.accentGreen,
                                inactive = colors.accentGreen,
                                ripple = colors.accentGreenGlow
                            )
                        )
                    }
                )

                HorizontalDivider(
                    color = colors.borderSubtle.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 66.dp, end = 16.dp)
                )

                SettingsGroupRow(
                    title = "About",
                    subtitle = "Version, license & project details",
                    onClick = { onNavigateToSubScreen(ActiveSubScreen.ABOUT) },
                    iconSlot = {
                        AboutSettingsButton(
                            onClick = { onNavigateToSubScreen(ActiveSubScreen.ABOUT) },
                            touchSize = 36.dp,
                            iconSize = 24.dp,
                            palette = MusicInteractionPalette(
                                active = colors.accentGreen,
                                inactive = colors.accentGreen,
                                ripple = colors.accentGreenGlow
                            )
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 2 Header: Data & Storage
            Text(
                text = "DATA & STORAGE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textSecondary,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            // Inset Grouped Card: Storage & Data Management
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.cardBg)
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(20.dp))
            ) {
                // Quick Toggle Row for Storage Management Actions
                SettingsGroupRow(
                    title = "Manage Storage & Cache",
                    subtitle = if (isDataExpanded) "Hide data actions" else "Clear cache, reset history or erase data",
                    onClick = { isDataExpanded = !isDataExpanded },
                    iconSlot = {
                        Icon(
                            imageVector = Icons.Rounded.Storage,
                            contentDescription = null,
                            tint = colors.accentGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                )

                AnimatedVisibility(
                    visible = isDataExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HorizontalDivider(
                            color = colors.borderSubtle.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        DataActionItemRow(
                            title = "Clear Cached Songs",
                            subtitle = "Frees temporary preview audio and deck memory",
                            actionLabel = "Clear",
                            isDestructive = false,
                            onClick = {
                                pendingAction = PendingDataAction(
                                    title = "Clear Cached Songs",
                                    description = "This will clear temporarily cached preview streams and deck data. Your likes and library will remain intact.",
                                    confirmLabel = "Clear Cache",
                                    isDestructive = false,
                                    onConfirm = { onClearCache() }
                                )
                            }
                        )

                        HorizontalDivider(
                            color = colors.borderSubtle.copy(alpha = 0.3f),
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp)
                        )

                        DataActionItemRow(
                            title = "Reset Liked Songs",
                            subtitle = "Removes all saved liked songs",
                            actionLabel = "Reset",
                            isDestructive = true,
                            onClick = {
                                pendingAction = PendingDataAction(
                                    title = "Reset Liked Songs",
                                    description = "This will remove all liked songs from your history and library. This cannot be undone.",
                                    confirmLabel = "Reset",
                                    isDestructive = true,
                                    onConfirm = { onResetLiked() }
                                )
                            }
                        )

                        HorizontalDivider(
                            color = colors.borderSubtle.copy(alpha = 0.3f),
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp)
                        )

                        DataActionItemRow(
                            title = "Reset Bookmarks",
                            subtitle = "Clears all in-app saved bookmarks",
                            actionLabel = "Reset",
                            isDestructive = true,
                            onClick = {
                                pendingAction = PendingDataAction(
                                    title = "Reset Bookmarks",
                                    description = "This will remove all in-app bookmarked songs from your library.",
                                    confirmLabel = "Reset",
                                    isDestructive = true,
                                    onConfirm = { onResetBookmarks() }
                                )
                            }
                        )

                        HorizontalDivider(
                            color = colors.borderSubtle.copy(alpha = 0.3f),
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp)
                        )

                        DataActionItemRow(
                            title = "Reset Disliked Songs",
                            subtitle = "Unbans all disliked tracks so they can reappear",
                            actionLabel = "Reset",
                            isDestructive = false,
                            onClick = {
                                pendingAction = PendingDataAction(
                                    title = "Reset Disliked Songs",
                                    description = "This will unban all previously disliked songs, allowing them to appear in discovery again.",
                                    confirmLabel = "Unban All",
                                    isDestructive = false,
                                    onConfirm = { onResetDisliked() }
                                )
                            }
                        )

                        HorizontalDivider(
                            color = colors.borderSubtle.copy(alpha = 0.3f),
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp)
                        )

                        DataActionItemRow(
                            title = "Reset Complete History",
                            subtitle = "Clears all past swipe records and interactions",
                            actionLabel = "Reset",
                            isDestructive = true,
                            onClick = {
                                pendingAction = PendingDataAction(
                                    title = "Reset Complete History",
                                    description = "This will clear your complete swipe history and reset all genre affinity scores.",
                                    confirmLabel = "Reset History",
                                    isDestructive = true,
                                    onConfirm = { onResetHistory() }
                                )
                            }
                        )

                        HorizontalDivider(
                            color = colors.borderSubtle.copy(alpha = 0.3f),
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp)
                        )

                        DataActionItemRow(
                            title = "Erase All App Data",
                            subtitle = "Full reset of preferences, history and library",
                            actionLabel = "Erase",
                            isDestructive = true,
                            onClick = {
                                pendingAction = PendingDataAction(
                                    title = "Erase All App Data",
                                    description = "This will completely reset Tunelyx back to fresh install state, removing all preferences, library tracks, and history.",
                                    confirmLabel = "Erase All Data",
                                    isDestructive = true,
                                    onConfirm = { onResetAllData() }
                                )
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    // Confirmation Alert Dialog for Data Operations
    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            icon = {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (action.isDestructive) colors.dislikeRed.copy(alpha = 0.15f) else colors.accentGreenGlow),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (action.isDestructive) Icons.Rounded.DeleteOutline else Icons.Rounded.Storage,
                        contentDescription = null,
                        tint = if (action.isDestructive) colors.dislikeRed else colors.accentGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
            },
            title = {
                Text(
                    text = action.title,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = action.description,
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        action.onConfirm()
                        pendingAction = null
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (action.isDestructive) colors.dislikeRed else colors.accentGreen,
                        contentColor = if (action.isDestructive) Color.White else Color.Black
                    )
                ) {
                    Text(action.confirmLabel, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            },
            dismissButton = {
                Button(
                    onClick = { pendingAction = null },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.cardBg,
                        contentColor = colors.textPrimary
                    )
                ) {
                    Text("Cancel", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }
            },
            containerColor = colors.bgSurfaceElevated,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun SettingsGroupRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    iconSlot: @Composable () -> Unit
) {
    val colors = TunelyxTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "groupRowScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                iconSlot()
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    letterSpacing = (-0.2).sp
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    lineHeight = 16.sp
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textSecondary.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun DataActionItemRow(
    title: String,
    subtitle: String,
    actionLabel: String,
    isDestructive: Boolean,
    onClick: () -> Unit
) {
    val colors = TunelyxTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "dataActionScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = colors.textSecondary,
                lineHeight = 14.sp
            )
        }
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isDestructive) colors.dislikeRed.copy(alpha = 0.15f) else colors.bgBase,
                contentColor = if (isDestructive) colors.dislikeRed else colors.textPrimary
            ),
            modifier = Modifier
                .scale(scale)
                .height(32.dp),
            interactionSource = interaction
        ) {
            Text(actionLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
