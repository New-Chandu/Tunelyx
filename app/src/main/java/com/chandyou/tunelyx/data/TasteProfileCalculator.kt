package com.chandyou.tunelyx.data

import com.chandyou.tunelyx.model.ActiveDayStat
import com.chandyou.tunelyx.model.Genre
import com.chandyou.tunelyx.model.GenreAffinityStat
import com.chandyou.tunelyx.model.GenreTrajectory
import com.chandyou.tunelyx.model.GenreTrajectoryPoint
import com.chandyou.tunelyx.model.HistoryRecord
import com.chandyou.tunelyx.model.SwipeDirection
import com.chandyou.tunelyx.model.TopArtistStat
import com.chandyou.tunelyx.model.Track
import com.chandyou.tunelyx.model.UserTasteProfile
import com.chandyou.tunelyx.model.extractPrimaryArtist
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TasteProfileCalculator {

    fun computeGenreStats(
        historyRecords: List<HistoryRecord>,
        affinityMap: Map<Genre, Int>
    ): List<GenreAffinityStat> {
        val totalAffirmative = historyRecords.count {
            it.action == SwipeDirection.LIKE || it.action == SwipeDirection.BOOKMARK_INAPP
        }

        return Genre.entries.mapNotNull { genre ->
            val genreRecords = historyRecords.filter { it.genre == genre }
            val affirmativeCount = genreRecords.count {
                it.action == SwipeDirection.LIKE || it.action == SwipeDirection.BOOKMARK_INAPP
            }
            val totalInteractions = genreRecords.size
            if (totalInteractions == 0 && affirmativeCount == 0) return@mapNotNull null

            val percentage = if (totalAffirmative > 0) {
                (affirmativeCount.toFloat() / totalAffirmative.toFloat()) * 100f
            } else if (historyRecords.isNotEmpty()) {
                (totalInteractions.toFloat() / historyRecords.size.toFloat()) * 100f
            } else 0f

            val affinityScore = affinityMap[genre] ?: 50
            GenreAffinityStat(
                genre = genre,
                affirmativeCount = affirmativeCount,
                totalInteractions = totalInteractions,
                percentage = percentage,
                affinityScore = affinityScore
            )
        }.sortedWith(
            compareByDescending<GenreAffinityStat> { it.affirmativeCount }
                .thenByDescending { it.percentage }
                .thenByDescending { it.affinityScore }
        )
    }

    fun computeTopArtists(
        historyRecords: List<HistoryRecord>,
        savedTracks: List<Track>,
        limit: Int = 5
    ): List<TopArtistStat> {
        if (historyRecords.isEmpty() && savedTracks.isEmpty()) return emptyList()

        val historyByArtist = historyRecords.filter { it.artist.isNotBlank() }
            .groupBy { extractPrimaryArtist(it.artist) }

        val savedByArtist = savedTracks.filter { it.artist.isNotBlank() }
            .groupBy { extractPrimaryArtist(it.artist) }

        val allKeys = (historyByArtist.keys + savedByArtist.keys).filter { it.isNotBlank() }.toSet()

        val displayNamesByKey = mutableMapOf<String, MutableList<String>>()
        for (record in historyRecords) {
            if (record.artist.isNotBlank()) {
                val key = extractPrimaryArtist(record.artist)
                if (key.isNotBlank()) {
                    displayNamesByKey.getOrPut(key) { mutableListOf() }.add(record.artist)
                }
            }
        }
        for (track in savedTracks) {
            if (track.artist.isNotBlank()) {
                val key = extractPrimaryArtist(track.artist)
                if (key.isNotBlank()) {
                    displayNamesByKey.getOrPut(key) { mutableListOf() }.add(track.artist)
                }
            }
        }

        return allKeys.mapNotNull { normKey ->
            val matchingHistory = historyByArtist[normKey] ?: emptyList()
            val matchingSaved = savedByArtist[normKey] ?: emptyList()

            val histLikes = matchingHistory.count {
                it.action == SwipeDirection.LIKE || it.action == SwipeDirection.BOOKMARK_INAPP
            }
            val effectiveLikes = maxOf(histLikes, matchingSaved.size)
            if (effectiveLikes <= 0) return@mapNotNull null

            val totalInteractions = maxOf(matchingHistory.size, effectiveLikes)
            val affirmativePercentage = if (totalInteractions > 0) {
                ((effectiveLikes.toFloat() / totalInteractions.toFloat()) * 100).toInt().coerceIn(1, 100)
            } else 100

            // Prefer properly capitalized/cased display name from original song metadata
            val occurrences = displayNamesByKey[normKey] ?: emptyList()
            val bestDisplayName = occurrences.firstOrNull { it != it.lowercase() }
                ?: occurrences.firstOrNull()
                ?: normKey

            val cleanedDisplayName = com.chandyou.tunelyx.model.splitArtistNames(bestDisplayName).firstOrNull() ?: bestDisplayName

            TopArtistStat(
                artistName = cleanedDisplayName,
                likesCount = effectiveLikes,
                totalInteractions = totalInteractions,
                affirmativePercentage = affirmativePercentage
            )
        }.sortedWith(
            compareByDescending<TopArtistStat> { it.likesCount }
                .thenByDescending { it.affirmativePercentage }
        ).take(limit)
    }

    fun computeActiveDays(
        historyRecords: List<HistoryRecord>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<ActiveDayStat> {
        if (historyRecords.isEmpty()) return emptyList()

        val cal = Calendar.getInstance()
        val todayCal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStart = todayCal.timeInMillis
        val yestStart = todayStart - 24 * 60 * 60 * 1000L
        val dayDateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
        val weekdayFormat = SimpleDateFormat("EEE", Locale.getDefault())

        val dayBuckets = historyRecords.groupBy { record ->
            cal.timeInMillis = record.timestamp
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }

        return dayBuckets.map { (dayStart, records) ->
            val label = when (dayStart) {
                todayStart -> "Today"
                yestStart -> "Yesterday"
                else -> "${weekdayFormat.format(Date(dayStart))}, ${dayDateFormat.format(Date(dayStart))}"
            }
            val saved = records.count {
                it.action == SwipeDirection.LIKE || it.action == SwipeDirection.BOOKMARK_INAPP
            }
            ActiveDayStat(
                dateLabel = label,
                timestamp = dayStart,
                totalSwipes = records.size,
                savedCount = saved
            )
        }.sortedByDescending { it.timestamp }.take(7)
    }

    fun computeTrajectories(
        historyRecords: List<HistoryRecord>,
        affinityMap: Map<Genre, Int>,
        selectedGenres: Set<Genre> = emptySet(),
        nowMillis: Long = System.currentTimeMillis()
    ): List<GenreTrajectory> {
        val activeDays = computeActiveDays(historyRecords, nowMillis)

        val genreStats = computeGenreStats(historyRecords, affinityMap)
        val candidateGenres = genreStats.map { it.genre }.toMutableList()
        if (candidateGenres.size < 3) {
            for (g in selectedGenres) {
                if (!candidateGenres.contains(g)) candidateGenres.add(g)
            }
        }
        if (candidateGenres.size < 3) {
            for (g in Genre.entries) {
                if (!candidateGenres.contains(g)) candidateGenres.add(g)
            }
        }
        val trackedGenres = candidateGenres.take(4)

        if (activeDays.size >= 2) {
            val chronologicalDays = activeDays.take(5).reversed()
            val dayDateFormat = SimpleDateFormat("EEE", Locale.getDefault())

            return trackedGenres.map { genre ->
                val baselineScore = if (selectedGenres.contains(genre)) 55 else 40
                var smoothedScore = baselineScore.toFloat()
                val liveScore = (affinityMap[genre] ?: 50).toFloat()

                val points = chronologicalDays.map { dayStat ->
                    val dayStart = dayStat.timestamp
                    val dayEnd = dayStart + 24 * 60 * 60 * 1000L - 1
                    val dayRecords = historyRecords.filter { it.genre == genre && it.timestamp in dayStart..dayEnd }
                    val positives = dayRecords.count { it.action == SwipeDirection.LIKE || it.action == SwipeDirection.BOOKMARK_INAPP }
                    val negatives = dayRecords.count { it.action == SwipeDirection.DISLIKE }

                    val rawDelta = (positives * 2.5f) - (negatives * 2.5f)
                    val targetScore = (smoothedScore + rawDelta).coerceIn(10f, 95f)
                    smoothedScore = (smoothedScore * 0.65f) + (targetScore * 0.35f)

                    val label = when (dayStat.dateLabel) {
                        "Today" -> "Today"
                        "Yesterday" -> "Yest"
                        else -> dayDateFormat.format(Date(dayStat.timestamp))
                    }
                    GenreTrajectoryPoint(label, smoothedScore.toInt().coerceIn(10, 95))
                }

                val finalPoints = if (points.isNotEmpty()) {
                    val lastPoint = points.last()
                    val smoothEnd = ((lastPoint.score * 0.60f) + (liveScore * 0.40f)).toInt().coerceIn(10, 95)
                    points.dropLast(1) + lastPoint.copy(score = smoothEnd)
                } else {
                    points
                }

                val p0 = finalPoints.firstOrNull()?.score ?: baselineScore
                val pEnd = finalPoints.lastOrNull()?.score ?: baselineScore
                val totalDelta = pEnd - p0
                val momentum = when {
                    totalDelta > 6 -> "Surging"
                    totalDelta > 2 -> "Rising"
                    totalDelta < -6 -> "Fading"
                    else -> "Steady"
                }

                GenreTrajectory(genre, finalPoints, totalDelta, momentum)
            }
        } else {
            return trackedGenres.map { genre ->
                val baseline = if (selectedGenres.contains(genre)) 55 else 40
                val liveScore = (affinityMap[genre] ?: baseline).coerceIn(10, 95)
                val midScore = ((baseline + liveScore) / 2).coerceIn(10, 95)
                val points = listOf(
                    GenreTrajectoryPoint("Start", baseline),
                    GenreTrajectoryPoint("Mid", midScore),
                    GenreTrajectoryPoint("Now", liveScore)
                )
                val delta = liveScore - baseline
                val momentum = when {
                    delta > 5 -> "Rising"
                    delta < -5 -> "Fading"
                    else -> "Steady"
                }
                GenreTrajectory(genre, points, delta, momentum)
            }
        }
    }

    fun computeTasteProfile(
        historyRecords: List<HistoryRecord>,
        savedTracks: List<Track>,
        totalSwipesCount: Int,
        affinityMap: Map<Genre, Int>,
        selectedGenres: Set<Genre> = emptySet(),
        nowMillis: Long = System.currentTimeMillis()
    ): UserTasteProfile {
        val totalExplored = totalSwipesCount.coerceAtLeast(historyRecords.size)
        val totalSaved = savedTracks.size
        val discoveryRate = if (totalExplored > 0) {
            ((totalSaved.toFloat() / totalExplored.toFloat()) * 100).toInt().coerceIn(0, 100)
        } else 0

        val genreStats = computeGenreStats(historyRecords, affinityMap)
        val topArtists = computeTopArtists(historyRecords, savedTracks)
        val activeDays = computeActiveDays(historyRecords, nowMillis)
        val trajectories = computeTrajectories(historyRecords, affinityMap, selectedGenres, nowMillis)

        return UserTasteProfile(
            totalSongsExplored = totalExplored,
            totalSongsSaved = totalSaved,
            discoveryRatePercentage = discoveryRate,
            topGenres = genreStats,
            topArtists = topArtists,
            activeDays = activeDays,
            trajectories = trajectories
        )
    }
}
