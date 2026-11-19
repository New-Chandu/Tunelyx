package com.chandyou.tunelyx.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.chandyou.tunelyx.data.providers.YouTubeMusicProvider
import com.chandyou.tunelyx.model.SwipeDirection
import com.chandyou.tunelyx.model.Track
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import kotlin.math.ceil

object AudioCacheManager {
    private const val CACHE_MAX_BYTES = 120 * 1024 * 1024L // 120 MB LRU offline audio cache
    @Volatile
    private var simpleCache: SimpleCache? = null

    @Synchronized
    fun getCache(context: Context): SimpleCache {
        return simpleCache ?: run {
            val cacheFolder = File(context.applicationContext.cacheDir, "tunelyx_audio_cache")
            if (!cacheFolder.exists()) {
                cacheFolder.mkdirs()
            }
            val evictor = LeastRecentlyUsedCacheEvictor(CACHE_MAX_BYTES)
            val databaseProvider = StandaloneDatabaseProvider(context.applicationContext)
            SimpleCache(cacheFolder, evictor, databaseProvider).also {
                simpleCache = it
            }
        }
    }

    @Synchronized
    fun clearCache() {
        simpleCache?.let { cache ->
            try {
                val keys = cache.keys.toList()
                for (key in keys) {
                    try {
                        cache.removeResource(key)
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }
}

class AudioPreviewEngine(private val context: Context) {

    companion object {
        const val MAX_PREVIEW_MS = 30_000L
    }

    private var exoPlayer: ExoPlayer? = null
    private var progressTrackingJob: Job? = null
    private var slowBufferingJob: Job? = null
    private var prewarmJob: Job? = null
    private var streamLoadJob: Job? = null
    private var hasAttemptedFallback = false
    private val warmedUrls = Collections.synchronizedSet(LinkedHashSet<String>())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e("AudioPreviewEngine", "Silent coroutine error in audio engine", throwable)
    })

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isAudioReady = MutableStateFlow(false)
    val isAudioReady: StateFlow<Boolean> = _isAudioReady.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    private val _secondsRemaining = MutableStateFlow(0)
    val secondsRemaining: StateFlow<Int> = _secondsRemaining.asStateFlow()

    private val _previewDurationMs = MutableStateFlow(0L)
    val previewDurationMs: StateFlow<Long> = _previewDurationMs.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _streamingError = MutableStateFlow<String?>(null)
    val streamingError: StateFlow<String?> = _streamingError.asStateFlow()

    private val _networkWarning = MutableStateFlow<String?>(null)
    val networkWarning: StateFlow<String?> = _networkWarning.asStateFlow()

    fun clearStreamingError() {
        _streamingError.value = null
    }

    fun clearNetworkWarning() {
        _networkWarning.value = null
    }

    init {
        initExoPlayer()
    }

    private fun initExoPlayer() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val lowLatencyLoadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                1500,  // minBufferMs (steady playback buffer)
                15000, // maxBufferMs (pre-buffers up to 15s to prevent mid-song stalls)
                250,   // bufferForPlaybackMs (instant 250ms startup latency)
                500    // bufferForPlaybackAfterRebufferMs
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val ytmHttpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(YouTubeMusicProvider.YTM_STREAM_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(10000)

        val defaultHttpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(10000)

        val routingDataSourceFactory = DataSource.Factory {
            val ytmSource = ytmHttpDataSourceFactory.createDataSource()
            val defaultSource = defaultHttpDataSourceFactory.createDataSource()
            object : DataSource {
                private var activeSource: DataSource = defaultSource
                override fun addTransferListener(transferListener: TransferListener) {
                    ytmSource.addTransferListener(transferListener)
                    defaultSource.addTransferListener(transferListener)
                }
                override fun open(dataSpec: DataSpec): Long {
                    val uriStr = dataSpec.uri.toString()
                    activeSource = if (uriStr.contains("googlevideo.com") || uriStr.contains("youtube.com")) {
                        ytmSource
                    } else {
                        defaultSource
                    }
                    return activeSource.open(dataSpec)
                }
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                    activeSource.read(buffer, offset, length)
                override fun getUri() = activeSource.uri
                override fun getResponseHeaders() = activeSource.responseHeaders
                override fun close() = activeSource.close()
            }
        }

        val audioCache = AudioCacheManager.getCache(context)
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(audioCache)
            .setUpstreamDataSourceFactory(routingDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(cacheDataSourceFactory)

        exoPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setLoadControl(lowLatencyLoadControl)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 1.0f
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                slowBufferingJob?.cancel()
                                slowBufferingJob = scope.launch {
                                    delay(4000L)
                                    if (_isPlaying.value && exoPlayer?.playbackState == Player.STATE_BUFFERING) {
                                        _networkWarning.value = "Slow internet detected — audio is taking longer to buffer."
                                    }
                                }
                            }
                            Player.STATE_READY -> {
                                slowBufferingJob?.cancel()
                                _networkWarning.value = null
                                _isAudioReady.value = true
                                exoPlayer?.repeatMode = Player.REPEAT_MODE_ONE
                                val rawDur = exoPlayer?.duration ?: C.TIME_UNSET
                                val cappedDur = if (rawDur > 0L && rawDur != C.TIME_UNSET) {
                                    rawDur.coerceIn(1000L, MAX_PREVIEW_MS)
                                } else {
                                    _previewDurationMs.value.coerceIn(1000L, MAX_PREVIEW_MS)
                                }
                                _previewDurationMs.value = cappedDur
                                val pos = exoPlayer?.currentPosition?.coerceIn(0L, cappedDur) ?: 0L
                                _currentPositionMs.value = pos
                                _playbackProgress.value = (pos.toFloat() / cappedDur).coerceIn(0f, 1f)
                                val remMs = (cappedDur - pos).coerceAtLeast(0L)
                                _secondsRemaining.value = ceil(remMs / 1000.0).toInt().coerceAtLeast(0)
                            }
                            Player.STATE_ENDED -> {
                                slowBufferingJob?.cancel()
                                // Enforce continuous loop even on edge streams where EOS fires instead of repeat
                                if (_isPlaying.value && _currentTrack.value != null) {
                                    exoPlayer?.seekTo(0L)
                                    exoPlayer?.play()
                                }
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        slowBufferingJob?.cancel()
                        val track = _currentTrack.value
                        if (track != null && track.provider == com.chandyou.tunelyx.model.MusicProvider.YOUTUBE_MUSIC && !hasAttemptedFallback) {
                            hasAttemptedFallback = true
                            streamLoadJob?.cancel()
                            streamLoadJob = scope.launch {
                                try {
                                    val fallback = YouTubeMusicProvider.resolveCrossProviderPreview(track.title, track.artist, track.language)
                                    if (!fallback.isNullOrBlank() && _currentTrack.value?.id == track.id) {
                                        _streamingError.value = null
                                        startExoPlayerWithUri(track, fallback)
                                        return@launch
                                    }
                                    _streamingError.value = "Audio preview unavailable on YouTube Music."
                                    _isPlaying.value = false
                                } catch (_: Exception) {
                                    _streamingError.value = "Audio preview unavailable on YouTube Music."
                                    _isPlaying.value = false
                                }
                            }
                            return
                        }
                        val message = when (error.errorCode) {
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
                                "No internet or audio server unreachable."
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                                "Audio server is not responding (streaming timed out)."
                            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                                "Audio preview unavailable on ${_currentTrack.value?.provider?.displayName ?: "music server"}."
                            PlaybackException.ERROR_CODE_DECODING_FAILED,
                            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ->
                                "Internal audio decoder error while playing track."
                            else -> "Playback error: ${error.localizedMessage ?: "streaming failed"}"
                        }
                        _streamingError.value = message
                        _isPlaying.value = false
                    }
                })
            }
    }

    fun playTrack(track: Track) {
        progressTrackingJob?.cancel()
        slowBufferingJob?.cancel()
        streamLoadJob?.cancel()
        hasAttemptedFallback = false
        _currentTrack.value = track
        _streamingError.value = null
        _networkWarning.value = null
        _isAudioReady.value = false
        _isPlaying.value = true
        // Never expose > 30s full duration before player settles; default preview length is 30s (or shorter clip duration)
        val initialDurationMs = if (track.durationSeconds in 1..30) {
            track.durationSeconds * 1000L
        } else {
            MAX_PREVIEW_MS
        }
        _previewDurationMs.value = initialDurationMs
        _currentPositionMs.value = 0L
        _secondsRemaining.value = (initialDurationMs / 1000L).toInt()
        _playbackProgress.value = 0f

        startProgressTimer()

        val rawUrl = track.previewUrl
        if (rawUrl.isNullOrEmpty()) {
            _streamingError.value = "No audio stream available for ${track.title} on ${track.provider.displayName}."
            _isPlaying.value = false
            return
        }

        if (YouTubeMusicProvider.isYtmPreviewUri(rawUrl)) {
            val videoId = YouTubeMusicProvider.extractVideoId(rawUrl)
            if (videoId == null) {
                _streamingError.value = "Invalid YouTube Music track reference."
                _isPlaying.value = false
                return
            }
            streamLoadJob = scope.launch {
                try {
                    val res = YouTubeMusicProvider.resolveAudioStreamUrl(videoId, track)
                    if (_currentTrack.value?.id != track.id) return@launch
                    if (!res.streamUrl.isNullOrBlank()) {
                        startExoPlayerWithUri(track, res.streamUrl)
                    } else {
                        val fallback = YouTubeMusicProvider.resolveCrossProviderPreview(track.title, track.artist, track.language)
                        if (!fallback.isNullOrBlank() && _currentTrack.value?.id == track.id) {
                            startExoPlayerWithUri(track, fallback)
                        } else {
                            _streamingError.value = res.errorMessage ?: "Unable to stream ${track.title} from YT Music."
                            _isPlaying.value = false
                        }
                    }
                } catch (_: Exception) {
                    if (_currentTrack.value?.id == track.id) {
                        _streamingError.value = "Unable to stream ${track.title}."
                        _isPlaying.value = false
                    }
                }
            }
        } else {
            startExoPlayerWithUri(track, rawUrl)
        }
    }

    private fun startExoPlayerWithUri(track: Track, streamUri: String) {
        try {
            val clipping = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(0L)
                .setEndPositionMs(MAX_PREVIEW_MS)
                .build()
            val mediaItem = MediaItem.Builder()
                .setUri(streamUri)
                .setClippingConfiguration(clipping)
                .build()
            exoPlayer?.volume = if (_isMuted.value) 0f else 1.0f
            exoPlayer?.repeatMode = Player.REPEAT_MODE_ONE
            exoPlayer?.setMediaItem(mediaItem)
            exoPlayer?.prepare()
            exoPlayer?.seekTo(0L)
            exoPlayer?.play()
        } catch (e: Exception) {
            _streamingError.value = "Internal player error while loading ${track.title}."
            _isPlaying.value = false
        }
    }

    /**
     * Seeks playback to a normalized [fraction] in [0f, 1f] of the capped (<= 30s) preview.
     */
    fun seekToFraction(fraction: Float) {
        val clamped = fraction.coerceIn(0f, 1f)
        val playerDur = exoPlayer?.duration?.takeIf { it > 0L && it != C.TIME_UNSET }?.coerceAtMost(MAX_PREVIEW_MS)
        val totalMs = playerDur ?: _previewDurationMs.value.coerceAtMost(MAX_PREVIEW_MS)
        if (totalMs <= 0L) return
        val targetMs = (clamped * totalMs).toLong().coerceIn(0L, totalMs)
        exoPlayer?.seekTo(targetMs)
        _currentPositionMs.value = targetMs
        _playbackProgress.value = clamped
        val remMs = (totalMs - targetMs).coerceAtLeast(0L)
        _secondsRemaining.value = ceil(remMs / 1000.0).toInt().coerceAtLeast(0)
    }

    /**
     * Predictive deck prefetching: pre-resolves stream URLs and pre-caches the initial
     * audio bytes into Media3 SimpleCache for up to 3 upcoming cards off the main thread.
     * Note: YouTube Music tracks are only prewarmed if their stream URL is already cached
     * to avoid consuming InnerTube rate-limit slots on background cards.
     */
    fun prewarmTracks(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        prewarmJob?.cancel()
        prewarmJob = scope.launch(Dispatchers.IO) {
            try {
                val audioCache = AudioCacheManager.getCache(context)
                for (track in tracks.take(3)) {
                    if (!isActive) break
                    val rawUrl = track.previewUrl ?: continue
                    val resolvedUrl = if (YouTubeMusicProvider.isYtmPreviewUri(rawUrl)) {
                        val vid = YouTubeMusicProvider.extractVideoId(rawUrl) ?: continue
                        YouTubeMusicProvider.getCachedStreamUrl(vid) ?: continue
                    } else {
                        rawUrl
                    }
                    if (!resolvedUrl.startsWith("https://")) continue
                    if (!warmedUrls.add(resolvedUrl)) continue
                    if (warmedUrls.size > 50) {
                        try {
                            synchronized(warmedUrls) {
                                val it = warmedUrls.iterator()
                                if (it.hasNext()) { it.next(); it.remove() }
                            }
                        } catch (_: Exception) {}
                    }

                    // Pre-buffer initial audio segment directly into Media3 SimpleCache
                    var cacheSource: DataSource? = null
                    try {
                        val dataSpec = DataSpec.Builder()
                            .setUri(Uri.parse(resolvedUrl))
                            .setLength(131072L) // 128 KB initial buffer for instant 0ms start
                            .build()

                        val ytmSource = DefaultHttpDataSource.Factory()
                            .setUserAgent(YouTubeMusicProvider.YTM_STREAM_USER_AGENT)
                            .setAllowCrossProtocolRedirects(true)
                            .setConnectTimeoutMs(4000)
                            .setReadTimeoutMs(5000)

                        val defaultSource = DefaultHttpDataSource.Factory()
                            .setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                            .setAllowCrossProtocolRedirects(true)
                            .setConnectTimeoutMs(4000)
                            .setReadTimeoutMs(5000)

                        val upstream = if (resolvedUrl.contains("googlevideo.com") || resolvedUrl.contains("youtube.com")) {
                            ytmSource
                        } else {
                            defaultSource
                        }

                        val cacheSourceInstance = CacheDataSource.Factory()
                            .setCache(audioCache)
                            .setUpstreamDataSourceFactory(upstream)
                            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                            .createDataSource()
                        cacheSource = cacheSourceInstance

                        cacheSourceInstance.open(dataSpec)
                        val buf = ByteArray(8192)
                        var bytesRead = 0
                        while (isActive && bytesRead < 131072) {
                            val read = cacheSourceInstance.read(buf, 0, buf.size)
                            if (read == C.RESULT_END_OF_INPUT || read <= 0) break
                            bytesRead += read
                        }
                    } catch (_: Exception) {
                        // Best-effort predictive audio cache fill
                    } finally {
                        try { cacheSource?.close() } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun prewarmTrack(track: Track?) {
        if (track != null) {
            prewarmTracks(listOf(track))
        }
    }

    fun clearOfflineCache() {
        AudioCacheManager.clearCache()
        warmedUrls.clear()
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            pause()
        } else {
            resume()
        }
    }

    fun resume() {
        if (_currentTrack.value != null) {
            _isPlaying.value = true
            if (exoPlayer?.playbackState == Player.STATE_ENDED) {
                exoPlayer?.seekTo(0L)
            }
            exoPlayer?.play()
            startProgressTimer(resumeFromCurrent = true)
        }
    }

    fun pause() {
        _isPlaying.value = false
        slowBufferingJob?.cancel()
        exoPlayer?.pause()
        progressTrackingJob?.cancel()
    }

    fun stop() {
        _isPlaying.value = false
        _isAudioReady.value = false
        _currentTrack.value = null // Clears selected track so mini-player and preview dismiss completely
        slowBufferingJob?.cancel()
        prewarmJob?.cancel()
        exoPlayer?.stop()
        progressTrackingJob?.cancel()
        _playbackProgress.value = 0f
        _previewDurationMs.value = 0L
        _currentPositionMs.value = 0L
        _secondsRemaining.value = 0
        _networkWarning.value = null
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
        exoPlayer?.volume = if (_isMuted.value) 0f else 1.0f
    }

    private fun startProgressTimer(resumeFromCurrent: Boolean = false) {
        progressTrackingJob?.cancel()
        progressTrackingJob = scope.launch {
            val intervalMs = 100L
            while (isActive && _isPlaying.value) {
                delay(intervalMs)
                val playerDur = exoPlayer?.duration
                    ?.takeIf { it > 0L && it != C.TIME_UNSET }
                    ?.coerceIn(1000L, MAX_PREVIEW_MS)
                val totalMs = playerDur ?: _previewDurationMs.value.coerceAtMost(MAX_PREVIEW_MS)
                if (totalMs > 0L) {
                    if (playerDur != null && _previewDurationMs.value != playerDur) {
                        _previewDurationMs.value = playerDur
                    }
                    val currentPos = exoPlayer?.currentPosition?.coerceIn(0L, totalMs) ?: 0L
                    _currentPositionMs.value = currentPos
                    _playbackProgress.value = (currentPos.toFloat() / totalMs).coerceIn(0f, 1f)
                    val remMs = (totalMs - currentPos).coerceAtLeast(0L)
                    _secondsRemaining.value = ceil(remMs / 1000.0).toInt().coerceAtLeast(0)
                }
            }
        }
    }

    fun release() {
        stop()
        exoPlayer?.release()
        exoPlayer = null
    }
}
