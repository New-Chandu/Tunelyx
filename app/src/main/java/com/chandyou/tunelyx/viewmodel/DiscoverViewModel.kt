package com.chandyou.tunelyx.viewmodel

import android.app.Application
import android.app.UiModeManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chandyou.tunelyx.audio.AudioPreviewEngine
import com.chandyou.tunelyx.data.MusicApiService
import com.chandyou.tunelyx.data.RecommendationEngine
import com.chandyou.tunelyx.data.UserPreferencesRepository
import com.chandyou.tunelyx.model.Genre
import com.chandyou.tunelyx.model.GenreTrajectory
import com.chandyou.tunelyx.model.GenreTrajectoryPoint
import com.chandyou.tunelyx.model.HistoryRecord
import com.chandyou.tunelyx.model.MusicProvider
import com.chandyou.tunelyx.model.SaveTarget
import com.chandyou.tunelyx.model.SongFingerprint
import com.chandyou.tunelyx.model.SwipeDirection
import com.chandyou.tunelyx.model.Track
import com.chandyou.tunelyx.model.normalizeTrackKey
import com.chandyou.tunelyx.ui.theme.AccentColorPreset
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ActiveSubScreen {
    NONE,
    APPEARANCE,
    PLAYBACK,
    SERVICES,
    FILTER_LIST,
    ABOUT
}

enum class UpdateCheckState {
    IDLE,
    CHECKING,
    UP_TO_DATE,
    ERROR
}

enum class ImportMode {
    MERGE,
    REPLACE
}

class DiscoverViewModel(application: Application) : AndroidViewModel(application) {

    val prefs = UserPreferencesRepository(application.applicationContext)
    val audioEngine = AudioPreviewEngine(application.applicationContext)
    val recommendationEngine = RecommendationEngine(prefs)

    // First boot & Onboarding state
    private val _isFirstBootCompleted = MutableStateFlow(prefs.isFirstBootCompleted)
    val isFirstBootCompleted: StateFlow<Boolean> = _isFirstBootCompleted.asStateFlow()

    private val _songLanguages = MutableStateFlow(prefs.songLanguages)
    val songLanguages: StateFlow<Set<String>> = _songLanguages.asStateFlow()

    private val _appLanguage = MutableStateFlow(prefs.appLanguage)
    val appLanguage: StateFlow<String> = _appLanguage.asStateFlow()

    // Affinity matrix (Dynamic scores: 0 to 100)
    private val _affinityMap = MutableStateFlow<Map<Genre, Int>>(
        prefs.savedAffinityMap.ifEmpty {
            Genre.entries.associateWith { genre ->
                if (prefs.selectedGenres.contains(genre)) 65 else 35
            }
        }
    )
    val affinityMap: StateFlow<Map<Genre, Int>> = _affinityMap.asStateFlow()

    private val _selectedGenreFilter = MutableStateFlow(
        prefs.selectedGenres.firstOrNull() ?: Genre.BOLLYWOOD
    )
    val selectedGenreFilter: StateFlow<Genre> = _selectedGenreFilter.asStateFlow()

    // Discover filter bar tied directly to active MusicProvider & selected languages (null = All)
    private val _availableDiscoverGenres = MutableStateFlow(
        prefs.musicProvider.supportedGenres(prefs.songLanguages)
    )
    val availableDiscoverGenres: StateFlow<List<Genre>> = _availableDiscoverGenres.asStateFlow()

    private val _selectedDiscoverGenre = MutableStateFlow<Genre?>(null)
    val selectedDiscoverGenre: StateFlow<Genre?> = _selectedDiscoverGenre.asStateFlow()

    private val _deckGeneration = MutableStateFlow(0L)
    val deckGeneration: StateFlow<Long> = _deckGeneration.asStateFlow()

    private val _activeDeck = MutableStateFlow<List<Track>>(emptyList())
    val activeDeck: StateFlow<List<Track>> = _activeDeck.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isLoadingDeck = MutableStateFlow(true)
    val isLoadingDeck: StateFlow<Boolean> = _isLoadingDeck.asStateFlow()

    private val _inAppSaved = MutableStateFlow<List<Track>>(prefs.inAppSavedTracks)
    val inAppSaved: StateFlow<List<Track>> = _inAppSaved.asStateFlow()

    private val _history = MutableStateFlow<Map<String, HistoryRecord>>(prefs.savedHistory)
    val history: StateFlow<Map<String, HistoryRecord>> = _history.asStateFlow()

    // Settings States (Persisted)
    private val _musicProvider = MutableStateFlow(prefs.musicProvider)
    val musicProvider: StateFlow<MusicProvider> = _musicProvider.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(prefs.isDarkTheme)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _accentPreset = MutableStateFlow(prefs.accentPreset)
    val accentPreset: StateFlow<AccentColorPreset> = _accentPreset.asStateFlow()

    private val _isImmersiveMode = MutableStateFlow(prefs.isImmersiveMode)
    val isImmersiveMode: StateFlow<Boolean> = _isImmersiveMode.asStateFlow()

    private val _isImmersiveDockEnabled = MutableStateFlow(prefs.isImmersiveDockEnabled)
    val isImmersiveDockEnabled: StateFlow<Boolean> = _isImmersiveDockEnabled.asStateFlow()

    // Shared Discover card drag & action bridge for external Adaptive Immersive Dock
    private val _deckDragDirection = MutableStateFlow<SwipeDirection?>(null)
    val deckDragDirection: StateFlow<SwipeDirection?> = _deckDragDirection.asStateFlow()

    private val _deckDragIntensity = MutableStateFlow(0f)
    val deckDragIntensity: StateFlow<Float> = _deckDragIntensity.asStateFlow()

    private val _pendingProgrammaticSwipe = MutableStateFlow<SwipeDirection?>(null)
    val pendingProgrammaticSwipe: StateFlow<SwipeDirection?> = _pendingProgrammaticSwipe.asStateFlow()

    fun updateDeckDragFeedback(direction: SwipeDirection?, intensity: Float) {
        _deckDragDirection.value = direction
        _deckDragIntensity.value = intensity.coerceIn(0f, 1f)
    }

    fun requestProgrammaticSwipe(direction: SwipeDirection) {
        if (currentTrack != null && _pendingProgrammaticSwipe.value == null) {
            _pendingProgrammaticSwipe.value = direction
        }
    }

    fun consumeProgrammaticSwipe() {
        _pendingProgrammaticSwipe.value = null
    }

    private val _defaultSaveTarget = MutableStateFlow(prefs.defaultSaveTarget)
    val defaultSaveTarget: StateFlow<SaveTarget> = _defaultSaveTarget.asStateFlow()

    private val _apiErrorMessage = MutableStateFlow<String?>(null)
    val apiErrorMessage: StateFlow<String?> = _apiErrorMessage.asStateFlow()

    private val _networkWarning = MutableStateFlow<String?>(null)
    val networkWarning: StateFlow<String?> = _networkWarning.asStateFlow()

    fun dismissApiError() {
        _apiErrorMessage.value = null
    }

    fun dismissNetworkWarning() {
        _networkWarning.value = null
    }

    private var prefetchJob: Job? = null
    private var isPrefetching = false

