package com.ozorhaar.thingward

import android.net.Uri
import android.telecom.Call
import android.telecom.Connection
import android.telecom.CallScreeningService

/**
 * Screens incoming calls on the phone, before they ring, with the caller's real number:
 *  - caller ID that fails the carrier's anti-spoofing check: rejected
 *  - your contacts: ring
 *  - the same number again within 3 minutes: rings (someone who really needs you)
 *  - everyone else: sent to voicemail without ringing, and listed in Thingward
 * Nothing is sent anywhere; the decisions are listed only in Thingward's own log.
 */
class CallGuard : CallScreeningService() {
    companion object { const val REPEAT_MS = 3 * 60_000L }

    override fun onScreenCall(d: Call.Details) {
        if (d.callDirection != Call.Details.DIRECTION_INCOMING) return respondToCall(d, allow())
        val number = d.handle?.takeIf { it.scheme == "tel" }?.schemeSpecificPart.orEmpty()
        val shown = number.ifBlank { "Hidden number" }
        if (!Ward.callGuard(this)) return respondToCall(d, allow())

        if (d.callerNumberVerificationStatus == Connection.VERIFICATION_STATUS_FAILED) {
            Ward.log(this, shown, "Call", "call: rejected (spoofed caller ID)", null)
            return respondToCall(d, sendAway())
        }
        if (number.isNotBlank() && Ward.isKnown(this, number)) return respondToCall(d, allow())

        val now = System.currentTimeMillis()
        val key = "call_" + Ward.digits(number)
        val p = Ward.prefs(this)
        val before = if (number.isBlank()) 0L else p.getLong(key, 0L)
        if (number.isNotBlank()) p.edit().putLong(key, now).apply()
        if (before > 0 && now - before < REPEAT_MS) {
            Ward.log(this, shown, "Call", "call: rang (called again within 3 minutes)", null)
            return respondToCall(d, allow())
        }
        Ward.log(this, shown, "Call", "call: sent to voicemail (unknown caller)", null)
        respondToCall(d, sendAway())
    }

    private fun allow() = CallResponse.Builder().build()

    /** Declined as if you'd tapped decline (so it goes to voicemail), no ring, no missed-call alert; it stays in your call history. */
    private fun sendAway() = CallResponse.Builder()
        .setDisallowCall(true).setRejectCall(true).setSkipNotification(true).setSkipCallLog(false).build()
}
