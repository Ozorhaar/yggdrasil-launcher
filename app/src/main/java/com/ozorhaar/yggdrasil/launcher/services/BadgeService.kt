package com.ozorhaar.yggdrasil.launcher.services

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Unread-badge counts per package, fed by [BadgeService]. */
object Badges {
    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = _counts
    @Volatile var connected = false

    internal fun publish(map: Map<String, Int>) {
        _counts.value = map
    }
}

class BadgeService : NotificationListenerService() {

    override fun onListenerConnected() {
        Badges.connected = true
        refresh()
    }

    override fun onListenerDisconnected() {
        Badges.connected = false
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
        for (sbn in active) {
            val n = sbn.notification
            if (sbn.isOngoing || !sbn.isClearable) continue
            val count = unreadIn(n)
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) {
                summaries[sbn.packageName] = maxOf(summaries[sbn.packageName] ?: 0, count)
                continue
            }
            map[sbn.packageName] = (map[sbn.packageName] ?: 0) + count
        }
        // an app that posted only a summary (no individual notifications) still gets its count
        summaries.forEach { (pkg, c) -> if (pkg !in map) map[pkg] = c }
        Badges.publish(map)
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
