package com.chandyou.tunelyx

import androidx.compose.ui.graphics.Color
import com.chandyou.tunelyx.data.ItunesRateLimiter
import com.chandyou.tunelyx.data.RecommendationEngine
import com.chandyou.tunelyx.data.UserPreferencesProvider
import com.chandyou.tunelyx.data.providers.JioSaavnProvider
import com.chandyou.tunelyx.data.providers.ProviderNormalizer
import com.chandyou.tunelyx.data.providers.YouTubeMusicProvider
import com.chandyou.tunelyx.ui.theme.AccentColorPreset
import com.chandyou.tunelyx.ui.theme.ArtworkAmbientHelper
import com.chandyou.tunelyx.model.ArtistAffinity
import com.chandyou.tunelyx.model.Genre
import com.chandyou.tunelyx.model.HistoryRecord
import com.chandyou.tunelyx.model.MusicProvider
import com.chandyou.tunelyx.model.SongFingerprint
import com.chandyou.tunelyx.model.SwipeDirection
import com.chandyou.tunelyx.model.Track
import com.chandyou.tunelyx.model.cleanTrackTitle
import com.chandyou.tunelyx.model.formatDuration
import com.chandyou.tunelyx.model.isSameTrack
import com.chandyou.tunelyx.model.normalizeTitle
import com.chandyou.tunelyx.model.splitArtistNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeUserPreferencesProvider(
    override val filterRemix: Boolean = false,
    override val filterAcoustic: Boolean = false,
    override val filterDiffVers: Boolean = false,
    override val filterTrending: Boolean = false,
    override val blockedArtists: Set<String> = emptySet(),
    override val blockedKeywords: Set<String> = emptySet(),
    override var savedArtistAffinities: Map<String, ArtistAffinity> = emptyMap()
) : UserPreferencesProvider

class RecommendationEngineTest {

    private lateinit var fakePrefs: FakeUserPreferencesProvider
    private lateinit var engine: RecommendationEngine

    private val defaultGenreMap = mapOf(
        Genre.BOLLYWOOD to 70,
        Genre.HIPHOP to 50,
        Genre.INDIE to 40,
        Genre.DANCE to 40,
        Genre.ACOUSTIC to 30,
        Genre.RETRO to 20,
        Genre.PUNJABI to 60
    )

    @Before
    fun setUp() {
        fakePrefs = FakeUserPreferencesProvider()
        engine = RecommendationEngine(fakePrefs)
    }

    private fun createSampleTrack(
        id: String = "t1",
        title: String = "Test Song",
        artist: String = "Arijit Singh",
        genre: Genre = Genre.BOLLYWOOD,
        language: String? = "Hindi",
        provider: MusicProvider = MusicProvider.JIOSAAVN
    ): Track {
        return Track(
            id = id,
            title = title,
            artist = artist,
            genre = genre,
            coverGradient = listOf(Color.Red, Color.Blue),
            glowColor = Color.Red,
            language = language,
            provider = provider
        )
    }

    @Test
    fun strictLanguageFilter_rejectsUnselectedAndBlankLanguage() {
        val selectedLanguages = setOf("Hindi")
        val hindiTrack = createSampleTrack(id = "1", language = "Hindi")
        val englishTrack = createSampleTrack(id = "2", language = "English")
        val blankLangTrack = createSampleTrack(id = "3", language = "")
        val nullLangTrack = createSampleTrack(id = "4", language = null)

        val hindiScore = engine.scoreCandidate(hindiTrack, emptyMap(), selectedLanguages, defaultGenreMap, emptyList())
        val englishScore = engine.scoreCandidate(englishTrack, emptyMap(), selectedLanguages, defaultGenreMap, emptyList())
        val blankScore = engine.scoreCandidate(blankLangTrack, emptyMap(), selectedLanguages, defaultGenreMap, emptyList())
        val nullScore = engine.scoreCandidate(nullLangTrack, emptyMap(), selectedLanguages, defaultGenreMap, emptyList())

        assertTrue("Hindi song should have positive score", hindiScore > 0.0)
        assertEquals("English song must be strictly rejected (-9999.0)", -9999.0, englishScore, 0.001)
        assertEquals("Blank language song must be rejected", -9999.0, blankScore, 0.001)
        assertEquals("Null language song must be rejected", -9999.0, nullScore, 0.001)
    }

    @Test
    fun dislikedTrack_permanentlyBanned() {
        val track = createSampleTrack(id = "disliked_1")
        val history = mapOf(
            track.id to HistoryRecord(
                trackId = track.id,
                action = SwipeDirection.DISLIKE,
                timestamp = System.currentTimeMillis() - 100_000_000L // Long ago
            )
        )

        val score = engine.scoreCandidate(track, history, setOf("Hindi"), defaultGenreMap, emptyList())
        assertEquals("Disliked songs must be permanently banned", -9999.0, score, 0.001)
    }

