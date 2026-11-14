package com.chandyou.tunelyx.data.providers

import com.chandyou.tunelyx.model.Genre
import com.chandyou.tunelyx.model.MusicProvider
import com.chandyou.tunelyx.model.Track
import com.chandyou.tunelyx.model.cleanTrackTitle
import com.chandyou.tunelyx.model.splitArtistNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import java.net.URLEncoder

object DeezerProvider : MusicProviderClient {

    override val provider: MusicProvider = MusicProvider.DEEZER

    private val mutex = Mutex()
    private val rateGuard = ProviderRateGuard(
        minIntervalMs = 350L,
        maxRequestsPerWindow = 25,
        windowMs = 60_000L,
        cacheTtlMs = 5 * 60_000L,
        defaultCooldownMs = 45_000L
    )

    private fun nativeChartGenreId(genre: Genre?): Int? = when (genre) {
        Genre.DANCE -> 113
        Genre.HIPHOP -> 116
        Genre.INDIE -> 85
        else -> null
    }

    override suspend fun fetchTracks(
        genre: Genre?,
        languages: Set<String>,
        limit: Int,
        isColdStart: Boolean,
        artistSeed: String?,
        page: Int
    ): ProviderFetchResult {
        val targetLanguages = if (languages.isNotEmpty()) languages else setOf("Hindi")
        val cacheKey = "deezer::g=${genre?.name}::l=${targetLanguages.sorted().joinToString(",")}::a=$artistSeed::p=$page::n=$limit"
        rateGuard.getCached(cacheKey)?.let { cached ->
            return ProviderFetchResult(tracks = cached)
        }

        val cooldownSec = rateGuard.remainingCooldownSeconds()
        if (cooldownSec > 0L) {
            return ProviderFetchResult(
                tracks = emptyList(),
                errorMessage = "Deezer server cooldown (${cooldownSec}s). Try again shortly or switch service."
            )
        }

        val results = mutableListOf<Track>()
        var lastError: String? = null
        var lastWarning: String? = null

        try {
            val queryLangs = targetLanguages.take(3)
            val perLangLimit = (limit / queryLangs.size.coerceAtLeast(1)).coerceIn(15, 30)
            val offset = ((page - 1).coerceAtLeast(0) * perLangLimit)

            val responses = coroutineScope {
                queryLangs.map { lang ->
                    async(Dispatchers.IO) {
                        val chartId = if (lang.equals("English", ignoreCase = true) && artistSeed == null && page <= 2) {
                            nativeChartGenreId(genre)
                        } else null

                        val endpoint = if (chartId != null) {
                            "https://api.deezer.com/chart/$chartId/tracks?limit=$perLangLimit&index=$offset"
                        } else {
                            // Use strict concise keywords + /search/track so Deezer AND-matching returns dense results
                            val queryTerm = ProviderNormalizer.getDiverseQueryTerm(genre, lang, artistSeed, page, concise = true)
                            val encoded = URLEncoder.encode(queryTerm, "UTF-8")
                            "https://api.deezer.com/search/track?q=$encoded&order=RANKING&limit=$perLangLimit&index=$offset"
                        }

                        val slotError = rateGuard.acquireSlotOrError(mutex, "Deezer")
                        val httpRes = if (slotError != null) {
                            HttpJsonResult(errorMessage = slotError)
                        } else {
                            ProviderHttpClient.getJson(endpoint, rateGuard = rateGuard, providerName = "Deezer")
                        }
                        lang to httpRes
                    }
                }.awaitAll()
            }

            for ((lang, httpRes) in responses) {
                if (httpRes.warningMessage != null) lastWarning = httpRes.warningMessage
                if (httpRes.errorMessage != null) lastError = httpRes.errorMessage

                val jsonObj = httpRes.json ?: continue
                // Check explicit Deezer JSON error envelope (e.g., quota limit code 4)
                val deezerErr = jsonObj.optJSONObject("error")
                if (deezerErr != null) {
                    val errMsg = deezerErr.optString("message", "Deezer service error")
                    val errCode = deezerErr.optInt("code", 0)
                    if (errCode == 4) rateGuard.triggerCooldown(30L)
                    lastError = "Deezer error: $errMsg"
                    continue
                }

                val dataArray = jsonObj.optJSONArray("data")
                    ?: jsonObj.optJSONObject("tracks")?.optJSONArray("data")
                    ?: continue

                for (i in 0 until dataArray.length()) {
                    val item = dataArray.getJSONObject(i)
                    if (!item.optBoolean("readable", true)) continue

                    val id = "deezer_" + item.optLong("id", System.currentTimeMillis() + i)
                    val rawTitle = item.optString("title", "Unknown Track")
                    val title = cleanTrackTitle(ProviderNormalizer.cleanHtmlEntities(rawTitle))

                    val artistObj = item.optJSONObject("artist")
                    val mainArtist = ProviderNormalizer.cleanHtmlEntities(artistObj?.optString("name", "Unknown Artist") ?: "Unknown Artist")
                    val contributorsArray = item.optJSONArray("contributors")
                    val contributorNames = if (contributorsArray != null && contributorsArray.length() > 0) {
                        (0 until contributorsArray.length()).mapNotNull { idx ->
                            contributorsArray.optJSONObject(idx)?.optString("name")?.takeIf { it.isNotBlank() }
                        }.joinToString(", ")
                    } else ""
                    val combinedArtistRaw = if (contributorNames.isNotBlank()) "$mainArtist, $contributorNames" else mainArtist
                    val separatedArtists = splitArtistNames(combinedArtistRaw, title)
                    val displayArtist = separatedArtists.joinToString(", ")

                    val previewUrl = ProviderNormalizer.normalizeHttpsUrl(
                        item.optString("preview").takeIf { it.startsWith("http") }
                    )
                    val realDurationSec = item.optInt("duration", 0)

                    val albumObj = item.optJSONObject("album")
                    val albumName = ProviderNormalizer.cleanHtmlEntities(albumObj?.optString("title", "Single") ?: "Single")
                    val artworkUrl = ProviderNormalizer.normalizeHttpsUrl(
                        albumObj?.optString("cover_xl")?.takeIf { it.startsWith("http") }
                            ?: albumObj?.optString("cover_big")?.takeIf { it.startsWith("http") }
                            ?: albumObj?.optString("cover_medium")?.takeIf { it.startsWith("http") }
                    )

                    val detectedLanguage = ProviderNormalizer.detectTrackLanguage(
                        explicitLanguage = null,
                        title = title,
                        artist = displayArtist,
                        album = albumName,
                        extraMeta = genre?.displayName.orEmpty(),
                        fallbackCountry = if (lang.equals("English", ignoreCase = true)) "US" else ""
                    )

                    if (!ProviderNormalizer.matchesLanguageSelection(
                            targetLanguages = targetLanguages,
                            explicitLanguage = detectedLanguage,
                            title = title,
                            artist = displayArtist,
                            album = albumName
                        )
                    ) {
                        continue
                    }

                    if (!previewUrl.isNullOrEmpty()) {
                        val actualGenre = ProviderNormalizer.resolveTrackGenre(genre, title, displayArtist, albumName)
                        val coverColors = ProviderNormalizer.generateGradientForTrack(actualGenre, title, displayArtist)
                        results.add(
                            Track(
                                id = id,
                                title = title,
                                artist = displayArtist,
                                genre = actualGenre,
                                durationSeconds = realDurationSec,
                                bpm = null,
                                musicalKey = null,
                                previewUrl = previewUrl,
                                coverGradient = coverColors,
                                glowColor = coverColors.first(),
                                artworkUrl = artworkUrl,
                                provider = MusicProvider.DEEZER,
                                album = albumName,
                                language = detectedLanguage,
                                artists = separatedArtists
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            lastError = "Internal catalog error (Deezer): ${e.localizedMessage ?: "unknown failure"}"
        }

        val finalTracks = results.distinctBy { it.id }.take(limit)
        if (finalTracks.isNotEmpty()) {
            rateGuard.putCached(cacheKey, finalTracks)
        }
        return ProviderFetchResult(
            tracks = finalTracks,
            errorMessage = if (finalTracks.isEmpty()) lastError else null,
            warningMessage = lastWarning
        )
    }

    override fun clearQueryCache() {
        rateGuard.clearQueryCache()
    }
}
