package com.chandyou.tunelyx.data

import android.content.Context
import android.content.SharedPreferences
import com.chandyou.tunelyx.model.ArtistAffinity
import com.chandyou.tunelyx.model.Genre
import com.chandyou.tunelyx.model.HapticIntensity
import com.chandyou.tunelyx.model.HistoryRecord
import com.chandyou.tunelyx.model.Mood
import com.chandyou.tunelyx.model.MusicProvider
import com.chandyou.tunelyx.model.SaveTarget
import com.chandyou.tunelyx.model.SwipeDirection
import com.chandyou.tunelyx.model.Track
import com.chandyou.tunelyx.ui.theme.AccentColorPreset
import org.json.JSONArray
import org.json.JSONObject

interface UserPreferencesProvider {
    val filterRemix: Boolean
    val filterAcoustic: Boolean
    val filterDiffVers: Boolean
    val filterTrending: Boolean
    val blockedArtists: Set<String>
    val blockedKeywords: Set<String>
    var savedArtistAffinities: Map<String, ArtistAffinity>
}

class UserPreferencesRepository(context: Context) : UserPreferencesProvider {
    private val prefs: SharedPreferences = context.getSharedPreferences("tunelyx_prefs", Context.MODE_PRIVATE)
    private val dbHelper = TunelyxDbHelper(context)

    companion object {
        private const val KEY_FIRST_BOOT = "key_first_boot_completed"
        private const val KEY_SONG_LANGUAGES = "key_song_languages"
        private const val KEY_APP_LANGUAGE = "key_app_language"
        private const val KEY_SELECTED_GENRES = "key_selected_genres"
        private const val KEY_DARK_THEME = "key_dark_theme"
        private const val KEY_ACCENT_PRESET = "key_accent_preset"
        private const val KEY_MUSIC_PROVIDER = "key_music_provider"
        private const val KEY_IMMERSIVE_MODE = "key_immersive_mode"
        private const val KEY_IMMERSIVE_DOCK = "key_immersive_dock"
        private const val KEY_DOCK_SHOW_LABELS = "key_dock_show_labels"
        private const val KEY_SAVE_TARGET = "key_save_target"
        private const val KEY_TOTAL_SWIPES = "key_total_swipes"
        private const val KEY_AFFINITY_MAP = "key_affinity_map"
        private const val KEY_ARTIST_AFFINITIES = "key_artist_affinities"
        private const val KEY_LANGUAGE_AFFINITIES = "key_language_affinities"
        private const val KEY_AUTO_UPDATE_CHECK = "key_auto_update_check"

        // Legacy SharedPreferences keys for one-time SQLite migration
        private const val LEGACY_KEY_HISTORY = "key_swipe_history"
        private const val LEGACY_KEY_INAPP_SAVED = "key_inapp_saved"

        val AVAILABLE_SONG_LANGUAGES = listOf(
            "Hindi", "English", "Punjabi", "Haryanvi",
            "Bhojpuri", "Tamil", "Telugu", "Bengali",
            "Gujarati", "Marathi", "Kannada", "Malayalam"
        )

        val AVAILABLE_APP_LANGUAGES = listOf("English", "Hindi")
    }

    init {
        migrateLegacyJsonToSqlite()
    }

