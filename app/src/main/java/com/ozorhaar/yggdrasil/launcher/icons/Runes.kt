package com.ozorhaar.yggdrasil.launcher.icons

import java.text.Normalizer

/**
 * The rune carved on an app's shield when it has neither a themed icon nor a Yggdrasil glyph:
 * the first letter of its name, in Elder Futhark. Sowilo, tiwaz, algiz, othala and jera are never
 * used: S, T, O and J/Y take their medieval forms (short-twig sol, short-twig tyr, oss, dotted ur),
 * and Z the medieval z.
 */
object Runes {
    private val BY_LETTER = mapOf(
        'a' to "ᚨ", 'b' to "ᛒ", 'c' to "ᚲ", 'd' to "ᛞ", 'e' to "ᛖ", 'f' to "ᚠ", 'g' to "ᚷ",
        'h' to "ᚺ", 'i' to "ᛁ", 'j' to "ᚤ", 'k' to "ᚲ", 'l' to "ᛚ", 'm' to "ᛗ", 'n' to "ᚾ",
        'o' to "ᚬ", 'p' to "ᛈ", 'q' to "ᚲ", 'r' to "ᚱ", 's' to "ᛌ", 't' to "ᛐ", 'u' to "ᚢ",
        'v' to "ᚡ", 'w' to "ᚹ", 'x' to "ᚷ", 'y' to "ᚤ", 'z' to "ᛎ",
    )

    /** ingwaz, for names that start with a digit or anything else */
    private const val OTHER = "ᛜ"

    fun forLabel(label: String): String {
        val plain = Normalizer.normalize(label, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()
        val first = plain.firstOrNull { it.isLetterOrDigit() } ?: return OTHER
        return BY_LETTER[first] ?: OTHER
    }
}
