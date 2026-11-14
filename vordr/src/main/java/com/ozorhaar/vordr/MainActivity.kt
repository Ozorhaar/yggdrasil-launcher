package com.ozorhaar.vordr

import android.Manifest
import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.DateFormat
import java.util.Date

/** The watchman's report: warnings first, then notes, then what's fine. Tap a finding to go fix it. */
class MainActivity : Activity() {
    private val night = Color.rgb(11, 14, 21); private val gold = Color.rgb(236, 208, 143)
    private val bone = Color.rgb(232, 220, 196); private val ash = Color.rgb(150, 150, 160)
    private val ember = Color.rgb(226, 120, 80); private val frost = Color.rgb(143, 196, 224); private val moss = Color.rgb(140, 190, 130)
    private lateinit var col: LinearLayout

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = night; window.navigationBarColor = night
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != 0) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        Watch.schedule(this)
        col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(26), dp(18), dp(26)) }
        setContentView(ScrollView(this).apply { setBackgroundColor(night); addView(col) })
    }

    override fun onResume() { super.onResume(); render() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun render() {
        col.removeAllViews()
        val before = Watch.last(this)
        val (r, fresh) = Watch.look(this)
        val freshIds = fresh.map { it.id }.toSet()
        val warn = r.findings.count { it.level == Scan.Level.WARN }
        col.addView(TextView(this).apply { text = "Vörðr"; textSize = 28f; setTextColor(gold); typeface = Typeface.DEFAULT_BOLD })
        col.addView(TextView(this).apply {
            text = (if (warn == 0) "All quiet. " else "$warn to look at. ") +
                (before?.let { "Last look ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it.at))}." } ?: "First look.") +
                " Checks again each week and tells you if something new turns up."
            textSize = 14f; setTextColor(ash); setPadding(0, dp(6), 0, dp(10))
        })
        val order = listOf(Scan.Level.WARN, Scan.Level.NOTE, Scan.Level.OK)
        for (f in r.findings.sortedBy { order.indexOf(it.level) }) {
            val accent = when (f.level) { Scan.Level.WARN -> ember; Scan.Level.NOTE -> frost; Scan.Level.OK -> moss }
            col.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply { setColor(Color.rgb(20, 24, 34)); cornerRadius = dp(10).toFloat(); setStroke(dp(1), accent) }
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
                addView(TextView(context).apply {
                    text = (if (f.id in freshIds && before != null) "NEW · " else "") + (if (f.trusted) "Trusted · " else "") + f.title
                    textSize = 15f; setTextColor(if (f.level == Scan.Level.OK) bone else accent); typeface = Typeface.DEFAULT_BOLD
                })
                if (f.detail.isNotEmpty()) addView(TextView(context).apply { text = f.detail; textSize = 13f; setTextColor(ash); setPadding(0, dp(3), 0, 0) })
                f.fix?.let { i -> setOnClickListener { runCatching { startActivity(i) } } }
                if (f.trusted || f.trustable) setOnLongClickListener {
                    android.app.AlertDialog.Builder(this@MainActivity)
                        .setTitle(if (f.trusted) "Stop trusting this?" else "Trust this?")
                        .setMessage(if (f.trusted) "It will show as a warning again." else
                            "It moves to blue and won't alert you. If another app joins it, or the app gains a new power, it warns again.")
                        .setPositiveButton(if (f.trusted) "Stop trusting" else "Trust") { _, _ -> Watch.setTrusted(this@MainActivity, f.id, !f.trusted); render() }
                        .setNegativeButton("Cancel", null).show()
                    true
                }
            })
        }
        col.addView(Button(this).apply {
            text = "Look again"; isAllCaps = false; setTextColor(night); setBackgroundColor(gold)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) }
            setOnClickListener { render() }
        })
        col.addView(TextView(this).apply {
            text = "Tap a finding to open its setting. Long-press a red one you've checked to trust it.\n\nReads settings and the app list only. No internet permission: nothing it sees leaves the phone."
            textSize = 12f; setTextColor(ash); setPadding(0, dp(14), 0, 0)
        })
    }
}
