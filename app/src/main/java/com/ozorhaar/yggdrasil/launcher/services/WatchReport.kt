package com.ozorhaar.yggdrasil.launcher.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ozorhaar.yggdrasil.launcher.ygg
import org.json.JSONArray
import org.json.JSONObject

/**
 * Vörðr has no internet on purpose; when it finds a new warning it hands it here (only an app
 * signed with the Yggdrasil key may, see the REPORT permission), and the launcher passes it to
 * Home Assistant as a `yggdrasil_phone_warning` event, which the fortress's alert automation turns
 * into a phone alert alongside its own.
 */
class WatchReport : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val titles = intent.getStringArrayExtra("titles") ?: return
        val details = intent.getStringArrayExtra("details") ?: arrayOf()
        val link = ctx.ygg.homeLink
        if (!link.configured || titles.isEmpty()) return
        val done = goAsync()
        Thread {
            try {
                val items = JSONArray()
                titles.forEachIndexed { i, t -> items.put(JSONObject().put("title", t.take(200)).put("detail", details.getOrNull(i).orEmpty().take(400))) }
                link.fireEvent("yggdrasil_phone_warning", JSONObject().put("source", "Vörðr").put("warnings", items))
            } catch (_: Throwable) {
            } finally { done.finish() }
        }.start()
    }

    companion object { const val ACTION = "com.ozorhaar.yggdrasil.launcher.WATCH_REPORT" }
}
