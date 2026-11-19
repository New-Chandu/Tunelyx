package com.chandyou.tunelyx.data.providers

import androidx.compose.ui.graphics.Color
import com.chandyou.tunelyx.model.Genre
import com.chandyou.tunelyx.model.MusicProvider
import com.chandyou.tunelyx.model.Track
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class ProviderFetchResult(
    val tracks: List<Track>,
    val errorMessage: String? = null,
    val warningMessage: String? = null
)

interface MusicProviderClient {
    val provider: MusicProvider
    suspend fun fetchTracks(
        genre: Genre?,
        languages: Set<String>,
        limit: Int,
        isColdStart: Boolean,
        artistSeed: String?,
        page: Int
    ): ProviderFetchResult

    fun clearQueryCache()
}

/**
 * Shared thread-safe rate limiter, request spacing guard, cooldown circuit breaker,
 * and TTL in-memory query cache usable by every music provider.
 */
class ProviderRateGuard(
    private val minIntervalMs: Long = 350L,
    private val maxRequestsPerWindow: Int = 30,
    private val windowMs: Long = 60_000L,
    private val cacheTtlMs: Long = 5 * 60_000L,
    private val defaultCooldownMs: Long = 60_000L
) {
    private data class CacheEntry(val tracks: List<Track>, val cachedAtMs: Long)

    private val queryCache = LinkedHashMap<String, CacheEntry>()
    private val requestTimestamps = ArrayDeque<Long>()
    private var lastRequestMs: Long = 0L
    private var cooldownUntilMs: Long = 0L

    @Synchronized
    fun getCached(key: String, nowMs: Long = System.currentTimeMillis()): List<Track>? {
        val entry = queryCache[key] ?: return null
        if (nowMs - entry.cachedAtMs > cacheTtlMs) {
            queryCache.remove(key)
            return null
        }
        return entry.tracks
    }

    @Synchronized
    fun putCached(key: String, tracks: List<Track>, nowMs: Long = System.currentTimeMillis()) {
        if (tracks.isEmpty()) return
        if (queryCache.size >= 50) {
            val oldestKey = queryCache.keys.firstOrNull()
            if (oldestKey != null) queryCache.remove(oldestKey)
        }
        queryCache[key] = CacheEntry(tracks, nowMs)
    }

    @Synchronized
    fun remainingCooldownSeconds(nowMs: Long = System.currentTimeMillis()): Long {
        val diff = cooldownUntilMs - nowMs
        return if (diff > 0L) ((diff + 999L) / 1000L) else 0L
    }

    /**
     * Returns delay in ms needed before issuing the request, or -1L if blocked by cooldown/window cap.
     */
    @Synchronized
    fun acquireSlotDelayMs(nowMs: Long = System.currentTimeMillis()): Long {
        if (nowMs < cooldownUntilMs) return -1L
        while (requestTimestamps.isNotEmpty() && nowMs - requestTimestamps.first() >= windowMs) {
            requestTimestamps.removeFirst()
        }
        if (requestTimestamps.size >= maxRequestsPerWindow) {
            return -1L
        }
        val elapsedSinceLast = nowMs - lastRequestMs
        return if (lastRequestMs > 0L && elapsedSinceLast < minIntervalMs) {
            minIntervalMs - elapsedSinceLast
        } else {
            0L
        }
    }

    @Synchronized
    fun recordRequest(nowMs: Long = System.currentTimeMillis()) {
        lastRequestMs = nowMs
        requestTimestamps.addLast(nowMs)
    }

    @Synchronized
    fun triggerCooldown(retryAfterSeconds: Long? = null, nowMs: Long = System.currentTimeMillis()) {
        val boundedMs = if (retryAfterSeconds != null && retryAfterSeconds > 0L) {
            (retryAfterSeconds * 1000L).coerceIn(10_000L, 180_000L)
        } else {
            defaultCooldownMs
        }
        cooldownUntilMs = maxOf(cooldownUntilMs, nowMs + boundedMs)
    }

    @Synchronized
    fun clearQueryCache() {
        queryCache.clear()
    }

    @Synchronized
    fun clear() {
        queryCache.clear()
        requestTimestamps.clear()
        lastRequestMs = 0L
        cooldownUntilMs = 0L
    }

    /**
     * Acquires a rate-limit slot under [mutex] and releases the lock before HTTP I/O
     * so concurrent provider requests never serialize each other's network calls.
     * Returns null when cleared to proceed, or a user-facing rate-limit message.
     */
    suspend fun acquireSlotOrError(mutex: Mutex, providerName: String): String? {
        return mutex.withLock {
            val waitMs = acquireSlotDelayMs()
            if (waitMs < 0L) {
                val remCooldown = remainingCooldownSeconds()
                return@withLock if (remCooldown > 0L) {
                    "$providerName rate limit active. Cooling down for ${remCooldown}s."
                } else {
                    "$providerName request limit reached briefly. Waiting to protect access."
                }
            }
            if (waitMs > 0L) delay(waitMs)
            recordRequest()
            null
        }
    }
}