    @Test
    fun skipCooldown_blocksWithinSevenDays_allowsAfterSevenDays() {
        val trackRecentSkip = createSampleTrack(id = "skip_recent")
        val trackOldSkip = createSampleTrack(id = "skip_old")
        val now = System.currentTimeMillis()
        val twoDaysAgo = now - (2 * 24 * 60 * 60 * 1000L)
        val eightDaysAgo = now - (8 * 24 * 60 * 60 * 1000L)

        val history = mapOf(
            trackRecentSkip.id to HistoryRecord(trackRecentSkip.id, SwipeDirection.SKIP_NEUTRAL, twoDaysAgo),
            trackOldSkip.id to HistoryRecord(trackOldSkip.id, SwipeDirection.SKIP_NEUTRAL, eightDaysAgo)
        )

        val recentScore = engine.scoreCandidate(trackRecentSkip, history, setOf("Hindi"), defaultGenreMap, emptyList())
        val oldScore = engine.scoreCandidate(trackOldSkip, history, setOf("Hindi"), defaultGenreMap, emptyList())

        assertEquals("Recent skip (2d) must be in cooldown", -9999.0, recentScore, 0.001)
        assertTrue("Old skip (8d) cooldown should be expired", oldScore > 0.0)
    }

    @Test
    fun saturationPenaltyRemovedFromScoring_retainsOrganicScoresWithoutArtificialDrop() {
        val track1 = createSampleTrack(id = "1", artist = "Arijit Singh")
        val track2 = createSampleTrack(id = "2", artist = "Arijit Singh")

        val scoreFreshArtist = engine.scoreCandidate(track1, emptyMap(), setOf("Hindi"), defaultGenreMap, emptyList())
        val scoreRepeatedArtist = engine.scoreCandidate(track2, emptyMap(), setOf("Hindi"), defaultGenreMap, listOf("Arijit Singh"))

        assertTrue(
            "Repeated artist candidate should not suffer huge 0.70 saturation penalty",
            kotlin.math.abs(scoreFreshArtist - scoreRepeatedArtist) < 0.08
        )
    }

    @Test
    fun banditDeckSynthesis_terminatesGuaranteedWhenArtistsClustered() {
        // Create 20 candidates all by the exact same artist
        val singleArtistCandidates = (1..20).map { i ->
            createSampleTrack(id = "sa_$i", title = "Track $i", artist = "Arijit Singh")
        }

        // Must terminate safely and not loop indefinitely
        val deck = engine.buildDeck(
            candidates = singleArtistCandidates,
            history = emptyMap(),
            selectedLanguages = setOf("Hindi"),
            genreAffinityMap = defaultGenreMap,
            targetSize = 10
        )

        assertTrue("Deck should not be empty", deck.isNotEmpty())
        assertTrue("Deck should cap single artist representation", deck.size <= 2)
    }

    @Test
    fun crossProviderTrackDeduplication_detectsIdenticalSongs() {
        val titleA = "Kesariya (From Brahmastra)"
        val titleB = "Kesariya - Film Version"
        val artistA = "Arijit Singh, Pritam"
        val artistB = "Pritam feat. Arijit Singh"

        assertTrue("Should detect identical songs across providers", isSameTrack(titleA, artistA, titleB, artistB))

        val diffTitle = "Apna Bana Le"
        assertFalse("Should distinguish different songs by same artist", isSameTrack(titleA, artistA, diffTitle, artistA))
    }

    @Test
    fun onTrackInteraction_updatesGenreAndArtistAffinitiesCorrectly() {
        val track = createSampleTrack(artist = "Diljit Dosanjh", genre = Genre.PUNJABI)
        val affinityMap = mutableMapOf(Genre.PUNJABI to 50)

        // Like interaction increases genre and artist score (+5 at 50, +0.08 artist)
        engine.onTrackInteraction(track, SwipeDirection.LIKE, affinityMap)
        assertEquals(55, affinityMap[Genre.PUNJABI])

        // Dislike interaction decreases genre (-3) and suppresses artist at 3 dislikes or score <= -0.60
        val badTrack = createSampleTrack(artist = "Random Artist", genre = Genre.PUNJABI)
        engine.onTrackInteraction(badTrack, SwipeDirection.DISLIKE, affinityMap)
        engine.onTrackInteraction(badTrack, SwipeDirection.DISLIKE, affinityMap)

        val scoreAfterTwo = engine.scoreCandidate(badTrack, emptyMap(), setOf("Hindi"), affinityMap, emptyList())
        assertTrue("Artist should NOT be suppressed after only 2 dislikes under rebalanced threshold", scoreAfterTwo > 0.0)

        engine.onTrackInteraction(badTrack, SwipeDirection.DISLIKE, affinityMap)
        val scoreAfterThree = engine.scoreCandidate(badTrack, emptyMap(), setOf("Hindi"), affinityMap, emptyList())
        assertEquals("Artist should be suppressed after 3 dislikes", -9999.0, scoreAfterThree, 0.001)
    }

    @Test
    fun unicodeTitleDeduplication_preservesNonAsciiCharacters() {
        val hindiTitleA = "तुम ही हो (From Aashiqui 2)"
        val hindiTitleB = "तुम ही हो - Lofi"
        val hindiArtistA = "अरिजीत सिंह"
        val hindiArtistB = "अरिजीत सिंह feat. मिथुन"

        assertTrue("Should detect identical Hindi Unicode songs", isSameTrack(hindiTitleA, hindiArtistA, hindiTitleB, hindiArtistB))

        val punjabiTitleA = "ਕੀ ਹਾਲ (Official Video)"
        val punjabiTitleB = "ਕੀ ਹਾਲ"
        val punjabiArtist = "ਦਿਲਜੀਤ ਦੋਸਾਂਝ"
        assertTrue("Should detect identical Punjabi Unicode songs", isSameTrack(punjabiTitleA, punjabiArtist, punjabiTitleB, punjabiArtist))
    }

