package com.ozorhaar.thingward

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/** Every incoming text, as the phone receives it (the default messaging app still gets and keeps it). */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val subId = intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", intent.getIntExtra("subscription", -1))
        val pending = goAsync()
        Thread {
            try {
                // a long text arrives in pieces: put each sender's pieces back together
                parts.groupBy { it.originatingAddress.orEmpty() }.forEach { (from, ps) ->
                    Ward.handle(ctx.applicationContext, from, ps.joinToString("") { it.messageBody.orEmpty() }, subId)
                }
            } finally { pending.finish() }
        }.start()
    }
}
