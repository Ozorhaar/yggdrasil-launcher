package com.ozorhaar.yggdrasil.launcher.icons

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.ozorhaar.yggdrasil.launcher.R
import com.ozorhaar.yggdrasil.launcher.data.AppEntry

/**
 * Yggdrasil glyphs carved in place of an app's own icon, for apps that bring no themed
 * (monochrome) icon or can't be rebuilt, and the accent colour of each app's shield ring.
 * Every shield face is gold; frost, ember and bone only ever appear as the thin ring.
 *
 * Apps are matched by package; apps with several home-screen entries by the entry's activity;
 * pinned shortcuts by their own label (the home-screen "Calendar" is a shortcut into Nine's
 * calendar, "Assist" one into Home Assistant). The glyph drawables come from tools/glyphs.py.
 */
object Glyphs {
    private data class Glyph(val res: Int?, val accent: Int)

    const val FROST = 0xFF8FC4E0.toInt()
    const val EMBER = 0xFFE0703A.toInt()
    const val BONE = 0xFFE8DCC4.toInt()

    private val BY_PACKAGE = mapOf(
        // texting stays on Google/Samsung Messages for RCS: they get Muninn's raven, ember like its seal
        "com.google.android.apps.messaging" to Glyph(R.drawable.glyph_muninn, EMBER),
        "com.samsung.android.messaging" to Glyph(R.drawable.glyph_muninn, EMBER),
        // Huginn and Mimir carve their own monochrome layers; only their rings are set here
        "com.ozorhaar.yggdrasil.phone" to Glyph(null, BONE),
        "com.ozorhaar.yggdrasil.contacts" to Glyph(null, FROST),
        "com.samsung.android.calendar" to Glyph(R.drawable.glyph_calendar, BONE),
        "com.sec.android.app.popupcalculator" to Glyph(R.drawable.glyph_calculator, BONE),
        "com.sec.android.gallery3d" to Glyph(R.drawable.glyph_gallery, FROST),
        "com.ninefolders.hd3" to Glyph(R.drawable.glyph_mail, FROST),
        "com.scopely.monopolygo" to Glyph(R.drawable.glyph_dice, EMBER),
        "bbl.intl.bambulab.com" to Glyph(R.drawable.glyph_anvil, EMBER),
        "com.samsung.vvm" to Glyph(R.drawable.glyph_voicemail, BONE),
        "com.tmobile.vvm.application" to Glyph(R.drawable.glyph_voicemail, BONE),
        "com.brave.browser" to Glyph(R.drawable.glyph_helm, EMBER),
        "io.homeassistant.companion.android" to Glyph(R.drawable.glyph_longhouse, BONE),
        "com.sense.androidclient" to Glyph(R.drawable.glyph_mjolnir, FROST),
    )

    // apps with more than one home-screen entry: matched by the entry's own activity
    private val BY_ACTIVITY = mapOf(
        "com.ninefolders.hd3.activity.CalendarAliasActivity" to Glyph(R.drawable.glyph_calendar, BONE),
    )

    private val BY_LABEL = mapOf(
        "visual voicemail" to Glyph(R.drawable.glyph_voicemail, BONE),
        "voicemail" to Glyph(R.drawable.glyph_voicemail, BONE),
    )

    private val BY_SHORTCUT_LABEL = mapOf(
        "calendar" to Glyph(R.drawable.glyph_calendar, BONE),
        "assist" to Glyph(R.drawable.glyph_assist, FROST),
    )

    private fun find(app: AppEntry): Glyph? {
        val label = app.label.trim().lowercase()
        return if (app.isShortcut) BY_SHORTCUT_LABEL[label]
        else BY_ACTIVITY[app.component.className] ?: BY_PACKAGE[app.packageName] ?: BY_LABEL[label]
    }

    fun drawable(ctx: Context, app: AppEntry): Drawable? =
        find(app)?.res?.let { ContextCompat.getDrawable(ctx, it) }

    fun accent(app: AppEntry): Int? = find(app)?.accent
}
