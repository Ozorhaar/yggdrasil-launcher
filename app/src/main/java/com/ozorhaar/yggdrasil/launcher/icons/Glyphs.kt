package com.ozorhaar.yggdrasil.launcher.icons

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.ozorhaar.yggdrasil.launcher.R
import com.ozorhaar.yggdrasil.launcher.data.AppEntry

/**
 * Yggdrasil glyphs carved in place of an app's own icon, for apps that bring no themed
 * (monochrome) icon or can't be rebuilt. Each entry also picks the shield's face colour.
 * Apps are matched by package; a few are also matched by their label, for apps whose
 * package differs between carriers or regions (visual voicemail). Pinned shortcuts are matched by
 * their own label (the home-screen "Calendar" is a shortcut into Nine's calendar).
 * The glyph drawables come from tools/glyphs.py.
 */
object Glyphs {
    private data class Glyph(val res: Int?, val face: Int)

    private const val GOLD = 0xFFC8A25A.toInt()
    private const val FROST = 0xFF8FC4E0.toInt()
    private const val EMBER = 0xFFE0703A.toInt()
    private const val BONE = 0xFFE8DCC4.toInt()
    private const val OAK = 0xFFA67C4E.toInt()

    private val BY_PACKAGE = mapOf(
        // texting stays on Google/Samsung Messages for RCS: they get Muninn's raven
        "com.google.android.apps.messaging" to Glyph(R.drawable.glyph_muninn, GOLD),
        "com.samsung.android.messaging" to Glyph(R.drawable.glyph_muninn, GOLD),
        // Huginn and Mimir carve their own glyphs; only their shields are pinned to gold
        "com.ozorhaar.yggdrasil.phone" to Glyph(null, GOLD),
        "com.ozorhaar.yggdrasil.contacts" to Glyph(null, GOLD),
        "com.samsung.android.calendar" to Glyph(R.drawable.glyph_calendar, BONE),
        "com.sec.android.app.popupcalculator" to Glyph(R.drawable.glyph_calculator, BONE),
        "com.sec.android.gallery3d" to Glyph(R.drawable.glyph_gallery, FROST),
        "com.ninefolders.hd3" to Glyph(R.drawable.glyph_mail, FROST),
        "com.scopely.monopolygo" to Glyph(R.drawable.glyph_dice, EMBER),
        "bbl.intl.bambulab.com" to Glyph(R.drawable.glyph_anvil, OAK),
        "com.samsung.vvm" to Glyph(R.drawable.glyph_voicemail, OAK),
        "com.tmobile.vvm.application" to Glyph(R.drawable.glyph_voicemail, OAK),
    )

    private val BY_LABEL = mapOf(
        "visual voicemail" to Glyph(R.drawable.glyph_voicemail, OAK),
        "voicemail" to Glyph(R.drawable.glyph_voicemail, OAK),
        "nine" to Glyph(R.drawable.glyph_mail, FROST),
        "monopoly go!" to Glyph(R.drawable.glyph_dice, EMBER),
        "monopoly go" to Glyph(R.drawable.glyph_dice, EMBER),
        "bambu handy" to Glyph(R.drawable.glyph_anvil, OAK),
    )

    private val BY_SHORTCUT_LABEL = mapOf(
        "calendar" to Glyph(R.drawable.glyph_calendar, BONE),
    )

    // apps with more than one home-screen entry: matched by the entry's own activity
    private val BY_ACTIVITY = mapOf(
        "com.ninefolders.hd3.activity.CalendarAliasActivity" to Glyph(R.drawable.glyph_calendar, BONE),
    )

    private fun find(app: AppEntry): Glyph? {
        val label = app.label.trim().lowercase()
        return if (app.isShortcut) BY_SHORTCUT_LABEL[label]
        else BY_ACTIVITY[app.component.className] ?: BY_PACKAGE[app.packageName] ?: BY_LABEL[label]
    }

    fun drawable(ctx: Context, app: AppEntry): Drawable? =
        find(app)?.res?.let { ContextCompat.getDrawable(ctx, it) }

    fun face(app: AppEntry): Int? = find(app)?.face
}
