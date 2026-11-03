package com.ozorhaar.yggdrasil.launcher.services

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Exists only to lock the screen on a home-screen double-tap. It ignores every
 * accessibility event and cannot read window content (see res/xml/lock_service.xml).
 */
class LockService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    companion object {
        @Volatile private var instance: LockService? = null
        val enabled: Boolean get() = instance != null

        /** Returns false if the service isn't switched on in Accessibility settings. */
        fun lockScreen(): Boolean =
            instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false
    }
}
