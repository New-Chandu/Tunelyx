package com.chandyou.tunelyx.model

import androidx.compose.ui.graphics.Color

enum class Genre(val displayName: String, val baseColor: Color) {
    BOLLYWOOD("Bollywood", Color(0xFFEC4899)),
    HIPHOP("Hip Hop", Color(0xFFF97316)),
    INDIE("Indie Pop", Color(0xFFEAB308)),
    DANCE("Party & Dance", Color(0xFF06B6D4)),
    ACOUSTIC("Acoustic & Sufi", Color(0xFF8B5CF6)),
    RETRO("Retro 90s", Color(0xFF10B981)),
    PUNJABI("Punjabi Hits", Color(0xFF3B82F6))
}

enum class Mood(val displayName: String, val associatedGenres: List<Genre>, val queryHint: String) {
    ALL("All", Genre.entries, "trending hits"),
    FOCUS("Focus", listOf(Genre.ACOUSTIC, Genre.INDIE), "focus acoustic peaceful"),
    PARTY("Party", listOf(Genre.DANCE, Genre.HIPHOP, Genre.PUNJABI), "party dance club remix"),
    CHILL("Chill", listOf(Genre.INDIE, Genre.ACOUSTIC), "chill lo-fi acoustic"),
    NIGHT_DRIVE("Night Drive", listOf(Genre.HIPHOP, Genre.BOLLYWOOD, Genre.INDIE), "night drive late night beats"),
    WORKOUT("Workout", listOf(Genre.PUNJABI, Genre.HIPHOP, Genre.DANCE), "workout gym bass fitness")
}

enum class SwipeDirection {
    LIKE,
    DISLIKE,
    BOOKMARK_INAPP,
    SKIP_NEUTRAL
}

enum class SaveTarget(val displayName: String, val subtitle: String) {
    LIKED("Liked Songs", "Saved directly to your Liked collection"),
    BOOKMARKS("Bookmarks", "Saved to your Bookmarked collection")
}

enum class HapticIntensity(val displayName: String, val level: Int) {
    LITE("Lite", 1),
    NORMAL("Normal", 2);

    companion object {
        fun fromLevel(level: Int): HapticIntensity = when (level) {
            1 -> LITE
            else -> NORMAL
        }
    }
}

enum class MusicProvider(val displayName: String, val description: String, val badge: String) {
    JIOSAAVN("JioSaavn", "Deep Bollywood, Hindi Indie, Punjabi & Desi Hip Hop", "Best for Hindi"),
    ITUNES("iTunes", "Official 30s AAC preview clips & Global Catalog", "Apple Preview"),
    YOUTUBE_MUSIC("YT Music", "Official & indie tracks from YouTube Music", "Broad Catalog");

