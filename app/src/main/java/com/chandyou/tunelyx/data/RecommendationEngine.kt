package com.chandyou.tunelyx.data

import com.chandyou.tunelyx.model.ArtistAffinity
import com.chandyou.tunelyx.model.Genre
import com.chandyou.tunelyx.model.HistoryRecord
import com.chandyou.tunelyx.model.MusicProvider
import com.chandyou.tunelyx.model.SongFingerprint
import com.chandyou.tunelyx.model.SwipeDirection
import com.chandyou.tunelyx.model.Track
import com.chandyou.tunelyx.model.extractPrimaryArtist
import kotlin.random.Random

class RecommendationEngine(
    private val prefs: UserPreferencesProvider
) {
    // In-memory active affinity states initialized from persistence
    private val artistAffinities = prefs.savedArtistAffinities.toMutableMap()

    // 1. Learning Updates on User Feedback (Balanced Progression & Suppressions)
    fun onTrackInteraction(
        track: Track,
        direction: SwipeDirection,
        currentAffinityMap: MutableMap<Genre, Int>
    ) {
        val now = System.currentTimeMillis()
        val artist = track.artist.trim()
        val currentArtist = artistAffinities[artist] ?: ArtistAffinity(artistName = artist)
        val genreScore = currentAffinityMap[track.genre] ?: 40

        when (direction) {
            SwipeDirection.LIKE -> {
                // Balanced progression: +0.08 artist score, stepped genre delta
                val newScore = (currentArtist.score + 0.08).coerceAtMost(1.0)
                artistAffinities[artist] = currentArtist.copy(
                    score = newScore,
                    likeCount = currentArtist.likeCount + 1,
                    lastInteractionTime = now,
                    isSuppressed = false
                )
                val delta = when {
                    genreScore >= 80 -> 1
                    genreScore >= 60 -> 3
                    else -> 5
                }
                currentAffinityMap[track.genre] = (genreScore + delta).coerceAtMost(95)
            }
            SwipeDirection.BOOKMARK_INAPP -> {
                val newScore = (currentArtist.score + 0.05).coerceAtMost(1.0)
                artistAffinities[artist] = currentArtist.copy(
                    score = newScore,
                    likeCount = currentArtist.likeCount + 1,
                    lastInteractionTime = now,
                    isSuppressed = false
                )
                val delta = when {
                    genreScore >= 80 -> 1
                    genreScore >= 60 -> 2
                    else -> 3
                }
                currentAffinityMap[track.genre] = (genreScore + delta).coerceAtMost(95)
            }
            SwipeDirection.DISLIKE -> {
                val newDislikeCount = currentArtist.dislikeCount + 1
                val newScore = (currentArtist.score - 0.12).coerceAtLeast(-1.0)
                // 3+ dislikes or severe negative score (<= -0.60) suppresses artist for 14 days
                val shouldSuppress = newDislikeCount >= 3 || newScore <= -0.60
                artistAffinities[artist] = currentArtist.copy(
                    score = newScore,
                    dislikeCount = newDislikeCount,
                    lastInteractionTime = now,
                    isSuppressed = shouldSuppress
                )
                currentAffinityMap[track.genre] = (genreScore - 3).coerceAtLeast(10)
            }
            SwipeDirection.SKIP_NEUTRAL -> {
                // Neutral skip (7-day cooldown recorded in HistoryRecord)
            }
        }

        // Persist updated states
        prefs.savedArtistAffinities = artistAffinities
    }

    // 2. Passive Genre Decay: nudges inactive genres (>3 days) 1 point toward neutral (40)
    fun applyPassiveDecay(affinityMap: MutableMap<Genre, Int>, history: Map<String, HistoryRecord>) {
        val now = System.currentTimeMillis()
        val threeDaysMs = 3 * 24 * 60 * 60 * 1000L
        for (genre in Genre.entries) {
            val lastInteraction = history.values
                .filter { it.genre == genre }
                .maxByOrNull { it.timestamp }?.timestamp ?: 0L
            if (now - lastInteraction > threeDaysMs) {
                val current = affinityMap[genre] ?: 40
                val neutral = 40
                if (current > neutral) {
                    affinityMap[genre] = current - 1
                } else if (current < neutral) {
                    affinityMap[genre] = current + 1
                }
            }
        }
    }

    // 3. Candidate Filtering & Multi-Criteria Scoring (Weights sum to 1.00 + jitter)
    fun scoreCandidate(
        track: Track,
        history: Map<String, HistoryRecord>,
        selectedLanguages: Set<String>,
        genreAffinityMap: Map<Genre, Int>,
        alreadySelectedArtists: List<String>
    ): Double {
        val now = System.currentTimeMillis()
        val sevenDaysMs = 7 * 24 * 60 * 60 * 1000L
        val fourteenDaysMs = 14 * 24 * 60 * 60 * 1000L

        // Hard Filter 0: Blocked Artists Check
        val primaryArtist = extractPrimaryArtist(track.artist)
        if (prefs.blockedArtists.any { it.equals(track.artist.trim(), ignoreCase = true) || it.equals(primaryArtist, ignoreCase = true) }) {
            return -9999.0
        }

        // Hard Filter 0.1: Strict Audio Language Verification
        // JioSaavn supplies explicit native language tags (enforce non-blank);
        // Deezer & iTunes omit explicit language tags so unknown (null) tracks from targeted queries pass with mild priority offset.
        if (selectedLanguages.isNotEmpty()) {
            if (track.language.isNullOrBlank()) {
                if (track.provider == MusicProvider.JIOSAAVN) {
                    return -9999.0
                }
            } else {
                val matchesLanguage = selectedLanguages.any { it.equals(track.language, ignoreCase = true) }
                if (!matchesLanguage) {
                    return -9999.0
                }
                val userWantsEnglish = selectedLanguages.any { it.equals("English", ignoreCase = true) }
                if (!userWantsEnglish && track.language.equals("English", ignoreCase = true)) {
                    return -9999.0
                }
            }
        }

        // Hard Filter 0.5: Track Version Filters (Remix, Acoustic, Diff Versions, Trending)
        val textToCheck = "${track.title} ${track.album.orEmpty()}".lowercase()
        if (prefs.filterRemix && listOf("remix", "mix", "club mix", "dj", "re-mix", "mashup").any { textToCheck.contains(it) }) {
            return -9999.0
        }
        if (prefs.filterAcoustic && listOf("acoustic", "unplugged", "stripped", "piano version").any { textToCheck.contains(it) }) {
            return -9999.0
        }
        if (prefs.filterDiffVers && listOf("live", "cover", "slowed", "reverb", "tribute", "instrumental", "karaoke").any { textToCheck.contains(it) }) {
            return -9999.0
        }
        if (prefs.filterTrending && listOf("sped up", "speed up", "slowed", "reverb", "nightcore", "tiktok", "trending", "bass boosted", "slowed+reverb", "slowed & reverb").any { textToCheck.contains(it) }) {
            return -9999.0
        }

        // Hard Filter 0.8: Custom Blocked Keywords / Phrases Filter
        val fullSearchableText = "${track.title} ${track.artist} ${track.album.orEmpty()}".lowercase()
        if (prefs.blockedKeywords.isNotEmpty() && prefs.blockedKeywords.any { kw ->
                kw.isNotBlank() && fullSearchableText.contains(kw.trim().lowercase())
            }) {
            return -9999.0
        }

        // Hard Filter 1: Check history by exact ID AND cross-source normalized fuzzy matching (title, artist, album)
        val matchingHistoryRecord = history[track.id] ?: history.values.firstOrNull { record ->
            track.isSameSongAs(record)
        }
        if (matchingHistoryRecord != null) {
            // Disliked tracks are PERMANENTLY banned from discovery
            if (matchingHistoryRecord.action == SwipeDirection.DISLIKE) return -9999.0
            // Liked and bookmarked tracks are already saved in Library; hard filter so Discover deck only shows fresh songs
            if (matchingHistoryRecord.action == SwipeDirection.LIKE || matchingHistoryRecord.action == SwipeDirection.BOOKMARK_INAPP) return -9999.0
            // Skipped tracks have a strict 7-day cooldown
            if (matchingHistoryRecord.action == SwipeDirection.SKIP_NEUTRAL && (now - matchingHistoryRecord.timestamp) < sevenDaysMs) return -9999.0
        }

        // Hard Filter 2: Check artist suppression / blacklist
        val artistProfile = artistAffinities[track.artist.trim()]
        if (artistProfile?.isSuppressed == true) {
            // Un-suppress after 14 days cooldown if older
            if ((now - artistProfile.lastInteractionTime) < fourteenDaysMs) {
                return -9999.0
            }
        }

        // Feature 1: Genre Affinity (45% weight) - The core driver of musical vibe and mood
        val genreScoreRaw = (genreAffinityMap[track.genre] ?: 40).toDouble()
        val genreNormalized = (genreScoreRaw / 100.0).coerceIn(0.10, 1.0)

        // Feature 2: Artist Affinity (15% weight) - Subtle preference nudge, never dominates
        val artistNormalized = if (artistProfile != null) {
            ((artistProfile.score + 1.0) / 2.0).coerceIn(0.0, 1.0)
        } else {
            0.50 // Neutral baseline for unfamiliar artists
        }

        // Feature 3: Novelty Gradient (20% weight) - Fades gradually as user interacts more with an artist
        val totalInteractions = (artistProfile?.likeCount ?: 0) + (artistProfile?.dislikeCount ?: 0)
        val noveltyBonus = when {
            artistProfile == null -> 0.30
            totalInteractions <= 1 -> 0.20
            totalInteractions <= 3 -> 0.12
            totalInteractions <= 6 -> 0.06
            else -> 0.02
        }
        val noveltyGradient = (noveltyBonus / 0.30).coerceIn(0.0, 1.0)

        // Feature 4: Provider Diversity Bonus (10% weight) - Encourages variety across JioSaavn, Deezer, iTunes, YTM
        val candidateProvider = track.provider
        val providerDiversity: Double = if (alreadySelectedArtists.isNotEmpty()) {
            val providerCounts = alreadySelectedArtists.count { entry ->
                entry.startsWith("${candidateProvider.name}:", ignoreCase = true)
            }
            val totalWithProvider = alreadySelectedArtists.count { it.contains(":") }
            if (totalWithProvider > 0) {
                val dominantThreshold = totalWithProvider / 2
                when {
                    providerCounts == 0 -> 1.5 // Underrepresented provider gets top boost (0.15 effective)
                    providerCounts <= 1 -> 1.0 // Moderate representation gets solid boost (0.10 effective)
                    providerCounts > dominantThreshold -> 0.2 // Dominant provider gets minimal boost (0.02 effective)
                    else -> 0.8
                }
            } else {
                // Heuristic when caller supplies plain artist names: non-JioSaavn gets 1.0 (0.10), default JioSaavn gets 0.2 (0.02)
                if (candidateProvider != MusicProvider.JIOSAAVN) 1.0 else 0.2
            }
        } else {
            if (candidateProvider != MusicProvider.JIOSAAVN) 1.0 else 0.5
        }

        // Feature 5: Track Quality / Freshness Signal (5% weight) - Real songs with known duration (> 0) score higher
        val qualitySignal = if (track.durationSeconds > 0) 1.0 else 0.0

        // Feature 6: Tiny baseline (5% weight) ensuring all valid candidates have non-zero baseline floor
        val baseline = 0.05

        // Feature 7: Stochastic Jitter (+/- 0.03) for organic variance without overriding genuine preferences
        val jitter = (Random.nextDouble() - 0.5) * 0.06

        // Final Normalized Multi-Criteria Scoring Formula:
        // Weights: 0.45 (genre) + 0.15 (artist) + 0.20 (novelty) + 0.10 (provider) + 0.05 (quality) + 0.05 (baseline) = 1.00
        return (0.45 * genreNormalized) +
               (0.15 * artistNormalized) +
               (0.20 * noveltyGradient) +
               (0.10 * providerDiversity) +
               (0.05 * qualitySignal) +
               baseline +
               jitter
    }

    // 4. Multi-Armed Bandit Deck Synthesis (45% Genre Exploit, 35% Explore, 20% Wildcard)
    fun buildDeck(
        candidates: List<Track>,
        history: Map<String, HistoryRecord>,
        selectedLanguages: Set<String>,
        genreAffinityMap: Map<Genre, Int>,
        targetSize: Int = 20
    ): List<Track> {
        // Pre-filter candidates by user's chosen languages and blocked artists
        val languageFiltered = candidates.filter { track ->
            val primary = extractPrimaryArtist(track.artist)
            if (prefs.blockedArtists.any { it.equals(track.artist.trim(), ignoreCase = true) || it.equals(primary, ignoreCase = true) }) {
                return@filter false
            }
            if (selectedLanguages.isNotEmpty()) {
                val userWantsEnglish = selectedLanguages.any { it.equals("English", ignoreCase = true) }
                val lang = track.language?.trim()
                if (lang.isNullOrBlank()) {
                    if (track.provider == MusicProvider.JIOSAAVN) return@filter false
                } else {
                    val matches = selectedLanguages.any { it.equals(lang, ignoreCase = true) }
                    if (!matches) return@filter false
                    if (!userWantsEnglish && lang.equals("English", ignoreCase = true)) return@filter false
                }
            }
            true
        }

        // Pre-index blocked history entries once (O(H)) instead of nested regex loops per candidate
        val blockedHistoryIds = HashSet<String>(history.size)
        val blockedHistoryTitleIndex = HashMap<String, MutableList<SongFingerprint>>()
        val now = System.currentTimeMillis()
        val sevenDaysMs = 7 * 24 * 60 * 60 * 1000L
        for (rec in history.values) {
            if (rec.action != SwipeDirection.SKIP_NEUTRAL || (now - rec.timestamp) < sevenDaysMs) {
                blockedHistoryIds.add(rec.trackId)
                val fp = SongFingerprint.from(rec)
                if (fp.normTitle.isNotEmpty()) {
                    blockedHistoryTitleIndex.getOrPut(fp.normTitle) { mutableListOf() }.add(fp)
                }
            }
        }
        val allBlockedFingerprints = blockedHistoryTitleIndex.values.flatten()

        val eligibleCandidates = mutableListOf<Track>()
        val eligibleFingerprints = mutableListOf<SongFingerprint>()
        val eligibleExactTitles = HashSet<String>()
        for (cand in languageFiltered) {
            if (blockedHistoryIds.contains(cand.id)) continue
            val candFp = SongFingerprint.from(cand)
            val exactBucket = blockedHistoryTitleIndex[candFp.normTitle]
            val blockedByExact = exactBucket != null && exactBucket.any { it.matches(candFp) }
            val blockedBySub = !blockedByExact && candFp.normTitle.length >= 4 &&
                allBlockedFingerprints.any { it.normTitle.length >= 4 && (it.normTitle.contains(candFp.normTitle) || candFp.normTitle.contains(it.normTitle)) && it.matches(candFp) }
            if (blockedByExact || blockedBySub) continue

            val dupInEligible = if (eligibleExactTitles.contains(candFp.normTitle)) {
                eligibleFingerprints.any { it.matches(candFp) }
            } else {
                candFp.normTitle.length >= 4 && eligibleFingerprints.any { it.matches(candFp) }
            }
            if (!dupInEligible) {
                eligibleCandidates.add(cand)
                eligibleFingerprints.add(candFp)
                if (candFp.normTitle.isNotEmpty()) eligibleExactTitles.add(candFp.normTitle)
            }
        }

        val selectedArtists = mutableListOf<String>()
        val scoredList = mutableListOf<Pair<Track, Double>>()

        for (candidate in eligibleCandidates) {
            // Fix #1: Pass real history map to scoreCandidate so history-based filters & fuzzy matches are respected
            val score = scoreCandidate(candidate, history, selectedLanguages, genreAffinityMap, selectedArtists)
            if (score > -100.0) {
                scoredList.add(candidate to score)
                selectedArtists.add("${candidate.provider.name}:${candidate.artist}")
            }
        }

        if (scoredList.isEmpty()) return emptyList()

        // Sort descending by score
        val sortedCandidates = scoredList.sortedByDescending { it.second }.map { it.first }

        // Multi-Armed Bandit Partitioning (45% Exploit, 35% Explore, 20% Wildcard)
        val exploitationCount = (targetSize * 0.45).toInt().coerceAtLeast(1)
        val explorationCount = (targetSize * 0.35).toInt().coerceAtLeast(1)
        val wildcardCount = (targetSize - exploitationCount - explorationCount).coerceAtLeast(1)

        val exploitationPool = sortedCandidates.take(exploitationCount * 2).toMutableList()
        val remainingAfterExploit = sortedCandidates.drop(exploitationCount * 2)
        val explorationPool = remainingAfterExploit.take(explorationCount * 2).toMutableList()
        val wildcardPool = remainingAfterExploit.drop(explorationCount * 2).toMutableList()

        val finalDeck = mutableListOf<Track>()
        val chosenArtists = mutableListOf<String>()

        fun tryAddTrack(pool: MutableList<Track>): Boolean {
            val iterator = pool.iterator()
            while (iterator.hasNext()) {
                val candidate = iterator.next()
                val primaryArtist = extractPrimaryArtist(candidate.artist)
                val artistCount = chosenArtists.count { it.equals(primaryArtist, ignoreCase = true) }
                val isDuplicateSong = finalDeck.any { it.isSameSongAs(candidate) }

                // Strict 1-track-per-artist cap in the primary pass; zero duplicate songs
                if (!isDuplicateSong && artistCount == 0) {
                    finalDeck.add(candidate)
                    chosenArtists.add(primaryArtist)
                    iterator.remove()
                    return true
                }
            }
            return false
        }

        // Interleave with guaranteed loop termination
        var progressMade = true
        while (finalDeck.size < targetSize && progressMade) {
            progressMade = false

            // Step 1: Preferred Genre Exploitation
            if (tryAddTrack(exploitationPool)) progressMade = true
            if (finalDeck.size >= targetSize) break

            // Step 2: Fresh Exploration
            if (tryAddTrack(explorationPool)) progressMade = true
            if (finalDeck.size >= targetSize) break

            // Step 3: Preferred Genre Exploitation
            if (tryAddTrack(exploitationPool)) progressMade = true
            if (finalDeck.size >= targetSize) break

            // Step 4: Wildcard (Serendipity)
            if (tryAddTrack(wildcardPool)) progressMade = true
            if (finalDeck.size >= targetSize) break

            // Step 5: Fresh Exploration
            if (tryAddTrack(explorationPool)) progressMade = true
        }

        // Secondary Pass: Allow up to 2 tracks per artist with minimum distance separation (>= 6)
        if (finalDeck.size < targetSize) {
            val allRemaining = (exploitationPool + explorationPool + wildcardPool).toMutableList()
            for (track in allRemaining) {
                val primaryArtist = extractPrimaryArtist(track.artist)
                val artistCount = chosenArtists.count { it.equals(primaryArtist, ignoreCase = true) }
                val isDuplicateSong = finalDeck.any { it.isSameSongAs(track) }
                val lastIndexOfArtist = chosenArtists.lastIndexOf(primaryArtist)
                val distance = if (lastIndexOfArtist >= 0) chosenArtists.size - lastIndexOfArtist else 99

                if (!isDuplicateSong && artistCount < 2 && distance >= 6) {
                    finalDeck.add(track)
                    chosenArtists.add(primaryArtist)
                    if (finalDeck.size >= targetSize) break
                }
            }
        }

        // Fallback: If still under target size, fill with eligible unique songs respecting max cap of 2 per artist
        if (finalDeck.size < targetSize) {
            for (cand in eligibleCandidates) {
                val primaryArtist = extractPrimaryArtist(cand.artist)
                val artistCount = chosenArtists.count { it.equals(primaryArtist, ignoreCase = true) }
                if (!finalDeck.any { it.isSameSongAs(cand) } && artistCount < 2) {
                    finalDeck.add(cand)
                    chosenArtists.add(primaryArtist)
                    if (finalDeck.size >= targetSize) break
                }
            }
        }

        return finalDeck
    }

    // Top liked artists for query seeding (requires at least 3 likes)
    fun getTopLikedArtists(limit: Int = 3): List<String> {
        return artistAffinities.values
            .filter { it.likeCount >= 3 && it.score > 0.15 && !it.isSuppressed }
            .sortedByDescending { it.score }
            .take(limit)
            .map { it.artistName }
    }
}