    private fun migrateLegacyJsonToSqlite() {
        try {
            val legacyHist = prefs.getString(LEGACY_KEY_HISTORY, null)
            if (!legacyHist.isNullOrEmpty()) {
                val array = JSONArray(legacyHist)
                val histList = mutableMapOf<String, HistoryRecord>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val trackId = obj.getString("trackId")
                    val actionName = obj.getString("action")
                    val timestamp = obj.getLong("timestamp")
                    val action = try {
                        if (actionName == "LIKE_SPOTIFY") SwipeDirection.LIKE else SwipeDirection.valueOf(actionName)
                    } catch (e: Exception) {
                        SwipeDirection.LIKE
                    }
                    val providerName = obj.optString("provider", MusicProvider.JIOSAAVN.name)
                    val provider = try {
                        MusicProvider.valueOf(providerName)
                    } catch (e: Exception) {
                        MusicProvider.JIOSAAVN
                    }
                    val title = obj.optString("title", "")
                    val artist = obj.optString("artist", "")
                    val genreName = obj.optString("genre", Genre.BOLLYWOOD.name)
                    val genre = try { Genre.valueOf(genreName) } catch (e: Exception) { Genre.BOLLYWOOD }
                    val artworkUrl = obj.optString("artworkUrl").takeIf { it.isNotEmpty() }
                    val previewUrl = obj.optString("previewUrl").takeIf { it.isNotEmpty() }
                    val album = obj.optString("album").takeIf { it.isNotEmpty() }
                    val language = obj.optString("language").takeIf { it.isNotEmpty() }

                    histList[trackId] = HistoryRecord(
                        trackId = trackId,
                        action = action,
                        timestamp = timestamp,
                        provider = provider,
                        title = title,
                        artist = artist,
                        genre = genre,
                        artworkUrl = artworkUrl,
                        previewUrl = previewUrl,
                        album = album,
                        language = language
                    )
                }
                dbHelper.insertHistoryMap(histList)
                prefs.edit().remove(LEGACY_KEY_HISTORY).apply()
            }

            val legacySaved = prefs.getString(LEGACY_KEY_INAPP_SAVED, null)
            if (!legacySaved.isNullOrEmpty()) {
                val array = JSONArray(legacySaved)
                val trackList = mutableListOf<Track>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.getString("id")
                    val title = obj.getString("title")
                    val artist = obj.getString("artist")
                    val genreName = obj.optString("genre", Genre.BOLLYWOOD.name)
                    val genre = try { Genre.valueOf(genreName) } catch (e: Exception) { Genre.BOLLYWOOD }
                    val duration = obj.optInt("durationSeconds", 30)
                    val preview = obj.optString("previewUrl").takeIf { it.isNotEmpty() }
                    val glowVal = obj.optLong("glowColor", genre.baseColor.value.toLong())
                    val glowColor = androidx.compose.ui.graphics.Color(glowVal.toULong())
                    val savedAt = obj.optLong("savedAtTimestamp", 0L)
                    val artwork = obj.optString("artworkUrl").takeIf { it.isNotEmpty() }
                    val album = obj.optString("album").takeIf { it.isNotEmpty() }
                    val language = obj.optString("language").takeIf { it.isNotEmpty() }
                    val providerName = obj.optString("provider", MusicProvider.JIOSAAVN.name)
                    val provider = try {
                        MusicProvider.valueOf(providerName)
                    } catch (e: Exception) {
                        MusicProvider.JIOSAAVN
                    }
                    val coverGradient = listOf(genre.baseColor, glowColor)
                    trackList.add(
                        Track(
                            id = id,
                            title = title,
                            artist = artist,
                            genre = genre,
                            durationSeconds = duration,
                            bpm = null,
                            musicalKey = null,
                            previewUrl = preview,
                            coverGradient = coverGradient,
                            glowColor = glowColor,
                            savedAtTimestamp = savedAt,
                            artworkUrl = artwork,
                            provider = provider,
                            album = album,
                            language = language
                        )
                    )
                }
                dbHelper.insertSavedTracks(trackList)
                prefs.edit().remove(LEGACY_KEY_INAPP_SAVED).apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    var isFirstBootCompleted: Boolean
        get() = prefs.getBoolean(KEY_FIRST_BOOT, false)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_BOOT, value).apply()

    var songLanguages: Set<String>
        get() = prefs.getStringSet(KEY_SONG_LANGUAGES, setOf("Hindi")) ?: setOf("Hindi")
        set(value) = prefs.edit().putStringSet(KEY_SONG_LANGUAGES, value).apply()

    var appLanguage: String
        get() = prefs.getString(KEY_APP_LANGUAGE, "English") ?: "English"
        set(value) = prefs.edit().putString(KEY_APP_LANGUAGE, value).apply()

    var selectedGenres: Set<Genre>
        get() {
            val savedNames = prefs.getStringSet(KEY_SELECTED_GENRES, null)
            return if (savedNames != null) {
                savedNames.mapNotNull { name ->
                    try { Genre.valueOf(name) } catch (e: Exception) { null }
                }.toSet().ifEmpty { Genre.entries.toSet() }
            } else {
                Genre.entries.toSet()
            }
        }
        set(value) {
            val names = value.map { it.name }.toSet()
            prefs.edit().putStringSet(KEY_SELECTED_GENRES, names).apply()
        }

    var isDarkTheme: Boolean
        get() = prefs.getBoolean(KEY_DARK_THEME, true)
        set(value) = prefs.edit().putBoolean(KEY_DARK_THEME, value).apply()

    var accentPreset: AccentColorPreset
        get() {
            val name = prefs.getString(KEY_ACCENT_PRESET, AccentColorPreset.WHITE.name)
            return try {
                AccentColorPreset.valueOf(name ?: AccentColorPreset.WHITE.name)
            } catch (e: Exception) {
                AccentColorPreset.WHITE
            }
        }
        set(value) = prefs.edit().putString(KEY_ACCENT_PRESET, value.name).apply()

