package com.ozorhaar.hreinsa

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** How to use it, and a button that cleans the link on the clipboard. */
class MainActivity : Activity() {
    private val night = Color.rgb(11, 14, 21); private val gold = Color.rgb(236, 208, 143)
    private val bone = Color.rgb(232, 220, 196); private val ash = Color.rgb(150, 150, 160)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = night; window.navigationBarColor = night
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(28), dp(20), dp(28)) }
        fun text(s: String, size: Float, color: Int, bold: Boolean = false, top: Int = 8) = col.addView(TextView(this).apply {
            text = s; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(top), 0, 0)
        })
        text("Hreinsa", 28f, gold, bold = true, top = 0)
        text("Cleans what you share before it leaves the phone.", 15f, bone)
        text("Links", 18f, gold, bold = true, top = 22)
        text("Share a link to \"Clean & share\": tracking tags (utm_, fbclid, gclid, YouTube and Spotify si=, X's s= and t=…) " +
            "come off, Google and Facebook redirect links are unwrapped to where they really go, and Amazon links are trimmed to the product.", 14f, ash)
        text("Photos", 18f, gold, bold = true, top = 22)
        text("Share photos to \"Clean & share\": each one is redrawn from its pixels alone, so the location, camera, date and every " +
            "other hidden detail is gone. They're kept upright, at most 4096 pixels on the long side, and the copies are deleted after an hour.", 14f, ash)
        text("No internet permission: nothing here can leave the phone except through the app you choose to share to.", 13f, ash, top = 22)
        val status = TextView(this).apply { textSize = 14f; setTextColor(bone); setPadding(0, dp(10), 0, 0) }
        col.addView(Button(this).apply {
            text = "Clean the link on the clipboard"; isAllCaps = false; setTextColor(night); setBackgroundColor(gold)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) }
            setOnClickListener {
                val cm = getSystemService(ClipboardManager::class.java)
                val t = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString()
                if (t.isNullOrBlank()) { status.text = "The clipboard is empty."; return@setOnClickListener }
                val r = LinkCleaner.clean(t)
                if (r.removed == 0) status.text = "Nothing to clean."
                else { cm.setPrimaryClip(ClipData.newPlainText("link", r.text)); status.text = "Removed ${r.removed} tracker${if (r.removed == 1) "" else "s"}. The clean link is on the clipboard." }
            }
        })
        col.addView(status)
        setContentView(ScrollView(this).apply { setBackgroundColor(night); addView(col) })
    }
}
