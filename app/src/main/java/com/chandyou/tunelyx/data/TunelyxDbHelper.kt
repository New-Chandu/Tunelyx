package com.chandyou.tunelyx.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.compose.ui.graphics.Color
import com.chandyou.tunelyx.model.Genre
import com.chandyou.tunelyx.model.HistoryRecord
import com.chandyou.tunelyx.model.MusicProvider
import com.chandyou.tunelyx.model.SwipeDirection
import com.chandyou.tunelyx.model.Track

open class TunelyxDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "tunelyx_library.db"
        const val DATABASE_VERSION = 2

        // Table: Saved Tracks
        const val TABLE_SAVED_TRACKS = "saved_tracks"
        const val COL_ID = "id"
        const val COL_TITLE = "title"
        const val COL_ARTIST = "artist"
        const val COL_GENRE = "genre"
        const val COL_DURATION = "duration_seconds"
        const val COL_BPM = "bpm"
        const val COL_KEY = "musical_key"
        const val COL_PREVIEW_URL = "preview_url"
        const val COL_GLOW_COLOR = "glow_color"
        const val COL_SAVED_AT = "saved_at_timestamp"
        const val COL_ARTWORK_URL = "artwork_url"
        const val COL_PROVIDER = "provider"
        const val COL_ALBUM = "album"
        const val COL_LANGUAGE = "language"

        // Table: Swipe History
        const val TABLE_HISTORY = "swipe_history"
        const val COL_HIST_TRACK_ID = "track_id"
        const val COL_HIST_ACTION = "action"
        const val COL_HIST_TIMESTAMP = "timestamp"
        const val COL_HIST_PROVIDER = "provider"
        const val COL_HIST_TITLE = "title"
        const val COL_HIST_ARTIST = "artist"
        const val COL_HIST_GENRE = "genre"
        const val COL_HIST_ARTWORK_URL = "artwork_url"
        const val COL_HIST_PREVIEW_URL = "preview_url"
        const val COL_HIST_ALBUM = "album"
        const val COL_HIST_LANGUAGE = "language"

        // Table: Persistent Discover Deck Cache
        const val TABLE_CACHED_DECK = "cached_deck"
        const val COL_CACHE_MOOD = "mood_name"
        const val COL_CACHE_POSITION = "position"
    }

    private val createCachedDeckTableSql = """
        CREATE TABLE IF NOT EXISTS $TABLE_CACHED_DECK (
            $COL_ID TEXT NOT NULL,
            $COL_CACHE_MOOD TEXT NOT NULL,
            $COL_CACHE_POSITION INTEGER NOT NULL,
            $COL_TITLE TEXT NOT NULL,
            $COL_ARTIST TEXT NOT NULL,
            $COL_GENRE TEXT NOT NULL,
            $COL_DURATION INTEGER NOT NULL DEFAULT 0,
            $COL_BPM INTEGER,
            $COL_KEY TEXT,
            $COL_PREVIEW_URL TEXT,
            $COL_GLOW_COLOR INTEGER NOT NULL,
            $COL_ARTWORK_URL TEXT,
            $COL_PROVIDER TEXT NOT NULL,
            $COL_ALBUM TEXT,
            $COL_LANGUAGE TEXT,
            PRIMARY KEY ($COL_ID, $COL_CACHE_MOOD)
        )
    """.trimIndent()

    override fun onCreate(db: SQLiteDatabase) {
        val createSavedTracksTable = """
            CREATE TABLE IF NOT EXISTS $TABLE_SAVED_TRACKS (
                $COL_ID TEXT PRIMARY KEY,
                $COL_TITLE TEXT NOT NULL,
                $COL_ARTIST TEXT NOT NULL,
                $COL_GENRE TEXT NOT NULL,
                $COL_DURATION INTEGER NOT NULL DEFAULT 30,
                $COL_BPM INTEGER,
                $COL_KEY TEXT,
                $COL_PREVIEW_URL TEXT,
                $COL_GLOW_COLOR INTEGER NOT NULL,
                $COL_SAVED_AT INTEGER NOT NULL,
                $COL_ARTWORK_URL TEXT,
                $COL_PROVIDER TEXT NOT NULL,
                $COL_ALBUM TEXT,
                $COL_LANGUAGE TEXT
            )
        """.trimIndent()

        val createHistoryTable = """
            CREATE TABLE IF NOT EXISTS $TABLE_HISTORY (
                $COL_HIST_TRACK_ID TEXT PRIMARY KEY,
                $COL_HIST_ACTION TEXT NOT NULL,
                $COL_HIST_TIMESTAMP INTEGER NOT NULL,
                $COL_HIST_PROVIDER TEXT NOT NULL,
                $COL_HIST_TITLE TEXT,
                $COL_HIST_ARTIST TEXT,
                $COL_HIST_GENRE TEXT,
                $COL_HIST_ARTWORK_URL TEXT,
                $COL_HIST_PREVIEW_URL TEXT,
                $COL_HIST_ALBUM TEXT,
                $COL_HIST_LANGUAGE TEXT
            )
        """.trimIndent()

        db.execSQL(createSavedTracksTable)
        db.execSQL(createHistoryTable)
        db.execSQL(createCachedDeckTableSql)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_history_timestamp ON $TABLE_HISTORY ($COL_HIST_TIMESTAMP)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_saved_at ON $TABLE_SAVED_TRACKS ($COL_SAVED_AT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cached_deck_mood ON $TABLE_CACHED_DECK ($COL_CACHE_MOOD, $COL_CACHE_POSITION)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(createCachedDeckTableSql)
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_cached_deck_mood ON $TABLE_CACHED_DECK ($COL_CACHE_MOOD, $COL_CACHE_POSITION)")
        }
    }

    // --- Saved Tracks Operations ---

    fun insertOrUpdateSavedTrack(track: Track) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_ID, track.id)
            put(COL_TITLE, track.title)
            put(COL_ARTIST, track.artist)
            put(COL_GENRE, track.genre.name)
            put(COL_DURATION, track.durationSeconds)
            if (track.bpm != null) put(COL_BPM, track.bpm) else putNull(COL_BPM)
            if (track.musicalKey != null) put(COL_KEY, track.musicalKey) else putNull(COL_KEY)
            put(COL_PREVIEW_URL, track.previewUrl)
            put(COL_GLOW_COLOR, track.glowColor.value.toLong())
            put(COL_SAVED_AT, track.savedAtTimestamp)
            put(COL_ARTWORK_URL, track.artworkUrl)
            put(COL_PROVIDER, track.provider.name)
            put(COL_ALBUM, track.album)
            put(COL_LANGUAGE, track.language)
        }
        db.insertWithOnConflict(TABLE_SAVED_TRACKS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun insertSavedTracks(tracks: List<Track>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            tracks.forEach { track ->
                val values = ContentValues().apply {
                    put(COL_ID, track.id)
                    put(COL_TITLE, track.title)
                    put(COL_ARTIST, track.artist)
                    put(COL_GENRE, track.genre.name)
                    put(COL_DURATION, track.durationSeconds)
                    if (track.bpm != null) put(COL_BPM, track.bpm) else putNull(COL_BPM)
                    if (track.musicalKey != null) put(COL_KEY, track.musicalKey) else putNull(COL_KEY)
                    put(COL_PREVIEW_URL, track.previewUrl)
                    put(COL_GLOW_COLOR, track.glowColor.value.toLong())
                    put(COL_SAVED_AT, track.savedAtTimestamp)
                    put(COL_ARTWORK_URL, track.artworkUrl)
                    put(COL_PROVIDER, track.provider.name)
                    put(COL_ALBUM, track.album)
                    put(COL_LANGUAGE, track.language)
                }
                db.insertWithOnConflict(TABLE_SAVED_TRACKS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun deleteSavedTrack(trackId: String) {
        val db = writableDatabase
        db.delete(TABLE_SAVED_TRACKS, "$COL_ID = ?", arrayOf(trackId))
    }

    fun getAllSavedTracks(): List<Track> {
        val list = mutableListOf<Track>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_SAVED_TRACKS,
            null,
            null,
            null,
            null,
            null,
            "$COL_SAVED_AT DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getString(it.getColumnIndexOrThrow(COL_ID))
                val title = it.getString(it.getColumnIndexOrThrow(COL_TITLE))
                val artist = it.getString(it.getColumnIndexOrThrow(COL_ARTIST))
                val genreName = it.getString(it.getColumnIndexOrThrow(COL_GENRE))
                val genre = try { Genre.valueOf(genreName) } catch (e: Exception) { Genre.BOLLYWOOD }
                val duration = it.getInt(it.getColumnIndexOrThrow(COL_DURATION))
                val bpmCol = it.getColumnIndexOrThrow(COL_BPM)
                val bpm = if (it.isNull(bpmCol)) null else it.getInt(bpmCol)
                val keyCol = it.getColumnIndexOrThrow(COL_KEY)
                val musicalKey = if (it.isNull(keyCol)) null else it.getString(keyCol)
                val previewUrl = it.getString(it.getColumnIndexOrThrow(COL_PREVIEW_URL))
                val glowVal = it.getLong(it.getColumnIndexOrThrow(COL_GLOW_COLOR))
                val glowColor = Color(glowVal.toULong())
                val savedAt = it.getLong(it.getColumnIndexOrThrow(COL_SAVED_AT))
                val artworkUrl = it.getString(it.getColumnIndexOrThrow(COL_ARTWORK_URL))
                val providerName = it.getString(it.getColumnIndexOrThrow(COL_PROVIDER))
                val provider = try { MusicProvider.valueOf(providerName) } catch (e: Exception) { MusicProvider.JIOSAAVN }
                val album = it.getString(it.getColumnIndexOrThrow(COL_ALBUM))
                val language = it.getString(it.getColumnIndexOrThrow(COL_LANGUAGE))
                val coverGradient = listOf(genre.baseColor, glowColor)

                list.add(
                    Track(
                        id = id,
                        title = title,
                        artist = artist,
                        genre = genre,
                        durationSeconds = duration,
                        bpm = bpm,
                        musicalKey = musicalKey,
                        previewUrl = previewUrl,
                        coverGradient = coverGradient,
                        glowColor = glowColor,
                        savedAtTimestamp = savedAt,
                        artworkUrl = artworkUrl,
                        provider = provider,
                        album = album,
                        language = language
                    )
                )
            }
        }
        return list
    }

    fun clearSavedTracks() {
        val db = writableDatabase
        db.delete(TABLE_SAVED_TRACKS, null, null)
    }

    // --- Swipe History Operations ---

    fun insertOrUpdateHistory(record: HistoryRecord) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_HIST_TRACK_ID, record.trackId)
            put(COL_HIST_ACTION, record.action.name)
            put(COL_HIST_TIMESTAMP, record.timestamp)
            put(COL_HIST_PROVIDER, record.provider.name)
            put(COL_HIST_TITLE, record.title)
            put(COL_HIST_ARTIST, record.artist)
            put(COL_HIST_GENRE, record.genre.name)
            put(COL_HIST_ARTWORK_URL, record.artworkUrl)
            put(COL_HIST_PREVIEW_URL, record.previewUrl)
            put(COL_HIST_ALBUM, record.album)
            put(COL_HIST_LANGUAGE, record.language)
        }
        db.insertWithOnConflict(TABLE_HISTORY, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun insertHistoryMap(historyMap: Map<String, HistoryRecord>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            historyMap.values.forEach { record ->
                val values = ContentValues().apply {
                    put(COL_HIST_TRACK_ID, record.trackId)
                    put(COL_HIST_ACTION, record.action.name)
                    put(COL_HIST_TIMESTAMP, record.timestamp)
                    put(COL_HIST_PROVIDER, record.provider.name)
                    put(COL_HIST_TITLE, record.title)
                    put(COL_HIST_ARTIST, record.artist)
                    put(COL_HIST_GENRE, record.genre.name)
                    put(COL_HIST_ARTWORK_URL, record.artworkUrl)
                    put(COL_HIST_PREVIEW_URL, record.previewUrl)
                    put(COL_HIST_ALBUM, record.album)
                    put(COL_HIST_LANGUAGE, record.language)
                }
                db.insertWithOnConflict(TABLE_HISTORY, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun deleteHistoryRecord(trackId: String) {
        val db = writableDatabase
        db.delete(TABLE_HISTORY, "$COL_HIST_TRACK_ID = ?", arrayOf(trackId))
    }

    fun getAllHistory(): Map<String, HistoryRecord> {
        val map = mutableMapOf<String, HistoryRecord>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_HISTORY,
            null,
            null,
            null,
            null,
            null,
            "$COL_HIST_TIMESTAMP DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                val trackId = it.getString(it.getColumnIndexOrThrow(COL_HIST_TRACK_ID))
                val actionName = it.getString(it.getColumnIndexOrThrow(COL_HIST_ACTION))
                val action = try {
                    if (actionName == "LIKE_SPOTIFY") SwipeDirection.LIKE else SwipeDirection.valueOf(actionName)
                } catch (e: Exception) {
                    SwipeDirection.LIKE
                }
                val timestamp = it.getLong(it.getColumnIndexOrThrow(COL_HIST_TIMESTAMP))
                val providerName = it.getString(it.getColumnIndexOrThrow(COL_HIST_PROVIDER))
                val provider = try {
                    MusicProvider.valueOf(providerName)
                } catch (e: Exception) {
                    MusicProvider.JIOSAAVN
                }
                val title = it.getString(it.getColumnIndexOrThrow(COL_HIST_TITLE)).orEmpty()
                val artist = it.getString(it.getColumnIndexOrThrow(COL_HIST_ARTIST)).orEmpty()
                val genreName = it.getString(it.getColumnIndexOrThrow(COL_HIST_GENRE)).orEmpty()
                val genre = try { Genre.valueOf(genreName) } catch (e: Exception) { Genre.BOLLYWOOD }
                val artworkUrl = it.getString(it.getColumnIndexOrThrow(COL_HIST_ARTWORK_URL))
                val previewUrl = it.getString(it.getColumnIndexOrThrow(COL_HIST_PREVIEW_URL))
                val album = it.getString(it.getColumnIndexOrThrow(COL_HIST_ALBUM))
                val language = it.getString(it.getColumnIndexOrThrow(COL_HIST_LANGUAGE))

                map[trackId] = HistoryRecord(
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
        }
        return map
    }

    fun clearHistory() {
        val db = writableDatabase
        db.delete(TABLE_HISTORY, null, null)
    }

    fun clearHistoryByAction(action: SwipeDirection) {
        val db = writableDatabase
        db.delete(TABLE_HISTORY, "$COL_HIST_ACTION = ?", arrayOf(action.name))
    }

    fun clearHistoryByActions(actions: List<SwipeDirection>) {
        val db = writableDatabase
        val placeholders = actions.joinToString(",") { "?" }
        val args = actions.map { it.name }.toTypedArray()
        db.delete(TABLE_HISTORY, "$COL_HIST_ACTION IN ($placeholders)", args)
    }

    // --- Persistent Discover Deck Cache ---

    fun saveCachedDeck(moodName: String, tracks: List<Track>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_CACHED_DECK, "$COL_CACHE_MOOD = ?", arrayOf(moodName))
            tracks.take(35).forEachIndexed { index, track ->
                val values = ContentValues().apply {
                    put(COL_ID, track.id)
                    put(COL_CACHE_MOOD, moodName)
                    put(COL_CACHE_POSITION, index)
                    put(COL_TITLE, track.title)
                    put(COL_ARTIST, track.artist)
                    put(COL_GENRE, track.genre.name)
                    put(COL_DURATION, track.durationSeconds)
                    if (track.bpm != null) put(COL_BPM, track.bpm) else putNull(COL_BPM)
                    if (track.musicalKey != null) put(COL_KEY, track.musicalKey) else putNull(COL_KEY)
                    put(COL_PREVIEW_URL, track.previewUrl)
                    put(COL_GLOW_COLOR, track.glowColor.value.toLong())
                    put(COL_ARTWORK_URL, track.artworkUrl)
                    put(COL_PROVIDER, track.provider.name)
                    put(COL_ALBUM, track.album)
                    put(COL_LANGUAGE, track.language)
                }
                db.insertWithOnConflict(TABLE_CACHED_DECK, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getCachedDeck(moodName: String): List<Track> {
        val list = mutableListOf<Track>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_CACHED_DECK,
            null,
            "$COL_CACHE_MOOD = ?",
            arrayOf(moodName),
            null,
            null,
            "$COL_CACHE_POSITION ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getString(it.getColumnIndexOrThrow(COL_ID))
                val title = it.getString(it.getColumnIndexOrThrow(COL_TITLE))
                val artist = it.getString(it.getColumnIndexOrThrow(COL_ARTIST))
                val genreName = it.getString(it.getColumnIndexOrThrow(COL_GENRE))
                val genre = try { Genre.valueOf(genreName) } catch (e: Exception) { Genre.BOLLYWOOD }
                val duration = it.getInt(it.getColumnIndexOrThrow(COL_DURATION))
                val bpmCol = it.getColumnIndexOrThrow(COL_BPM)
                val bpm = if (it.isNull(bpmCol)) null else it.getInt(bpmCol)
                val keyCol = it.getColumnIndexOrThrow(COL_KEY)
                val musicalKey = if (it.isNull(keyCol)) null else it.getString(keyCol)
                val previewUrl = it.getString(it.getColumnIndexOrThrow(COL_PREVIEW_URL))
                val glowVal = it.getLong(it.getColumnIndexOrThrow(COL_GLOW_COLOR))
                val glowColor = Color(glowVal.toULong())
                val artworkUrl = it.getString(it.getColumnIndexOrThrow(COL_ARTWORK_URL))
                val providerName = it.getString(it.getColumnIndexOrThrow(COL_PROVIDER))
                val provider = try { MusicProvider.valueOf(providerName) } catch (e: Exception) { MusicProvider.JIOSAAVN }
                val album = it.getString(it.getColumnIndexOrThrow(COL_ALBUM))
                val language = it.getString(it.getColumnIndexOrThrow(COL_LANGUAGE))
                val coverGradient = listOf(genre.baseColor, glowColor)

                list.add(
                    Track(
                        id = id,
                        title = title,
                        artist = artist,
                        genre = genre,
                        durationSeconds = duration,
                        bpm = bpm,
                        musicalKey = musicalKey,
                        previewUrl = previewUrl,
                        coverGradient = coverGradient,
                        glowColor = glowColor,
                        artworkUrl = artworkUrl,
                        provider = provider,
                        album = album,
                        language = language
                    )
                )
            }
        }
        return list
    }

    fun clearCachedDeck() {
        val db = writableDatabase
        db.delete(TABLE_CACHED_DECK, null, null)
    }
}
