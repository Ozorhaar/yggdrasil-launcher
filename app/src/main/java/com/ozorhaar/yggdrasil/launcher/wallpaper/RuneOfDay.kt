package com.ozorhaar.yggdrasil.launcher.wallpaper

import java.util.Calendar

/**
 * The rune of the day, carved on the runestone (hold the stone to read it): one of the Elder
 * Futhark, with the sense the old rune poems give it. Sowilo, tiwaz, algiz, othala and jera are
 * never used. Strokes are line segments (x1, y1, x2, y2) in a unit box, y down.
 */
object RuneOfDay {
    class Rune(val name: String, val glyph: String, val meaning: String, val strokes: FloatArray)

    val ALL = listOf(
        Rune("Fehu", "ᚠ", "Wealth, and what you've earned", floatArrayOf(0f, 0f, 0f, 1f, 0f, .3f, .65f, 0f, 0f, .58f, .65f, .28f)),
        Rune("Uruz", "ᚢ", "The aurochs: strength", floatArrayOf(0f, 1f, 0f, 0f, 0f, 0f, .7f, .35f, .7f, .35f, .7f, 1f)),
        Rune("Thurisaz", "ᚦ", "The thorn: a giant's force", floatArrayOf(0f, 0f, 0f, 1f, 0f, .25f, .6f, .5f, .6f, .5f, 0f, .75f)),
        Rune("Ansuz", "ᚨ", "Odin's word: wisdom spoken", floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, .6f, .3f, 0f, .3f, .6f, .6f)),
        Rune("Raidho", "ᚱ", "The ride: a journey", floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, .6f, .22f, .6f, .22f, 0f, .45f, 0f, .45f, .6f, 1f)),
        Rune("Kenaz", "ᚲ", "The torch: knowing, and craft", floatArrayOf(.6f, 0f, 0f, .5f, 0f, .5f, .6f, 1f)),
        Rune("Gebo", "ᚷ", "A gift, and what it binds", floatArrayOf(0f, 0f, 1f, 1f, 1f, 0f, 0f, 1f)),
        Rune("Wunjo", "ᚹ", "Joy", floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, .6f, .22f, .6f, .22f, 0f, .45f)),
        Rune("Hagalaz", "ᚺ", "Hail: what can't be helped", floatArrayOf(0f, 0f, 0f, 1f, 1f, 0f, 1f, 1f, 0f, .35f, 1f, .65f)),
        Rune("Naudiz", "ᚾ", "Need: endurance", floatArrayOf(.5f, 0f, .5f, 1f, .15f, .35f, .85f, .62f)),
        Rune("Isa", "ᛁ", "Ice: stillness", floatArrayOf(.5f, 0f, .5f, 1f)),
        Rune("Eihwaz", "ᛇ", "The yew: what endures", floatArrayOf(.5f, 0f, .5f, 1f, .5f, 0f, .85f, .22f, .5f, 1f, .15f, .78f)),
        Rune("Perthro", "ᛈ", "The lot-cup: chance, and what's hidden", floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, .55f, .25f, .55f, .25f, .55f, .05f, 0f, 1f, .55f, .75f, .55f, .75f, .55f, .95f)),
        Rune("Ingwaz", "ᛜ", "Ing's seed: what's stored up", floatArrayOf(.5f, 0f, 1f, .5f, 1f, .5f, .5f, 1f, .5f, 1f, 0f, .5f, 0f, .5f, .5f, 0f)),
        Rune("Dagaz", "ᛞ", "Day: dawn, a turning", floatArrayOf(0f, 0f, 0f, 1f, 1f, 0f, 1f, 1f, 0f, 0f, 1f, 1f, 1f, 0f, 0f, 1f)),
        Rune("Mannaz", "ᛗ", "Man: yourself, and your kin", floatArrayOf(0f, 0f, 0f, 1f, 1f, 0f, 1f, 1f, 0f, 0f, 1f, .45f, 1f, 0f, 0f, .45f)),
        Rune("Laguz", "ᛚ", "Water: the deep, the flow", floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, .6f, .3f)),
        Rune("Berkano", "ᛒ", "The birch: growth, beginnings", floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, .6f, .25f, .6f, .25f, 0f, .5f, 0f, .5f, .6f, .75f, .6f, .75f, 0f, 1f)),
        Rune("Ehwaz", "ᛖ", "The horse: trust between two", floatArrayOf(0f, 0f, 0f, 1f, 1f, 0f, 1f, 1f, 0f, 0f, .5f, .35f, .5f, .35f, 1f, 0f)),
    )

    /** Today's rune: a different one each day, every one in turn. */
    fun today(now: Long = System.currentTimeMillis()): Rune {
        val c = Calendar.getInstance().apply { timeInMillis = now }
        val n = c.get(Calendar.YEAR) * 400 + c.get(Calendar.DAY_OF_YEAR)
        return ALL[Math.floorMod(n * 7, ALL.size)]
    }
}
