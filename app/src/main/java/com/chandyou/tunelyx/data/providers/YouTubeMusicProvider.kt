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
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class YtmStreamResolution(
    val streamUrl: String? = null,
    val errorMessage: String? = null
)

/**
 * Dedicated YouTube Music provider handler backed by InnerTube search + on-demand cipher-free audio extraction.
 */
object YouTubeMusicProvider : MusicProviderClient {

    override val provider: MusicProvider = MusicProvider.YOUTUBE_MUSIC

    const val YTM_STREAM_USER_AGENT = "com.google.android.apps.youtube.vr.oculus/1.65.10"

    private const val YTM_SCHEME_PREFIX = "ytm://"
    private const val SEARCH_ENDPOINT = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false"
    private const val PLAYER_ENDPOINT = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
    private const val SONGS_FILTER_PARAMS = "EgWKAQIIAWo="
    private const val STREAM_TTL_MS = 3 * 60 * 60_000L

    private val mutex = Mutex()
    private val rateGuard = ProviderRateGuard(
        minIntervalMs = 350L,
        maxRequestsPerWindow = 30,
        windowMs = 60_000L,
        cacheTtlMs = 5 * 60_000L,
        defaultCooldownMs = 45_000L
    )

    private data class CachedStream(val url: String, val cachedAtMs: Long)
    private val streamCache = LinkedHashMap<String, CachedStream>()

    fun isYtmPreviewUri(previewUrl: String?): Boolean =
        !previewUrl.isNullOrBlank() && previewUrl.startsWith(YTM_SCHEME_PREFIX)

