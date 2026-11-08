package com.ozorhaar.yggdrasil.launcher.wallpaper

import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/**
 * Máni's face: the moon's real phase, worked out from the clock (no network). Shared by the
 * fjord wallpaper and the Sky card. Drawn as seen from the northern hemisphere: lit from the
 * right while waxing, from the left while waning.
 */
object Moon {
    private const val SYNODIC_DAYS = 29.530588853
    /** A known new moon: 6 January 2000, 18:14 UTC. */
    private const val NEW_MOON_MS = 947_182_440_000L

    /** Where the moon is in its month: 0 = new, 0.25 = first quarter, 0.5 = full, 0.75 = last quarter. */
    fun age(now: Long = System.currentTimeMillis()): Double {
        val days = (now - NEW_MOON_MS) / 86_400_000.0
        val a = (days / SYNODIC_DAYS) % 1.0
        return if (a < 0) a + 1 else a
    }

    /** How much of the face is lit, 0..1. */
    fun lit(age: Double): Float = ((1 - cos(2 * PI * age)) / 2).toFloat()

    /** The lit part of a moon of radius [r] centred on ([cx], [cy]). */
    fun litPath(cx: Float, cy: Float, r: Float, age: Double): Path {
        val p = Path()
        val k = cos(2 * PI * age).toFloat()          // 1 new, 0 quarter, -1 full
        val rx = r * abs(k)
        val disc = RectF(cx - r, cy - r, cx + r, cy + r)
        val term = RectF(cx - rx, cy - r, cx + rx, cy + r)
        p.moveTo(cx, cy - r)
        if (age < 0.5) {
            p.arcTo(disc, -90f, 180f)                   // right limb, top to bottom
            p.arcTo(term, 90f, if (k > 0) -180f else 180f) // terminator back to the top
        } else {
            p.arcTo(disc, -90f, -180f)                  // left limb, top to bottom
            p.arcTo(term, 90f, if (k > 0) 180f else -180f)
        }
        p.close()
        return p
    }
}
