package com.chandyou.tunelyx.data.providers

import com.chandyou.tunelyx.model.Genre
import com.chandyou.tunelyx.model.MusicProvider
import com.chandyou.tunelyx.model.Track
import com.chandyou.tunelyx.model.cleanTrackTitle
import com.chandyou.tunelyx.model.splitArtistNames
import kotlinx.coroutines.sync.Mutex
import java.net.URLEncoder

object ITunesProvider : MusicProviderClient {

    override val provider: MusicProvider = MusicProvider.ITUNES

    private val mutex = Mutex()
    internal val rateGuard = ProviderRateGuard(
        minIntervalMs = 1600L,
        maxRequestsPerWindow = 12,
        windowMs = 60_000L,
        cacheTtlMs = 10 * 60_000L,
        defaultCooldownMs = 60_000L
    )

    override suspend fun fetchTracks(
        genre: Genre?,
        languages: Set<String>,
        limit: Int,
        isColdStart: Boolean,
        artistSeed: String?,
        page: Int
    ): ProviderFetchResult {
        val targetLanguages = if (languages.isNotEmpty()) languages else setOf("Hindi")
        // Rotate queried language cleanly by page to prevent burst calls to Apple Search API
        val langIndex = ((page - 1).coerceAtLeast(0)) % targetLanguages.size
        val lang = targetLanguages.elementAt(langIndex)
        val country = if (lang.equals("English", ignoreCase = true)) "US" else "IN"
        val effectiveLimit = limit.coerceIn(15, 40)

        val queryTerm = ProviderNormalizer.getDiverseQueryTerm(genre, lang, artistSeed, page)
        val offset = ((page - 1).coerceAtLeast(0) * effectiveLimit)
        val cacheKey = "itunes::${queryTerm.lowercase()}::country=$country::offset=$offset::limit=$effectiveLimit"

        rateGuard.getCached(cacheKey)?.let { cached ->
            return ProviderFetchResult(tracks = cached)
        }

        val cooldownSec = rateGuard.remainingCooldownSeconds()
        if (cooldownSec > 0L) {
            return ProviderFetchResult(
                tracks = emptyList(),
                errorMessage = "Apple iTunes API rate limit active. Waiting ${cooldownSec}s before retrying."
            )
        }

        val results = mutableListOf<Track>()
        var lastError: String? = null
        var lastWarning: String? = null

        try {
            rateGuard.getCached(cacheKey)?.let { cached ->
                return ProviderFetchResult(tracks = cached)
            }
            val slotError = rateGuard.acquireSlotOrError(mutex, "Apple iTunes")
            val httpRes = if (slotError != null) {
                HttpJsonResult(errorMessage = slotError)
            } else {
                val encodedQuery = URLEncoder.encode(queryTerm, "UTF-8")
                val endpoint = "https://itunes.apple.com/search?term=$encodedQuery&country=$country&media=music&entity=song&limit=$effectiveLimit&offset=$offset"
                ProviderHttpClient.getJson(endpoint, rateGuard = rateGuard, providerName = "Apple iTunes")
            }

            if (httpRes.warningMessage != null) lastWarning = httpRes.warningMessage
            if (httpRes.errorMessage != null) lastError = httpRes.errorMessage

            val dataArray = httpRes.json?.optJSONArray("results")
            if (dataArray != null) {
                for (i in 0 until dataArray.length()) {
                    val item = dataArray.getJSONObject(i)
                    val trackId = "itunes_" + item.optLong("trackId", System.currentTimeMillis() + i).toString()
                    val title = cleanTrackTitle(ProviderNormalizer.cleanHtmlEntities(item.optString("trackName", "Unknown Track")))
                    val rawArtist = ProviderNormalizer.cleanHtmlEntities(item.optString("artistName", "Unknown Artist"))
                    val separatedArtists = splitArtistNames(rawArtist, title)
                    val displayArtist = separatedArtists.joinToString(", ")

                    val album = ProviderNormalizer.cleanHtmlEntities(item.optString("collectionName", "Single"))
                    val previewUrl = ProviderNormalizer.normalizeHttpsUrl(
                        if (item.has("previewUrl")) item.optString("previewUrl") else null
                    )
                    val realDurationSec = (item.optLong("trackTimeMillis", 0L) / 1000L).toInt()

                    // 600x600 loads ~2.5x faster than 1000x1000 while staying crisp on high-DPI cards
                    val rawArtwork = item.optString("artworkUrl100", "")
                    val artworkUrl = if (rawArtwork.isNotEmpty()) {
                        ProviderNormalizer.normalizeHttpsUrl(
                            rawArtwork.replace(Regex("\\d+x\\d+bb\\.jpg"), "600x600bb.jpg")
                        )
                    } else null

                    val itunesGenreName = item.optString("primaryGenreName", "")
                    val detectedLanguage = ProviderNormalizer.detectTrackLanguage(
                        explicitLanguage = null,
                        title = title,
                        artist = displayArtist,
                        album = album,
                        extraMeta = itunesGenreName,
                        fallbackCountry = country
                    )

                    if (!ProviderNormalizer.matchesLanguageSelection(
                            targetLanguages = targetLanguages,
                            explicitLanguage = detectedLanguage,
                            title = title,
                            artist = displayArtist,
                            album = album,
                            genreMeta = itunesGenreName
                        )
                    ) {
                        continue
                    }

                    if (!previewUrl.isNullOrEmpty()) {
                        val resolvedGenre = ProviderNormalizer.resolveTrackGenre(genre, title, displayArtist, album, itunesGenreName)
                        val coverColors = ProviderNormalizer.generateGradientForTrack(resolvedGenre, title, displayArtist)
                        results.add(
                            Track(
                                id = trackId,
                                title = title,
                                artist = displayArtist,
                                genre = resolvedGenre,
                                durationSeconds = realDurationSec,
                                bpm = null,
                                musicalKey = null,
                                previewUrl = previewUrl,
                                coverGradient = coverColors,
                                glowColor = coverColors.first(),
                                artworkUrl = artworkUrl,
                                provider = MusicProvider.ITUNES,
                                album = album,
                                language = detectedLanguage,
                                artists = separatedArtists
                            )
                        )
                    }
                }
            }

            if (results.isNotEmpty()) {
                rateGuard.putCached(cacheKey, results)
            }
        } catch (e: Exception) {
            lastError = "Internal catalog error (Apple iTunes): ${e.localizedMessage ?: "unknown failure"}"
        }

        return ProviderFetchResult(
            tracks = results,
            errorMessage = if (results.isEmpty()) lastError else null,
            warningMessage = lastWarning
        )
    }

    override fun clearQueryCache() {
        rateGuard.clearQueryCache()
    }
}