    fun extractVideoId(previewUrl: String?): String? {
        if (previewUrl.isNullOrBlank() || !previewUrl.startsWith(YTM_SCHEME_PREFIX)) return null
        return previewUrl.removePrefix(YTM_SCHEME_PREFIX).trim().takeIf { it.isNotEmpty() }
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
        val cacheKey = "ytm::g=${genre?.name}::l=${targetLanguages.sorted().joinToString(",")}::a=$artistSeed::p=$page::n=$limit"
        rateGuard.getCached(cacheKey)?.let { cached ->
            return ProviderFetchResult(tracks = cached)
        }

        val cooldownSec = rateGuard.remainingCooldownSeconds()
        if (cooldownSec > 0L) {
            return ProviderFetchResult(
                tracks = emptyList(),
                errorMessage = "YT Music cooldown (${cooldownSec}s). Waiting to retry..."
            )
        }

        val results = mutableListOf<Track>()
        var lastError: String? = null
        var lastWarning: String? = null

        try {
            val queryLangs = targetLanguages.take(2)
            val responses = coroutineScope {
                queryLangs.map { lang ->
                    async(Dispatchers.IO) {
                        val gl = if (lang.equals("English", ignoreCase = true)) "US" else "IN"
                        val queryTerm = ProviderNormalizer.getDiverseQueryTerm(genre, lang, artistSeed, page)
                        val body = JSONObject().apply {
                            put("context", JSONObject().apply {
                                put("client", JSONObject().apply {
                                    put("clientName", "WEB_REMIX")
                                    put("clientVersion", "1.20260201.01.00")
                                    put("hl", "en")
                                    put("gl", gl)
                                })
                            })
                            put("query", queryTerm)
                            put("params", SONGS_FILTER_PARAMS)
                        }.toString()

                        val headers = mapOf(
                            "Origin" to "https://music.youtube.com",
                            "Referer" to "https://music.youtube.com/",
                            "X-Youtube-Client-Name" to "67",
                            "X-Youtube-Client-Version" to "1.20260201.01.00"
                        )

                        val slotError = rateGuard.acquireSlotOrError(mutex, "YT Music")
                        val httpRes = if (slotError != null) {
                            HttpJsonResult(errorMessage = slotError)
                        } else {
                            ProviderHttpClient.postJson(
                                urlString = SEARCH_ENDPOINT,
                                jsonBody = body,
                                rateGuard = rateGuard,
                                providerName = "YT Music",
                                headers = headers
                            )
                        }
                        lang to httpRes
                    }
                }.awaitAll()
            }

            for ((lang, httpRes) in responses) {
                if (httpRes.warningMessage != null) lastWarning = httpRes.warningMessage
                if (httpRes.errorMessage != null) lastError = httpRes.errorMessage
                val json = httpRes.json ?: continue

                val parsed = parseSearchResponse(
                    root = json,
                    requestedGenre = genre,
                    targetLanguages = targetLanguages,
                    fallbackCountry = if (lang.equals("English", ignoreCase = true)) "US" else "IN"
                )
                results.addAll(parsed)
            }
        } catch (e: Exception) {
            lastError = "Internal catalog error (YT Music): ${e.localizedMessage ?: "unknown failure"}"
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

    /**
     * Resolves a direct cipher-free audio stream URL for a YouTube Music [videoId] on Dispatchers.IO,
     * caching URLs briefly so swipe playback and pre-warming stay responsive.
     */
    suspend fun resolveAudioStreamUrl(videoId: String): YtmStreamResolution = withContext(Dispatchers.IO) {
        getCachedStreamUrl(videoId)?.let { cached ->
            return@withContext YtmStreamResolution(streamUrl = cached)
        }

        val clientProfiles = listOf(
            PlayerClientProfile(
                name = "ANDROID_VR",
                version = "1.65.10",
                clientIdHeader = "28",
                userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
                extraFields = mapOf("androidSdkVersion" to 32, "deviceModel" to "Quest 3", "osName" to "Android", "osVersion" to "12L")
            ),
            PlayerClientProfile(
                name = "IOS",
                version = "20.10.4",
                clientIdHeader = "5",
                userAgent = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X)",
                extraFields = mapOf("deviceMake" to "Apple", "deviceModel" to "iPhone16,2", "osName" to "iPhone", "osVersion" to "18.3.2.22D82")
            ),
            PlayerClientProfile(
                name = "ANDROID_MUSIC",
                version = "6.20.51",
                clientIdHeader = "21",
                userAgent = "com.google.android.apps.youtube.music/6.20.51 (Linux; U; Android 14) gzip",
                extraFields = mapOf("androidSdkVersion" to 34, "osName" to "Android", "osVersion" to "14")
            )
        )

        var lastErr: String? = null
        for (profile in clientProfiles) {
            val payload = JSONObject().apply {
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", profile.name)
                        put("clientVersion", profile.version)
                        put("hl", "en")
                        put("gl", "US")
                        profile.extraFields.forEach { (k, v) -> put(k, v) }
                    })
                })
            }.toString()

            val headers = mapOf(
                "User-Agent" to profile.userAgent,
                "X-Youtube-Client-Name" to profile.clientIdHeader,
                "X-Youtube-Client-Version" to profile.version
            )

            val res = ProviderHttpClient.postJson(
                urlString = PLAYER_ENDPOINT,
                jsonBody = payload,
                rateGuard = rateGuard,
                providerName = "YT Music",
                headers = headers
            )
            if (res.errorMessage != null) {
                lastErr = res.errorMessage
                continue
            }
            val json = res.json ?: continue
            val directUrl = extractBestAudioUrl(json)
            if (!directUrl.isNullOrBlank()) {
                putCachedStreamUrl(videoId, directUrl)
                return@withContext YtmStreamResolution(streamUrl = directUrl)
            }
            val reason = json.optJSONObject("playabilityStatus")?.optString("reason").orEmpty()
            if (reason.isNotBlank()) {
                lastErr = "YT Music stream unavailable: $reason"
            }
        }

        YtmStreamResolution(errorMessage = lastErr ?: "YT Music audio stream unavailable for this track right now.")
    }

    internal fun parseSearchResponse(
        root: JSONObject,
        requestedGenre: Genre?,
        targetLanguages: Set<String>,
        fallbackCountry: String
    ): List<Track> {
        val renderers = mutableListOf<JSONObject>()
        collectResponsiveRenderers(root, renderers)

        val tracks = mutableListOf<Track>()
        for (renderer in renderers) {
            val videoId = extractVideoIdFromRenderer(renderer) ?: continue
            val flexCols = renderer.optJSONArray("flexColumns") ?: continue
            val title = extractColumnText(flexCols.optJSONObject(0))?.let { cleanTrackTitle(ProviderNormalizer.cleanHtmlEntities(it)) }
                ?: continue
            if (title.isBlank()) continue

            val secondColText = extractColumnText(flexCols.optJSONObject(1)).orEmpty()
            val (rawArtists, albumName, durationSec) = parseSubtitleSegments(
                runs = extractColumnRuns(flexCols.optJSONObject(1)),
                fallbackLine = secondColText
            )

            val separatedArtists = splitArtistNames(rawArtists, title)
            val displayArtist = separatedArtists.joinToString(", ")

            val detectedLanguage = ProviderNormalizer.detectTrackLanguage(
                explicitLanguage = null,
                title = title,
                artist = displayArtist,
                album = albumName,
                extraMeta = requestedGenre?.displayName.orEmpty(),
                fallbackCountry = fallbackCountry
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

            val rawThumb = extractBestThumbnail(renderer)
            val artworkUrl = upgradeYtmArtworkUrl(rawThumb, videoId)
            val resolvedGenre = ProviderNormalizer.resolveTrackGenre(requestedGenre, title, displayArtist, albumName)
            val coverColors = ProviderNormalizer.generateGradientForTrack(resolvedGenre, title, displayArtist)

            tracks.add(
                Track(
                    id = "ytm_$videoId",
                    title = title,
                    artist = displayArtist,
                    genre = resolvedGenre,
                    durationSeconds = durationSec,
                    bpm = null,
                    musicalKey = null,
                    previewUrl = "$YTM_SCHEME_PREFIX$videoId",
                    coverGradient = coverColors,
                    glowColor = coverColors.first(),
                    artworkUrl = artworkUrl,
                    provider = MusicProvider.YOUTUBE_MUSIC,
                    album = albumName,
                    language = detectedLanguage,
                    artists = separatedArtists
                )
            )
        }
        return tracks
    }

    internal fun extractBestAudioUrl(playerJson: JSONObject): String? {
        val streamingData = playerJson.optJSONObject("streamingData") ?: return null
        val adaptive = streamingData.optJSONArray("adaptiveFormats")
        var bestMp4Url: String? = null
        var bestMp4Bitrate = -1
        var bestWebmUrl: String? = null
        var bestWebmBitrate = -1

        if (adaptive != null) {
            for (i in 0 until adaptive.length()) {
                val fmt = adaptive.optJSONObject(i) ?: continue
                val url = fmt.optString("url").takeIf { it.startsWith("http") } ?: continue
                val mime = fmt.optString("mimeType").lowercase()
                val bitrate = fmt.optInt("bitrate", 0)
                if (mime.startsWith("audio/mp4")) {
                    if (bitrate >= bestMp4Bitrate) {
                        bestMp4Bitrate = bitrate
                        bestMp4Url = url
                    }
                } else if (mime.startsWith("audio/webm")) {
                    if (bitrate >= bestWebmBitrate) {
                        bestWebmBitrate = bitrate
                        bestWebmUrl = url
                    }
                }
            }
        }
        if (bestMp4Url != null) return ProviderNormalizer.normalizeHttpsUrl(bestMp4Url)
        if (bestWebmUrl != null) return ProviderNormalizer.normalizeHttpsUrl(bestWebmUrl)

        val progressive = streamingData.optJSONArray("formats")
        if (progressive != null) {
            for (i in 0 until progressive.length()) {
                val fmt = progressive.optJSONObject(i) ?: continue
                val url = fmt.optString("url").takeIf { it.startsWith("http") } ?: continue
                return ProviderNormalizer.normalizeHttpsUrl(url)
            }
        }
        return null
    }

    internal fun upgradeYtmArtworkUrl(rawUrl: String?, videoId: String): String {
        if (!rawUrl.isNullOrBlank()) {
            val clean = rawUrl.replace(Regex("=w\\d+-h\\d+[^\"&\\s]*"), "=w544-h544-l90-rj")
            ProviderNormalizer.normalizeHttpsUrl(clean)?.let { return it }
        }
        return "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
    }

    internal fun parseDurationSeconds(text: String): Int {
        val parts = text.trim().split(":")
        return when (parts.size) {
            2 -> {
                val m = parts[0].toIntOrNull() ?: return 0
                val s = parts[1].toIntOrNull() ?: return 0
                m * 60 + s
            }
            3 -> {
                val h = parts[0].toIntOrNull() ?: return 0
                val m = parts[1].toIntOrNull() ?: return 0
                val s = parts[2].toIntOrNull() ?: return 0
                h * 3600 + m * 60 + s
            }
            else -> 0
        }
    }

    private fun collectResponsiveRenderers(node: Any?, out: MutableList<JSONObject>) {
        when (node) {
            is JSONObject -> {
                val renderer = node.optJSONObject("musicResponsiveListItemRenderer")
                if (renderer != null) {
                    out.add(renderer)
                } else {
                    val keys = node.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        collectResponsiveRenderers(node.opt(key), out)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    collectResponsiveRenderers(node.opt(i), out)
                }
            }
        }
    }

    private fun extractVideoIdFromRenderer(renderer: JSONObject): String? {
        val playlistItemId = renderer.optJSONObject("playlistItemData")?.optString("videoId")
        if (!playlistItemId.isNullOrBlank()) return playlistItemId

        val overlayPlay = renderer.optJSONObject("overlay")
            ?.optJSONObject("musicItemThumbnailOverlayRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("musicPlayButtonRenderer")
            ?.optJSONObject("playNavigationEndpoint")
            ?.optJSONObject("watchEndpoint")
            ?.optString("videoId")
        if (!overlayPlay.isNullOrBlank()) return overlayPlay

        val firstColRuns = extractColumnRuns(renderer.optJSONArray("flexColumns")?.optJSONObject(0))
        if (firstColRuns != null) {
            for (i in 0 until firstColRuns.length()) {
                val navVid = firstColRuns.optJSONObject(i)
                    ?.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("watchEndpoint")
                    ?.optString("videoId")
                if (!navVid.isNullOrBlank()) return navVid
            }
        }
        return null
    }

    private fun extractColumnRuns(columnObj: JSONObject?): JSONArray? =
        columnObj?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")

    private fun extractColumnText(columnObj: JSONObject?): String? {
        val runs = extractColumnRuns(columnObj) ?: return null
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            val t = runs.optJSONObject(i)?.optString("text").orEmpty()
            sb.append(t)
        }
        return sb.toString().trim().takeIf { it.isNotEmpty() }
    }

    private fun parseSubtitleSegments(runs: JSONArray?, fallbackLine: String): Triple<String, String, Int> {
        val artistParts = mutableListOf<String>()
        var album = "Single"
        var durationSec = 0

        if (runs != null && runs.length() > 0) {
            // Split bullet-separated groups cleanly
            val currentGroup = mutableListOf<JSONObject>()
            val groups = mutableListOf<List<JSONObject>>()
            for (i in 0 until runs.length()) {
                val run = runs.optJSONObject(i) ?: continue
                val txt = run.optString("text")
                if (txt.contains("•")) {
                    if (currentGroup.isNotEmpty()) {
                        groups.add(currentGroup.toList())
                        currentGroup.clear()
                    }
                } else if (txt.isNotBlank()) {
                    currentGroup.add(run)
                }
            }
            if (currentGroup.isNotEmpty()) groups.add(currentGroup)

            for (group in groups) {
                val groupText = group.joinToString("") { it.optString("text") }.trim()
                if (groupText.equals("Song", ignoreCase = true) || groupText.equals("Video", ignoreCase = true)) {
                    continue
                }
                val parsedDur = parseDurationSeconds(groupText)
                if (parsedDur > 0) {
                    durationSec = parsedDur
                    continue
                }
                val isAlbumRun = group.any {
                    it.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("browseEndpoint")
                        ?.optString("browseId")
                        ?.startsWith("MPREb") == true
                }
                if (isAlbumRun) {
                    album = ProviderNormalizer.cleanHtmlEntities(groupText)
                } else if (artistParts.isEmpty()) {
                    artistParts.add(ProviderNormalizer.cleanHtmlEntities(groupText))
                } else if (album == "Single") {
                    album = ProviderNormalizer.cleanHtmlEntities(groupText)
                }
            }
        }

        if (artistParts.isEmpty() && fallbackLine.isNotBlank()) {
            val parts = fallbackLine.split("•").map { it.trim() }.filter {
                it.isNotEmpty() && !it.equals("Song", ignoreCase = true) && parseDurationSeconds(it) == 0
            }
            if (parts.isNotEmpty()) {
                artistParts.add(ProviderNormalizer.cleanHtmlEntities(parts.first()))
                if (parts.size >= 2) album = ProviderNormalizer.cleanHtmlEntities(parts[1])
            }
        }

        return Triple(artistParts.joinToString(", ").ifBlank { "Various Artists" }, album, durationSec)
    }

    private fun extractBestThumbnail(renderer: JSONObject): String? {
        val thumbs = renderer.optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            ?: return null
        var bestUrl: String? = null
        var bestWidth = -1
        for (i in 0 until thumbs.length()) {
            val item = thumbs.optJSONObject(i) ?: continue
            val w = item.optInt("width", 0)
            val u = item.optString("url").takeIf { it.startsWith("http") } ?: continue
            if (w >= bestWidth) {
                bestWidth = w
                bestUrl = u
            }
        }
        return bestUrl
    }

    @Synchronized
    private fun getCachedStreamUrl(videoId: String): String? {
        val entry = streamCache[videoId] ?: return null
        if (System.currentTimeMillis() - entry.cachedAtMs > STREAM_TTL_MS) {
            streamCache.remove(videoId)
            return null
        }
        return entry.url
    }

    @Synchronized
    private fun putCachedStreamUrl(videoId: String, url: String) {
        if (streamCache.size >= 80) {
            val oldest = streamCache.keys.firstOrNull()
            if (oldest != null) streamCache.remove(oldest)
        }
        streamCache[videoId] = CachedStream(url, System.currentTimeMillis())
    }

    private data class PlayerClientProfile(
        val name: String,
        val version: String,
        val clientIdHeader: String,
        val userAgent: String,
        val extraFields: Map<String, Any>
    )

    override fun clearQueryCache() {
        rateGuard.clearQueryCache()
        synchronized(this) { streamCache.clear() }
    }
}
