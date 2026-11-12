package com.ozorhaar.thingward

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Takes the messaging app's notification away for texts Thingward handled. It reads only the
 * notifications of messaging apps, only to match them, and keeps nothing.
 */
class Silencer : NotificationListenerService() {
    companion object {
        private val MESSAGING = setOf("com.samsung.android.messaging", "com.google.android.apps.messaging", "com.android.mms")
        @Volatile private var live: Silencer? = null
        /** A text was just handled: its notification may already be up. */
        fun rescan() { live?.let { s -> runCatching { s.activeNotifications?.forEach { s.check(it) } } } }
    }

    override fun onListenerConnected() { live = this; rescan() }
    override fun onListenerDisconnected() { if (live === this) live = null }
    override fun onNotificationPosted(sbn: StatusBarNotification) = check(sbn)

    private fun check(sbn: StatusBarNotification) {
        if (sbn.packageName !in MESSAGING || !sbn.isClearable) return
        val x = sbn.notification.extras
        val title = x.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = buildString {
            append(x.getCharSequence(Notification.EXTRA_TEXT) ?: "")
            append(' '); append(x.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: "")
            x.getParcelableArray(Notification.EXTRA_MESSAGES)?.forEach { b ->
                (b as? android.os.Bundle)?.getCharSequence("text")?.let { append(' '); append(it) }
            }
        }
        // a few seconds' grace: the text's broadcast may reach Thingward just after the notification
        if (Ward.shouldSilence(title, text)) cancelNotification(sbn.key)
    }
}