data class HttpJsonResult(
    val json: JSONObject? = null,
    val errorMessage: String? = null,
    val warningMessage: String? = null
)

object ProviderHttpClient {
    init {
        try {
            // Enable persistent HTTP keep-alive connection pooling across provider requests
            System.setProperty("http.keepAlive", "true")
            System.setProperty("http.maxConnections", "8")
        } catch (_: SecurityException) {
            // Safe fallback if system property modification is restricted
        }
    }

    fun getJson(
        urlString: String,
        rateGuard: ProviderRateGuard? = null,
        providerName: String = "Music server",
        headers: Map<String, String> = emptyMap()
    ): HttpJsonResult {
        return executeJsonRequest(
            urlString = urlString,
            method = "GET",
            jsonBody = null,
            headers = headers,
            rateGuard = rateGuard,
            providerName = providerName
        )
    }

    fun postJson(
        urlString: String,
        jsonBody: String,
        rateGuard: ProviderRateGuard? = null,
        providerName: String = "Music server",
        headers: Map<String, String> = emptyMap()
    ): HttpJsonResult {
        return executeJsonRequest(
            urlString = urlString,
            method = "POST",
            jsonBody = jsonBody,
            headers = headers,
            rateGuard = rateGuard,
            providerName = providerName
        )
    }

    private fun executeJsonRequest(
        urlString: String,
        method: String,
        jsonBody: String?,
        headers: Map<String, String>,
        rateGuard: ProviderRateGuard?,
        providerName: String
    ): HttpJsonResult {
        var connection: HttpURLConnection? = null
        val startMs = System.currentTimeMillis()
        var warning: String? = null
        return try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6500
                readTimeout = 8000
                instanceFollowRedirects = true
                requestMethod = method
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Connection", "keep-alive")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
                if (jsonBody != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                }
            }

            if (jsonBody != null) {
                connection.outputStream.use { out ->
                    out.write(jsonBody.toByteArray(Charsets.UTF_8))
                }
            }

            val code = connection.responseCode
            val elapsedMs = System.currentTimeMillis() - startMs
            if (elapsedMs > 4500L) {
                warning = "Slow internet detected — songs may take longer to load."
            }

