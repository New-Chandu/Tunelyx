# Tunelyx

Tunelyx is a modern, gesture-driven music discovery application built for Android with Jetpack Compose. Inspired by fluid card swiping interactions, Tunelyx delivers instant 30-second audio previews, adaptive UI controls, live taste profile analytics, and multi-service music streaming.

---

## Features

### 1. 4-Way Gesture Discovery Deck
- **Swipe Right (Like):** Saves song to Liked Library, increases artist affinity (+15 pts), and boosts genre taste trajectory (+12 pts).
- **Swipe Left (Dislike):** Bans song permanently, decreases artist affinity (-20 pts), and lowers genre trajectory (-10 pts).
- **Swipe Down (Bookmark):** Saves song to in-app bookmarks without impacting recommendation score.
- **Swipe Up (Skip):** Neutral skip with a 7-day cooldown before the song is eligible to reappear.
- **Card Peek & Depth Physics:** 3-card stack layered with organic angular offsets, elevation shadows, and spring promotion physics.
- **Tap to Expand:** Single tap toggles high-contrast song metadata (title, artist, album, duration, genre tag, and share button).

### 2. Multi-Service Music Provider Engine
- **JioSaavn:** High-fidelity regional and Bollywood music streaming.
- **YouTube Music:** Piped/Invidious and direct stream extraction with adaptive fallback.
- **Deezer:** Global catalog with 30s preview streams.
- **iTunes:** Apple Music global catalog with preview resolution.

### 3. Modular Settings & Customization
- **Appearance:**
  - True AMOLED pure black dark theme (`#000000`) and high-contrast light mode.
  - 6 Accent color palette presets (Spotify Green, Purple, Cyan Blue, Crimson Red, Amber, and Monochrome).
  - Navigation controls toggle: Adaptive Immersive Dock vs. Classic Floating Pill Bar.
  - Card aesthetics selector: Full-bleed Immersive cover art vs. Minimalist card.
- **Playback:**
  - Preview duration preferences.
  - Crossfade transition options (Off, 2s, 4s).
  - Haptic feedback vibration on gestures and button actions.
- **Services:**
  - Quick switch between active music providers.
  - Discovery preferences editor (Language filters and favorite genres).
- **Filter List:**
  - Audio filters: Toggle filtering for remixes, acoustic versions, and live recordings/covers.
  - Trending Version Suppressor: Rejects sped-up, slowed+reverb, nightcore, and bass-boosted audio.
  - Keyword Blacklist: Custom text-based filtering that bans matching tracks from ever entering the deck.
  - Blocked Artists Manager: Direct list of all blocked artists with instant unblock controls.
- **About Tunelyx:**
  - Version info and release channel.
  - Interactive "Check for Updates" function.
  - Automatic update check toggle.
  - Placeholder release updater URL.

### 4. Taste Profile & "You" Page
- **Live Affinity Trajectories:** Animated multi-line cubic bezier curves tracking genre affinities (0 to 100 pts) across a 5-day sliding window.
- **Uninterrupted Audio:** Audio continues playing seamlessly when navigating to the "You" page, supported by a persistent mini-player.
- **Non-Destructive Backup & Restore:**
  - Full JSON backup export and import.
  - Plain text track list import (`Song - Artist` lines).
  - Non-destructive merge mode that adds imported songs without wiping existing library history.

---

## Tech Stack & Architecture

- **Language:** Kotlin 2.x
- **UI Framework:** Jetpack Compose (Material 3 with custom physics-driven styling)
- **Audio Engine:** AndroidX Media3 (ExoPlayer) with prefetching and circular progress tracking
- **Architecture:** MVI / MVVM with Kotlin Coroutines and StateFlow
- **Storage:** Structured SQLite Database (`TunelyxDbHelper`) with SharedPreferences caching
- **Image Loading:** Coil Compose
- **Target SDK:** 37 (Android 15 / 16 Preview)
- **Min SDK:** 33 (Android 13)

---

## Getting Started

### Prerequisites
- Android Studio Ladybug (2024.2.1+) or newer
- JDK 17 or JDK 21 (configured as `JAVA_HOME`)
- Android SDK Platform 33+

### Building from Source

1. Clone or open the project folder in Android Studio:
   ```bash
   cd C:\Users\chand\AndroidStudioProjects\tunelyx
   ```

2. Compile Kotlin source code:
   ```powershell
   .\gradlew.bat compileDebugKotlin
   ```

3. Run the unit test suite:
   ```powershell
   .\gradlew.bat testDebugUnitTest
   ```

4. Build a Release APK:
   ```powershell
   .\gradlew.bat assembleRelease
   ```
   The compiled APK will be located at:
   `app\build\outputs\apk\release\app-release.apk`

---

## Testing

The project includes unit tests verifying recommendation logic, filtering rules, and platform URL resolution:

- **Location:** `app/src/test/java/com/chandyou/tunelyx/RecommendationEngineTest.kt`
- **Coverage:**
  - Affinity score boosting on like and penalty on dislike
  - Neutral skip 7-day cooldown enforcement
  - Blocked artists rejection
  - Trending versions filtering ("sped up", "slowed", "reverb")
  - Custom keyword blacklist filtering
  - Platform share URL generation for JioSaavn, YouTube Music, Deezer, and iTunes

Execute tests:
```powershell
.\gradlew.bat testDebugUnitTest --no-daemon
```

---

## License
Tunelyx is developed for personal and non-commercial music discovery experimentation.