    /**
     * Returns only the discover filter genres reliably supported by this provider
     * for the active user language selection.
     */
    fun supportedGenres(languages: Set<String> = setOf("Hindi")): List<Genre> {
        val isEnglishOnly = languages.size == 1 && languages.any { it.equals("English", ignoreCase = true) }
        val base = when (this) {
            JIOSAAVN -> listOf(
                Genre.BOLLYWOOD, Genre.HIPHOP, Genre.DANCE,
                Genre.PUNJABI, Genre.ACOUSTIC, Genre.INDIE, Genre.RETRO
            )
            ITUNES -> listOf(
                Genre.BOLLYWOOD, Genre.HIPHOP, Genre.DANCE, Genre.PUNJABI
            )
            YOUTUBE_MUSIC -> listOf(
                Genre.BOLLYWOOD, Genre.HIPHOP, Genre.DANCE,
                Genre.PUNJABI, Genre.ACOUSTIC, Genre.INDIE, Genre.RETRO
            )
        }
        return if (isEnglishOnly) {
            base.filter { it != Genre.BOLLYWOOD && it != Genre.PUNJABI && it != Genre.RETRO }
        } else {
            base
        }
    }
}

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val genre: Genre,
    val durationSeconds: Int = 0,
    val bpm: Int? = null,
    val musicalKey: String? = null,
    val previewUrl: String? = null,
    val coverGradient: List<Color>,
    val glowColor: Color,
    val savedAtTimestamp: Long = 0L,
    val artworkUrl: String? = null,
    val provider: MusicProvider = MusicProvider.JIOSAAVN,
    val album: String? = null,
    val language: String? = null,
    val artists: List<String> = emptyList()
) {
    fun displayArtists(): List<String> = artists.ifEmpty { splitArtistNames(artist, title) }

    fun isSameSongAs(other: Track): Boolean {
        if (id == other.id) return true
        if (isSameTrack(title, artist, other.title, other.artist)) return true
        val normTitleA = normalizeTitle(title)
        val normTitleB = normalizeTitle(other.title)
        if (normTitleA.isNotEmpty() && normTitleA == normTitleB) {
            if (!album.isNullOrBlank() && !other.album.isNullOrBlank()) {
                val normAlbumA = normalizeTitle(album)
                val normAlbumB = normalizeTitle(other.album)
                if (normAlbumA.isNotEmpty() && normAlbumA == normAlbumB) return true
            }
        }
        return false
    }

    fun isSameSongAs(record: HistoryRecord): Boolean {
        if (id == record.trackId) return true
        if (isSameTrack(title, artist, record.title, record.artist)) return true
        val normTitleA = normalizeTitle(title)
        val normTitleB = normalizeTitle(record.title)
        if (normTitleA.isNotEmpty() && normTitleA == normTitleB) {
            if (!album.isNullOrBlank() && !record.album.isNullOrBlank()) {
                val normAlbumA = normalizeTitle(album)
                val normAlbumB = normalizeTitle(record.album)
                if (normAlbumA.isNotEmpty() && normAlbumA == normAlbumB) return true
            }
        }
        return false
    }

    /**
     * Resolves the direct external sharing URL on the respective streaming platform.
     */
    fun platformShareUrl(): String {
        return when (provider) {
            MusicProvider.YOUTUBE_MUSIC -> {
                val vid = id.removePrefix("yt_")
                "https://music.youtube.com/watch?v=$vid"
            }
            MusicProvider.ITUNES -> {
                val encoded = java.net.URLEncoder.encode("$title $artist", "UTF-8")
                "https://music.apple.com/search?term=$encoded"
            }
            MusicProvider.JIOSAAVN -> {
                val cleanSlug = title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                "https://www.jiosaavn.com/song/$cleanSlug/$id"
            }
        }
    }
}

data class HistoryRecord(
    val trackId: String,
    val action: SwipeDirection,
    val timestamp: Long,
    val provider: MusicProvider = MusicProvider.JIOSAAVN,
    val title: String = "",
    val artist: String = "",
    val genre: Genre = Genre.BOLLYWOOD,
    val artworkUrl: String? = null,
    val previewUrl: String? = null,
    val album: String? = null,
    val language: String? = null
) {
    fun isSameSongAs(track: Track): Boolean = track.isSameSongAs(this)

    fun toTrack(): Track {
        return Track(
            id = trackId,
            title = title.ifEmpty { "Track #$trackId" },
            artist = artist.ifEmpty { "Unknown Artist" },
            genre = genre,
            durationSeconds = 0,
            bpm = null,
            musicalKey = null,
            previewUrl = previewUrl,
            coverGradient = listOf(genre.baseColor, genre.baseColor.copy(alpha = 0.7f)),
            glowColor = genre.baseColor,
            savedAtTimestamp = timestamp,
            artworkUrl = artworkUrl,
            provider = provider,
            album = album,
            language = language
        )
    }
}

fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return ""
    val mins = seconds / 60
    val secs = seconds % 60
    return "$mins:${secs.toString().padStart(2, '0')}"
}

