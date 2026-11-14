package com.chandyou.tunelyx.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.drawable.Drawable
import android.provider.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shared pure-Kotlin album artwork color extraction, LRU ambient cache,
 * restrained dock surface tint blending, and reduced-motion detection.
 */
object ArtworkAmbientHelper {

    private const val MAX_CACHE_SIZE = 60
    private val colorCache = LinkedHashMap<String, Color>()

    @Synchronized
    fun getCachedColor(key: String?): Color? {
        if (key.isNullOrBlank()) return null
        return colorCache[key]
    }

    @Synchronized
    fun putCachedColor(key: String?, color: Color) {
        if (key.isNullOrBlank()) return
        if (colorCache.size >= MAX_CACHE_SIZE && !colorCache.containsKey(key)) {
            val iterator = colorCache.keys.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
        colorCache[key] = color
    }

    @Synchronized
    fun clearCache() {
        colorCache.clear()
    }

    /**
     * Checks Android's system animator duration scale so reduced-motion users
     * get instant transitions instead of forced spatial/scale animations.
     */
    fun isReducedMotionEnabled(context: Context): Boolean {
        return try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Extracts a dominant chromatic color from a software-rendered Drawable using
     * 12-hue-bucket quantization so multi-colored covers keep accurate saturation.
     */
    suspend fun extractDominantColor(
        drawable: Drawable,
        cacheKey: String? = null
    ): Color? {
        getCachedColor(cacheKey)?.let { return it }

        val extracted = withContext(Dispatchers.Default) {
            try {
                val sampleSize = 16
                val bmp = Bitmap.createBitmap(sampleSize, sampleSize, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = AndroidCanvas(bmp)
                    drawable.setBounds(0, 0, sampleSize, sampleSize)
                    drawable.draw(canvas)

                    val bucketCount = 12
                    val bucketR = FloatArray(bucketCount)
                    val bucketG = FloatArray(bucketCount)
                    val bucketB = FloatArray(bucketCount)
                    val bucketWeight = FloatArray(bucketCount)
                    val hsv = FloatArray(3)

                    var fallbackR = 0f
                    var fallbackG = 0f
                    var fallbackB = 0f
                    var fallbackWeight = 0f

                    for (y in 0 until sampleSize) {
                        for (x in 0 until sampleSize) {
                            val px = bmp.getPixel(x, y)
                            if (android.graphics.Color.alpha(px) < 128) continue
                            val r = android.graphics.Color.red(px)
                            val g = android.graphics.Color.green(px)
                            val b = android.graphics.Color.blue(px)
                            android.graphics.Color.RGBToHSV(r, g, b, hsv)
                            val hue = hsv[0]
                            val sat = hsv[1]
                            val value = hsv[2]
                            if (value < 0.12f || value > 0.96f) continue

                            fallbackR += r
                            fallbackG += g
                            fallbackB += b
                            fallbackWeight += 1f

                            if (sat >= 0.18f) {
                                val bucket = ((hue / 360f) * bucketCount).toInt().coerceIn(0, bucketCount - 1)
                                val w = (sat * sat) * (0.4f + value * 0.6f)
                                bucketR[bucket] += r * w
                                bucketG[bucket] += g * w
                                bucketB[bucket] += b * w
                                bucketWeight[bucket] += w
                            }
                        }
                    }

                    var bestIdx = -1
                    var bestWeight = 0f
                    for (i in 0 until bucketCount) {
                        if (bucketWeight[i] > bestWeight) {
                            bestWeight = bucketWeight[i]
                            bestIdx = i
                        }
                    }

                    if (bestIdx >= 0 && bestWeight > 0f) {
                        Color(
                            red = (bucketR[bestIdx] / bestWeight / 255f).coerceIn(0f, 1f),
                            green = (bucketG[bestIdx] / bestWeight / 255f).coerceIn(0f, 1f),
                            blue = (bucketB[bestIdx] / bestWeight / 255f).coerceIn(0f, 1f),
                            alpha = 1f
                        )
                    } else if (fallbackWeight > 0f) {
                        Color(
                            red = (fallbackR / fallbackWeight / 255f).coerceIn(0f, 1f),
                            green = (fallbackG / fallbackWeight / 255f).coerceIn(0f, 1f),
                            blue = (fallbackB / fallbackWeight / 255f).coerceIn(0f, 1f),
                            alpha = 1f
                        )
                    } else {
                        null
                    }
                } finally {
                    bmp.recycle()
                }
            } catch (_: Exception) {
                null
            }
        }

        if (extracted != null) {
            putCachedColor(cacheKey, extracted)
        }
        return extracted
    }

    /**
     * Produces a restrained, readable background surface color for the Immersive Dock's upper
     * section: blends from the user's chosen [paletteAccent] when no song is loaded (loading/error states),
     * and smoothly incorporates the loaded track's [ambientColor] once artwork is available.
     */
    fun computeDockUpperSurfaceColor(
        ambientColor: Color,
        isDark: Boolean,
        paletteAccent: Color = SpotifyGreen,
        hasLoadedTrack: Boolean = true
    ): Color {
        val neutralBase = if (isDark) Color(0xF0171D1A) else Color(0xF5EFFBF3)
        val paletteTint = if (isDark) {
            lerp(Color(0xFF171D1A), paletteAccent, 0.22f).copy(alpha = 0.94f)
        } else {
            val isNeutralMonochrome = paletteAccent == Color(0xFF18181B)
            if (isNeutralMonochrome) {
                Color(0xF5F1F5F9)
            } else {
                lerp(Color(0xFFF6FBF8), paletteAccent, 0.16f).copy(alpha = 0.96f)
            }
        }
        if (!hasLoadedTrack) {
            return paletteTint
        }

        val softBase = if (paletteAccent == SpotifyGreen) {
            if (isDark) Color(0xF0172B22) else Color(0xF5E5FAEE)
        } else {
            lerp(neutralBase, paletteTint, 0.55f)
        }
        val restrainedAmbient = if (isDark) {
            lerp(Color(0xFF1A2024), ambientColor, 0.30f).copy(alpha = 0.94f)
        } else {
            lerp(Color(0xFFF3FBF7), ambientColor, 0.25f).copy(alpha = 0.96f)
        }
        return lerp(softBase, restrainedAmbient, if (isDark) 0.14f else 0.12f)
    }
}
