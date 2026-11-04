package com.ozorhaar.yggdrasil.launcher.wallpaper

import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.ozorhaar.yggdrasil.launcher.weather.Weather
import kotlin.concurrent.thread

/**
 * The living fjord wallpaper. It only draws while it can be seen: ~15 frames a
 * second when something moves (northern lights, rain, snow, storm), otherwise
 * once a minute so the sky can follow the time of day. Weather every 30 minutes.
 */
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
        private val tick = Runnable { drawFrame() }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            setOffsetNotificationsEnabled(true)
            scene = FjordScene(applicationContext)
        }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
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
                handler.post {
                    scene?.weather = w
                    if (visible) drawFrame()
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
            handler.postDelayed(tick, if (sc.animating) 66L else 60_000L)
        }
    }
}
