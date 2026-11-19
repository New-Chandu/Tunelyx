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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chandyou.tunelyx.ui.theme.AccentColorPreset
import com.chandyou.tunelyx.ui.theme.TunelyxTheme
import com.chandyou.tunelyx.viewmodel.DiscoverViewModel

@Composable
fun AppearanceScreen(
    viewModel: DiscoverViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = TunelyxTheme.colors
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val accentPreset by viewModel.accentPreset.collectAsState()
    val isImmersiveMode by viewModel.isImmersiveMode.collectAsState()
    val isImmersiveDockEnabled by viewModel.isImmersiveDockEnabled.collectAsState()
    val isDockShowLabelsEnabled by viewModel.isDockShowLabelsEnabled.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgBase)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Top Navigation Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.textPrimary
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Appearance",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                letterSpacing = (-0.5).sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 1. Theme Mode Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.cardBg)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "THEME MODE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textSecondary,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setDarkTheme(!isDarkTheme) }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Dark Theme",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary
                )
                Switch(
                    checked = isDarkTheme,
                    onCheckedChange = { viewModel.setDarkTheme(it) },
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

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Navigation Style Card (Immersive Dock toggle as segmented selector)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.cardBg)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "NAVIGATION CONTROLS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textSecondary,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(Color(0x18FFFFFF))
                    .padding(3.dp)
            ) {
                AppearanceSegmentButton(
                    text = "Dock",
                    isSelected = isImmersiveDockEnabled,
                    onClick = { viewModel.setImmersiveDockEnabled(true) },
                    modifier = Modifier.weight(1f)
                )
                AppearanceSegmentButton(
                    text = "Bar",
                    isSelected = !isImmersiveDockEnabled,
                    onClick = { viewModel.setImmersiveDockEnabled(false) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Card Visual Style Card (Immersive vs Minimalist)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.cardBg)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "CARD AESTHETICS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textSecondary,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(Color(0x18FFFFFF))
                    .padding(3.dp)
            ) {
                AppearanceSegmentButton(
                    text = "Immersive",
                    isSelected = isImmersiveMode,
                    onClick = { viewModel.setCardMode(true) },
                    modifier = Modifier.weight(1f)
                )
                AppearanceSegmentButton(
                    text = "Minimalist",
                    isSelected = !isImmersiveMode,
                    onClick = { viewModel.setCardMode(false) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Accent Color Palette Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.cardBg)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "ACCENT PALETTE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textSecondary,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AccentColorPreset.entries.forEach { preset ->
                    val isSelected = preset == accentPreset
                    val interaction = remember { MutableInteractionSource() }
                    val isPressed by interaction.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = if (isPressed) 0.88f else if (isSelected) 1.08f else 1.0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "accentScale_${preset.name}"
                    )

                    // In light mode, the white preset renders as sleek black (#18181B) to prevent being invisible on light cards
                    val swatchColor = if (!isDarkTheme && preset == AccentColorPreset.WHITE) {
                        Color(0xFF18181B)
                    } else {
                        preset.color
                    }
                    val swatchGlow = if (!isDarkTheme && preset == AccentColorPreset.WHITE) {
                        Color(0x2818181B)
                    } else {
                        preset.glow
                    }

                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .scale(scale)
                            .clip(CircleShape)
                            .background(if (isSelected) swatchGlow else Color.Transparent)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) swatchColor else colors.borderSubtle,
                                shape = CircleShape
                            )
                            .clickable(
                                interactionSource = interaction,
                                indication = null
                            ) { viewModel.setAccentPreset(preset) },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(swatchColor),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                val dotColor = if (!isDarkTheme && preset == AccentColorPreset.WHITE) {
                                    Color.White
                                } else if (preset == AccentColorPreset.WHITE) {
                                    Color.Black
                                } else {
                                    Color.White
                                }
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 5. Bottom Navigation Style (Title and Icon vs Icon Only)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.cardBg)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "BOTTOM NAV STYLE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textSecondary,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(colors.bgSurfaceElevated)
                    .border(1.dp, colors.borderSubtle, CircleShape)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AppearanceSegmentButton(
                    text = "Title & Icon",
                    isSelected = isDockShowLabelsEnabled,
                    onClick = { viewModel.setDockShowLabelsEnabled(true) },
                    modifier = Modifier.weight(1f)
                )
                AppearanceSegmentButton(
                    text = "Icon Only",
                    isSelected = !isDockShowLabelsEnabled,
                    onClick = { viewModel.setDockShowLabelsEnabled(false) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun AppearanceSegmentButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = TunelyxTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "appearanceSegmentScale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .fillMaxSize()
            .clip(CircleShape)
            .background(if (isSelected) colors.accentGreen else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) colors.onAccent else colors.textSecondary
        )
    }
}
