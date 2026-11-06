package com.ozorhaar.yggdrasil.launcher.search

import java.text.Normalizer

private val MARKS = Regex("\\p{Mn}+")

/** Lower-cases and drops accents, so "mimir" finds Mímir and "hafnir" finds Hafnír. */
fun String.folded(): String = Normalizer.normalize(this, Normalizer.Form.NFD).replace(MARKS, "").lowercase()

/** Accent- and case-insensitive contains (named so it never collides with String.matches(regex)). */
fun String.looselyHas(query: String): Boolean = folded().contains(query.trim().folded())

/** Accent- and case-insensitive startsWith. */
fun String.startsLike(query: String): Boolean = folded().startsWith(query.trim().folded())
