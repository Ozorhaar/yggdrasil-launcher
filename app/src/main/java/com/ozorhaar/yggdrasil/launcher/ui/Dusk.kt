package com.ozorhaar.yggdrasil.launcher.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.ozorhaar.yggdrasil.launcher.weather.Weather
import kotlinx.coroutines.delay
import java.util.Calendar

/**
 * Day and night for the launcher's metal: from sunset to sunrise the gold of the shields,
 * folder frames, dock band and card frames darkens to bronze, matching the fjord at night.
 * Sunrise and sunset come from the weather reading the Sky card and the wallpaper share.
 */
object Dusk {
    const val GOLD_INT = 0xFFC8A25A.toInt()
    const val BRONZE_INT = 0xFF9C7B43.toInt()

    var night by mutableStateOf(false)
        private set

    /** The metal colour right now. Reading it from a composable follows the change. */
    val gold: Color get() = if (night) Color(BRONZE_INT) else Ygg.Gold
    val goldInt: Int get() = if (night) BRONZE_INT else GOLD_INT

    fun update(ctx: Context, now: Long = System.currentTimeMillis()) {
        val w = Weather.cached(ctx)
        night = if (w != null && w.sunrise > 0 && w.sunset > w.sunrise) {
            // today's times, shifted onto today's date if the reading is from an earlier day
            val dayMs = 24 * 3600 * 1000L
            val days = Math.floorDiv(now - w.sunrise, dayMs)
            val rise = w.sunrise + days * dayMs
            val set = w.sunset + days * dayMs
            now < rise || now >= set
        } else {
            val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            h < 7 || h >= 18
        }
    }
}

/** Keeps [Dusk] current while the home screen is composed (checked every minute). */
@Composable
fun DuskClock() {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        while (true) {
            Dusk.update(ctx)
            delay(60_000L)
        }
    }
}