            if (code in 200..299) {
                val content = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                HttpJsonResult(json = JSONObject(content), warningMessage = warning)
            } else {
                // Drain errorStream cleanly so Android's HttpURLConnection keeps the socket pooled
                try {
                    connection.errorStream?.use { it.readBytes() }
                } catch (_: Exception) {
                }
                val retryAfterSec = connection.getHeaderField("Retry-After")?.toLongOrNull()
                if (code == 429 || (code == 403 && retryAfterSec != null)) {
                    rateGuard?.triggerCooldown(retryAfterSec)
                    val waitSec = rateGuard?.remainingCooldownSeconds() ?: 60L
                    HttpJsonResult(
                        errorMessage = "$providerName rate limit reached (HTTP $code). Cooling down for ${waitSec}s.",
                        warningMessage = warning
                    )
                } else {
                    HttpJsonResult(
                        errorMessage = "$providerName not responding properly (HTTP $code).",
                        warningMessage = warning
                    )
                }
            }
        } catch (e: java.net.UnknownHostException) {
            HttpJsonResult(errorMessage = "No internet connection. Please check Wi-Fi or mobile data.")
        } catch (e: java.net.ConnectException) {
            HttpJsonResult(errorMessage = "No internet or $providerName unreachable. Retrying...")
        } catch (e: java.net.SocketTimeoutException) {
            HttpJsonResult(errorMessage = "$providerName is not responding (timed out). Internet may be slow.")
        } catch (e: javax.net.ssl.SSLException) {
            HttpJsonResult(errorMessage = "Secure connection failed while reaching $providerName.")
        } catch (e: org.json.JSONException) {
            HttpJsonResult(errorMessage = "Invalid response received from $providerName.")
        } catch (e: Exception) {
            HttpJsonResult(errorMessage = "Network error ($providerName): ${e.localizedMessage ?: "connection failed"}")
        } finally {
            connection?.disconnect()
        }
    }
}

object ProviderNormalizer {

    val SAD_OR_SLOW_KEYWORDS = listOf(
        "sad", "dard", "judai", "judaai", "breakup", "alone", "cry", "crying", "rona",
        "emotional", "tadap", "tanhai", "slow", "unplugged", "acoustic", "alvida",
        "gam", "gham", "dukh", "heartbreak", "heartbroken", "broken", "bichhad",
        "tears", "pain", "virah", "maahi ve", "channa mereya", "thodi der",
        "agar tum saath ho", "tujhe bhula diya", "kabira", "bekhayali", "phir le aya",
        "khairiyat", "humsafar", "humdard", "ae dil hai mushkil", "tujhe kitna chahne",
        "lut gaye", "wafa", "bewafa", "judaiyaan", "tanha", "maula", "dua", "duaa",
        "bhula dena", "lofi", "lo-fi", "chill", "relax", "lullaby", "sleep"
    )

    private val WESTERN_ARTIST_TOKENS = listOf(
        "taylor swift", "drake", "ariana grande", "ed sheeran", "the weeknd", "justin bieber",
        "billie eilish", "dua lipa", "post malone", "bruno mars", "selena gomez", "shawn mendes",
        "coldplay", "maroon 5", "imagine dragons", "chainsmokers", "rihanna", "beyonce",
        "eminem", "kanye", "travis scott", "kendrick", "cardi b", "charlie puth", "adele",
        "sam smith", "calvin harris", "david guetta", "marshmello", "martin garrix", "alan walker",
        "bebe rexha", "camila cabello", "olivia rodrigo", "sabrina carpenter", "chappell roan"
    )

    private val FOREIGN_NON_INDIAN_GENRES = listOf(
        "k-pop", "j-pop", "anime", "country", "reggaeton", "musica mexicana",
        "french pop", "latin", "sertanejo", "enka", "afrobeats", "mandopop", "cantopop"
    )

    private val REGIONAL_CONFLICT_TOKENS = mapOf(
        "hindi" to listOf("bhojpuri", "haryanvi", "tamil", "telugu", "malayalam", "kannada"),
        "punjabi" to listOf("bhojpuri", "tamil", "telugu", "malayalam", "kannada"),
        "tamil" to listOf("punjabi", "bhojpuri", "haryanvi", "bengali", "gujarati"),
        "telugu" to listOf("punjabi", "bhojpuri", "haryanvi", "bengali", "gujarati"),
        "bengali" to listOf("punjabi", "bhojpuri", "tamil", "telugu"),
        "marathi" to listOf("punjabi", "bhojpuri", "tamil", "telugu"),
        "gujarati" to listOf("punjabi", "bhojpuri", "tamil", "telugu")
    )

    fun cleanHtmlEntities(text: String): String {
        return text
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun normalizeHttpsUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return if (url.startsWith("http://")) "https://" + url.removePrefix("http://") else url
    }