    @Test
    fun hardRepeatFilter_rejectsLikedAndBookmarkedSongsFromDiscovery() {
        val candidate = createSampleTrack(id = "cand_1", title = "Pehla Nasha", artist = "Udit Narayan")
        val history = mapOf(
            "liked_1" to HistoryRecord(
                trackId = "liked_1",
                action = SwipeDirection.LIKE,
                timestamp = System.currentTimeMillis() - 1000L,
                title = "Pehla Nasha (From Jo Jeeta Wohi Sikandar)",
                artist = "Udit Narayan & Sadhana Sargam"
            )
        )

        val score = engine.scoreCandidate(candidate, history, setOf("Hindi"), defaultGenreMap, emptyList())
        assertEquals("Liked songs must be hard filtered (-9999.0) so they never repeat in Discover deck", -9999.0, score, 0.001)
    }

    @Test
    fun blockedArtists_hardFiltersFromDiscoveryDeck() {
        val blockedPrefs = FakeUserPreferencesProvider(blockedArtists = setOf("Badshah"))
        val blockedEngine = RecommendationEngine(blockedPrefs)

        val badshahTrack = createSampleTrack(id = "b1", title = "Jugnu", artist = "Badshah")
        val score = blockedEngine.scoreCandidate(badshahTrack, emptyMap(), setOf("Hindi"), defaultGenreMap, emptyList())
        assertEquals("Blocked artist song must be strictly rejected (-9999.0)", -9999.0, score, 0.001)

        val deck = blockedEngine.buildDeck(
            candidates = listOf(badshahTrack, createSampleTrack(id = "ok1", title = "Tum Hi Ho", artist = "Arijit Singh")),
            history = emptyMap(),
            selectedLanguages = setOf("Hindi"),
            genreAffinityMap = defaultGenreMap,
            targetSize = 5
        )
        assertFalse("Deck should never contain songs by blocked artist", deck.any { it.artist == "Badshah" })
    }

    @Test
    fun formatDuration_formatsSecondsAccurately() {
        assertEquals("", formatDuration(0))
        assertEquals("0:18", formatDuration(18))
        assertEquals("4:05", formatDuration(245))
        assertEquals(Genre.BOLLYWOOD, HistoryRecord(trackId = "x", action = SwipeDirection.LIKE, timestamp = 1L).genre)
    }

    @Test
    fun itunesRateLimiter_enforcesSpacingWindowCapCooldownAndCache() {
        val limiter = ItunesRateLimiter(
            minIntervalMs = 1500L,
            maxRequestsPerWindow = 3,
            windowMs = 60_000L,
            cacheTtlMs = 10_000L,
            defaultCooldownMs = 30_000L
        )
        val t0 = 1_000_000L
        assertEquals(0L, limiter.acquireSlotDelayMs(t0))
        limiter.recordRequest(t0)

        // Immediate second request within minIntervalMs must wait remaining spacing time
        assertEquals(1000L, limiter.acquireSlotDelayMs(t0 + 500L))
        limiter.recordRequest(t0 + 1500L)
        limiter.recordRequest(t0 + 3000L)

        // 4th request inside same 60s window exceeds maxRequestsPerWindow (3) -> blocked (-1L)
        assertEquals(-1L, limiter.acquireSlotDelayMs(t0 + 5000L))

        // After 60s window expires, slots open again
        assertEquals(0L, limiter.acquireSlotDelayMs(t0 + 61_000L))

        // Trigger HTTP 429 cooldown -> blocks until cooldown elapses
        limiter.triggerCooldown(retryAfterSeconds = 20L, nowMs = t0 + 61_000L)
        assertTrue(limiter.remainingCooldownSeconds(t0 + 61_000L) >= 19L)
        assertEquals(-1L, limiter.acquireSlotDelayMs(t0 + 65_000L))

        // Query cache hit within TTL and expiry after TTL
        val sample = listOf(createSampleTrack(id = "it_1", provider = MusicProvider.ITUNES))
        limiter.putCached("test::key", sample, nowMs = t0)
        assertEquals(1, limiter.getCached("test::key", nowMs = t0 + 5_000L)?.size)
        assertEquals(null, limiter.getCached("test::key", nowMs = t0 + 15_000L))
    }

    @Test
    fun accentColorPreset_includesPureWhite() {
        assertEquals(Color(0xFFFFFFFF), AccentColorPreset.WHITE.color)
    }

    @Test
    fun splitArtistNames_separatesCombinedAndFeaturedArtists() {
        val itunesArtists = splitArtistNames(
            rawArtist = "Anirudh Ravichander & Jonita Gandhi",
            rawTitle = "Arabic Kuthu (feat. Vijay)"
        )
        assertEquals(listOf("Anirudh Ravichander", "Jonita Gandhi", "Vijay"), itunesArtists)

        val multiDelimArtists = splitArtistNames(
            rawArtist = "Arijit Singh, Pritam feat. Shilpa Rao x Badshah",
            rawTitle = "Chaleya"
        )
        assertEquals(listOf("Arijit Singh", "Pritam", "Shilpa Rao", "Badshah"), multiDelimArtists)
        assertEquals("Arabic Kuthu", cleanTrackTitle("Arabic Kuthu (feat. Vijay)"))
    }

