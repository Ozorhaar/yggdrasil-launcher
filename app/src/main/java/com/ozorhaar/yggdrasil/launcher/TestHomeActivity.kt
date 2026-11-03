package com.ozorhaar.yggdrasil.launcher

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Diagnostic home screen: a plain platform window on Android's classic wallpaper
 * theme, no Compose, no edge-to-edge setup. Used to tell whether the haze seen on
 * the S26 Ultra comes from the main launcher window's setup or from any home window.
 */
class TestHomeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val label = TextView(this).apply {
            text = "Yggdrasil test home\nCompare the wallpaper with the main launcher"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setShadowLayer(6f, 0f, 0f, Color.BLACK)
        }
        val root = FrameLayout(this)
        root.addView(
            label,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            ).apply { bottomMargin = 260 },
        )
        setContentView(root)
    }
}
