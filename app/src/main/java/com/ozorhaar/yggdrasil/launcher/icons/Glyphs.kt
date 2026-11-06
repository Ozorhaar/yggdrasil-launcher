package com.ozorhaar.yggdrasil.launcher.icons

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.ozorhaar.yggdrasil.launcher.R

/**
 * Yggdrasil glyphs carved in place of an app's own icon, for apps that can't be rebuilt
 * (Google's and Samsung's texting apps get Muninn's raven, since RCS keeps them in use).
 * Each entry also picks the shield's face colour.
 */
object Glyphs {
    private data class Glyph(val res: Int, val face: Int)

    private val GOLD = 0xFFC8A25A.toInt()

    private val BY_PACKAGE = mapOf(
        "com.google.android.apps.messaging" to Glyph(R.drawable.glyph_muninn, GOLD),
        "com.samsung.android.messaging" to Glyph(R.drawable.glyph_muninn, GOLD),
    )

    fun drawable(ctx: Context, pkg: String): Drawable? =
        BY_PACKAGE[pkg]?.let { ContextCompat.getDrawable(ctx, it.res) }

    fun face(pkg: String): Int? = BY_PACKAGE[pkg]?.face
}
