package com.ozorhaar.vordr

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** The daily look: scans quietly and only speaks up when a warning turns up that wasn't there before. */
class Watch : JobService() {
    companion object {
        private const val JOB = 4401
        private const val CHANNEL = "watch"
        private const val DAY = 86_400_000L

        /** Hands new warnings to the launcher to pass on to Home Assistant and the fortress. */
        fun report(ctx: Context, fresh: List<Scan.Finding>) {
            if (fresh.isEmpty()) return
            runCatching {
                ctx.sendBroadcast(Intent("com.ozorhaar.yggdrasil.launcher.WATCH_REPORT")
                    .setPackage("com.ozorhaar.yggdrasil.launcher")
                    .putExtra("titles", fresh.map { it.title }.toTypedArray())
                    .putExtra("details", fresh.map { it.detail }.toTypedArray()))
            }
        }

        fun schedule(ctx: Context) {
            val js = ctx.getSystemService(JobScheduler::class.java)
            if (js.getPendingJob(JOB)?.intervalMillis == DAY) return
            js.schedule(JobInfo.Builder(JOB, ComponentName(ctx, Watch::class.java))
                .setPeriodic(DAY, 6 * 3_600_000L).setPersisted(true).build())
        }

        private fun prefs(ctx: Context) = ctx.getSharedPreferences("watch", Context.MODE_PRIVATE)
        fun last(ctx: Context): Scan.Saved? = Scan.Saved.parse(prefs(ctx).getString("last", null))
        fun save(ctx: Context, r: Scan.Report) = prefs(ctx).edit().putString("last", Scan.Saved.from(r).toJson()).apply()

        /** Warnings you've reviewed and kept. Each id names the exact apps involved, so a new app or power is a new id and warns again. */
        fun trusted(ctx: Context): Set<String> = prefs(ctx).getStringSet("trusted", emptySet())!!.toSet()
        fun setTrusted(ctx: Context, id: String, on: Boolean) {
            val t = trusted(ctx).toMutableSet(); if (on) t += id else t -= id
            prefs(ctx).edit().putStringSet("trusted", t).apply()
        }

        /** Runs a scan against the last one, saves it, and returns the warnings that are new. */
        fun look(ctx: Context): Pair<Scan.Report, List<Scan.Finding>> {
            val before = last(ctx)
            val trust = trusted(ctx)
            val raw = Scan.run(ctx, before)
            val r = Scan.Report(raw.findings.map { if (it.id in trust && it.trustable) it.asTrusted() else it }, raw.grants, raw.signers, raw.at)
            val fresh = r.findings.filter { it.level == Scan.Level.WARN && (before == null || it.id !in before.ids) }
            save(ctx, r)
            report(ctx, fresh)
            return r to fresh
        }
    }

    override fun onStartJob(p: JobParameters): Boolean {
        Thread {
            runCatching {
                val (_, fresh) = look(this)
                if (fresh.isNotEmpty()) notify(fresh)
            }
            jobFinished(p, false)
        }.start()
        return true
    }

    override fun onStopJob(p: JobParameters) = true

    private fun notify(fresh: List<Scan.Finding>) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "New warnings", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(1, Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_vordr)
            .setContentTitle(if (fresh.size == 1) fresh[0].title else "${fresh.size} new security warnings")
            .setContentText(if (fresh.size == 1) fresh[0].detail else fresh.joinToString(" · ") { it.title })
            .setContentIntent(open).setAutoCancel(true).build())
    }
}