private val FEAT_TITLE_REGEX = Regex("\\s*[\\(\\[]\\s*(?:feat\\.?|ft\\.?|with)\\s+([^\\)\\]]+)[\\)\\]]", RegexOption.IGNORE_CASE)
private val ARTIST_SPLIT_REGEX = Regex("\\s*(?:,|&|/|\\||;|\\bfeat\\.(?=\\s|$)|\\bfeat\\b|\\bft\\.(?=\\s|$)|\\bft\\b|\\bwith\\b|\\bx\\b)\\s*", RegexOption.IGNORE_CASE)
private val BRACKET_PAREN_REGEX = Regex("\\(.*?\\)")
private val BRACKET_SQUARE_REGEX = Regex("\\[.*?\\]")
private val BRACKET_BRACE_REGEX = Regex("\\{.*?\\}")
private val TITLE_SUFFIX_REGEX = Regex("\\s*[-|–—:]\\s*(lofi|remix|acoustic|slowed|reverb|version|film version|original motion picture|soundtrack|ost|audio|official video|lyric video|lyrics|feat\\b.*|ft\\b.*|from\\b.*).*")
private val NON_ALNUM_REGEX = Regex("[^\\p{L}\\p{N}\\s]")
private val NON_TOKEN_CHAR_REGEX = Regex("[^\\p{L}\\p{N}]")
private val MULTI_SPACE_REGEX = Regex("\\s+")
private val ARTIST_TOKEN_SPLIT_REGEX = Regex("(?:,|/|&|\\||\\band\\b|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bwith\\b|\\s+)")

fun cleanTrackTitle(rawTitle: String): String {
    return rawTitle
        .replace(FEAT_TITLE_REGEX, "")
        .replace(MULTI_SPACE_REGEX, " ")
        .trim()
}

fun splitArtistNames(rawArtist: String, rawTitle: String = ""): List<String> {
    val result = mutableListOf<String>()
    val seenLower = mutableSetOf<String>()

    fun addParts(source: String) {
        source.split(ARTIST_SPLIT_REGEX)
            .map { it.trim().trimEnd(',', '&', '-', ':') }
            .filter { it.isNotEmpty() && !it.equals("various artists", ignoreCase = true) }
            .forEach { name ->
                val key = name.lowercase()
                if (seenLower.add(key)) {
                    result.add(name)
                }
            }
    }

    addParts(rawArtist)
    if (rawTitle.isNotBlank()) {
        FEAT_TITLE_REGEX.findAll(rawTitle).forEach { match ->
            match.groupValues.getOrNull(1)?.let { addParts(it) }
        }
    }
    return if (result.isNotEmpty()) result else listOf(rawArtist.ifBlank { "Unknown Artist" })
}

fun normalizeTitle(title: String): String {
    return title.lowercase()
        // Strip bracketed text: (From ...), [Remix], {Live}, etc.
        .replace(BRACKET_PAREN_REGEX, "")
        .replace(BRACKET_SQUARE_REGEX, "")
        .replace(BRACKET_BRACE_REGEX, "")
        // Strip common title suffixes separated by hyphen, pipe, dash, colon
        .replace(TITLE_SUFFIX_REGEX, "")
        // Strip trailing/leading punctuation but preserve all Unicode letters and digits
        .replace(NON_ALNUM_REGEX, "")
        .trim()
        .replace(MULTI_SPACE_REGEX, " ")
}

fun extractPrimaryArtist(artist: String): String {
    return artist.lowercase()
        .split(",", "&", "/", "feat.", "feat", "ft.", "ft", "with", "and", "|")
        .firstOrNull()?.trim()
        ?.replace(NON_ALNUM_REGEX, "")
        ?.trim()
        ?.replace(MULTI_SPACE_REGEX, " ")
        .orEmpty()
}

fun extractArtistTokens(artist: String): Set<String> {
    return artist.lowercase()
        .split(ARTIST_TOKEN_SPLIT_REGEX)
        .map { it.replace(NON_TOKEN_CHAR_REGEX, "").trim() }
        .filter { it.length >= 3 }
        .toSet()
}

/**
 * Pre-normalized song fingerprint for O(1) exact checks and zero-Regex-allocation fuzzy comparison.
 */
