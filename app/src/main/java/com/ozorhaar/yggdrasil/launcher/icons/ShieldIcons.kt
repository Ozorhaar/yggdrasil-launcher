package com.ozorhaar.yggdrasil.launcher.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.ozorhaar.yggdrasil.launcher.data.AppEntry
import kotlin.math.abs

/**
 * Renders every app as a round longship shield with its glyph "carved" in dark
 * iron. Apps that ship a monochrome (themed) icon get a clean carved glyph; the
 * rest get their own icon inlaid in the shield, toned down to match.
 */
class ShieldIcons(
    private val sizePx: Int,
    private val glyphOf: (String) -> Drawable? = { null },
    private val iconOf: (AppEntry) -> Drawable?,
) {

    private val cache = LruCache<String, ImageBitmap>(400)

    fun get(app: AppEntry): ImageBitmap {
        // Keyed by the installed copy too (its path changes on every update), so an app that
        // changes its icon gets a freshly carved shield instead of the one cached before.
        val k = app.key + "@" + (app.info?.applicationInfo?.sourceDir ?: "")
        return cache.get(k) ?: render(app).also { cache.put(k, it) }
    }

    fun clear() = cache.evictAll()

    private fun render(app: AppEntry): ImageBitmap {
        val s = sizePx.toFloat()
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = s / 2f
        val fill = Glyphs.face(app.packageName) ?: SHIELDS[abs(app.packageName.hashCode()) % SHIELDS.size]

        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // shield face
        p.style = Paint.Style.FILL
        p.color = fill
        c.drawCircle(cx, cx, s * 0.455f, p)
        // dark rim
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.045f
        p.color = NIGHT
        c.drawCircle(cx, cx, s * 0.455f, p)
        // inner groove
        p.strokeWidth = s * 0.022f
        p.color = 0x2E000000
        c.drawCircle(cx, cx, s * 0.375f, p)

        val icon: Drawable? = try {
            iconOf(app)
        } catch (_: Exception) {
            null
        }
        // a Yggdrasil glyph for this app wins over its own monochrome layer
        val glyph = if (app.isShortcut) null else try { glyphOf(app.packageName) } catch (_: Exception) { null }
        val mono = glyph ?: if (Build.VERSION.SDK_INT >= 33 && icon is AdaptiveIconDrawable) icon.monochrome else null

        if (mono != null) {
            val m = mono.mutate()
            val b = (s * 1.22f).toInt()
            val off = ((s - b) / 2f).toInt()
            m.setBounds(off, off, off + b, off + b)
            m.setTint(CARVE)
            m.draw(c)
        } else if (icon != null) {
            // inlay: the app's own icon, desaturated, clipped to a disc
            val save = c.save()
            val clip = Path().apply { addCircle(cx, cx, s * 0.33f, Path.Direction.CW) }
            c.clipPath(clip)
            val d = icon.mutate()
            val inset = if (icon is AdaptiveIconDrawable) -(s * 0.10f).toInt() else (s * 0.16f).toInt()
            d.setBounds(inset, inset, sizePx - inset, sizePx - inset)
            d.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0.35f) })
            d.draw(c)
            c.restoreToCount(save)
            p.style = Paint.Style.STROKE
            p.strokeWidth = s * 0.02f
            p.color = NIGHT
            c.drawCircle(cx, cx, s * 0.33f, p)
        }
        return bmp.asImageBitmap()
    }

    companion object {
        const val NIGHT = 0xFF0B1220.toInt()
        const val CARVE = 0xFF141A26.toInt()
        val SHIELDS = intArrayOf(
            0xFFC8A25A.toInt(), // gold
            0xFF8FC4E0.toInt(), // frost
            0xFFE0703A.toInt(), // ember
            0xFFE8DCC4.toInt(), // bone
            0xFFA67C4E.toInt(), // oak
        )
    }
}