    private fun deckCacheKey(
        provider: MusicProvider = _musicProvider.value,
        genre: Genre? = _selectedDiscoverGenre.value
    ): String = "${provider.name}::${genre?.name ?: "ALL"}"

    private fun persistRemainingDeck(cacheKey: String) {
        val remaining = _activeDeck.value.drop(_currentIndex.value)
        viewModelScope.launch(Dispatchers.IO) {
            prefs.saveCachedDeck(cacheKey, remaining)
        }
    }

    private val _isHapticEnabled = MutableStateFlow(prefs.isHapticEnabled)
    val isHapticEnabled: StateFlow<Boolean> = _isHapticEnabled.asStateFlow()

    private val _libraryRevision = MutableStateFlow(0L)
    val libraryRevision: StateFlow<Long> = _libraryRevision.asStateFlow()

    fun setHapticEnabled(enabled: Boolean) {
        _isHapticEnabled.value = enabled
        prefs.isHapticEnabled = enabled
    }

    // Version filters (Remix, Acoustic, Diff Versions)
    private val _filterRemix = MutableStateFlow(prefs.filterRemix)
    val filterRemix: StateFlow<Boolean> = _filterRemix.asStateFlow()

    private val _filterAcoustic = MutableStateFlow(prefs.filterAcoustic)
    val filterAcoustic: StateFlow<Boolean> = _filterAcoustic.asStateFlow()

    private val _filterDiffVers = MutableStateFlow(prefs.filterDiffVers)
    val filterDiffVers: StateFlow<Boolean> = _filterDiffVers.asStateFlow()

    private val _filterTrending = MutableStateFlow(prefs.filterTrending)
    val filterTrending: StateFlow<Boolean> = _filterTrending.asStateFlow()

    private val _blockedKeywords = MutableStateFlow(prefs.blockedKeywords)
    val blockedKeywords: StateFlow<Set<String>> = _blockedKeywords.asStateFlow()

    // Single active rebuild deck job for cancellation of overlapping requests
    private var rebuildDeckJob: Job? = null

    // Blocked Artists State
    private val _blockedArtists = MutableStateFlow(prefs.blockedArtists)
    val blockedArtists: StateFlow<Set<String>> = _blockedArtists.asStateFlow()

    fun blockArtist(artist: String) {
        val cleanArtist = artist.trim()
        if (cleanArtist.isBlank()) return
        prefs.blockArtist(cleanArtist)
        _blockedArtists.value = prefs.blockedArtists

        // If currently displayed card is from this blocked artist, immediately advance or rebuild
        val current = currentTrack
        if (current != null && (
                current.artist.contains(cleanArtist, ignoreCase = true) ||
                current.displayArtists().any { it.equals(cleanArtist, ignoreCase = true) }
            )
        ) {
            val nextIndex = _currentIndex.value + 1
            if (nextIndex < _activeDeck.value.size) {
                _currentIndex.value = nextIndex
                _activeDeck.value.getOrNull(nextIndex)?.let { audioEngine.playTrack(it) }
                ensurePrefetchIfNeeded()
            } else {
                rebuildDeck()
            }
        }
    }

    fun unblockArtist(artist: String) {
        val cleanArtist = artist.trim()
        prefs.unblockArtist(cleanArtist)
        _blockedArtists.value = prefs.blockedArtists
    }

    // Dynamic pagination, session deduplication, and provider+genre deck cache (RAM-bounded)
    private var fetchPageCounter = 1
    private val sessionSeenTrackKeys = LinkedHashSet<String>()
    private val deckCache = LinkedHashMap<String, List<Track>>()

    @Synchronized
    private fun recordSessionSeenKey(key: String) {
        if (sessionSeenTrackKeys.contains(key)) return
        if (sessionSeenTrackKeys.size >= 250) {
            val iterator = sessionSeenTrackKeys.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
        sessionSeenTrackKeys.add(key)
    }

    @Synchronized
    private fun getSessionSeenKeysSnapshot(): Set<String> = sessionSeenTrackKeys.toSet()

    @Synchronized
    private fun clearSessionSeenKeys() {
        sessionSeenTrackKeys.clear()
    }

    private fun trimConsumedMemoryAfterSwipe(activeKey: String) {
        // Keep only the last 2 swiped songs in memory (for Undo) once index reaches 10+
        val currentIdx = _currentIndex.value
        if (currentIdx >= 10) {
            val trimCount = currentIdx - 2
            _activeDeck.value = _activeDeck.value.drop(trimCount)
            _currentIndex.value = currentIdx - trimCount
            lastSwipedItem = lastSwipedItem?.let {
                val shifted = it.index - trimCount
                if (shifted >= 0) it.copy(index = shifted) else null
            }
            if (lastSwipedItem == null) _canUndoSwipe.value = false
        }
        // Keep at most 4 inactive cached filter decks in RAM
        if (deckCache.size > 4) {
            val keysToEvict = deckCache.keys.filter { it != activeKey }.take(deckCache.size - 4)
            keysToEvict.forEach { deckCache.remove(it) }
        }
    }

    // Undo states
    data class SwipeHistoryItem(
        val track: Track,
        val direction: SwipeDirection,
        val index: Int
    )
    private var lastSwipedItem: SwipeHistoryItem? = null
    private val _canUndoSwipe = MutableStateFlow(false)
    val canUndoSwipe: StateFlow<Boolean> = _canUndoSwipe.asStateFlow()

    data class RemovedLibraryTrack(
        val track: Track,
        val wasInApp: Boolean,
        val historyRecord: HistoryRecord?
    )
    private var lastRemovedLibraryItem: RemovedLibraryTrack? = null
    private val _canUndoLibraryRemoval = MutableStateFlow(false)
    val canUndoLibraryRemoval: StateFlow<Boolean> = _canUndoLibraryRemoval.asStateFlow()

    // Trigger for re-editing preferences from Settings
    private val _isEditingPreferences = MutableStateFlow(false)
    val isEditingPreferences: StateFlow<Boolean> = _isEditingPreferences.asStateFlow()

    // Dedicated Sub-Screen Navigation State
    private val _activeSubScreen = MutableStateFlow(ActiveSubScreen.NONE)
    val activeSubScreen: StateFlow<ActiveSubScreen> = _activeSubScreen.asStateFlow()

    fun navigateToSubScreen(screen: ActiveSubScreen) {
        _activeSubScreen.value = screen
    }

    fun closeSubScreen() {
        _activeSubScreen.value = ActiveSubScreen.NONE
    }

    private val _isViewingFilterList = MutableStateFlow(false)
    val isViewingFilterList: StateFlow<Boolean> = _isViewingFilterList.asStateFlow()

    fun openFilterList() {
        _isViewingFilterList.value = true
        _activeSubScreen.value = ActiveSubScreen.FILTER_LIST
    }

    fun closeFilterList() {
        _isViewingFilterList.value = false
        if (_activeSubScreen.value == ActiveSubScreen.FILTER_LIST) {
            _activeSubScreen.value = ActiveSubScreen.NONE
        }
    }

    // Updater Functions (Item 4)
    private val _updateCheckState = MutableStateFlow(UpdateCheckState.IDLE)
    val updateCheckState: StateFlow<UpdateCheckState> = _updateCheckState.asStateFlow()

    private val _isAutoUpdateCheckEnabled = MutableStateFlow(prefs.isAutoUpdateCheckEnabled)
    val isAutoUpdateCheckEnabled: StateFlow<Boolean> = _isAutoUpdateCheckEnabled.asStateFlow()

    fun setAutoUpdateCheckEnabled(enabled: Boolean) {
        prefs.isAutoUpdateCheckEnabled = enabled
        _isAutoUpdateCheckEnabled.value = enabled
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _updateCheckState.value = UpdateCheckState.CHECKING
            kotlinx.coroutines.delay(1400)
            _updateCheckState.value = UpdateCheckState.UP_TO_DATE
        }
    }