    fun resolveTrackGenre(
        requestedGenre: Genre?,
        title: String,
        artist: String,
        album: String,
        extraMeta: String = ""
    ): Genre {
        if (requestedGenre != null) return requestedGenre

        val combined = "$title $artist $album $extraMeta".lowercase()
        val hasSadTone = SAD_OR_SLOW_KEYWORDS.any { combined.contains(it) }

        return when {
            listOf("hip hop", "hip-hop", "rap", "divine", "emiway", "seedhe maut", "raftaar", "badshah", "krsna", "mc stan", "gully", "cypher", "freestyle")
                .any { combined.contains(it) } -> Genre.HIPHOP
            listOf("punjabi", "diljit", "karan aujla", "sidhu moose", "ap dhillon", "shubh", "gurdas", "bhangra", "jatt")
                .any { combined.contains(it) } -> Genre.PUNJABI
            hasSadTone || listOf("unplugged", "acoustic", "sufi", "lofi", "lo-fi", "stripped", "piano", "qawwali", "ghazal", "peaceful")
                .any { combined.contains(it) } -> Genre.ACOUSTIC
            !hasSadTone && listOf("remix", "dj ", "party", "dance", "club", "edm", "electronic", "mashup", "disco", "bass")
                .any { combined.contains(it) } -> Genre.DANCE
            listOf("indie", "prateek kuhad", "anuv jain", "local train", "when chai met toast", "ritviz", "zaeden", "mitraz")
                .any { combined.contains(it) } -> Genre.INDIE
            listOf("90s", "80s", "retro", "classic", "kumar sanu", "alka yagnik", "udit narayan", "kishore kumar", "rd burman", "lata mangeshkar", "mohammed rafi", "old ")
                .any { combined.contains(it) } -> Genre.RETRO
            else -> {
                val detected = detectTrackLanguage(null, title, artist, album, extraMeta)
                if (detected.equals("English", ignoreCase = true) || WESTERN_ARTIST_TOKENS.any { artist.lowercase().contains(it) }) {
                    Genre.INDIE
                } else {
                    Genre.BOLLYWOOD
                }
            }
        }
    }

    /**
     * Consistent cross-provider language & genre filter:
     * - Enforces target languages and blocks English leakage into Bollywood
     * - Eliminates sad and heartbroken songs from Party & Dance
     */
    fun matchesLanguageSelection(
        targetLanguages: Set<String>,
        explicitLanguage: String?,
        title: String,
        artist: String,
        album: String,
        genreMeta: String = "",
        requestedGenre: Genre? = null
    ): Boolean {
        val combined = "$title $artist $album $genreMeta".lowercase()

        // Hard Filter: Party & Dance must NEVER contain sad, crying, or heartbreak tracks
        if (requestedGenre == Genre.DANCE) {
            if (SAD_OR_SLOW_KEYWORDS.any { combined.contains(it) }) {
                return false
            }
        }

        // Hard Filter: Bollywood is strictly Indian / Hindi cinema music; never admit English pop tracks
        if (requestedGenre == Genre.BOLLYWOOD) {
            if (explicitLanguage != null && explicitLanguage.equals("english", ignoreCase = true)) {
                return false
            }
            if (WESTERN_ARTIST_TOKENS.any { artist.lowercase().contains(it) }) {
                return false
            }
            val detected = detectTrackLanguage(explicitLanguage, title, artist, album, genreMeta)
            if (detected.equals("English", ignoreCase = true)) {
                return false
            }
        }

        if (targetLanguages.isEmpty()) return true
        val userWantsEnglish = targetLanguages.any { it.equals("English", ignoreCase = true) }

        if (!explicitLanguage.isNullOrBlank()) {
            val cleanExplicit = explicitLanguage.trim()
            if (!userWantsEnglish && cleanExplicit.equals("english", ignoreCase = true)) return false
            return targetLanguages.any { it.equals(cleanExplicit, ignoreCase = true) }
        }

        if (!userWantsEnglish) {
            if (FOREIGN_NON_INDIAN_GENRES.any { combined.contains(it) }) {
                return false
            }
            if (WESTERN_ARTIST_TOKENS.any { artist.lowercase().contains(it) }) {
                return false
            }
            val detected = detectTrackLanguage(null, title, artist, album, genreMeta)
            if (detected.equals("English", ignoreCase = true)) {
                return false
            }
        }

        // If user selected only specific regional languages, avoid cross-regional mismatches tagged in text
        val allowedLower = targetLanguages.map { it.lowercase() }.toSet()
        for (lang in allowedLower) {
            val conflicts = REGIONAL_CONFLICT_TOKENS[lang].orEmpty()
            for (conflict in conflicts) {
                if (!allowedLower.contains(conflict) && combined.contains(conflict)) {
                    return false
                }
            }
        }
        return true
    }

