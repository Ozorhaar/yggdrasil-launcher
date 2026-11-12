package com.ozorhaar.thingward

import com.ozorhaar.thingward.Classifier.Verdict
import org.junit.Assert.assertEquals
import org.junit.Test

class ClassifierTest {
    private fun v(s: String) = Classifier.classify(s)

    @Test fun campaignWithDisclaimerFromEitherSide() {
        val a = v("URGENT: Our end-of-month deadline is midnight. Chip in \$15 to keep this seat blue! Paid for by Friends of Smith for Congress. Reply STOP to opt out")
        assertEquals(Verdict.OPT_OUT, a.verdict); assertEquals("STOP", a.keyword)
        val b = v("Patriot, the election is coming. Donate now and get a 500% MATCH. Pd for by Jones Victory Committee. Text END to quit.")
        assertEquals(Verdict.OPT_OUT, b.verdict); assertEquals("END", b.keyword)
    }

    @Test fun opt_out_variants() {
        assertEquals("STOP2END", v("Vote Tuesday! Paid for by Ohio Forward PAC. Txt STOP2END").keyword)
        assertEquals("QUIT", v("Polls close at 7:30. Paid for by Citizens for Lee. Reply with QUIT to stop receiving").keyword)
        assertEquals("STOP", v("Your ballot matters. Paid for by Vote Ohio. STOP to end").keyword)
    }

    @Test fun noDisclaimerIsOnlyNoted() {
        val r = v("Final notice: your vote is missing from our candidate survey! Donate to restore your status. Reply STOP to opt out")
        assertEquals(Verdict.SUSPECT, r.verdict)
    }

    @Test fun neverTouches() {
        assertEquals(Verdict.NONE, v("Your verification code is 482913. Reply STOP to opt out").verdict)
        assertEquals(Verdict.NONE, v("CVS Pharmacy: your prescription is ready. Reply STOP to opt out").verdict)
        assertEquals(Verdict.NONE, v("Your package was delivered. Reply STOP to cancel").verdict)
        assertEquals(Verdict.NONE, v("Hey, are we still on for dinner tonight?").verdict)
        assertEquals(Verdict.NONE, v("Flash sale! 30% off everything. Reply STOP to unsubscribe").verdict)
        assertEquals(Verdict.NONE, v("Reminder: city council election info at the library. No reply needed.").verdict)
    }

    @Test fun confirmations() {
        assertEquals(true, Classifier.isConfirmation("You have been unsubscribed and will receive no more messages."))
        assertEquals(true, Classifier.isConfirmation("You've opted out. Reply START to resubscribe"))
        assertEquals(false, Classifier.isConfirmation("Donate now before midnight!"))
    }
}
