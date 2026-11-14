package com.chandyou.tunelyx.ui.components

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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chandyou.tunelyx.ui.theme.TunelyxTheme

enum class MainTab {
    DISCOVER,
    LIBRARY,
    YOU;
    companion object {
        val ACCOUNT: MainTab get() = YOU
    }
}

@Composable
fun FloatingBottomNav(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = TunelyxTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 330.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Isolated Explore (Discover) Button
            val isHomeActive = currentTab == MainTab.DISCOVER
            val homeInteractionSource = remember { MutableInteractionSource() }
            val isHomePressed by homeInteractionSource.collectIsPressedAsState()
            val homePressScale by animateFloatAsState(
                targetValue = if (isHomePressed) 0.90f else 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
                label = "homePressScale"
            )
            val homeScale by animateFloatAsState(
                targetValue = if (isHomeActive) 1.04f else 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "homeScale"
            )

            Box(
                modifier = Modifier
                    .scale(homeScale * homePressScale)
                    .height(50.dp)
                    .width(78.dp)
                    .shadow(
                        elevation = if (colors.isDark) 0.dp else 6.dp,
                        shape = CircleShape,
                        spotColor = Color(0x20000000)
                    )
                    .clip(CircleShape)
                    .background(if (isHomeActive) colors.accentGreenGlow else colors.bgGlass)
                    .border(
                        width = 1.dp,
                        color = if (isHomeActive) colors.accentGreen else colors.borderSubtle,
                        shape = CircleShape
                    )
                    .clickable(
                        interactionSource = homeInteractionSource,
                        indication = null
                    ) { onTabSelected(MainTab.DISCOVER) },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Discover",
                        tint = if (isHomeActive) colors.accentGreen else colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Discover",
                        fontSize = 10.sp,
                        fontWeight = if (isHomeActive) FontWeight.Bold else FontWeight.Medium,
                        color = if (isHomeActive) colors.accentGreen else colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // 2. Grouped Capsule Pill (Library & You) with Apple Music-Style Sliding Indicator
            BoxWithConstraints(
                modifier = Modifier
                    .height(50.dp)
                    .weight(1f)
                    .shadow(
                        elevation = if (colors.isDark) 0.dp else 6.dp,
                        shape = CircleShape,
                        spotColor = Color(0x20000000)
                    )
                    .clip(CircleShape)
                    .background(colors.bgGlass)
                    .border(1.dp, colors.borderSubtle, CircleShape)
                    .padding(3.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val pillWidth = maxWidth / 2

                // Target X offset for sliding active pill indicator
                val targetOffsetFraction = when (currentTab) {
                    MainTab.LIBRARY -> 0f
                    MainTab.YOU -> 1f
                    MainTab.DISCOVER -> -1f // Hidden off-stage
                }

                val animatedFraction by animateFloatAsState(
                    targetValue = targetOffsetFraction,
                    animationSpec = spring(
                        dampingRatio = 0.72f,
                        stiffness = 320f
                    ),
                    label = "slidingPillOffset"
                )

                // Gliding active pill background synced to active theme accent palette
                if (currentTab != MainTab.DISCOVER) {
                    Box(
                        modifier = Modifier
                            .offset {
                                val xPx = (animatedFraction * pillWidth.toPx()).toInt()
                                IntOffset(xPx, 0)
                            }
                            .width(pillWidth)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(colors.accentGreenGlow)
                            .border(1.dp, colors.accentGreen, CircleShape)
                    )
                }

                // Interactive Tab Items
                Row(modifier = Modifier.fillMaxSize()) {
                    NavGroupItem(
                        label = "Library",
                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                        isActive = currentTab == MainTab.LIBRARY,
                        modifier = Modifier.weight(1f),
                        onClick = { onTabSelected(MainTab.LIBRARY) }
                    )
                    NavGroupItem(
                        label = "You",
                        icon = Icons.Default.Person,
                        isActive = currentTab == MainTab.YOU,
                        modifier = Modifier.weight(1f),
                        onClick = { onTabSelected(MainTab.YOU) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NavGroupItem(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = TunelyxTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "navGroupPressScale"
    )
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.08f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tabItemScale"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.scale(scale * pressScale)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) colors.accentGreen else colors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) colors.accentGreen else colors.textSecondary
            )
        }
    }
}
