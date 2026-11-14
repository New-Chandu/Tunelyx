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
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object JioSaavnProvider : MusicProviderClient {

    override val provider: MusicProvider = MusicProvider.JIOSAAVN

    private val mutex = Mutex()
    private val rateGuard = ProviderRateGuard(
        minIntervalMs = 200L,
        maxRequestsPerWindow = 45,
        windowMs = 60_000L,
        cacheTtlMs = 5 * 60_000L,
        defaultCooldownMs = 30_000L
    )

    private const val SAAVN_MEDIA_DES_KEY = "38346591"

    override suspend fun fetchTracks(
        genre: Genre?,
        languages: Set<String>,
        limit: Int,
        isColdStart: Boolean,
        artistSeed: String?,
        page: Int
    ): ProviderFetchResult {
        val targetLanguages = if (languages.isNotEmpty()) languages else setOf("Hindi")
        val cacheKey = "saavn::g=${genre?.name}::l=${targetLanguages.sorted().joinToString(",")}::a=$artistSeed::p=$page::n=$limit"
        rateGuard.getCached(cacheKey)?.let { cached ->
            return ProviderFetchResult(tracks = cached)
        }

        val cooldownSec = rateGuard.remainingCooldownSeconds()
        if (cooldownSec > 0L) {
            return ProviderFetchResult(
                tracks = emptyList(),
                errorMessage = "JioSaavn server cooldown (${cooldownSec}s). Try again shortly or switch service."
            )
        }

        val results = mutableListOf<Track>()
        var lastError: String? = null
        var lastWarning: String? = null

        try {
            val queryLangs = targetLanguages.take(3)
            val perLangLimit = (limit * 2 / queryLangs.size).coerceIn(20, 40)

            val responses = coroutineScope {
                queryLangs.map { lang ->
                    async(Dispatchers.IO) {
                        val queryTerm = ProviderNormalizer.getDiverseQueryTerm(genre, lang, artistSeed, page)
                        val encoded = URLEncoder.encode(queryTerm, "UTF-8")
                        val endpoint = "https://www.jiosaavn.com/api.php?__call=search.getResults&q=$encoded&_format=json&_marker=0&api_version=4&ctx=web6dot0&n=$perLangLimit&p=$page"

                        val slotError = rateGuard.acquireSlotOrError(mutex, "JioSaavn")
                        val httpRes = if (slotError != null) {
                            HttpJsonResult(errorMessage = slotError)
                        } else {
                            ProviderHttpClient.getJson(endpoint, rateGuard = rateGuard, providerName = "JioSaavn")
                        }
                        lang to httpRes
                    }
                }.awaitAll()
            }

            for ((_, httpRes) in responses) {
                if (httpRes.warningMessage != null) lastWarning = httpRes.warningMessage
                if (httpRes.errorMessage != null) lastError = httpRes.errorMessage

                val songArray = httpRes.json?.optJSONArray("results") ?: continue

                for (i in 0 until songArray.length()) {
                    val item = songArray.getJSONObject(i)
                    val id = item.optString("id", "saavn_${System.currentTimeMillis()}_${results.size}")
                    val title = cleanTrackTitle(ProviderNormalizer.cleanHtmlEntities(item.optString("title", "Unknown Track")))
                    val moreInfo = item.optJSONObject("more_info")

                    val rawSongLang = item.optString("language", "").trim().ifEmpty {
                        moreInfo?.optString("language", "")?.trim().orEmpty()
                    }
                    if (rawSongLang.isEmpty()) continue

                    val albumName = ProviderNormalizer.cleanHtmlEntities(moreInfo?.optString("album", "Single") ?: "Single")
                    val subtitleMeta = ProviderNormalizer.cleanHtmlEntities(item.optString("subtitle", ""))

                    val rawArtists = extractJioSaavnArtists(moreInfo, subtitleMeta)
                    val separatedArtists = splitArtistNames(rawArtists, title)
                    val displayArtist = separatedArtists.joinToString(", ")

                    if (!ProviderNormalizer.matchesLanguageSelection(
                            targetLanguages = targetLanguages,
                            explicitLanguage = rawSongLang,
                            title = title,
                            artist = displayArtist,
                            album = albumName,
                            genreMeta = subtitleMeta
                        )
                    ) {
                        continue
                    }

                    val artworkUrl = upgradeSaavnArtworkUrl(item.optString("image", ""))
                    val highQualityStream = decryptMediaUrl(
                        encryptedBase64 = moreInfo?.optString("encrypted_media_url").orEmpty(),
                        has320Kbps = moreInfo?.optString("320kbps").equals("true", ignoreCase = true)
                    )
                    val fallbackVlink = ProviderNormalizer.normalizeHttpsUrl(
                        moreInfo?.optString("vlink")?.takeIf { it.startsWith("http") }
                    )
                    val streamUrl = highQualityStream ?: fallbackVlink
                    val realDurationSec = moreInfo?.optString("duration")?.toIntOrNull()
                        ?: item.optString("duration")?.toIntOrNull()
                        ?: 0

                    if (!streamUrl.isNullOrEmpty()) {
                        val actualGenre = ProviderNormalizer.resolveTrackGenre(genre, title, displayArtist, albumName, subtitleMeta)
                        val detectedLanguage = ProviderNormalizer.detectTrackLanguage(
                            explicitLanguage = rawSongLang,
                            title = title,
                            artist = displayArtist,
                            album = albumName
                        )
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
                                previewUrl = streamUrl,
                                coverGradient = coverColors,
                                glowColor = coverColors.first(),
                                artworkUrl = artworkUrl,
                                provider = MusicProvider.JIOSAAVN,
                                album = albumName,
                                language = detectedLanguage,
                                artists = separatedArtists
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            lastError = "Internal catalog error (JioSaavn): ${e.localizedMessage ?: "unknown failure"}"
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

    internal fun upgradeSaavnArtworkUrl(rawUrl: String): String? {
        if (rawUrl.isBlank()) return null
        val upgraded = rawUrl.replace(Regex("(?<=[_-])\\d{2,3}x\\d{2,3}(?=\\.)"), "500x500")
        return ProviderNormalizer.normalizeHttpsUrl(upgraded)
    }

    internal fun decryptMediaUrl(encryptedBase64: String, has320Kbps: Boolean): String? {
        if (encryptedBase64.isBlank()) return null
        return try {
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            val keySpec = SecretKeySpec(SAAVN_MEDIA_DES_KEY.toByteArray(StandardCharsets.UTF_8), "DES")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            val decodedBytes = Base64.getDecoder().decode(encryptedBase64.trim())
            val decrypted = String(cipher.doFinal(decodedBytes), StandardCharsets.UTF_8).trim()
            if (!decrypted.startsWith("http")) return null
            val targetSuffix = if (has320Kbps) "_320.mp4" else "_160.mp4"
            val highQuality = decrypted.replace(Regex("_\\d+\\.mp4$"), targetSuffix)
            ProviderNormalizer.normalizeHttpsUrl(highQuality)
        } catch (_: Exception) {
            null
        }
    }

    private fun extractJioSaavnArtists(moreInfo: org.json.JSONObject?, subtitleFallback: String): String {
        val artistMap = moreInfo?.optJSONObject("artistMap")
        val primaryArray = artistMap?.optJSONArray("primary_artists")
        if (primaryArray != null && primaryArray.length() > 0) {
            val names = (0 until primaryArray.length()).mapNotNull { idx ->
                primaryArray.optJSONObject(idx)?.optString("name")?.takeIf { it.isNotBlank() }
            }
            if (names.isNotEmpty()) return ProviderNormalizer.cleanHtmlEntities(names.joinToString(", "))
        }
        val singers = moreInfo?.optString("singers")?.takeIf { it.isNotBlank() }
        if (singers != null) return ProviderNormalizer.cleanHtmlEntities(singers)

        val music = moreInfo?.optString("music")?.takeIf { it.isNotBlank() }
        if (music != null) return ProviderNormalizer.cleanHtmlEntities(music)

        return subtitleFallback.ifBlank { "Various Artists" }
    }

    override fun clearQueryCache() {
        rateGuard.clearQueryCache()
    }
}