data class SongFingerprint(
    val id: String,
    val normTitle: String,
    val artistTokens: Set<String>,
    val normAlbum: String
) {
    fun matches(other: SongFingerprint): Boolean {
        if (id == other.id) return true
        if (normTitle.isEmpty() || other.normTitle.isEmpty()) return false
        val titleMatch = normTitle == other.normTitle ||
            (normTitle.length >= 4 && other.normTitle.length >= 4 &&
                (normTitle.contains(other.normTitle) || other.normTitle.contains(normTitle)))
        if (titleMatch) {
            if (artistTokens.isEmpty() || other.artistTokens.isEmpty() || artistTokens.any { it in other.artistTokens }) {
                return true
            }
        }
        if (normTitle == other.normTitle && normAlbum.isNotEmpty() && other.normAlbum.isNotEmpty() && normAlbum == other.normAlbum) {
            return true
        }
        return false
    }

    companion object {
        fun from(track: Track) = SongFingerprint(
            id = track.id,
            normTitle = normalizeTitle(track.title),
            artistTokens = extractArtistTokens(track.artist),
            normAlbum = track.album?.let { normalizeTitle(it) }.orEmpty()
        )

        fun from(record: HistoryRecord) = SongFingerprint(
            id = record.trackId,
            normTitle = normalizeTitle(record.title),
            artistTokens = extractArtistTokens(record.artist),
            normAlbum = record.album?.let { normalizeTitle(it) }.orEmpty()
        )
    }
}

fun normalizeTrackKey(title: String, artist: String, album: String? = null): String {
    val normTitle = normalizeTitle(title)
    val primArtist = extractPrimaryArtist(artist)
    val normAlbum = album?.let { normalizeTitle(it) }?.takeIf { it.isNotEmpty() }
    return if (normAlbum != null) {
        "$normTitle::$primArtist::$normAlbum"
    } else {
        "$normTitle::$primArtist"
    }
}

fun isSameTrack(titleA: String, artistA: String, titleB: String, artistB: String): Boolean {
    val normA = normalizeTitle(titleA)
    val normB = normalizeTitle(titleB)
    if (normA.isEmpty() || normB.isEmpty()) return false

    // Exact or mutual inclusion check (e.g., "kesariya" and "kesariya audio")
    val titleMatch = normA == normB || 
        (normA.length >= 4 && normB.length >= 4 && (normA.contains(normB) || normB.contains(normA)))
    if (!titleMatch) return false

    val tokensA = extractArtistTokens(artistA)
    val tokensB = extractArtistTokens(artistB)
    if (tokensA.isEmpty() || tokensB.isEmpty()) return true
    return tokensA.any { it in tokensB }
}

data class GenreTrajectoryPoint(
    val dayLabel: String,
    val score: Int
)

data class GenreTrajectory(
    val genre: Genre,
    val points: List<GenreTrajectoryPoint>,
    val delta: Int,
    val momentumWord: String
)

data class ArtistAffinity(
    val artistName: String,
    val score: Double = 0.0,
    val likeCount: Int = 0,
    val dislikeCount: Int = 0,
    val lastInteractionTime: Long = 0L,
    val isSuppressed: Boolean = false
)

data class GenreAffinityStat(
    val genre: Genre,
    val affirmativeCount: Int,
    val totalInteractions: Int,
    val percentage: Float,
    val affinityScore: Int
)

data class TopArtistStat(
    val artistName: String,
    val likesCount: Int,
    val totalInteractions: Int,
    val affirmativePercentage: Int
)

data class ActiveDayStat(
    val dateLabel: String,
    val timestamp: Long,
    val totalSwipes: Int,
    val savedCount: Int
)

data class UserTasteProfile(
    val totalSongsExplored: Int,
    val totalSongsSaved: Int,
    val discoveryRatePercentage: Int,
    val topGenres: List<GenreAffinityStat>,
    val topArtists: List<TopArtistStat>,
    val activeDays: List<ActiveDayStat>,
    val trajectories: List<GenreTrajectory>
)