    @Test
    fun providerNormalizer_matchesLanguageAndResolvesGenreConsistently() {
        assertTrue(
            ProviderNormalizer.matchesLanguageSelection(
                targetLanguages = setOf("Tamil"),
                explicitLanguage = null,
                title = "Arabic Kuthu",
                artist = "Anirudh Ravichander",
                album = "Beast (Tamil)"
            )
        )
        assertFalse(
            ProviderNormalizer.matchesLanguageSelection(
                targetLanguages = setOf("English"),
                explicitLanguage = "Hindi",
                title = "Kesariya",
                artist = "Arijit Singh",
                album = "Brahmastra (Hindi)"
            )
        )
        assertEquals(
            Genre.PUNJABI,
            ProviderNormalizer.resolveTrackGenre(
                requestedGenre = null,
                title = "Softly",
                artist = "Karan Aujla",
                album = "Making Memories"
            )
        )
        assertEquals(
            Genre.ACOUSTIC,
            ProviderNormalizer.resolveTrackGenre(
                requestedGenre = Genre.ACOUSTIC,
                title = "Softly",
                artist = "Karan Aujla",
                album = "Making Memories"
            )
        )
    }

    @Test
    fun musicProvider_supportedGenres_tiesChipsToPlatformCapabilities() {
        val hindiSet = setOf("Hindi")
        val englishOnly = setOf("English")

        val saavnHindi = MusicProvider.JIOSAAVN.supportedGenres(hindiSet)
        assertTrue(saavnHindi.contains(Genre.DANCE))
        assertTrue(saavnHindi.contains(Genre.HIPHOP))
        assertTrue(saavnHindi.contains(Genre.RETRO))

        val deezerHindi = MusicProvider.DEEZER.supportedGenres(hindiSet)
        assertTrue(deezerHindi.contains(Genre.DANCE))
        assertTrue(deezerHindi.contains(Genre.HIPHOP))
        assertFalse(deezerHindi.contains(Genre.RETRO))

        val itunesHindi = MusicProvider.ITUNES.supportedGenres(hindiSet)
        assertTrue(itunesHindi.contains(Genre.DANCE))
        assertTrue(itunesHindi.contains(Genre.HIPHOP))
        assertFalse(itunesHindi.contains(Genre.INDIE))
        assertFalse(itunesHindi.contains(Genre.RETRO))

        val itunesEnglish = MusicProvider.ITUNES.supportedGenres(englishOnly)
        assertFalse(itunesEnglish.contains(Genre.BOLLYWOOD))
        assertFalse(itunesEnglish.contains(Genre.PUNJABI))
        assertTrue(itunesEnglish.contains(Genre.DANCE))
        assertTrue(itunesEnglish.contains(Genre.HIPHOP))
    }

    @Test
    fun detectTrackLanguage_classifiesActualLanguageWithoutHardcodingHindi() {
        assertEquals(
            "Punjabi",
            ProviderNormalizer.detectTrackLanguage(
                explicitLanguage = null,
                title = "Softly",
                artist = "Karan Aujla",
                album = "Making Memories"
            )
        )
        assertEquals(
            "English",
            ProviderNormalizer.detectTrackLanguage(
                explicitLanguage = null,
                title = "Titanium",
                artist = "David Guetta, Sia",
                album = "Nothing but the Beat",
                extraMeta = "Electronic",
                fallbackCountry = "US"
            )
        )
        assertEquals(
            "Hindi",
            ProviderNormalizer.detectTrackLanguage(
                explicitLanguage = null,
                title = "Tum Hi Ho",
                artist = "Arijit Singh",
                album = "Aashiqui 2"
            )
        )
    }

    @Test
    fun jioSaavn_upgradesArtworkResolutionAndAudioBitrate() {
        assertEquals(
            "https://c.saavncdn.com/123/Cover-2024-500x500.jpg",
            JioSaavnProvider.upgradeSaavnArtworkUrl("http://c.saavncdn.com/123/Cover-2024-150x150.jpg")
        )
        assertEquals(
            "https://c.saavncdn.com/123/Thumb_500x500.webp",
            JioSaavnProvider.upgradeSaavnArtworkUrl("https://c.saavncdn.com/123/Thumb_50x50.webp")
        )

        // Encrypt a known JioSaavn sample URL with DES-ECB key "38346591" to verify decryption & bitrate upgrade
        val samplePlain = "http://aac.saavncdn.com/123/abc_96.mp4"
        val cipher = javax.crypto.Cipher.getInstance("DES/ECB/PKCS5Padding")
        val keySpec = javax.crypto.spec.SecretKeySpec("38346591".toByteArray(), "DES")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec)
        val encrypted = java.util.Base64.getEncoder().encodeToString(cipher.doFinal(samplePlain.toByteArray()))