    var musicProvider: MusicProvider
        get() {
            val name = prefs.getString(KEY_MUSIC_PROVIDER, MusicProvider.JIOSAAVN.name)
            return try {
                MusicProvider.valueOf(name ?: MusicProvider.JIOSAAVN.name)
            } catch (e: Exception) {
                MusicProvider.JIOSAAVN
            }
        }
        set(value) = prefs.edit().putString(KEY_MUSIC_PROVIDER, value.name).apply()

    var isImmersiveMode: Boolean
        get() = prefs.getBoolean(KEY_IMMERSIVE_MODE, true)
        set(value) = prefs.edit().putBoolean(KEY_IMMERSIVE_MODE, value).apply()

    var isImmersiveDockEnabled: Boolean
        get() = prefs.getBoolean(KEY_IMMERSIVE_DOCK, true)
        set(value) = prefs.edit().putBoolean(KEY_IMMERSIVE_DOCK, value).apply()

    var isDockShowLabelsEnabled: Boolean
        get() = prefs.getBoolean(KEY_DOCK_SHOW_LABELS, true)
        set(value) = prefs.edit().putBoolean(KEY_DOCK_SHOW_LABELS, value).apply()

    var isAutoUpdateCheckEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_UPDATE_CHECK, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_UPDATE_CHECK, value).apply()

    var defaultSaveTarget: SaveTarget
        get() {
            val name = prefs.getString(KEY_SAVE_TARGET, SaveTarget.LIKED.name)
            return try {
                SaveTarget.valueOf(name ?: SaveTarget.LIKED.name)
            } catch (e: Exception) {
                SaveTarget.LIKED
            }
        }
        set(value) = prefs.edit().putString(KEY_SAVE_TARGET, value.name).apply()

    var totalSwipesCount: Int
        get() = prefs.getInt(KEY_TOTAL_SWIPES, 0)
        set(value) = prefs.edit().putInt(KEY_TOTAL_SWIPES, value).apply()

    override var filterRemix: Boolean
        get() = prefs.getBoolean("key_filter_remix", false)
        set(value) = prefs.edit().putBoolean("key_filter_remix", value).apply()

    override var filterAcoustic: Boolean
        get() = prefs.getBoolean("key_filter_acoustic", false)
        set(value) = prefs.edit().putBoolean("key_filter_acoustic", value).apply()

    override var filterDiffVers: Boolean
        get() = prefs.getBoolean("key_filter_diff_vers", false)
        set(value) = prefs.edit().putBoolean("key_filter_diff_vers", value).apply()

    override var filterTrending: Boolean
        get() = prefs.getBoolean("key_filter_trending", false)
        set(value) = prefs.edit().putBoolean("key_filter_trending", value).apply()

    override var blockedKeywords: Set<String>
        get() = prefs.getStringSet("key_blocked_keywords", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("key_blocked_keywords", value).apply()

    fun addBlockedKeyword(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        val current = blockedKeywords.toMutableSet()
        current.add(trimmed)
        blockedKeywords = current
    }

    fun removeBlockedKeyword(keyword: String) {
        val current = blockedKeywords.toMutableSet()
        current.remove(keyword.trim())
        blockedKeywords = current
    }

    override var blockedArtists: Set<String>
        get() = prefs.getStringSet("key_blocked_artists", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("key_blocked_artists", value).apply()

    fun blockArtist(artist: String) {
        val current = blockedArtists.toMutableSet()
        current.add(artist.trim())
        blockedArtists = current
    }

    fun unblockArtist(artist: String) {
        val current = blockedArtists.toMutableSet()
        current.remove(artist.trim())
        blockedArtists = current
    }

    // Structured SQLite Storage for Swipe History
    var savedHistory: Map<String, HistoryRecord>
        get() = dbHelper.getAllHistory()
        set(value) {
            dbHelper.clearHistory()
            dbHelper.insertHistoryMap(value)
        }

    // Structured SQLite Storage for Saved Tracks
    var inAppSavedTracks: List<Track>
        get() = dbHelper.getAllSavedTracks()
        set(value) {
            dbHelper.clearSavedTracks()
            dbHelper.insertSavedTracks(value)
        }

    var savedTracks: List<Track>
        get() = inAppSavedTracks
        set(value) {
            inAppSavedTracks = value
        }

    fun addSavedTrack(track: Track) {
        dbHelper.insertOrUpdateSavedTrack(track)
    }

    fun removeSavedTrack(trackId: String) {
        dbHelper.deleteSavedTrack(trackId)
    }

    fun recordHistory(record: HistoryRecord) {
        dbHelper.insertOrUpdateHistory(record)
    }

    fun deleteHistoryRecord(trackId: String) {
        dbHelper.deleteHistoryRecord(trackId)
    }

    fun saveCachedDeck(mood: Mood, tracks: List<Track>) {
        dbHelper.saveCachedDeck(mood.name, tracks)
    }

    fun getCachedDeck(mood: Mood): List<Track> {
        return dbHelper.getCachedDeck(mood.name)
    }

    fun saveCachedDeck(cacheKey: String, tracks: List<Track>) {
        dbHelper.saveCachedDeck(cacheKey, tracks)
    }

    fun getCachedDeck(cacheKey: String): List<Track> {
        return dbHelper.getCachedDeck(cacheKey)
    }

    fun clearCachedDeck() {
        dbHelper.clearCachedDeck()
    }

    var isHapticEnabled: Boolean
        get() = prefs.getBoolean("key_haptic_enabled", true)
        set(value) = prefs.edit().putBoolean("key_haptic_enabled", value).apply()

    var hapticIntensity: HapticIntensity
        get() {
            val name = prefs.getString("key_haptic_intensity", HapticIntensity.LITE.name)
            return try {
                HapticIntensity.valueOf(name ?: HapticIntensity.LITE.name)
            } catch (e: Exception) {
                HapticIntensity.LITE
            }
        }
        set(value) = prefs.edit().putString("key_haptic_intensity", value.name).apply()

    fun clearAllData() {
        dbHelper.clearHistory()
        dbHelper.clearSavedTracks()
        dbHelper.clearCachedDeck()
        prefs.edit().clear().apply()
    }

    fun resetHistoryData() {
        dbHelper.clearHistory()
    }

    fun resetLikedData() {
        dbHelper.clearHistoryByAction(SwipeDirection.LIKE)
        dbHelper.clearSavedTracks()
    }

    fun resetBookmarksData() {
        dbHelper.clearHistoryByAction(SwipeDirection.BOOKMARK_INAPP)
    }

    fun resetDislikedData() {
        dbHelper.clearHistoryByAction(SwipeDirection.DISLIKE)
    }

    fun resetSkippedData() {
        dbHelper.clearHistoryByAction(SwipeDirection.SKIP_NEUTRAL)
    }

    var savedAffinityMap: Map<Genre, Int>
        get() {
            val jsonString = prefs.getString(KEY_AFFINITY_MAP, null) ?: return emptyMap()
            val result = mutableMapOf<Genre, Int>()
            try {
                val obj = JSONObject(jsonString)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val genreName = keys.next()
                    val genre = try { Genre.valueOf(genreName) } catch (e: Exception) { null }
                    if (genre != null) {
                        result[genre] = obj.getInt(genreName)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return result
        }
        set(value) {
            try {
                val obj = JSONObject()
                value.forEach { (genre, score) ->
                    obj.put(genre.name, score)
                }
                prefs.edit().putString(KEY_AFFINITY_MAP, obj.toString()).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

    override var savedArtistAffinities: Map<String, ArtistAffinity>
        get() {
            val jsonString = prefs.getString(KEY_ARTIST_AFFINITIES, null) ?: return emptyMap()
            val result = mutableMapOf<String, ArtistAffinity>()
            try {
                val obj = JSONObject(jsonString)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val name = keys.next()
                    val item = obj.getJSONObject(name)
                    result[name] = ArtistAffinity(
                        artistName = name,
                        score = item.optDouble("score", 0.0),
                        likeCount = item.optInt("likeCount", 0),
                        dislikeCount = item.optInt("dislikeCount", 0),
                        lastInteractionTime = item.optLong("lastInteractionTime", 0L),
                        isSuppressed = item.optBoolean("isSuppressed", false)
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return result
        }
        set(value) {
            try {
                val obj = JSONObject()
                value.forEach { (name, aff) ->
                    val item = JSONObject().apply {
                        put("score", aff.score)
                        put("likeCount", aff.likeCount)
                        put("dislikeCount", aff.dislikeCount)
                        put("lastInteractionTime", aff.lastInteractionTime)
                        put("isSuppressed", aff.isSuppressed)
                    }
                    obj.put(name, item)
                }
                prefs.edit().putString(KEY_ARTIST_AFFINITIES, obj.toString()).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

    var savedLanguageAffinities: Map<String, Double>
        get() {
            val jsonString = prefs.getString(KEY_LANGUAGE_AFFINITIES, null) ?: return emptyMap()
            val result = mutableMapOf<String, Double>()
            try {
                val obj = JSONObject(jsonString)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val lang = keys.next()
                    result[lang] = obj.optDouble(lang, 0.0)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return result
        }
        set(value) {
            try {
                val obj = JSONObject()
                value.forEach { (lang, score) ->
                    obj.put(lang, score)
                }
                prefs.edit().putString(KEY_LANGUAGE_AFFINITIES, obj.toString()).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
}
