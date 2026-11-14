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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chandyou.tunelyx.data.UserPreferencesRepository
import com.chandyou.tunelyx.model.Genre
import com.chandyou.tunelyx.model.MusicProvider
import com.chandyou.tunelyx.ui.theme.TunelyxTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreferencesScreen(
    initialSongLanguages: Set<String>,
    initialAppLanguage: String,
    initialGenres: Set<Genre>,
    initialMusicProvider: MusicProvider = MusicProvider.JIOSAAVN,
    onComplete: (Set<String>, String, Set<Genre>, MusicProvider) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = TunelyxTheme.colors

    var selectedSongLanguages by remember { mutableStateOf(initialSongLanguages) }
    val selectedAppLanguage by remember { mutableStateOf(initialAppLanguage) }
    var selectedGenres by remember { mutableStateOf(initialGenres) }
    var selectedMusicProvider by remember { mutableStateOf(initialMusicProvider) }

    val saveInteractionSource = remember { MutableInteractionSource() }
    val isSavePressed by saveInteractionSource.collectIsPressedAsState()
    val saveScale by animateFloatAsState(
        targetValue = if (isSavePressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "saveScale"
    )

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
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Discovery Preferences",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            letterSpacing = (-0.5).sp
        )

        Spacer(modifier = Modifier.height(22.dp))

        // 1. Music Service Provider
        Text(
            text = "Service",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textSecondary
        )

        Spacer(modifier = Modifier.height(10.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MusicProvider.entries.chunked(2).forEach { rowProviders ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowProviders.forEach { provider ->
                        val isSelected = provider == selectedMusicProvider
                        ProviderOptionTile(
                            provider = provider,
                            isSelected = isSelected,
                            onClick = { selectedMusicProvider = provider },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // 2. Music Languages (Indian Languages)
        Text(
            text = "Languages",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textSecondary
        )

        Spacer(modifier = Modifier.height(10.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            UserPreferencesRepository.AVAILABLE_SONG_LANGUAGES.forEach { lang ->
                val isSelected = selectedSongLanguages.contains(lang)
                LanguageChip(
                    text = lang,
                    isSelected = isSelected,
                    onClick = {
                        selectedSongLanguages = if (isSelected) {
                            if (selectedSongLanguages.size > 1) selectedSongLanguages - lang else selectedSongLanguages
                        } else {
                            selectedSongLanguages + lang
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // 3. Favorite Genres
        Text(
            text = "Genres",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textSecondary
        )

        Spacer(modifier = Modifier.height(10.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Genre.entries.forEach { genre ->
                val isSelected = selectedGenres.contains(genre)
                GenreSelectionChip(
                    genre = genre,
                    isSelected = isSelected,
                    onClick = {
                        selectedGenres = if (isSelected) {
                            if (selectedGenres.size > 1) selectedGenres - genre else selectedGenres
                        } else {
                            selectedGenres + genre
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Save Button
        Button(
            onClick = {
                onComplete(selectedSongLanguages, selectedAppLanguage, selectedGenres, selectedMusicProvider)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .graphicsLayer {
                    scaleX = saveScale
                    scaleY = saveScale
                },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accentGreen,
                contentColor = colors.onAccent
            ),
            interactionSource = saveInteractionSource
        ) {
            Text(
                text = "Save Preferences",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onAccent
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ProviderOptionTile(
    provider: MusicProvider,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = TunelyxTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "providerScale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) colors.bgSurfaceElevated else colors.bgSurface)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) colors.accentGreen else colors.borderSubtle,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 14.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = provider.displayName,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) colors.accentGreen else colors.textPrimary,
            maxLines = 1
        )
    }
}

@Composable
private fun LanguageChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = TunelyxTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "langScale"
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) colors.accentGreen else colors.bgSurfaceElevated)
            .border(
                width = 1.dp,
                color = if (isSelected) colors.accentGreen else colors.borderSubtle,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = colors.onAccent,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) colors.onAccent else colors.textPrimary
            )
        }
    }
}

@Composable
private fun GenreSelectionChip(
    genre: Genre,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = TunelyxTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "genreScale"
    )

    val chipBg = if (isSelected) genre.baseColor.copy(alpha = 0.16f) else colors.bgSurfaceElevated
    val chipBorder = if (isSelected) genre.baseColor else colors.borderSubtle

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .background(chipBg)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = chipBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(genre.baseColor)
            )
            Text(
                text = genre.displayName,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) colors.textPrimary else colors.textSecondary
            )
        }
    }
}
