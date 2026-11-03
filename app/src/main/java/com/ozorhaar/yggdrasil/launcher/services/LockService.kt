package com.ozorhaar.yggdrasil.launcher.services

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Exists only to lock the screen on a home-screen double-tap. It ignores every
 * accessibility event and cannot read window content (see res/xml/lock_service.xml).
 */
class LockService : AccessibilityService() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    companion object {
        @Volatile private var instance: LockService? = null
        val running: Boolean get() = instance != null

        /** True when the user has switched the service on in Accessibility settings. */
        fun isEnabledInSettings(ctx: android.content.Context): Boolean {
            val flat = android.provider.Settings.Secure.getString(
                ctx.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val me = android.content.ComponentName(ctx, LockService::class.java)
            return flat.split(':').any {
                android.content.ComponentName.unflattenFromString(it) == me
            }
        }

        /** Returns false if the service isn't switched on in Accessibility settings. */
        fun lockScreen(): Boolean =
            instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false
    }
}
