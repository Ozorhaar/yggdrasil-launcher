package com.ozorhaar.yggdrasil.launcher.wallpaper

import android.content.Context
import java.util.Calendar

/**
 * Jason's name-day, 1 January: the steading keeps a feast. Fireworks over the fjord from midnight
 * until five, the hall and the longship dressed all day, Bifröst across the daytime sky, a raven
 * with a rune-stick on the roof, and a saga of his own in the morning.
 *
 * "Preview the birthday" in the steading settings turns it all on for a minute.
 */
object Birthday {
    const val NAME = "Jason"
    private const val MONTH = Calendar.JANUARY
    private const val DAY = 1
    /** Written by the preview button (in the wallpaper's prefs, so it redraws at once). */
    const val PREVIEW_PREF = "fjord_birthday_preview"
    private const val PREVIEW_MS = 75_000L

    private fun previewing(ctx: Context): Boolean {
        val at = ctx.applicationContext.getSharedPreferences("weather", Context.MODE_PRIVATE).getLong(PREVIEW_PREF, 0L)
        return System.currentTimeMillis() - at in 0..PREVIEW_MS
    }

    private fun cal(now: Long) = Calendar.getInstance().apply { timeInMillis = now }

    /** All of 1 January (or a preview). */
    fun today(ctx: Context, now: Long = System.currentTimeMillis()): Boolean {
        val c = cal(now)
        return (c.get(Calendar.MONTH) == MONTH && c.get(Calendar.DAY_OF_MONTH) == DAY) || previewing(ctx)
    }

    /** The first hours of 1 January, from midnight until five (or a preview): fireworks. */
    fun fireworks(ctx: Context, now: Long = System.currentTimeMillis()): Boolean {
        val c = cal(now)
        return (c.get(Calendar.MONTH) == MONTH && c.get(Calendar.DAY_OF_MONTH) == DAY && c.get(Calendar.HOUR_OF_DAY) < 5) || previewing(ctx)
    }

    fun preview(ctx: Context) {
        ctx.applicationContext.getSharedPreferences("weather", Context.MODE_PRIVATE).edit()
            .putLong(PREVIEW_PREF, System.currentTimeMillis()).apply()
    }

    /** A verse for the day (written for Jason, in the old alliterative manner). */
    val VERSE = "Hail, $NAME, born as the year turns, / when Sól climbs back from the long night's keeping. / " +
        "May your hall stand warm and your hearth burn bright, / your ravens fly far and bring home good tidings: / " +
        "mead for the feast, and fair wind for the year."

    /** What the raven's rune-stick says. */
    val GREETING = "From the Allfather's ravens, flown over the fjord: hail to you, $NAME, on your name-day! " +
        "The hall is dressed, the hearth is high and the mead is poured. Skål, and a year of good fortune."
}
