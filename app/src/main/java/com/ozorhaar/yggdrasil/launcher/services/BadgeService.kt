package com.ozorhaar.yggdrasil.launcher.services

import android.app.Notification
import android.app.PendingIntent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One waiting notification, for the preview when you hold an app. Kept in memory only. */
class Preview(val title: String, val text: String, val time: Long, val open: PendingIntent?)

/** Unread-badge counts per package, fed by [BadgeService]. */
object Badges {
    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = _counts
    private val _previews = MutableStateFlow<Map<String, List<Preview>>>(emptyMap())
    /** The newest few notifications per package (never written to disk). */
    val previews: StateFlow<Map<String, List<Preview>>> = _previews
    @Volatile var connected = false
    /** Key in [counts] for unseen camera alerts (Home Assistant's Frigate notifications). */
    const val CAMERAS = "ygg:cameras"
    @Volatile internal var service: BadgeService? = null
    /** Clears the camera alerts (you've gone to look): the beacon goes out. */
    fun clearCameraAlerts() { service?.clearCameras() }

    internal fun publish(map: Map<String, Int>, previews: Map<String, List<Preview>> = emptyMap()) {
        _counts.value = map
        _previews.value = previews
    }
}

class BadgeService : NotificationListenerService() {

    override fun onListenerConnected() {
        Badges.connected = true
        Badges.service = this
        refresh()
    }

    override fun onListenerDisconnected() {
        Badges.connected = false
        if (Badges.service === this) Badges.service = null
        Badges.publish(emptyMap())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        val active = try {
            activeNotifications ?: return
        } catch (_: Exception) {
            return
        }
        val map = HashMap<String, Int>()
        val summaries = HashMap<String, Int>()
        val previews = HashMap<String, MutableList<Preview>>()
        for (sbn in active) {
            val n = sbn.notification
            if (sbn.isOngoing || !sbn.isClearable) continue
            if (n.flags and Notification.FLAG_GROUP_SUMMARY == 0) previewOf(n, sbn.postTime)?.let {
                previews.getOrPut(sbn.packageName) { mutableListOf() }.add(it)
            }
            val count = unreadIn(n)
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) {
                summaries[sbn.packageName] = maxOf(summaries[sbn.packageName] ?: 0, count)
                continue
            }
            map[sbn.packageName] = (map[sbn.packageName] ?: 0) + count
            if (isCameraAlert(sbn)) map[Badges.CAMERAS] = (map[Badges.CAMERAS] ?: 0) + 1
        }
        // an app that posted only a summary (no individual notifications) still gets its count
        summaries.forEach { (pkg, c) -> if (pkg !in map) map[pkg] = c }
        Badges.publish(map, previews.mapValues { (_, l) -> l.sortedByDescending { it.time }.take(3) })
    }

    /** Home Assistant's camera alerts: the Frigate notifier tags them frigate-ai-… in the frigate-cameras group. */
    private fun isCameraAlert(sbn: StatusBarNotification) =
        sbn.packageName.startsWith("io.homeassistant.companion.android") &&
            (sbn.notification.group == "frigate-cameras" || sbn.tag?.startsWith("frigate-ai-") == true)

    internal fun clearCameras() {
        runCatching { activeNotifications?.filter { isCameraAlert(it) }?.forEach { cancelNotification(it.key) } }
    }

    private fun previewOf(n: Notification, time: Long): Preview? {
        val x = n.extras ?: return null
        @Suppress("DEPRECATION")
        val last = x.getParcelableArray(Notification.EXTRA_MESSAGES)?.lastOrNull() as? android.os.Bundle
        val title = (x.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE) ?: x.getCharSequence(Notification.EXTRA_TITLE))?.toString()
        val text = (last?.getCharSequence("text") ?: x.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: x.getCharSequence(Notification.EXTRA_TEXT))?.toString()
        if (title.isNullOrBlank() && text.isNullOrBlank()) return null
        val sender = last?.getCharSequence("sender")?.toString()
        val line = listOfNotNull(sender?.takeIf { it != title }, text).joinToString(": ")
        return Preview(title.orEmpty(), line.take(240), time, n.contentIntent)
    }

    /** How many unread things one notification stands for: the app's own count, its bundled messages, or 1. */
    private fun unreadIn(n: Notification): Int {
        if (n.number > 0) return n.number
        val msgs = n.extras?.let {
            @Suppress("DEPRECATION") it.getParcelableArray(Notification.EXTRA_MESSAGES)?.size
        } ?: 0
        if (msgs > 0) return msgs
        val lines = n.extras?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.size ?: 0
        return maxOf(1, lines)
    }
}
