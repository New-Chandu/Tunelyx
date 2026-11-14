package com.chandyou.tunelyx.data

import androidx.compose.ui.graphics.Color
import com.chandyou.tunelyx.data.providers.DeezerProvider
import com.chandyou.tunelyx.data.providers.ITunesProvider
import com.chandyou.tunelyx.data.providers.JioSaavnProvider
import com.chandyou.tunelyx.data.providers.ProviderNormalizer
import com.chandyou.tunelyx.data.providers.ProviderRateGuard
import com.chandyou.tunelyx.data.providers.YouTubeMusicProvider
import com.chandyou.tunelyx.model.Genre
import com.chandyou.tunelyx.model.MusicProvider
import com.chandyou.tunelyx.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

typealias ItunesRateLimiter = ProviderRateGuard

data class FetchResult(
    val tracks: List<Track>,
    val errorMessage: String? = null,
    val warningMessage: String? = null
)

object MusicApiService {

    internal val itunesRateLimiter: ProviderRateGuard
        get() = ITunesProvider.rateGuard

    suspend fun fetchTracksWithStatus(
        genre: Genre?,
        languages: Set<String> = setOf("Hindi"),
        provider: MusicProvider = MusicProvider.JIOSAAVN,
        limit: Int = 25,
        isColdStart: Boolean = false,
        artistSeed: String? = null,
        page: Int = 1
    ): FetchResult = withContext(Dispatchers.IO) {
        val client = when (provider) {
            MusicProvider.JIOSAAVN -> JioSaavnProvider
            MusicProvider.DEEZER -> DeezerProvider
            MusicProvider.ITUNES -> ITunesProvider
            MusicProvider.YOUTUBE_MUSIC -> YouTubeMusicProvider
        }
        val result = client.fetchTracks(
            genre = genre,
            languages = languages,
            limit = limit,
            isColdStart = isColdStart,
            artistSeed = artistSeed,
            page = page
        )
        FetchResult(
            tracks = result.tracks,
            errorMessage = result.errorMessage,
            warningMessage = result.warningMessage
        )
    }

    fun clearAllCaches() {
        JioSaavnProvider.clearQueryCache()
        DeezerProvider.clearQueryCache()
        ITunesProvider.clearQueryCache()
        YouTubeMusicProvider.clearQueryCache()
    }

    fun generateGradientForTrack(genre: Genre, title: String, artist: String): List<Color> {
        return ProviderNormalizer.generateGradientForTrack(genre, title, artist)
    }
}
