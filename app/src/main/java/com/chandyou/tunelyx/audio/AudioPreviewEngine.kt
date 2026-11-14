package com.chandyou.tunelyx.audio

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.chandyou.tunelyx.data.providers.YouTubeMusicProvider
import com.chandyou.tunelyx.model.SwipeDirection
import com.chandyou.tunelyx.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.ceil

class AudioPreviewEngine(private val context: Context) {

    companion object {
        const val MAX_PREVIEW_MS = 30_000L
    }

    private var exoPlayer: ExoPlayer? = null
    private var progressTrackingJob: Job? = null
    private var slowBufferingJob: Job? = null
    private var prewarmJob: Job? = null
    private var streamLoadJob: Job? = null
    private val warmedUrls = LinkedHashSet<String>()
    private val scope = CoroutineScope(Dispatchers.Main + Job())

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

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(routingDataSourceFactory)

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
                val res = YouTubeMusicProvider.resolveAudioStreamUrl(videoId)
                if (_currentTrack.value?.id != track.id) return@launch
                if (!res.streamUrl.isNullOrBlank()) {
                    startExoPlayerWithUri(track, res.streamUrl)
                } else {
                    _streamingError.value = res.errorMessage ?: "Unable to stream ${track.title} from YT Music."
                    _isPlaying.value = false
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
     * Pre-warms DNS/TLS/CDN edge connection and initial audio bytes for the upcoming card
     * on IO after the active track starts playing, reducing next-card audio startup delay.
     */
    fun prewarmTrack(track: Track?) {
        val rawUrl = track?.previewUrl ?: return
        prewarmJob?.cancel()
        prewarmJob = scope.launch(Dispatchers.IO) {
            val urlString = if (YouTubeMusicProvider.isYtmPreviewUri(rawUrl)) {
                val vid = YouTubeMusicProvider.extractVideoId(rawUrl) ?: return@launch
                YouTubeMusicProvider.resolveAudioStreamUrl(vid).streamUrl ?: return@launch
            } else {
                rawUrl
            }
            if (!urlString.startsWith("https://")) return@launch
            if (!warmedUrls.add(urlString)) return@launch
            if (warmedUrls.size > 40) {
                warmedUrls.iterator().let { if (it.hasNext()) { it.next(); it.remove() } }
            }
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 2500
                    readTimeout = 2500
                    requestMethod = "GET"
                    if (urlString.contains("googlevideo.com") || urlString.contains("youtube.com")) {
                        setRequestProperty("User-Agent", YouTubeMusicProvider.YTM_STREAM_USER_AGENT)
                    }
                    setRequestProperty("Range", "bytes=0-32767")
                }
                if (connection.responseCode in 200..299) {
                    val buf = ByteArray(4096)
                    connection.inputStream.use { stream ->
                        while (isActive && stream.read(buf) != -1) {
                            // Pull first chunk into network/OS buffer
                            break
                        }
                    }
                }
            } catch (_: Exception) {
                // Best-effort network warm-up
            } finally {
                connection?.disconnect()
            }
        }
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

    // Button sound effects disabled
    fun playSwipeSfx(direction: SwipeDirection) {
        // Silent
    }

    fun release() {
        stop()
        exoPlayer?.release()
        exoPlayer = null
    }
}
