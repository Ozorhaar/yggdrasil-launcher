package com.ozorhaar.yggdrasil.launcher.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.widget.RemoteViews
import com.ozorhaar.yggdrasil.launcher.R
import com.ozorhaar.yggdrasil.launcher.weather.Weather
import java.util.Calendar
import java.util.Locale
import kotlin.concurrent.thread

/**
 * The runestone clock: the time (kept by Android itself, so it costs nothing),
 * the Old Norse weekday and date, and the weather from Open-Meteo.
 * Refreshed every 30 minutes and just after midnight.
 */
class RunestoneClock : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        render(ctx, mgr, ids, Weather.cached(ctx))
        scheduleMidnight(ctx)
        val pending = goAsync()
        thread {
            try {
                render(ctx, mgr, ids, Weather.get(ctx))
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == ACTION_MIDNIGHT || intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED
        ) refreshAll(ctx)
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, options: android.os.Bundle) {
        render(ctx, mgr, intArrayOf(id), Weather.cached(ctx))
    }

    companion object {
        private const val ACTION_MIDNIGHT = "com.ozorhaar.yggdrasil.launcher.STONE_MIDNIGHT"

        /** Old Norse day names, Sunday first (spelled without accents so the runic font has every letter). */
        private val DAYS = arrayOf("Sunnudagr", "Manadagr", "Tysdagr", "Odinsdagr", "Thorsdagr", "Frjadagr", "Laugardagr")

        fun refreshAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, RunestoneClock::class.java))
            if (ids.isNotEmpty()) RunestoneClock().onUpdate(ctx, mgr, ids)
        }

        private fun render(ctx: Context, mgr: AppWidgetManager, ids: IntArray, w: Weather.Now?) {
            val cal = Calendar.getInstance()
            val day = DAYS[cal.get(Calendar.DAY_OF_WEEK) - 1]
            val month = cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault())
            val dayLine = "$day · $month ${cal.get(Calendar.DAY_OF_MONTH)}"
            val weatherLine = w?.let { "${it.tempF}° ${it.words} · ${it.highF}°/${it.lowF}°" } ?: "Reading the skies…"

            val open = PendingIntent.getActivity(
                ctx, 0,
                Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            ids.forEach { id ->
                val v = RemoteViews(ctx.packageName, R.layout.widget_runestone)
                v.setTextViewText(R.id.stone_day, dayLine)
                v.setTextViewText(R.id.stone_weather, weatherLine)
                v.setOnClickPendingIntent(R.id.stone_root, open)
                // scale the clock with the widget's height
                val h = mgr.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
                if (h > 0) {
                    v.setTextViewTextSize(R.id.stone_time, android.util.TypedValue.COMPLEX_UNIT_SP, (h * 0.36f).coerceIn(34f, 74f))
                }
                mgr.updateAppWidget(id, v)
            }
        }

        /** Wake just after midnight so the weekday turns over on time. */
        private fun scheduleMidnight(ctx: Context) {
            val am = ctx.getSystemService(AlarmManager::class.java) ?: return
            val next = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 30)
            }
            val pi = PendingIntent.getBroadcast(
                ctx, 1,
                Intent(ctx, RunestoneClock::class.java).setAction(ACTION_MIDNIGHT),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            am.setWindow(AlarmManager.RTC, next.timeInMillis, 5 * 60 * 1000L, pi)
        }
    }
}
