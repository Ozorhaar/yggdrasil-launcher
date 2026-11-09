package com.ozorhaar.yggdrasil.launcher.wallpaper

import android.app.WallpaperColors
import android.graphics.Canvas
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.ozorhaar.yggdrasil.launcher.weather.Weather
import kotlin.concurrent.thread

/**
 * The living fjord wallpaper. It only draws while it can be seen: ~15 frames a
 * second when something moves (northern lights, rain, snow, storm; ~8 for hearth smoke), otherwise
 * once a minute so the sky can follow the time of day. Weather every 30 minutes.
 */
private const val GOLD = 0xFFC8A25A.toInt()
private const val FROST = 0xFF8FC4E0.toInt()
private const val NIGHT = 0xFF0B1220.toInt()

class FjordWallpaper : WallpaperService() {

    override fun onCreateEngine(): Engine = FjordEngine()

    inner class FjordEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private var scene: FjordScene? = null
        private var visible = false
        private var width = 0
        private var height = 0
        private var xOffset = 0.5f
        private var lastWeather = 0L
        private var lastHouse = 0L
        private val tick = Runnable { drawFrame() }
        private val prefs by lazy { applicationContext.getSharedPreferences("weather", MODE_PRIVATE) }
        /** The launcher's "Show me the sky" button writes a start time here. */
        private val demoListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == DEMO_PREF) {
                scene?.demoStart = p.getLong(DEMO_PREF, 0L)
                if (visible) drawFrame()
            }
            if (key == Birthday.PREVIEW_PREF && visible) drawFrame()
            if (key == FLARE_PREF) {
                scene?.flareAt = p.getLong(FLARE_PREF, 0L)
                lastHouse = 0L                       // read the house again too
                if (visible) drawFrame()
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            setOffsetNotificationsEnabled(true)
            scene = FjordScene(applicationContext).also { sc ->
                val start = prefs.getLong(DEMO_PREF, 0L)
                if (System.currentTimeMillis() - start < DEMO_SECONDS * 1000) sc.demoStart = start
            }
            prefs.registerOnSharedPreferenceChangeListener(demoListener)
            notifyColorsChanged()
        }

        /**
         * The colours Android builds its palette from (Samsung's "Color palette", Material You).
         * Reporting Yggdrasil gold, frost and night, with a dark-theme hint, lets apps that follow
         * the palette, Google Messages among them, take on the Yggdrasil look.
         */
        override fun onComputeColors(): WallpaperColors = WallpaperColors(
            Color.valueOf(GOLD), Color.valueOf(FROST), Color.valueOf(NIGHT),
            WallpaperColors.HINT_SUPPORTS_DARK_THEME,
        )

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            // coming back into view: read the house again (at most every 30 s)
            if (v && System.currentTimeMillis() - lastHouse > 30_000L) lastHouse = 0L
            if (v) drawFrame() else handler.removeCallbacks(tick)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            super.onSurfaceChanged(holder, format, w, h)
            width = w; height = h
            drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(tick)
            super.onSurfaceDestroyed(holder)
        }

        override fun onOffsetsChanged(xOff: Float, yOff: Float, xStep: Float, yStep: Float, xPx: Int, yPx: Int) {
            if (xOff == xOffset) return
            xOffset = xOff
            if (scene?.animating != true) drawFrame()
        }

        override fun onDestroy() {
            handler.removeCallbacks(tick)
            prefs.unregisterOnSharedPreferenceChangeListener(demoListener)
            scene?.release()
            scene = null
            super.onDestroy()
        }

        private fun refreshWeather() {
            val now = System.currentTimeMillis()
            if (now - lastWeather < 30 * 60_000L) return
            lastWeather = now
            thread {
                val w = Weather.get(applicationContext)
                val kp = Weather.kp(applicationContext)
                handler.post {
                    scene?.weather = w
                    scene?.kp = kp
                    if (visible) drawFrame()
                }
            }
        }

        /** The steading on the shore follows the house: read from Home Assistant every 2 minutes while seen. */
        private fun refreshHouse() {
            val now = System.currentTimeMillis()
            if (now - lastHouse < 2 * 60_000L) return
            lastHouse = now
            val link = (applicationContext as com.ozorhaar.yggdrasil.launcher.YggApp).homeLink
            if (!link.configured) return
            thread {
                val h = runCatching { link.glance() }.getOrNull() ?: return@thread
                handler.post {
                    if (scene?.house != h) {
                        scene?.house = h
                        if (visible) drawFrame()
                    }
                }
            }
        }

        private fun drawFrame() {
            handler.removeCallbacks(tick)
            val sc = scene ?: return
            if (!visible || width == 0) return
            val holder = surfaceHolder
            var c: Canvas? = null
            try {
                c = try { holder.lockHardwareCanvas() } catch (_: Exception) { holder.lockCanvas() }
                if (c != null) sc.draw(c, width, height, xOffset, SystemClock.uptimeMillis())
            } finally {
                if (c != null) runCatching { holder.unlockCanvasAndPost(c) }
            }
            refreshWeather()
            refreshHouse()
            handler.postDelayed(tick, sc.frameMs)
        }
    }
}