        assertEquals(
            "https://aac.saavncdn.com/123/abc_320.mp4",
            JioSaavnProvider.decryptMediaUrl(encrypted, has320Kbps = true)
        )
        assertEquals(
            "https://aac.saavncdn.com/123/abc_160.mp4",
            JioSaavnProvider.decryptMediaUrl(encrypted, has320Kbps = false)
        )
    }

    @Test
    fun recommendationEngine_smoothsTasteUpdatesAndAcceptsUnlabeledDeezerTracks() {
        val affinity = mutableMapOf(Genre.HIPHOP to 50)
        val track = createSampleTrack(id = "d_1", genre = Genre.HIPHOP, provider = MusicProvider.DEEZER, language = null)

        // Single like should nudge gently (+5 at <60) instead of jumping dramatically
        engine.onTrackInteraction(track, SwipeDirection.LIKE, affinity)
        assertEquals(55, affinity[Genre.HIPHOP])

        // Neutral/unlabeled tracks from Deezer or iTunes pass discovery scoring rather than causing empty deck errors
        val score = engine.scoreCandidate(track, emptyMap(), setOf("Hindi"), affinity, emptyList())
        assertTrue("Deezer track without explicit language tag should remain eligible", score > 0.0)
    }

    @Test
    fun artworkAmbientHelper_cachesAndComputesRestrainedDockSurface() {
        ArtworkAmbientHelper.clearCache()
        val key = "https://example.com/art/cover_500.jpg"
        val crimson = Color(0.85f, 0.12f, 0.18f, 1f)

        assertEquals(null, ArtworkAmbientHelper.getCachedColor(key))
        ArtworkAmbientHelper.putCachedColor(key, crimson)
        assertEquals(crimson, ArtworkAmbientHelper.getCachedColor(key))

        val lightSurface = ArtworkAmbientHelper.computeDockUpperSurfaceColor(crimson, isDark = false)
        assertTrue("Light upper dock surface must remain light and translucent", lightSurface.green > 0.65f && lightSurface.alpha > 0.85f)

        val darkSurface = ArtworkAmbientHelper.computeDockUpperSurfaceColor(crimson, isDark = true)
        assertTrue("Dark upper dock surface must stay restrained/dark", darkSurface.green < 0.35f && darkSurface.alpha > 0.85f)

        ArtworkAmbientHelper.clearCache()
        assertEquals(null, ArtworkAmbientHelper.getCachedColor(key))
    }

    @Test
    fun songFingerprint_matchesDuplicatesCleanly() {
        val a = createSampleTrack(id = "a1", title = "Kesariya (From Brahmastra)", artist = "Arijit Singh, Pritam")
        val b = createSampleTrack(id = "b2", title = "Kesariya - Audio", artist = "Arijit Singh")
        val c = createSampleTrack(id = "c3", title = "Blinding Lights", artist = "The Weeknd")

        val fpA = SongFingerprint.from(a)
        val fpB = SongFingerprint.from(b)
        val fpC = SongFingerprint.from(c)

        assertTrue("Precomputed fingerprint should match normalized variants", fpA.matches(fpB))
        assertFalse("Unrelated songs must not collide", fpA.matches(fpC))
    }

    @Test
    fun youTubeMusicProvider_andDockPaletteFallback_workConsistently() {
        assertTrue(YouTubeMusicProvider.isYtmPreviewUri("ytm://dQw4w9WgXcQ"))
        assertFalse(YouTubeMusicProvider.isYtmPreviewUri("https://cdn.example.com/audio.mp3"))
        assertEquals("dQw4w9WgXcQ", YouTubeMusicProvider.extractVideoId("ytm://dQw4w9WgXcQ"))
        assertEquals(213, YouTubeMusicProvider.parseDurationSeconds("3:33"))
        assertEquals(
            "https://lh3.googleusercontent.com/abc=w544-h544-l90-rj",
            YouTubeMusicProvider.upgradeYtmArtworkUrl("http://lh3.googleusercontent.com/abc=w120-h120-l90-rj", "abc")
        )
        assertEquals(
            "https://i.ytimg.com/vi/xyz123/hqdefault.jpg",
            YouTubeMusicProvider.upgradeYtmArtworkUrl(null, "xyz123")
        )

        val hindiGenres = MusicProvider.YOUTUBE_MUSIC.supportedGenres(setOf("Hindi"))
        assertTrue(hindiGenres.contains(Genre.BOLLYWOOD) && hindiGenres.contains(Genre.INDIE))

        // When no song is loaded (loading/error), dock upper surface tint follows user's Settings accent palette
        val purpleSurface = ArtworkAmbientHelper.computeDockUpperSurfaceColor(
            ambientColor = Color.Green,
            isDark = false,
            paletteAccent = AccentColorPreset.PURPLE.color,
            hasLoadedTrack = false
        )
        assertTrue("Purple palette loading/error tint should shift blue/red above neutral green", purpleSurface.blue > purpleSurface.green)
    }

    @Test
    fun fix1_buildDeck_passesRealHistoryToScoreCandidate() {
        val cand1 = createSampleTrack(id = "c1", title = "Chaleya", artist = "Arijit Singh")
        val cand2 = createSampleTrack(id = "c2", title = "Tum Hi Ho", artist = "Arijit Singh")
        val history = mapOf(
            "c1" to HistoryRecord(trackId = "c1", action = SwipeDirection.DISLIKE, timestamp = System.currentTimeMillis())
        )
        val deck = engine.buildDeck(
            candidates = listOf(cand1, cand2),
            history = history,
            selectedLanguages = setOf("Hindi"),
            genreAffinityMap = defaultGenreMap,
            targetSize = 5
        )
        assertFalse("Deck should never contain disliked songs passed via history", deck.any { it.id == "c1" })
        assertTrue("Deck should contain eligible song", deck.any { it.id == "c2" })
    }

    @Test
    fun fix2_providerDiversityBonus_rewardsUnderrepresentedProviders() {
        val saavnTrack = createSampleTrack(id = "s1", provider = MusicProvider.JIOSAAVN)
        val deezerTrack = createSampleTrack(id = "d1", provider = MusicProvider.DEEZER)
        val ytmTrack = createSampleTrack(id = "y1", provider = MusicProvider.YOUTUBE_MUSIC)

        val selectedWithSaavnDominant = listOf(
            "JIOSAAVN:Artist A",
            "JIOSAAVN:Artist B",
            "JIOSAAVN:Artist C",
            "JIOSAAVN:Artist D"
        )

        // Run multiple trials to ensure difference holds over jitter (+/- 0.03)
        val saavnScores = (1..20).map { engine.scoreCandidate(saavnTrack, emptyMap(), setOf("Hindi"), defaultGenreMap, selectedWithSaavnDominant) }
        val deezerScores = (1..20).map { engine.scoreCandidate(deezerTrack, emptyMap(), setOf("Hindi"), defaultGenreMap, selectedWithSaavnDominant) }
        val ytmScores = (1..20).map { engine.scoreCandidate(ytmTrack, emptyMap(), setOf("Hindi"), defaultGenreMap, selectedWithSaavnDominant) }

        assertTrue("Deezer underrepresented provider should score higher on average than dominant JioSaavn", deezerScores.average() > saavnScores.average())
        assertTrue("YouTube Music underrepresented provider should score higher on average than dominant JioSaavn", ytmScores.average() > saavnScores.average())
    }

    @Test
    fun fix3_qualitySignal_boostsSongsWithKnownDuration() {
        val withDuration = createSampleTrack(id = "q1").copy(durationSeconds = 215)
        val zeroDuration = createSampleTrack(id = "q2").copy(durationSeconds = 0)

        val scoresWithDuration = (1..20).map { engine.scoreCandidate(withDuration, emptyMap(), setOf("Hindi"), defaultGenreMap, emptyList()) }
        val scoresZeroDuration = (1..20).map { engine.scoreCandidate(zeroDuration, emptyMap(), setOf("Hindi"), defaultGenreMap, emptyList()) }

        assertTrue("Tracks with known duration should score higher on average than zero-duration tracks", scoresWithDuration.average() > scoresZeroDuration.average())
    }

    @Test
    fun fix4_gradientNovelty_fadesGraduallyAcrossInteractionTiers() {
        val unseen = createSampleTrack(id = "n0", artist = "Unseen Artist")

        val prefsWithProfiles = FakeUserPreferencesProvider(
            savedArtistAffinities = mapOf(
                "Artist 1" to ArtistAffinity(artistName = "Artist 1", likeCount = 1, dislikeCount = 0),
                "Artist 3" to ArtistAffinity(artistName = "Artist 3", likeCount = 2, dislikeCount = 1),
                "Artist 6" to ArtistAffinity(artistName = "Artist 6", likeCount = 4, dislikeCount = 2),
                "Artist 10" to ArtistAffinity(artistName = "Artist 10", likeCount = 7, dislikeCount = 3)
            )
        )
        val customEngine = RecommendationEngine(prefsWithProfiles)

        val t0 = unseen
        val t1 = createSampleTrack(id = "n1", artist = "Artist 1")
        val t3 = createSampleTrack(id = "n3", artist = "Artist 3")
        val t6 = createSampleTrack(id = "n6", artist = "Artist 6")
        val t10 = createSampleTrack(id = "n10", artist = "Artist 10")

        fun avgScore(track: Track): Double = (1..50).map {
            customEngine.scoreCandidate(track, emptyMap(), setOf("Hindi"), defaultGenreMap, emptyList())
        }.average()

        val s0 = avgScore(t0)
        val s1 = avgScore(t1)
        val s3 = avgScore(t3)
        val s6 = avgScore(t6)
        val s10 = avgScore(t10)

        assertTrue("Novelty should fade strictly monotonically: s0 > s1", s0 > s1)
        assertTrue("Novelty should fade strictly monotonically: s1 > s3", s1 > s3)
        assertTrue("Novelty should fade strictly monotonically: s3 > s6", s3 > s6)
        assertTrue("Novelty should fade strictly monotonically: s6 > s10", s6 > s10)
    }

    @Test
    fun fix5_rebalancedLearningDeltas_progressesAcrossGenreTiers() {
        val lowAffinity = mutableMapOf(Genre.INDIE to 40)
        val midAffinity = mutableMapOf(Genre.INDIE to 65)
        val highAffinity = mutableMapOf(Genre.INDIE to 85)

        val track = createSampleTrack(genre = Genre.INDIE)

        // Like: <60 -> +5, >=60 -> +3, >=80 -> +1
        engine.onTrackInteraction(track, SwipeDirection.LIKE, lowAffinity)
        assertEquals(45, lowAffinity[Genre.INDIE])

        engine.onTrackInteraction(track, SwipeDirection.LIKE, midAffinity)
        assertEquals(68, midAffinity[Genre.INDIE])

        engine.onTrackInteraction(track, SwipeDirection.LIKE, highAffinity)
        assertEquals(86, highAffinity[Genre.INDIE])

        // Bookmark: <60 -> +3, >=60 -> +2, >=80 -> +1
        val lowB = mutableMapOf(Genre.INDIE to 40)
        val midB = mutableMapOf(Genre.INDIE to 65)
        val highB = mutableMapOf(Genre.INDIE to 85)

        engine.onTrackInteraction(track, SwipeDirection.BOOKMARK_INAPP, lowB)
        assertEquals(43, lowB[Genre.INDIE])

        engine.onTrackInteraction(track, SwipeDirection.BOOKMARK_INAPP, midB)
        assertEquals(67, midB[Genre.INDIE])

        engine.onTrackInteraction(track, SwipeDirection.BOOKMARK_INAPP, highB)
        assertEquals(86, highB[Genre.INDIE])

        // Dislike: -3
        val dislikeMap = mutableMapOf(Genre.INDIE to 50)
        engine.onTrackInteraction(track, SwipeDirection.DISLIKE, dislikeMap)
        assertEquals(47, dislikeMap[Genre.INDIE])
    }

    @Test
    fun fix6_passiveGenreDecay_nudgesInactiveGenresTowardNeutral() {
        val now = System.currentTimeMillis()
        val fourDaysAgo = now - (4 * 24 * 60 * 60 * 1000L)
        val oneDayAgo = now - (1 * 24 * 60 * 60 * 1000L)

        val affinityMap = mutableMapOf(
            Genre.BOLLYWOOD to 75,
            Genre.HIPHOP to 25,
            Genre.DANCE to 40,
            Genre.INDIE to 80
        )
        val history = mapOf(
            "b1" to HistoryRecord(trackId = "b1", action = SwipeDirection.LIKE, timestamp = fourDaysAgo, genre = Genre.BOLLYWOOD),
            "h1" to HistoryRecord(trackId = "h1", action = SwipeDirection.DISLIKE, timestamp = fourDaysAgo, genre = Genre.HIPHOP),
            "d1" to HistoryRecord(trackId = "d1", action = SwipeDirection.SKIP_NEUTRAL, timestamp = fourDaysAgo, genre = Genre.DANCE),
            "i1" to HistoryRecord(trackId = "i1", action = SwipeDirection.LIKE, timestamp = oneDayAgo, genre = Genre.INDIE)
        )

        engine.applyPassiveDecay(affinityMap, history)

        assertEquals("Inactive high genre (75) nudges down to 74", 74, affinityMap[Genre.BOLLYWOOD])
        assertEquals("Inactive low genre (25) nudges up to 26", 26, affinityMap[Genre.HIPHOP])
        assertEquals("Neutral genre (40) remains unchanged at 40", 40, affinityMap[Genre.DANCE])
        assertEquals("Recent active genre (1d old) does not decay", 80, affinityMap[Genre.INDIE])
    }

    @Test
    fun fix7_scoreJitter_boundedWithinThreePercent() {
        val track = createSampleTrack(id = "jitter_test")
        val scores = (1..100).map {
            engine.scoreCandidate(track, emptyMap(), setOf("Hindi"), defaultGenreMap, emptyList())
        }
        val minScore = scores.minOrNull() ?: 0.0
        val maxScore = scores.maxOrNull() ?: 0.0
        val delta = maxScore - minScore

        assertTrue("Score jitter must be bounded within +/-0.03 (max spread <= 0.065), actual delta: $delta", delta <= 0.065)
    }

    @Test
    fun fix8_saturationPenaltyRemovedFromScoring_buildDeckEnforcesArtistCap() {
        val track1 = createSampleTrack(id = "sp1", artist = "Arijit Singh")
        val track2 = createSampleTrack(id = "sp2", artist = "Arijit Singh")

        val score1 = engine.scoreCandidate(track1, emptyMap(), setOf("Hindi"), defaultGenreMap, emptyList())
        val score2 = engine.scoreCandidate(track2, emptyMap(), setOf("Hindi"), defaultGenreMap, listOf("Arijit Singh"))

        assertTrue("Scores must not drop by 0.70/2.50 due to saturation penalty", kotlin.math.abs(score1 - score2) < 0.08)

        val candidates = (1..10).map { i ->
            createSampleTrack(id = "rep_$i", title = "Song $i", artist = "Arijit Singh")
        }
        val deck = engine.buildDeck(
            candidates = candidates,
            history = emptyMap(),
            selectedLanguages = setOf("Hindi"),
            genreAffinityMap = defaultGenreMap,
            targetSize = 10
        )
        val arijitCount = deck.count { it.artist == "Arijit Singh" }
        assertTrue("buildDeck must cap artist representation to at most 2", arijitCount <= 2)
    }

    @Test
    fun ytmStreamUserAgent_isOculusVrUserAgent() {
        val ua = YouTubeMusicProvider.YTM_STREAM_USER_AGENT
        assertTrue("YTM stream User-Agent must be non-blank", ua.isNotBlank())
        assertTrue("YTM stream User-Agent must specify Android VR client", ua.contains("com.google.android.apps.youtube.vr.oculus"))
    }

    @Test
    fun filterTrending_blocksSpedUpAndSlowedReverb() {
        val trendingPrefs = FakeUserPreferencesProvider(filterTrending = true)
        val trendingEngine = RecommendationEngine(trendingPrefs)

        val regularTrack = createSampleTrack(id = "reg_1", title = "Tum Hi Ho", artist = "Arijit Singh")
        val spedUpTrack = createSampleTrack(id = "trend_1", title = "Tum Hi Ho (Sped Up)", artist = "Arijit Singh")
        val slowedReverbTrack = createSampleTrack(id = "trend_2", title = "Tum Hi Ho - Slowed & Reverb", artist = "Arijit Singh")
        val nightcoreTrack = createSampleTrack(id = "trend_3", title = "Tum Hi Ho (Nightcore Mix)", artist = "Arijit Singh")
        val bassBoostedTrack = createSampleTrack(id = "trend_4", title = "Tum Hi Ho [Bass Boosted]", artist = "Arijit Singh")

        val regScore = trendingEngine.scoreCandidate(regularTrack, emptyMap(), setOf("Hindi"), defaultGenreMap, emptyList())
        val spedUpScore = trendingEngine.scoreCandidate(spedUpTrack, emptyMap(), setOf("Hindi"), defaultGenreMap, emptyList())
        val slowedScore = trendingEngine.scoreCandidate(slowedReverbTrack, emptyMap(), setOf("Hindi"), defaultGenreMap, emptyList())
        val nightcoreScore = trendingEngine.scoreCandidate(nightcoreTrack, emptyMap(), setOf("Hindi"), defaultGenreMap, emptyList())
        val bassScore = trendingEngine.scoreCandidate(bassBoostedTrack, emptyMap(), setOf("Hindi"), defaultGenreMap, emptyList())

        assertTrue("Regular song should be accepted", regScore > 0.0)
        assertEquals("Sped up version must be blocked (-9999.0)", -9999.0, spedUpScore, 0.001)
        assertEquals("Slowed & reverb must be blocked (-9999.0)", -9999.0, slowedScore, 0.001)
        assertEquals("Nightcore must be blocked (-9999.0)", -9999.0, nightcoreScore, 0.001)
        assertEquals("Bass boosted must be blocked (-9999.0)", -9999.0, bassScore, 0.001)

        // When trending filter is disabled, they should be accepted
        val normalEngine = RecommendationEngine(FakeUserPreferencesProvider(filterTrending = false))
        val unblockedScore = normalEngine.scoreCandidate(spedUpTrack, emptyMap(), setOf("Hindi"), defaultGenreMap, emptyList())
        assertTrue("When trending filter is false, track is scored normally", unblockedScore > 0.0)
    }

    @Test
    fun blockedKeywords_blocksMatchingSongs() {
        val keywordPrefs = FakeUserPreferencesProvider(blockedKeywords = setOf("lofi", "remake"))
        val keywordEngine = RecommendationEngine(keywordPrefs)

        val cleanTrack = createSampleTrack(id = "k_1", title = "Kesariya", artist = "Pritam")
        val lofiTrack = createSampleTrack(id = "k_2", title = "Kesariya (Lofi Flip)", artist = "Pritam")
        val remakeTrack = createSampleTrack(id = "k_3", title = "Tip Tip Barsa Paani", artist = "Remake Queen")

        val cleanScore = keywordEngine.scoreCandidate(cleanTrack, emptyMap(), setOf("Hindi"), defaultGenreMap, emptyList())
        val lofiScore = keywordEngine.scoreCandidate(lofiTrack, emptyMap(), setOf("Hindi"), defaultGenreMap, emptyList())
        val remakeScore = keywordEngine.scoreCandidate(remakeTrack, emptyMap(), setOf("Hindi"), defaultGenreMap, emptyList())

        assertTrue("Clean track without blacklisted keywords should be scored", cleanScore > 0.0)
        assertEquals("Track with blacklisted keyword 'lofi' must be rejected (-9999.0)", -9999.0, lofiScore, 0.001)
        assertEquals("Track with blacklisted keyword 'remake' in artist must be rejected (-9999.0)", -9999.0, remakeScore, 0.001)
    }

    @Test
    fun platformShareUrl_resolvesCorrectPlatformUrls() {
        val ytmTrack = createSampleTrack(id = "yt_abc123", provider = MusicProvider.YOUTUBE_MUSIC)
        val deezerTrack = createSampleTrack(id = "987654", provider = MusicProvider.DEEZER)
        val itunesTrack = createSampleTrack(id = "it_555", title = "Shape of You", artist = "Ed Sheeran", provider = MusicProvider.ITUNES)
        val jioTrack = createSampleTrack(id = "jio_999", title = "Chaleya", artist = "Arijit Singh", provider = MusicProvider.JIOSAAVN)

        assertEquals("https://music.youtube.com/watch?v=abc123", ytmTrack.platformShareUrl())
        assertEquals("https://www.deezer.com/track/987654", deezerTrack.platformShareUrl())
        assertTrue(itunesTrack.platformShareUrl().startsWith("https://music.apple.com/search?term="))
        assertTrue(jioTrack.platformShareUrl().startsWith("https://www.jiosaavn.com/song/"))
    }
}