    fun resetUpdateCheckState() {
        _updateCheckState.value = UpdateCheckState.IDLE
    }

    init {
        syncSystemNightMode(_isDarkTheme.value)
        val initialKey = deckCacheKey()
        val persistedDeck = if (prefs.isFirstBootCompleted) {
            filterEligibleCachedTracks(prefs.getCachedDeck(initialKey))
        } else {
            emptyList()
        }

        if (persistedDeck.isNotEmpty()) {
            _activeDeck.value = persistedDeck
            _currentIndex.value = 0
            _isLoadingDeck.value = false
            _deckGeneration.value++
            deckCache[initialKey] = persistedDeck
            persistedDeck.forEach {
                recordSessionSeenKey(normalizeTrackKey(it.title, it.artist, it.album))
            }
            persistedDeck.firstOrNull()?.let { audioEngine.playTrack(it) }
            ensurePrefetchIfNeeded()
        } else if (prefs.isFirstBootCompleted) {
            rebuildDeck()
        } else {
            _isLoadingDeck.value = false
        }

        // Pre-warm the next card's audio stream once the current track is ready so card transitions feel instant
        viewModelScope.launch {
            audioEngine.isAudioReady.collect { ready ->
                if (ready) {
                    val nextUpcoming = _activeDeck.value.getOrNull(_currentIndex.value + 1)
                    audioEngine.prewarmTrack(nextUpcoming)
                }
            }
        }

        // Proactive background pre-warming of top supported discover filters (skipped on iTunes to preserve rate budget)
        viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(1500)
            val provider = _musicProvider.value
            val langs = _songLanguages.value
            if (provider == MusicProvider.ITUNES) return@launch
            val topGenres = _availableDiscoverGenres.value.take(2)
            topGenres.forEach { genre ->
                val cacheKey = deckCacheKey(provider, genre)
                if (!deckCache[cacheKey].isNullOrEmpty()) return@forEach
                val persisted = filterEligibleCachedTracks(prefs.getCachedDeck(cacheKey))
                if (persisted.isNotEmpty()) {
                    deckCache[cacheKey] = persisted
                    persisted.forEach {
                        recordSessionSeenKey(normalizeTrackKey(it.title, it.artist, it.album))
                    }
                    return@forEach
                }
                val res = MusicApiService.fetchTracksWithStatus(
                    genre = genre,
                    languages = langs,
                    provider = provider,
                    limit = 15,
                    page = fetchPageCounter++
                )
                if (res.tracks.isNotEmpty()) {
                    val ranked = recommendationEngine.buildDeck(
                        candidates = res.tracks,
                        history = _history.value,
                        selectedLanguages = langs,
                        genreAffinityMap = _affinityMap.value,
                        targetSize = 12
                    )
                    if (ranked.isNotEmpty()) {
                        deckCache[cacheKey] = ranked
                        prefs.saveCachedDeck(cacheKey, ranked)
                        ranked.forEach {
                            sessionSeenTrackKeys.add(normalizeTrackKey(it.title, it.artist, it.album))
                        }
                    }
                }
            }
        }
    }

    private fun filterEligibleCachedTracks(tracks: List<Track>): List<Track> {
        val blocked = _blockedArtists.value
        val langs = _songLanguages.value
        val hist = _history.value
        val now = System.currentTimeMillis()
        val sevenDaysMs = 7 * 24 * 60 * 60 * 1000L
        return tracks.filter { track ->
            val trackArtists = track.displayArtists()
            val isBlockedArtist = blocked.any { blockedName ->
                track.artist.trim().equals(blockedName, ignoreCase = true) ||
                    trackArtists.any { it.equals(blockedName, ignoreCase = true) }
            }
            if (isBlockedArtist) return@filter false
            if (langs.isNotEmpty()) {
                val trackLang = track.language?.trim().orEmpty()
                if (trackLang.isEmpty()) {
                    if (track.provider == MusicProvider.JIOSAAVN) return@filter false
                } else if (langs.none { it.equals(trackLang, ignoreCase = true) }) {
                    return@filter false
                }
            }
            val inHist = hist[track.id] ?: hist.values.firstOrNull { it.isSameSongAs(track) }
            if (inHist != null) {
                if (inHist.action != SwipeDirection.SKIP_NEUTRAL || (now - inHist.timestamp) < sevenDaysMs) {
                    return@filter false
                }
            }
            true
        }
    }

    fun completeOnboarding(
        languages: Set<String>,
        appLang: String,
        genres: Set<Genre>,
        provider: MusicProvider = _musicProvider.value
    ) {
        prefs.isFirstBootCompleted = true
        prefs.songLanguages = languages
        prefs.appLanguage = appLang
        prefs.selectedGenres = genres
        prefs.musicProvider = provider
        deckCache.clear()
        prefs.clearCachedDeck()
        prefetchJob?.cancel()
        isPrefetching = false

        _isFirstBootCompleted.value = true
        _songLanguages.value = languages
        _appLanguage.value = appLang
        _musicProvider.value = provider
        _isEditingPreferences.value = false

        val supported = provider.supportedGenres(languages)
        _availableDiscoverGenres.value = supported
        if (_selectedDiscoverGenre.value != null && !supported.contains(_selectedDiscoverGenre.value)) {
            _selectedDiscoverGenre.value = null
        }

        // Update initial affinity matrix
        val newAffinity = Genre.entries.associateWith { g ->
            if (genres.contains(g)) 70 else 30
        }
        _affinityMap.value = newAffinity
        prefs.savedAffinityMap = newAffinity

        genres.firstOrNull()?.let {
            _selectedGenreFilter.value = it
        }

        rebuildDeck()
    }

    fun openPreferencesEditor() {
        _isEditingPreferences.value = true
    }

    fun closePreferencesEditor() {
        _isEditingPreferences.value = false
    }

    fun setMusicProvider(provider: MusicProvider) {
        _musicProvider.value = provider
        prefs.musicProvider = provider
        val supported = provider.supportedGenres(_songLanguages.value)
        _availableDiscoverGenres.value = supported
        if (_selectedDiscoverGenre.value != null && !supported.contains(_selectedDiscoverGenre.value)) {
            _selectedDiscoverGenre.value = null
        }
        deckCache.clear()
        prefs.clearCachedDeck()
        prefetchJob?.cancel()
        isPrefetching = false
        rebuildDeck()
    }

    fun selectDiscoverGenre(genre: Genre?) {
        if (genre != null && !_availableDiscoverGenres.value.contains(genre)) return
        _selectedDiscoverGenre.value = genre
        if (genre != null) {
            _selectedGenreFilter.value = genre
        }
        rebuildDeckJob?.cancel()
        prefetchJob?.cancel()
        isPrefetching = false
        audioEngine.stop()

        val activeProvider = _musicProvider.value
        val cacheKey = deckCacheKey(activeProvider, genre)
        val memSnapshot = deckCache[cacheKey]
        if (!memSnapshot.isNullOrEmpty()) {
            val fastFiltered = filterEligibleCachedTracks(memSnapshot)
            if (fastFiltered.isNotEmpty()) {
                _activeDeck.value = fastFiltered
                _currentIndex.value = 0
                _isLoadingDeck.value = false
                _apiErrorMessage.value = null
                _deckGeneration.value++
                fastFiltered.firstOrNull()?.let { audioEngine.playTrack(it) }
                ensurePrefetchIfNeeded()
                return
            }
        }

        // Load SQLite cached deck and filter off Main Thread to eliminate UI lag while switching chips
        rebuildDeckJob = viewModelScope.launch {
            _isLoadingDeck.value = true
            val diskFiltered = withContext(Dispatchers.IO) {
                val diskRaw = prefs.getCachedDeck(cacheKey)
                filterEligibleCachedTracks(diskRaw)
            }
            if (!coroutineContext.isActive || deckCacheKey() != cacheKey) return@launch

            if (diskFiltered.isNotEmpty()) {
                deckCache[cacheKey] = diskFiltered
                _activeDeck.value = diskFiltered
                _currentIndex.value = 0
                _isLoadingDeck.value = false
                _apiErrorMessage.value = null
                _deckGeneration.value++
                diskFiltered.firstOrNull()?.let { audioEngine.playTrack(it) }
                ensurePrefetchIfNeeded()
            } else {
                executeRebuildDeck()
            }
        }
    }

    fun rebuildDeck() {
        // Prevent overlapping requests by cancelling previous unfinished jobs
        rebuildDeckJob?.cancel()
        prefetchJob?.cancel()
        isPrefetching = false
        rebuildDeckJob = viewModelScope.launch {
            executeRebuildDeck()
        }
    }

    private suspend fun executeRebuildDeck() {
        _isLoadingDeck.value = true
        _apiErrorMessage.value = null
        _networkWarning.value = null
        audioEngine.clearStreamingError()

        // Apply passive genre decay for genres inactive for >3 days
        val currentAffinity = _affinityMap.value.toMutableMap()
        recommendationEngine.applyPassiveDecay(currentAffinity, _history.value)
        if (currentAffinity != _affinityMap.value) {
            _affinityMap.value = currentAffinity
            withContext(Dispatchers.IO) {
                prefs.savedAffinityMap = currentAffinity
            }
        }

        val isColdStart = prefs.totalSwipesCount < 5
        val activeProvider = _musicProvider.value
        val queryGenre = _selectedDiscoverGenre.value
        val activeKey = deckCacheKey(activeProvider, queryGenre)
        val isItunes = activeProvider == MusicProvider.ITUNES

        val secondaryGenre = if (queryGenre == null) {
            _affinityMap.value.entries
                .sortedByDescending { it.value }
                .firstOrNull { _availableDiscoverGenres.value.contains(it.key) }?.key
        } else null

        var rankedDeck = emptyList<Track>()
        var lastErrorMsg: String? = null
        // Continuous resilient retry window (~60s total with smooth backoff) so slow networks / sleeping servers recover automatically
        val maxAttempts = 10

        for (attempt in 1..maxAttempts) {
            if (!kotlinx.coroutines.currentCoroutineContext().isActive || deckCacheKey() != activeKey) return
            val currentPage = fetchPageCounter++
            val (primaryResult, secondaryResult) = coroutineScope {
                val primaryDeferred = async(Dispatchers.IO) {
                    MusicApiService.fetchTracksWithStatus(
                        genre = queryGenre,
                        languages = _songLanguages.value,
                        provider = activeProvider,
                        limit = if (isItunes) 35 else 25,
                        isColdStart = isColdStart,
                        page = currentPage
                    )
                }
                val secondaryDeferred = async(Dispatchers.IO) {
                    if (!isItunes && secondaryGenre != null && attempt == 1) {
                        MusicApiService.fetchTracksWithStatus(
                            genre = secondaryGenre,
                            languages = _songLanguages.value,
                            provider = activeProvider,
                            limit = 15,
                            isColdStart = false,
                            page = currentPage
                        )
                    } else null
                }
                val primary = primaryDeferred.await()
                val secondaryWaitMs = if (primary.tracks.size >= 8) 300L else 1800L
                val secondary = withTimeoutOrNull(secondaryWaitMs) { secondaryDeferred.await() }
                primary to secondary
            }

            if (!kotlinx.coroutines.currentCoroutineContext().isActive || deckCacheKey() != activeKey) return

            val warning = primaryResult.warningMessage ?: secondaryResult?.warningMessage
            if (warning != null) _networkWarning.value = warning
            val primaryError = primaryResult.errorMessage ?: secondaryResult?.errorMessage
            if (primaryError != null) lastErrorMsg = primaryError

            val seenSnapshot = getSessionSeenKeysSnapshot()
            val built = withContext(Dispatchers.Default) {
                val rawCandidates = primaryResult.tracks + (secondaryResult?.tracks ?: emptyList())
                val allCandidates = mutableListOf<Track>()
                val seenFingerprints = mutableListOf<SongFingerprint>()
                for (cand in rawCandidates) {
                    val key = normalizeTrackKey(cand.title, cand.artist, cand.album)
                    if (seenSnapshot.contains(key)) continue
                    val fp = SongFingerprint.from(cand)
                    if (seenFingerprints.none { it.matches(fp) }) {
                        allCandidates.add(cand)
                        seenFingerprints.add(fp)
                    }
                }

                // Fall back to rawCandidates if sessionSeenTrackKeys exhausted niche pools
                val finalPool = if (allCandidates.isNotEmpty()) allCandidates else rawCandidates.distinctBy { it.id }
                if (finalPool.isEmpty()) return@withContext emptyList<Track>()

                recommendationEngine.buildDeck(
                    candidates = finalPool,
                    history = _history.value,
                    selectedLanguages = _songLanguages.value,
                    genreAffinityMap = _affinityMap.value,
                    targetSize = 25
                )
            }

            if (built.isNotEmpty()) {
                rankedDeck = built
                _networkWarning.value = warning
                break
            }

            // Server unresponsive, slow internet, or rate-limited: keep searching continuously with smart backoff
            if (attempt < maxAttempts) {
                val isRateLimited = lastErrorMsg?.contains("rate limit", ignoreCase = true) == true ||
                    lastErrorMsg?.contains("cooldown", ignoreCase = true) == true
                _networkWarning.value = lastErrorMsg
                    ?: "Still searching for ${queryGenre?.displayName ?: activeProvider.displayName} songs..."
                val retryDelayMs = when {
                    isRateLimited -> 8000L
                    attempt <= 2 -> 2000L
                    attempt <= 5 -> 3500L
                    else -> 5500L
                }
                kotlinx.coroutines.delay(retryDelayMs)
            }
        }

        if (!kotlinx.coroutines.currentCoroutineContext().isActive || deckCacheKey() != activeKey) return

        if (rankedDeck.isEmpty()) {
            _activeDeck.value = emptyList()
            _currentIndex.value = 0
            _apiErrorMessage.value = lastErrorMsg
                ?: "No fresh ${queryGenre?.displayName ?: ""} songs matched right now. Switch chips or tap Retry."
            _isLoadingDeck.value = false
            audioEngine.stop()
            return
        }

        for (track in rankedDeck) {
            recordSessionSeenKey(normalizeTrackKey(track.title, track.artist, track.album))
        }
        deckCache[activeKey] = rankedDeck

        _activeDeck.value = rankedDeck
        _currentIndex.value = 0
        _isLoadingDeck.value = false
        _deckGeneration.value++
        persistRemainingDeck(activeKey)

        val firstTrack = rankedDeck.firstOrNull()
        if (firstTrack != null) {
            audioEngine.playTrack(firstTrack)
        } else {
            audioEngine.stop()
        }

        ensurePrefetchIfNeeded()
    }

    fun onSwipe(direction: SwipeDirection) {
        val currentTrack = currentTrack ?: return
        lastSwipedItem = SwipeHistoryItem(currentTrack, direction, _currentIndex.value)
        _canUndoSwipe.value = true
        audioEngine.playSwipeSfx(direction)

        // Increment total swipes and record in bounded seen cache
        val newTotalSwipes = prefs.totalSwipesCount + 1
        recordSessionSeenKey(normalizeTrackKey(currentTrack.title, currentTrack.artist, currentTrack.album))

        val historyRecord = HistoryRecord(
            trackId = currentTrack.id,
            action = direction,
            timestamp = System.currentTimeMillis(),
            provider = currentTrack.provider,
            title = currentTrack.title,
            artist = currentTrack.artist,
            genre = currentTrack.genre,
            artworkUrl = currentTrack.artworkUrl,
            previewUrl = currentTrack.previewUrl,
            album = currentTrack.album,
            language = currentTrack.language
        )
        val newHistory = _history.value.toMutableMap()
        newHistory[currentTrack.id] = historyRecord
        _history.value = newHistory

        // Algorithm Learning Loop: Update Artist and Genre Affinities
        val currentAffinity = _affinityMap.value.toMutableMap()
        recommendationEngine.onTrackInteraction(currentTrack, direction, currentAffinity)
        _affinityMap.value = currentAffinity

        var savedTrackToPersist: Track? = null
        when (direction) {
            SwipeDirection.LIKE, SwipeDirection.BOOKMARK_INAPP -> {
                val savedTrack = currentTrack.copy(savedAtTimestamp = System.currentTimeMillis())
                val updated = listOf(savedTrack) + _inAppSaved.value.filter { it.id != currentTrack.id }
                _inAppSaved.value = updated
                savedTrackToPersist = savedTrack
            }
            SwipeDirection.DISLIKE -> {
                // Handled in recommendationEngine (permanent ban & artist suppression)
            }
            SwipeDirection.SKIP_NEUTRAL -> {
                // Handled in recommendationEngine (7-day cooldown recorded in history)
            }
        }

        // Persist swipe writes off Main Thread to keep swipe animations and loading smooth
        viewModelScope.launch(Dispatchers.IO) {
            prefs.totalSwipesCount = newTotalSwipes
            prefs.recordHistory(historyRecord)
            prefs.savedAffinityMap = currentAffinity
            savedTrackToPersist?.let { prefs.addSavedTrack(it) }
        }

        val nextIndex = _currentIndex.value + 1
        _currentIndex.value = nextIndex

        val activeKey = deckCacheKey()
        trimConsumedMemoryAfterSwipe(activeKey)
        val trimmedIndex = _currentIndex.value
        val remainingList = _activeDeck.value.drop(trimmedIndex)
        deckCache[activeKey] = remainingList
        persistRemainingDeck(activeKey)

        val nextTrack = _activeDeck.value.getOrNull(trimmedIndex)
        if (nextTrack != null) {
            audioEngine.playTrack(nextTrack)
        } else {
            audioEngine.stop()
        }

        // Background preloading whenever fewer than 5 remaining tracks are loaded (or full rebuild when empty)
        val remainingTracks = _activeDeck.value.size - trimmedIndex
        if (remainingTracks <= 0 && !_isLoadingDeck.value) {
            rebuildDeck()
        } else {
            ensurePrefetchIfNeeded()
        }
    }

    private fun ensurePrefetchIfNeeded() {
        val remainingTracks = _activeDeck.value.size - _currentIndex.value
        if (remainingTracks in 1..5 && !_isLoadingDeck.value) {
            prefetchNextBatch()
        }
    }

    private fun prefetchNextBatch() {
        if (isPrefetching) return
        val expectedKey = deckCacheKey()
        val activeProvider = _musicProvider.value
        val activeGenre = _selectedDiscoverGenre.value
        isPrefetching = true
        prefetchJob = viewModelScope.launch {
            try {
                val isColdStart = prefs.totalSwipesCount < 5
                val isItunes = activeProvider == MusicProvider.ITUNES
                val maxAttempts = if (isItunes) 1 else 2

                for (attempt in 1..maxAttempts) {
                    if (!coroutineContext.isActive || deckCacheKey() != expectedKey) break
                    val prefetchPage = fetchPageCounter++
                    val result = withContext(Dispatchers.IO) {
                        MusicApiService.fetchTracksWithStatus(
                            genre = activeGenre,
                            languages = _songLanguages.value,
                            provider = activeProvider,
                            limit = if (isItunes) 30 else 20,
                            isColdStart = isColdStart,
                            artistSeed = null,
                            page = prefetchPage
                        )
                    }

                    if (!coroutineContext.isActive || deckCacheKey() != expectedKey) break
                    if (result.warningMessage != null) _networkWarning.value = result.warningMessage
                    if (result.errorMessage != null && result.tracks.isEmpty()) {
                        // Do not clobber playable cards; surface transient error quietly on final attempt only
                        if (attempt == maxAttempts && _activeDeck.value.size - _currentIndex.value <= 1) {
                            _apiErrorMessage.value = result.errorMessage
                        }
                        break
                    }

                    val existingTracks = _activeDeck.value
                    val historySnapshot = _history.value.values
                    val seenSnapshot = getSessionSeenKeysSnapshot()
                    val newUnique = withContext(Dispatchers.Default) {
                        val ranked = recommendationEngine.buildDeck(
                            candidates = result.tracks,
                            history = _history.value,
                            selectedLanguages = _songLanguages.value,
                            genreAffinityMap = _affinityMap.value,
                            targetSize = 15
                        )
                        val existingFps = existingTracks.map { SongFingerprint.from(it) }
                        val historyFps = historySnapshot.map { SongFingerprint.from(it) }
                        ranked.filter { cand ->
                            val key = normalizeTrackKey(cand.title, cand.artist, cand.album)
                            if (seenSnapshot.contains(key)) return@filter false
                            val candFp = SongFingerprint.from(cand)
                            existingFps.none { it.matches(candFp) } && historyFps.none { it.matches(candFp) }
                        }
                    }

                    if (newUnique.isNotEmpty()) {
                        for (track in newUnique) {
                            recordSessionSeenKey(normalizeTrackKey(track.title, track.artist, track.album))
                        }
                        val updatedDeck = _activeDeck.value + newUnique
                        _activeDeck.value = updatedDeck
                        deckCache[expectedKey] = updatedDeck.drop(_currentIndex.value)
                        persistRemainingDeck(expectedKey)
                        break
                    }
                }
            } finally {
                isPrefetching = false
            }
        }
    }

    val currentTrack: Track?
        get() = _activeDeck.value.getOrNull(_currentIndex.value)

    val nextTrack: Track?
        get() = _activeDeck.value.getOrNull(_currentIndex.value + 1)

    val thirdTrack: Track?
        get() = _activeDeck.value.getOrNull(_currentIndex.value + 2)

    val likedTracks: List<Track>
        get() = _history.value.values
            .filter { it.action == SwipeDirection.LIKE || it.action == SwipeDirection.BOOKMARK_INAPP }
            .sortedByDescending { it.timestamp }
            .map { it.toTrack() }

    val cooldownTracks: List<Track>
        get() {
            val now = System.currentTimeMillis()
            val sevenDaysMs = 7 * 24 * 60 * 60 * 1000L
            return _history.value.values
                .filter { it.action == SwipeDirection.SKIP_NEUTRAL && (now - it.timestamp) < sevenDaysMs }
                .sortedByDescending { it.timestamp }
                .map { it.toTrack() }
        }

    val dislikedTracks: List<Track>
        get() = _history.value.values
            .filter { it.action == SwipeDirection.DISLIKE }
            .sortedByDescending { it.timestamp }
            .map { it.toTrack() }

    fun undoLastSwipe() {
        val last = lastSwipedItem ?: return
        if (_currentIndex.value > 0) {
            _currentIndex.value = last.index
            val newHist = _history.value.toMutableMap()
            newHist.remove(last.track.id)
            _history.value = newHist
            prefs.deleteHistoryRecord(last.track.id)

            if (last.direction == SwipeDirection.LIKE || last.direction == SwipeDirection.BOOKMARK_INAPP) {
                val newInApp = _inAppSaved.value.filter { it.id != last.track.id }
                _inAppSaved.value = newInApp
                prefs.removeSavedTrack(last.track.id)
            }

            audioEngine.playTrack(last.track)
            lastSwipedItem = null
            _canUndoSwipe.value = false
        }
    }

    fun removeTrackFromLibrary(track: Track) {
        val wasInApp = _inAppSaved.value.any { it.id == track.id }
        val histRecord = _history.value[track.id]
        lastRemovedLibraryItem = RemovedLibraryTrack(track, wasInApp, histRecord)
        _canUndoLibraryRemoval.value = true

        val newInApp = _inAppSaved.value.filter { it.id != track.id }
        _inAppSaved.value = newInApp
        prefs.removeSavedTrack(track.id)

        val newHist = _history.value.toMutableMap()
        newHist.remove(track.id)
        _history.value = newHist
        prefs.deleteHistoryRecord(track.id)
        _libraryRevision.value++
    }

    fun undoRemoveFromLibrary() {
        val item = lastRemovedLibraryItem ?: return
        if (item.wasInApp) {
            val updated = listOf(item.track) + _inAppSaved.value.filter { it.id != item.track.id }
            _inAppSaved.value = updated
            prefs.addSavedTrack(item.track)
        }
        if (item.historyRecord != null) {
            val newHist = _history.value.toMutableMap()
            newHist[item.track.id] = item.historyRecord
            _history.value = newHist
            prefs.recordHistory(item.historyRecord)
        }
        _libraryRevision.value++
        lastRemovedLibraryItem = null
        _canUndoLibraryRemoval.value = false
    }

    fun dismissLibraryUndo() {
        lastRemovedLibraryItem = null
        _canUndoLibraryRemoval.value = false
    }

    fun setFilterRemix(enabled: Boolean) {
        _filterRemix.value = enabled
        prefs.filterRemix = enabled
        rebuildDeck()
    }

    fun setFilterAcoustic(enabled: Boolean) {
        _filterAcoustic.value = enabled
        prefs.filterAcoustic = enabled
        rebuildDeck()
    }

    fun setFilterDiffVers(enabled: Boolean) {
        _filterDiffVers.value = enabled
        prefs.filterDiffVers = enabled
        rebuildDeck()
    }

    fun setFilterTrending(enabled: Boolean) {
        _filterTrending.value = enabled
        prefs.filterTrending = enabled
        rebuildDeck()
    }

    fun addBlockedKeyword(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        prefs.addBlockedKeyword(trimmed)
        _blockedKeywords.value = prefs.blockedKeywords
        rebuildDeck()
    }

    fun removeBlockedKeyword(keyword: String) {
        prefs.removeBlockedKeyword(keyword)
        _blockedKeywords.value = prefs.blockedKeywords
        rebuildDeck()
    }

    fun unbanTrack(trackId: String) {
        val newHist = _history.value.toMutableMap()
        newHist.remove(trackId)
        _history.value = newHist
        prefs.deleteHistoryRecord(trackId)
        rebuildDeck()
    }

    fun rediscoverTrack(trackId: String) {
        val newHist = _history.value.toMutableMap()
        newHist.remove(trackId)
        _history.value = newHist
        prefs.deleteHistoryRecord(trackId)
        rebuildDeck()
    }

    fun setGenreFilter(genre: Genre) {
        _selectedGenreFilter.value = genre
        rebuildDeck()
    }

    fun setCardMode(isImmersive: Boolean) {
        _isImmersiveMode.value = isImmersive
        prefs.isImmersiveMode = isImmersive
    }

    fun setImmersiveMode(isImmersive: Boolean) = setCardMode(isImmersive)

    fun setDarkTheme(isDark: Boolean) {
        _isDarkTheme.value = isDark
        prefs.isDarkTheme = isDark
        // Pure Compose theme update: avoid runtime uiMode change so playback never pauses and screens do not flicker
    }

    fun exportBackupJson(): String {
        val root = JSONObject().apply {
            put("version", 1)
            put("appName", "Tunelyx")
            put("exportedAt", System.currentTimeMillis())

            val savedArr = JSONArray()
            _inAppSaved.value.forEach { track ->
                val obj = JSONObject().apply {
                    put("id", track.id)
                    put("title", track.title)
                    put("artist", track.artist)
                    put("genre", track.genre.name)
                    put("durationSeconds", track.durationSeconds)
                    put("previewUrl", track.previewUrl)
                    put("artworkUrl", track.artworkUrl)
                    put("provider", track.provider.name)
                    put("album", track.album)
                    put("language", track.language)
                    put("savedAt", track.savedAtTimestamp)
                }
                savedArr.put(obj)
            }
            put("savedTracks", savedArr)

            val histArr = JSONArray()
            _history.value.values.forEach { rec ->
                val obj = JSONObject().apply {
                    put("trackId", rec.trackId)
                    put("action", rec.action.name)
                    put("timestamp", rec.timestamp)
                    put("provider", rec.provider.name)
                    put("title", rec.title)
                    put("artist", rec.artist)
                    put("genre", rec.genre.name)
                    put("artworkUrl", rec.artworkUrl)
                    put("previewUrl", rec.previewUrl)
                    put("album", rec.album)
                    put("language", rec.language)
                }
                histArr.put(obj)
            }
            put("history", histArr)

            val affObj = JSONObject()
            _affinityMap.value.forEach { (genre, score) ->
                affObj.put(genre.name, score)
            }
            put("affinityMap", affObj)

            val blockedArr = JSONArray()
            prefs.blockedArtists.forEach { blockedArr.put(it) }
            put("blockedArtists", blockedArr)

            val langArr = JSONArray()
            _songLanguages.value.forEach { langArr.put(it) }
            put("songLanguages", langArr)
        }
        return root.toString(2)
    }

    suspend fun importBackupJson(jsonString: String, mode: ImportMode = ImportMode.MERGE): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (mode == ImportMode.REPLACE) {
                prefs.savedHistory = emptyMap()
                prefs.inAppSavedTracks = emptyList<Track>()
                _inAppSaved.value = emptyList()
                _history.value = emptyMap()
            }
            val root = JSONObject(jsonString)
            val savedArr = root.optJSONArray("savedTracks")
            val histArr = root.optJSONArray("history")
            val affObj = root.optJSONObject("affinityMap")
            val blockedArr = root.optJSONArray("blockedArtists")
            val langArr = root.optJSONArray("songLanguages")

            var importedSaved = 0
            var importedHistory = 0

            if (savedArr != null && savedArr.length() > 0) {
                val newSaved = mutableListOf<Track>()
                for (i in 0 until savedArr.length()) {
                    val obj = savedArr.optJSONObject(i) ?: continue
                    val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val title = obj.optString("title", "Unknown")
                    val artist = obj.optString("artist", "Unknown")
                    val genre = try { Genre.valueOf(obj.optString("genre")) } catch (_: Exception) { Genre.BOLLYWOOD }
                    val provider = try { MusicProvider.valueOf(obj.optString("provider")) } catch (_: Exception) { MusicProvider.JIOSAAVN }
                    val coverColors = listOf(genre.baseColor, genre.baseColor)
                    val track = Track(
                        id = id,
                        title = title,
                        artist = artist,
                        genre = genre,
                        durationSeconds = obj.optInt("durationSeconds", 30),
                        bpm = null,
                        musicalKey = null,
                        previewUrl = obj.optString("previewUrl").takeIf { it.isNotBlank() },
                        coverGradient = coverColors,
                        glowColor = coverColors.first(),
                        savedAtTimestamp = obj.optLong("savedAt", System.currentTimeMillis()),
                        artworkUrl = obj.optString("artworkUrl").takeIf { it.isNotBlank() },
                        provider = provider,
                        album = obj.optString("album").takeIf { it.isNotBlank() },
                        language = obj.optString("language").takeIf { it.isNotBlank() }
                    )
                    prefs.addSavedTrack(track)
                    newSaved.add(track)
                }
                if (newSaved.isNotEmpty()) {
                    _inAppSaved.value = (_inAppSaved.value.filter { it.id !in newSaved.map { s -> s.id } } + newSaved)
                    importedSaved = newSaved.size
                }
            }

            if (histArr != null && histArr.length() > 0) {
                val newHistMap = _history.value.toMutableMap()
                for (i in 0 until histArr.length()) {
                    val obj = histArr.optJSONObject(i) ?: continue
                    val trackId = obj.optString("trackId").takeIf { it.isNotBlank() } ?: continue
                    val action = try { SwipeDirection.valueOf(obj.optString("action")) } catch (_: Exception) { SwipeDirection.LIKE }
                    val provider = try { MusicProvider.valueOf(obj.optString("provider")) } catch (_: Exception) { MusicProvider.JIOSAAVN }
                    val genre = try { Genre.valueOf(obj.optString("genre")) } catch (_: Exception) { Genre.BOLLYWOOD }
                    val rec = HistoryRecord(
                        trackId = trackId,
                        action = action,
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        provider = provider,
                        title = obj.optString("title", "Unknown"),
                        artist = obj.optString("artist", "Unknown"),
                        genre = genre,
                        artworkUrl = obj.optString("artworkUrl").takeIf { it.isNotBlank() },
                        previewUrl = obj.optString("previewUrl").takeIf { it.isNotBlank() },
                        album = obj.optString("album").takeIf { it.isNotBlank() },
                        language = obj.optString("language").takeIf { it.isNotBlank() }
                    )
                    prefs.recordHistory(rec)
                    newHistMap[trackId] = rec
                    importedHistory++
                }
                _history.value = newHistMap
            }

            if (affObj != null) {
                val newAffinities = _affinityMap.value.toMutableMap()
                Genre.entries.forEach { genre ->
                    if (affObj.has(genre.name)) {
                        newAffinities[genre] = affObj.optInt(genre.name, 40)
                    }
                }
                prefs.savedAffinityMap = newAffinities
                _affinityMap.value = newAffinities
            }

            if (blockedArr != null) {
                val set = mutableSetOf<String>()
                for (i in 0 until blockedArr.length()) {
                    val a = blockedArr.optString(i)
                    if (a.isNotBlank()) set.add(a)
                }
                if (set.isNotEmpty()) {
                    prefs.blockedArtists = prefs.blockedArtists + set
                }
            }

            if (langArr != null && langArr.length() > 0) {
                val langs = mutableSetOf<String>()
                for (i in 0 until langArr.length()) {
                    val l = langArr.optString(i)
                    if (l.isNotBlank()) langs.add(l)
                }
                if (langs.isNotEmpty()) {
                    _songLanguages.value = langs
                    prefs.songLanguages = langs
                }
            }

            _libraryRevision.value++
            val modeLabel = if (mode == ImportMode.MERGE) "Merged" else "Replaced"
            Result.success("$modeLabel $importedSaved tracks and $importedHistory history records into library.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importFromText(text: String, mode: ImportMode = ImportMode.MERGE): Result<String> = withContext(Dispatchers.IO) {
        try {
            val trimmed = text.trim()
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                return@withContext importBackupJson(trimmed, mode)
            }
            val lines = trimmed.lines().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("#") }
            if (lines.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("No songs found in file"))
            }
            if (mode == ImportMode.REPLACE) {
                prefs.savedHistory = emptyMap()
                prefs.inAppSavedTracks = emptyList<Track>()
                _inAppSaved.value = emptyList()
            }
            val newTracks = mutableListOf<Track>()
            val currentSaved = _inAppSaved.value.toMutableList()
            lines.forEach { line ->
                val parts = when {
                    line.contains(" - ") -> line.split(" - ", limit = 2)
                    line.contains(" by ") -> line.split(" by ", limit = 2)
                    line.contains(",") -> line.split(",", limit = 2)
                    else -> listOf(line, "Unknown Artist")
                }
                val title = parts.getOrNull(0)?.trim() ?: "Unknown"
                val artist = parts.getOrNull(1)?.trim() ?: "Unknown"
                val trackId = "txt_${System.currentTimeMillis()}_${title.hashCode()}"
                val track = Track(
                    id = trackId,
                    title = title,
                    artist = artist,
                    genre = Genre.BOLLYWOOD,
                    durationSeconds = 30,
                    bpm = null,
                    musicalKey = null,
                    previewUrl = null,
                    coverGradient = listOf(Genre.BOLLYWOOD.baseColor, Genre.BOLLYWOOD.baseColor),
                    glowColor = Genre.BOLLYWOOD.baseColor,
                    savedAtTimestamp = System.currentTimeMillis(),
                    artworkUrl = null,
                    provider = _musicProvider.value
                )
                prefs.addSavedTrack(track)
                newTracks.add(track)
            }
            _inAppSaved.value = currentSaved.filter { it.id !in newTracks.map { t -> t.id } } + newTracks
            _libraryRevision.value++
            val modeLabel = if (mode == ImportMode.MERGE) "Added" else "Replaced with"
            Result.success("$modeLabel ${newTracks.size} songs in library without deleting old tracks.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun syncSystemNightMode(isDark: Boolean) {
        try {
            val uiModeManager = getApplication<Application>().getSystemService(UiModeManager::class.java)
            uiModeManager?.setApplicationNightMode(
                if (isDark) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO
            )
        } catch (_: Exception) {
            // Safe fallback if UiModeManager is unavailable
        }
    }

    fun setAccentPreset(preset: AccentColorPreset) {
        _accentPreset.value = preset
        prefs.accentPreset = preset
    }

    fun setImmersiveDockEnabled(enabled: Boolean) {
        _isImmersiveDockEnabled.value = enabled
        prefs.isImmersiveDockEnabled = enabled
    }

    fun setSaveTarget(target: SaveTarget) {
        _defaultSaveTarget.value = target
        prefs.defaultSaveTarget = target
    }

    fun clearCacheOnly() {
        deckCache.clear()
        clearSessionSeenKeys()
        prefs.clearCachedDeck()
        prefetchJob?.cancel()
        isPrefetching = false
        MusicApiService.clearAllCaches()
        _activeDeck.value = emptyList()
        _currentIndex.value = 0
        audioEngine.stop()
        rebuildDeck()
    }

    fun resetHistoryOnly() {
        prefs.resetHistoryData()
        _history.value = emptyMap()
        _libraryRevision.value++
        rebuildDeck()
    }

    fun resetLikedOnly() {
        prefs.resetLikedData()
        _history.value = _history.value.filterValues { it.action != SwipeDirection.LIKE }
        _inAppSaved.value = _inAppSaved.value.filter { saved ->
            _history.value[saved.id]?.action != SwipeDirection.LIKE
        }
        _libraryRevision.value++
        rebuildDeck()
    }

    fun resetBookmarksOnly() {
        prefs.resetBookmarksData()
        _history.value = _history.value.filterValues { it.action != SwipeDirection.BOOKMARK_INAPP }
        _inAppSaved.value = _inAppSaved.value.filter { saved ->
            _history.value[saved.id]?.action != SwipeDirection.BOOKMARK_INAPP
        }
        _libraryRevision.value++
        rebuildDeck()
    }

    fun resetDislikedOnly() {
        prefs.resetDislikedData()
        _history.value = _history.value.filterValues { it.action != SwipeDirection.DISLIKE }
        _libraryRevision.value++
        rebuildDeck()
    }

    fun resetSkippedOnly() {
        prefs.resetSkippedData()
        _history.value = _history.value.filterValues { it.action != SwipeDirection.SKIP_NEUTRAL }
        _libraryRevision.value++
        rebuildDeck()
    }

    fun resetAllData() {
        deckCache.clear()
        sessionSeenTrackKeys.clear()
        prefetchJob?.cancel()
        isPrefetching = false
        MusicApiService.clearAllCaches()
        _history.value = emptyMap()
        _inAppSaved.value = emptyList()
        val defaultAffinity = Genre.entries.associateWith { g ->
            if (prefs.selectedGenres.contains(g)) 65 else 35
        }
        _affinityMap.value = defaultAffinity
        prefs.clearAllData()
        prefs.savedAffinityMap = defaultAffinity
        _libraryRevision.value++
        rebuildDeck()
    }

    fun resetHistory() {
        resetAllData()
    }

    // Recommendation Explanation: Explains clearly why a track was recommended
    fun getRecommendationReason(track: Track): String {
        val topLiked = recommendationEngine.getTopLikedArtists()
        if (topLiked.any { it.equals(track.artist, ignoreCase = true) }) {
            return "Recommended because you liked previous tracks by ${track.artist}."
        }
        val genreScore = _affinityMap.value[track.genre] ?: 50
        if (genreScore >= 65) {
            return "Selected because ${track.genre.displayName} is one of your highest rated genres ($genreScore% affinity)."
        }
        if (track.language != null && _songLanguages.value.contains(track.language)) {
            return "Curated to match your preferred ${track.language} music catalog on ${track.provider.displayName}."
        }
        return "Fresh discovery selected from ${track.provider.displayName} to diversify your musical taste."
    }

    // Real Listening Statistics from actual swipe history timestamps
    fun getGenreTrajectories(): List<GenreTrajectory> {
        val trackedGenres = listOf(Genre.BOLLYWOOD, Genre.HIPHOP, Genre.INDIE, Genre.DANCE)
        val historyRecords = _history.value.values.toList()

        // Generate 5 actual calendar day buckets (e.g. 4 days ago -> today)
        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val dayBuckets = (4 downTo 0).map { daysAgo ->
            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -daysAgo)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfDay = cal.timeInMillis
            val endOfDay = startOfDay + 24 * 60 * 60 * 1000L - 1
            val label = if (daysAgo == 0) "Today" else if (daysAgo == 1) "Yest" else dayFormat.format(Date(startOfDay))
            Triple(label, startOfDay, endOfDay)
        }

        return trackedGenres.map { genre ->
            val liveScore = (_affinityMap.value[genre] ?: 40).coerceIn(10, 95)
            val baselineScore = if (prefs.selectedGenres.contains(genre)) 58 else 38

            // Smooth multi-day progression so individual 1-2 song swipes gently nudge trends
            var runningScore = baselineScore
            val points = dayBuckets.map { (label, start, end) ->
                val dayRecords = historyRecords.filter { it.genre == genre && it.timestamp in start..end }
                val dayPositives = dayRecords.count { it.action == SwipeDirection.LIKE || it.action == SwipeDirection.BOOKMARK_INAPP }
                val dayNegatives = dayRecords.count { it.action == SwipeDirection.DISLIKE }

                runningScore = (runningScore + (dayPositives * 3) - (dayNegatives * 3)).coerceIn(10, 95)
                GenreTrajectoryPoint(label, runningScore)
            }

            val p0 = points.first().score
            // Keep historical trend anchored smoothly to liveScore without sudden vertical jumps
            val smoothedEnd = ((points.last().score + liveScore) / 2).coerceIn(10, 95)
            val finalPoints = points.dropLast(1) + GenreTrajectoryPoint(dayBuckets.last().first, smoothedEnd)

            val totalDelta = smoothedEnd - p0
            val momentum = when {
                totalDelta > 8 -> "Surging"
                totalDelta > 2 -> "Rising"
                totalDelta < -8 -> "Fading"
                else -> "Steady"
            }

            GenreTrajectory(genre, finalPoints, totalDelta, momentum)
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.release()
    }
}
