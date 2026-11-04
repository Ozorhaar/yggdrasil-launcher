package com.ozorhaar.yggdrasil.launcher.security

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
import android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.os.SystemClock
import android.widget.Toast

/**
 * The fingerprint gate for locked apps, locked folders, hidden apps and layout backups.
 * Uses Android's own prompt (fingerprint, falling back to your PIN/pattern), so the
 * launcher never sees or stores anything about your print.
 *
 * One pass stays good for a few seconds (so opening a locked folder and then an app
 * inside it asks once), and is forgotten as soon as the home screen goes to the background.
 */
object Guard {
    private const val AUTHENTICATORS = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
    private const val GRACE_MS = 15_000L
    private var until = 0L

    /** True when the phone has a fingerprint or screen lock that can guard things. */
    fun available(ctx: Context): Boolean =
        ctx.getSystemService(BiometricManager::class.java).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /** Forget the last pass (called when the launcher leaves the screen). */
    fun relock() {
        until = 0L
    }

    /** Runs [onPass] once you've proved it's you. [fresh] always asks, even right after a pass. */
    fun check(ctx: Context, title: String, fresh: Boolean = false, onPass: () -> Unit) {
        if (!fresh && SystemClock.elapsedRealtime() < until) {
            onPass(); return
        }
        if (!available(ctx)) {
            Toast.makeText(ctx, "Set up a fingerprint or screen lock first", Toast.LENGTH_LONG).show()
            return
        }
        val prompt = BiometricPrompt.Builder(ctx)
            .setTitle(title)
            .setSubtitle("Yggdrasil")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .setConfirmationRequired(false)
            .build()
        prompt.authenticate(CancellationSignal(), ctx.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                until = SystemClock.elapsedRealtime() + GRACE_MS
                onPass()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.BIOMETRIC_ERROR_CANCELED
                ) Toast.makeText(ctx, errString, Toast.LENGTH_SHORT).show()
            }
        })
    }
}
