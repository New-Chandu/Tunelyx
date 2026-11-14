package com.chandyou.tunelyx.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chandyou.tunelyx.ui.theme.TunelyxTheme
import com.chandyou.tunelyx.viewmodel.DiscoverViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterListScreen(
    viewModel: DiscoverViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = TunelyxTheme.colors

    val filterRemix by viewModel.filterRemix.collectAsState()
    val filterAcoustic by viewModel.filterAcoustic.collectAsState()
    val filterDiffVers by viewModel.filterDiffVers.collectAsState()
    val filterTrending by viewModel.filterTrending.collectAsState()
    val blockedKeywords by viewModel.blockedKeywords.collectAsState()
    val blockedArtists by viewModel.blockedArtists.collectAsState()

    var keywordInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgBase)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Top Navigation Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Filter List",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION: VERSION FILTERS
        Text(
            text = "AUDIO FILTERS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = colors.accentGreen,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        FilterSwitchRow(
            label = "Filter Remixes",
            checked = filterRemix,
            onCheckedChange = { viewModel.setFilterRemix(it) }
        )

        FilterSwitchRow(
            label = "Filter Acoustic Versions",
            checked = filterAcoustic,
            onCheckedChange = { viewModel.setFilterAcoustic(it) }
        )

        FilterSwitchRow(
            label = "Filter Covers & Live Recordings",
            checked = filterDiffVers,
            onCheckedChange = { viewModel.setFilterDiffVers(it) }
        )

        FilterSwitchRow(
            label = "Block Trending Versions",
            checked = filterTrending,
            onCheckedChange = { viewModel.setFilterTrending(it) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // SECTION: KEYWORD BLACKLIST
        Text(
            text = "KEYWORD BLACKLIST",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = colors.accentGreen,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x18FFFFFF))
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = keywordInput,
                    onValueChange = { keywordInput = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    cursorBrush = SolidColor(colors.accentGreen),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val trimmed = keywordInput.trim()
                            if (trimmed.isNotBlank()) {
                                viewModel.addBlockedKeyword(trimmed)
                                keywordInput = ""
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            val addInteraction = remember { MutableInteractionSource() }
            val isAddPressed by addInteraction.collectIsPressedAsState()
            val addScale by animateFloatAsState(
                targetValue = if (isAddPressed) 0.92f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "addKeywordScale"
            )

            Button(
                onClick = {
                    val trimmed = keywordInput.trim()
                    if (trimmed.isNotBlank()) {
                        viewModel.addBlockedKeyword(trimmed)
                        keywordInput = ""
                    }
                },
                interactionSource = addInteraction,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentGreen),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(44.dp)
                    .scale(addScale)
            ) {
                Text(
                    text = "Add",
                    color = colors.onAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (blockedKeywords.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                blockedKeywords.forEach { keyword ->
                    val chipInteraction = remember { MutableInteractionSource() }
                    val isChipPressed by chipInteraction.collectIsPressedAsState()
                    val chipScale by animateFloatAsState(
                        targetValue = if (isChipPressed) 0.92f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "chipScale"
                    )
                    Row(
                        modifier = Modifier
                            .scale(chipScale)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x18FFFFFF))
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(8.dp))
                            .clickable(
                                interactionSource = chipInteraction,
                                indication = null
                            ) { viewModel.removeBlockedKeyword(keyword) }
                            .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = keyword,
                            fontSize = 12.sp,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Remove keyword",
                            tint = colors.dislikeRed,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Blocked Artists Management Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.cardBg)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BLOCKED ARTISTS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textSecondary,
                    letterSpacing = 0.8.sp
                )
                if (blockedArtists.isNotEmpty()) {
                    Text(
                        text = "${blockedArtists.size} blocked",
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (blockedArtists.isEmpty()) {
                Text(
                    text = "No artists blocked. Blocked artists will never appear in your discovery deck.",
                    fontSize = 13.sp,
                    color = colors.textTertiary,
                    lineHeight = 18.sp
                )
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    blockedArtists.forEach { artistName ->
                        val artistInteraction = remember { MutableInteractionSource() }
                        val isArtistPressed by artistInteraction.collectIsPressedAsState()
                        val artistScale by animateFloatAsState(
                            targetValue = if (isArtistPressed) 0.92f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                            label = "artistScale"
                        )

                        Row(
                            modifier = Modifier
                                .scale(artistScale)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x18FFFFFF))
                                .border(1.dp, colors.borderSubtle, RoundedCornerShape(8.dp))
                                .clickable(
                                    interactionSource = artistInteraction,
                                    indication = null
                                ) { viewModel.unblockArtist(artistName) }
                                .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = artistName,
                                fontSize = 12.sp,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Unblock artist",
                                tint = colors.dislikeRed,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun FilterSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = TunelyxTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "switchRowScale"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = interaction,
                indication = null
            ) { onCheckedChange(!checked) }
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f).padding(end = 12.dp)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccent,
                checkedTrackColor = colors.accentGreen,
                checkedBorderColor = colors.accentGreen,
                uncheckedThumbColor = colors.textSecondary,
                uncheckedTrackColor = colors.bgBase,
                uncheckedBorderColor = colors.borderFocus
            )
        )
    }
}
