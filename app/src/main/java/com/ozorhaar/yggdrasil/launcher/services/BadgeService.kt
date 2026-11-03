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
        for (sbn in active) {
            val n = sbn.notification
            if (sbn.isOngoing || !sbn.isClearable) continue
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) continue
            map[sbn.packageName] = (map[sbn.packageName] ?: 0) + 1
        }
        Badges.publish(map)
    }
}