    /**
     * Resolves actual song language from explicit provider tags first, falling back to
     * Unicode script and regional/artist cues rather than blindly inheriting the search filter.
     */
    fun detectTrackLanguage(
        explicitLanguage: String?,
        title: String,
        artist: String,
        album: String,
        extraMeta: String = "",
        fallbackCountry: String = ""
    ): String? {
        if (!explicitLanguage.isNullOrBlank()) {
            return normalizeLanguageName(explicitLanguage)
        }

        val combined = "$title $artist $album $extraMeta"
        // Check native Indian Unicode scripts in title/album/artist
        for (ch in combined) {
            when (ch.code) {
                in 0x0900..0x097F -> return "Hindi"
                in 0x0A00..0x0A7F -> return "Punjabi"
                in 0x0B80..0x0BFF -> return "Tamil"
                in 0x0C00..0x0C7F -> return "Telugu"
                in 0x0980..0x09FF -> return "Bengali"
                in 0x0A80..0x0AFF -> return "Gujarati"
                in 0x0C80..0x0CFF -> return "Kannada"
                in 0x0D00..0x0D7F -> return "Malayalam"
            }
        }

        val lower = combined.lowercase()
        val hasIndianScriptHint = listOf(
            "hindi", "bollywood", "arijit", "pritam", "shreya ghoshal", "neha kakkar",
            "badshah", "divine", "seedhe maut", "raftaar", "anuv jain", "prateek kuhad",
            "atif aslam", "kumar sanu", "alka yagnik", "udit narayan", "kishore kumar",
            "lata mangeshkar", "a.r. rahman", "ar rahman", "vishal-shekhar", "mithoon",
            "darshan raval", "jubin nautiyal", "arman malik", "sonu nigam",
            "ishq", "pyar", "pyaar", "mohabbat", "zindagi", "khuda", "rabba", "naina",
            "aashiqui", "channa", "bulleya", "sajna", "raanjhana", "maahi", "kesariya",
            "raabta", "subhanallah", "khairiyat", "humsafar", "shiddat", "humdard", "kahani",
            "tere ", "mere ", "tum ", "dil ", "yaar "
        ).any { lower.contains(it) }

        return when {
            listOf("punjabi", "bhangra", "jatt", "diljit", "karan aujla", "sidhu moose", "ap dhillon", "shubh")
                .any { lower.contains(it) } -> "Punjabi"
            listOf("tamil", "kollywood", "anirudh").any { lower.contains(it) } -> "Tamil"
            listOf("telugu", "tollywood", "devi sri prasad").any { lower.contains(it) } -> "Telugu"
            lower.contains("bhojpuri") -> "Bhojpuri"
            lower.contains("haryanvi") -> "Haryanvi"
            lower.contains("bengali") || lower.contains("rabindra") -> "Bengali"
            lower.contains("marathi") || lower.contains("lavani") -> "Marathi"
            lower.contains("gujarati") || lower.contains("garba") -> "Gujarati"
            lower.contains("kannada") || lower.contains("sandalwood") -> "Kannada"
            lower.contains("malayalam") || lower.contains("mollywood") -> "Malayalam"
            hasIndianScriptHint -> "Hindi"
            fallbackCountry.equals("US", ignoreCase = true) ||
                listOf("billboard", "edm", "trap", "hip-hop", "r&b", "dance", "electronic", "house", "pop", "rock")
                    .any { extraMeta.lowercase().contains(it) } -> "English"
            else -> null
        }
    }

