package com.ozorhaar.yggdrasil.launcher.data

import android.content.Context

/**
 * How often each app is opened from the launcher, kept on the phone only. Older use fades (each
 * count halves after a month without use), so a place's peek shows what you use now.
 */
object Usage {
    private const val HALF_LIFE_MS = 30L * 24 * 3600_000

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("ygg_usage", Context.MODE_PRIVATE)

    private fun score(ctx: Context, key: String, now: Long): Double {
        val p = prefs(ctx)
        val n = p.getFloat("n:$key", 0f).toDouble()
        val at = p.getLong("t:$key", now)
        return n * Math.pow(0.5, (now - at).toDouble() / HALF_LIFE_MS)
    }

    fun bump(ctx: Context, key: String) {
        val now = System.currentTimeMillis()
        val s = score(ctx, key, now) + 1.0
        prefs(ctx).edit().putFloat("n:$key", s.toFloat()).putLong("t:$key", now).apply()
    }

    /** The [n] most-used of [keys] (in their own order when unused). */
    fun top(ctx: Context, keys: List<String>, n: Int): List<String> {
        val now = System.currentTimeMillis()
        return keys.withIndex().sortedWith(compareByDescending<IndexedValue<String>> { score(ctx, it.value, now) }.thenBy { it.index })
            .take(n).map { it.value }
    }
}
