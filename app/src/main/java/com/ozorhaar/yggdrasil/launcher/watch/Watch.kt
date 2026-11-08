package com.ozorhaar.yggdrasil.launcher.watch

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.RemoteViews
import android.widget.TextView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * The watch: an app widget the launcher keeps bound but never shows (the work on-call widget),
 * read on the phone to answer one question, "am I on call?". The answer is a small frost rune on
 * the folder that holds that app (or the folder named Work). Nothing is fetched and no account or
 * key is involved: the launcher only reads the text the widget would have drawn.
 */
class Watch(private val ctx: Context, private val host: () -> AppWidgetHost) {
    private val prefs = ctx.getSharedPreferences("ygg_watch", Context.MODE_PRIVATE)

    /** The hidden widget's id, or -1 when nothing is watched. */
    var widgetId: Int = prefs.getInt("id", -1)
        private set
    var provider: String = prefs.getString("provider", "") ?: ""
        private set
    val providerPackage: String get() = provider.substringBefore('/')

    private val _texts = MutableStateFlow(prefs.getString("texts", "") ?: "")
    /** Everything the widget last said, joined (kept so the rune survives a restart). */
    val texts: StateFlow<String> = _texts

    private var view: AppWidgetHostView? = null

    /** Starts reading the hidden widget, if there is one. */
    fun start(info: (Int) -> AppWidgetProviderInfo?) {
        if (widgetId < 0) return
        val i = info(widgetId) ?: return
        view = runCatching { host().createView(ctx, widgetId, i) }.getOrNull()
        view?.let { read(it) }
    }

    /** Takes over a home-screen widget as the watch (it leaves the grid but stays bound). */
    fun set(id: Int, provider: String, info: (Int) -> AppWidgetProviderInfo?) {
        stop()
        widgetId = id; this.provider = provider
        prefs.edit().putInt("id", id).putString("provider", provider).apply()
        start(info)
    }

    /** Stops watching and lets the widget go. */
    fun clear() {
        val id = widgetId
        stop()
        if (id >= 0) runCatching { host().deleteAppWidgetId(id) }
        widgetId = -1; provider = ""
        prefs.edit().clear().apply()
        _texts.value = ""
    }

    private fun stop() { view = null }

    /** Called by the launcher's widget host whenever any widget gets new contents. */
    fun onUpdated(id: Int, v: AppWidgetHostView) { if (id == widgetId) read(v) }

    private fun read(v: View) {
        val out = mutableListOf<String>()
        fun walk(x: View) {
            if (x is TextView) x.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out += it }
            if (x is ViewGroup) for (i in 0 until x.childCount) walk(x.getChildAt(i))
        }
        walk(v)
        if (out.isEmpty()) return
        val joined = out.joinToString(" | ")
        _texts.value = joined
        prefs.edit().putString("texts", joined).apply()
    }

    companion object {
        private val ON = Regex("\\bon[- ]?call\\b", RegexOption.IGNORE_CASE)
        private val OFF = Regex("\\boff[- ]?call\\b", RegexOption.IGNORE_CASE)
        private val WHEN = Regex("([A-Z][a-z]{2}) (\\d{1,2}), (\\d{1,2}):(\\d{2})")

        /**
         * On call, judged from what the widget says. "Off-Call" with the next shift's start time
         * counts as on call once that time has passed, even if the widget hasn't refreshed yet.
         */
        fun isOnCall(texts: String, now: Long = System.currentTimeMillis()): Boolean {
            if (texts.isBlank()) return false
            if (OFF.containsMatchIn(texts)) {
                val start = WHEN.find(texts)?.let { parse(it, now) } ?: return false
                return now >= start
            }
            return ON.containsMatchIn(texts)
        }

        private fun parse(m: MatchResult, now: Long): Long? = runCatching {
            val month = SimpleDateFormat("MMM", Locale.US).parse(m.groupValues[1])!!
            val c = Calendar.getInstance().apply { timeInMillis = now }
            val year = c.get(Calendar.YEAR)
            val mc = Calendar.getInstance().apply { time = month }
            c.set(year, mc.get(Calendar.MONTH), m.groupValues[2].toInt(), m.groupValues[3].toInt(), m.groupValues[4].toInt(), 0)
            c.set(Calendar.MILLISECOND, 0)
            // a shift in January seen in December belongs to next year
            if (c.timeInMillis < now - 180L * 24 * 3600 * 1000) c.add(Calendar.YEAR, 1)
            c.timeInMillis
        }.getOrNull()
    }
}

/** The launcher's widget host: like Android's, but tells the watch whenever a widget changes. */
class YggWidgetHost(ctx: Context, hostId: Int, private val watch: () -> Watch?) : AppWidgetHost(ctx, hostId) {
    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
        object : AppWidgetHostView(context) {
            override fun updateAppWidget(remoteViews: RemoteViews?) {
                super.updateAppWidget(remoteViews)
                watch()?.onUpdated(appWidgetId, this)
            }
        }
}