    private fun normalizeLanguageName(raw: String): String {
        val cleaned = raw.trim()
        return if (cleaned.isEmpty()) "" else cleaned.lowercase().replaceFirstChar { it.uppercase() }
    }

    fun getDiverseQueryTerm(
        genre: Genre?,
        lang: String,
        artistSeed: String?,
        page: Int,
        concise: Boolean = false
    ): String {
        if (!artistSeed.isNullOrBlank()) return "$artistSeed $lang"
        if (concise) {
            // Concise 1-2 keyword terms for concise search queries
            val concisePool = if (lang.equals("Hindi", ignoreCase = true)) {
                when (genre) {
                    null -> listOf("bollywood", "arijit singh", "hindi", "pritam", "punjabi", "indie hindi")
                    Genre.BOLLYWOOD -> listOf("bollywood", "arijit singh", "pritam", "shreya ghoshal", "hindi film", "hindi movie songs")
                    Genre.HIPHOP -> listOf("hindi rap", "divine", "krsna", "raftaar", "badshah", "emiway")
                    Genre.INDIE -> listOf("anuv jain", "prateek kuhad", "local train", "ritviz", "indie hindi")
                    Genre.DANCE -> listOf("bollywood dance hits", "hindi party hits", "peppy hindi dance", "club hindi songs", "high energy dance")
                    Genre.ACOUSTIC -> listOf("unplugged hindi", "sufi", "lofi hindi", "atif aslam", "acoustic hindi")
                    Genre.RETRO -> listOf("kumar sanu", "kishore kumar", "90s bollywood", "udit narayan")
                    Genre.PUNJABI -> listOf("punjabi", "diljit dosanjh", "karan aujla", "ap dhillon")
                }
            } else if (lang.equals("English", ignoreCase = true)) {
                when (genre) {
                    null -> listOf("top hits", "pop hits", "weekend", "billboard")
                    Genre.HIPHOP -> listOf("hip hop", "rap", "kendrick", "drake", "travis scott")
                    Genre.INDIE -> listOf("indie pop", "alternative", "tame impala", "arctic monkeys")
                    Genre.DANCE -> listOf("dance", "edm", "calvin harris", "david guetta", "house")
                    Genre.ACOUSTIC -> listOf("acoustic", "unplugged", "chill acoustic", "ed sheeran")
                    else -> listOf("${genre?.displayName ?: "pop"} hits", "pop")
                }
            } else {
                when (genre) {
                    null -> listOf(lang, "$lang hits", "$lang songs")
                    Genre.HIPHOP -> listOf("$lang rap", "$lang hip hop")
                    Genre.INDIE -> listOf("$lang indie", "$lang pop")
                    Genre.DANCE -> listOf("$lang dance", "$lang party")
                    Genre.ACOUSTIC -> listOf("$lang acoustic", "$lang melody")
                    Genre.PUNJABI -> listOf("punjabi", "diljit dosanjh", "karan aujla")
                    else -> listOf(lang, "$lang hits")
                }
            }
            return concisePool[(page - 1).coerceAtLeast(0) % concisePool.size]
        }

        if (lang.equals("Hindi", ignoreCase = true)) {
            val pool = when (genre) {
                null -> listOf(
                    "latest hindi trending top hits",
                    "bollywood top songs hits",
                    "indian indie pop hindi hits",
                    "punjabi hindi top trending",
                    "bollywood romantic acoustic sufi",
                    "hindi party dance hits",
                    "desi hip hop hindi rap"
                )
                Genre.BOLLYWOOD -> listOf(
                    "latest bollywood hindi trending hits",
                    "bollywood romantic songs hindi",
                    "arijit singh bollywood hits",
                    "pritam hindi movie songs",
                    "shreya ghoshal bollywood hits",
                    "bollywood golden melodies",
                    "latest hindi film songs 2024"
                )
                Genre.HIPHOP -> listOf(
                    "hindi hip hop rap",
                    "desi hip hop trending",
                    "divine emiway hindi rap",
                    "seedhe maut underground rap",
                    "gully gang hindi hip hop"
                )
                Genre.INDIE -> listOf(
                    "hindi indie hits",
                    "prateek kuhad indie pop",
                    "anuv jain hindi acoustic indie",
                    "independent hindi music",
                    "the local train indie rock"
                )
                Genre.DANCE -> listOf(
                    "high energy bollywood dance party hits",
                    "peppy bollywood party dance songs",
                    "upbeat hindi party dance club hits",
                    "fast energetic bollywood dance hits",
                    "punjabi bollywood party dance upbeat"
                )
                Genre.ACOUSTIC -> listOf(
                    "hindi acoustic songs",
                    "bollywood unplugged acoustic sufi",
                    "peaceful acoustic hindi melodies",
                    "sufi acoustic hindi hits",
                    "acoustic guitar hindi songs"
                )
                Genre.RETRO -> listOf(
                    "bollywood retro classic hindi",
                    "90s bollywood golden era hits",
                    "kumar sanu alka yagnik romantic",
                    "udit narayan 90s classic hits",
                    "rd burman kishore kumar classics"
                )
                Genre.PUNJABI -> listOf(
                    "punjabi top hits",
                    "diljit dosanjh punjabi songs",
                    "karan aujla punjabi hits",
                    "latest punjabi trending songs",
                    "sidhu moose wala punjabi"
                )
            }
            return pool[(page - 1).coerceAtLeast(0) % pool.size]
        } else {
            val pool = when (genre) {
                null -> listOf("$lang top trending hits", "latest $lang songs", "$lang indie pop acoustic", "$lang party dance hits")
                Genre.BOLLYWOOD -> listOf("$lang top trending hits", "latest $lang songs", "$lang movie hits")
                Genre.HIPHOP -> if (lang.equals("English", ignoreCase = true)) {
                    listOf("top hip hop rap hits", "billboard rap hip hop", "trap hip hop hits", "best hip hop songs")
                } else {
                    listOf("$lang hip hop rap", "$lang rap trending", "underground $lang hip hop")
                }
                Genre.INDIE -> if (lang.equals("English", ignoreCase = true)) {
                    listOf("indie pop hits", "alternative indie top songs", "indie folk acoustic", "modern indie rock")
                } else {
                    listOf("$lang indie hits", "$lang indie pop", "independent $lang music")
                }
                Genre.DANCE -> if (lang.equals("English", ignoreCase = true)) {
                    listOf("edm dance party hits", "electronic dance club", "top dance pop hits", "house dance music")
                } else {
                    listOf("$lang dance party songs", "$lang dj remix party", "club dance $lang")
                }
                Genre.ACOUSTIC -> listOf("$lang acoustic unplugged", "$lang acoustic melodies", "peaceful $lang songs")
                Genre.RETRO -> listOf("$lang retro classic hits", "$lang 90s golden hits", "evergreen $lang classics")
                Genre.PUNJABI -> listOf("punjabi top hits", "diljit dosanjh punjabi songs", "karan aujla punjabi hits")
            }
            return pool[(page - 1).coerceAtLeast(0) % pool.size]
        }
    }

    fun generateGradientForTrack(genre: Genre, title: String, artist: String): List<Color> {
        val seed = (title + artist).hashCode().toLong() and 0xFFFFFFFFL
        val hue1 = (seed % 360L).toFloat()
        val hue2 = ((hue1 + 45f + (seed % 40L)) % 360f)
        val color1 = Color.hsl(hue1, 0.85f, 0.52f)
        val color2 = Color.hsl(hue2, 0.90f, 0.38f)
        return listOf(color1, color2)
    }
}
