package com.ozorhaar.thingward

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.SmsManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * What Thingward does with a text: leave it, note it, or reply with its opt-out word and silence
 * its notification. Everything it remembers stays in the app's private storage on the phone.
 */
object Ward {
    private const val PREFS = "ward"
    private const val MAX_REPLIES_PER_DAY = 20
    private const val REPLY_AGAIN_AFTER_MS = 30L * 24 * 3600_000     // one opt-out per sender per month
    private const val SILENCE_WINDOW_MS = 10 * 60_000L

    /** Texts whose notifications should go: (sender digits, start of the message, when). */
    private val toSilence = ArrayList<Triple<String, String, Long>>()

    fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun autoReply(ctx: Context) = prefs(ctx).getBoolean("auto_reply", true)
    fun setAutoReply(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("auto_reply", on).apply()

    fun granted(ctx: Context, p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    fun digits(s: String) = s.filter { it.isDigit() }.takeLast(10)

    fun handle(ctx: Context, from: String, body: String, subId: Int) {
        if (from.isBlank() || body.isBlank()) return
        val now = System.currentTimeMillis()
        val p = prefs(ctx)
        // never anything from someone in your contacts, and nothing at all if we can't check
        if (!granted(ctx, Manifest.permission.READ_CONTACTS)) return
        if (isContact(ctx, from)) return

        val key = "replied_" + digits(from)
        val repliedAt = p.getLong(key, 0L)
        // after we've opted out of a sender, its "you've been unsubscribed" (or anything else it sends) is just silenced
        if (repliedAt > 0 && now - repliedAt < REPLY_AGAIN_AFTER_MS) {
            silence(ctx, from, body, now)
            log(ctx, from, body, if (Classifier.isConfirmation(body)) "confirmed" else "after opt-out", null)
            return
        }

        val r = Classifier.classify(body)
        when (r.verdict) {
            Classifier.Verdict.NONE -> return
            Classifier.Verdict.SUSPECT -> log(ctx, from, body, "noted (no disclaimer)", r.keyword)
            Classifier.Verdict.OPT_OUT -> {
                val word = r.keyword ?: "STOP"
                val day = now / 86_400_000L
                val sentToday = if (p.getLong("day", -1) == day) p.getInt("sent_today", 0) else 0
                val canSend = autoReply(ctx) && granted(ctx, Manifest.permission.SEND_SMS) && sentToday < MAX_REPLIES_PER_DAY
                if (canSend) {
                    runCatching {
                        val sms = ctx.getSystemService(SmsManager::class.java).let { if (subId >= 0) it.createForSubscriptionId(subId) else it }
                        sms.sendTextMessage(from, null, word, null, null)
                    }.onSuccess {
                        p.edit().putLong(key, now).putLong("day", day).putInt("sent_today", sentToday + 1).apply()
                        log(ctx, from, body, "replied $word", word)
                    }.onFailure { log(ctx, from, body, "reply failed", word) }
                } else log(ctx, from, body, if (autoReply(ctx)) "would reply $word (limit or permission)" else "watch only: would reply $word", word)
                silence(ctx, from, body, now)
            }
        }
    }

    private fun isContact(ctx: Context, from: String): Boolean = runCatching {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(from))
        ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)?.use { it.count > 0 } ?: false
    }.getOrDefault(true)        // if the lookup fails, treat it as a contact: leave it alone

    // ---------------------------------------------------------------- silencing
    private fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")

    @Synchronized private fun silence(ctx: Context, from: String, body: String, now: Long) {
        toSilence.removeAll { now - it.third > SILENCE_WINDOW_MS }
        toSilence += Triple(digits(from), norm(body).take(40), now)
        Silencer.rescan()
    }

    /** Whether a messaging notification (its title and text) is one of the texts to silence. */
    @Synchronized fun shouldSilence(title: String, text: String): Boolean {
        val now = System.currentTimeMillis()
        toSilence.removeAll { now - it.third > SILENCE_WINDOW_MS }
        val t = norm(text); val d = digits(title)
        return toSilence.any { (from, head, _) -> (head.isNotEmpty() && t.contains(head.take(30))) || (d.length >= 5 && d == from) }
    }

    // ---------------------------------------------------------------- the log (private file, newest first, 200 kept)
    private fun logFile(ctx: Context) = File(ctx.filesDir, "log.json")

    @Synchronized fun log(ctx: Context, from: String, body: String, what: String, word: String?) {
        val a = readLog(ctx)
        val o = JSONObject().put("at", System.currentTimeMillis()).put("from", from).put("what", what)
            .put("text", body.replace(Regex("\\s+"), " ").take(140))
        if (word != null) o.put("word", word)
        val out = JSONArray().put(o)
        for (i in 0 until minOf(a.length(), 199)) out.put(a.get(i))
        logFile(ctx).writeText(out.toString())
    }

    @Synchronized fun readLog(ctx: Context): JSONArray =
        runCatching { JSONArray(logFile(ctx).readText()) }.getOrDefault(JSONArray())

    @Synchronized fun clearLog(ctx: Context) { logFile(ctx).delete() }
}
