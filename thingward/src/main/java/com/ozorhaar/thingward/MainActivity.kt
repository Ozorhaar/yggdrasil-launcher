package com.ozorhaar.thingward

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/** One screen: what Thingward may do, a switch for replying, and what it has done. */
class MainActivity : Activity() {
    private val night = Color.rgb(11, 14, 21); private val gold = Color.rgb(236, 208, 143)
    private val bone = Color.rgb(232, 220, 196); private val ash = Color.rgb(150, 150, 160); private val ember = Color.rgb(224, 112, 58)
    private lateinit var col: LinearLayout

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = night; window.navigationBarColor = night
        col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(28), dp(20), dp(28)) }
        setContentView(ScrollView(this).apply { setBackgroundColor(night); addView(col) })
    }

    override fun onResume() { super.onResume(); render() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun text(s: String, size: Float = 15f, color: Int = bone, bold: Boolean = false, top: Int = 6) =
        TextView(this).apply {
            this.text = s; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(top), 0, 0)
        }.also { col.addView(it) }
    private fun button(s: String, go: () -> Unit) = Button(this).apply {
        text = s; isAllCaps = false; setTextColor(night); setBackgroundColor(gold)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
        setOnClickListener { go() }
    }.also { col.addView(it) }

    private fun listenerOn(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners").orEmpty()
        return flat.split(':').any { ComponentName.unflattenFromString(it)?.packageName == packageName }
    }

    private fun render() {
        col.removeAllViews()
        text("Thingward", 28f, gold, bold = true, top = 0)
        text("Political campaign texts that carry the \"Paid for by\" line get their opt-out word replied (STOP, END…) " +
            "and their notification silenced. Texts from your contacts, codes, banks, pharmacies and deliveries are never touched. " +
            "No internet permission: nothing here leaves the phone.", 14f, ash)

        text("Permissions", 18f, gold, bold = true, top = 22)
        val sms = Ward.granted(this, Manifest.permission.RECEIVE_SMS) && Ward.granted(this, Manifest.permission.SEND_SMS)
        val contacts = Ward.granted(this, Manifest.permission.READ_CONTACTS)
        val notif = listenerOn()
        fun line(ok: Boolean, s: String) = text((if (ok) "ᛁ  " else "✕  ") + s, 15f, if (ok) bone else ember)
        line(sms, "Read incoming texts and send the reply")
        line(contacts, "Look up your contacts (to leave them alone)")
        line(notif, "Notification access (to silence the texts)")
        if (!sms || !contacts) {
            button("Grant texts and contacts") {
                requestPermissions(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS), 1)
            }
            text("If Android says these are restricted: open App info below, tap ⋮ (top right) → Allow restricted settings, then come back and grant.", 13f, ash)
            button("App info") { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
        }
        if (!notif) button("Notification access") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }

        text("Replying", 18f, gold, bold = true, top = 22)
        col.addView(Switch(this).apply {
            text = "Reply with the opt-out word"; textSize = 15f; setTextColor(bone); isChecked = Ward.autoReply(this@MainActivity)
            setPadding(0, dp(8), 0, 0)
            setOnCheckedChangeListener { _, on -> Ward.setAutoReply(this@MainActivity, on); render() }
        })
        text(if (Ward.autoReply(this)) "On: at most 20 replies a day, and one per sender a month." else
            "Watch only: it notes what it would reply, sends nothing.", 13f, ash)

        text("What it has done", 18f, gold, bold = true, top = 22)
        val log = Ward.readLog(this)
        if (log.length() == 0) text("Nothing yet.", 14f, ash)
        for (i in 0 until log.length()) {
            val o = log.getJSONObject(i)
            val at = DateFormat.format("MMM d, h:mm a", o.optLong("at")).toString()
            text("$at · ${o.optString("from")} · ${o.optString("what")}", 13f, gold, top = 12)
            text(o.optString("text"), 13f, ash, top = 2)
        }
        if (log.length() > 0) button("Clear the list") { Ward.clearLog(this); render() }
        col.addView(View(this).apply { minimumHeight = dp(24) })
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) = render()
}
