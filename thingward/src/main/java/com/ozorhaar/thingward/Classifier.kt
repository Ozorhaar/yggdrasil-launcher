package com.ozorhaar.thingward

/**
 * Decides whether a text is a political campaign message, by the shape campaigns are required or
 * accustomed to use, never by party or candidate: the "Paid for by" disclaimer federal and state
 * rules require, an explicit opt-out instruction ("Reply STOP to opt out"), and fundraising or
 * election wording. Pure Kotlin, no Android, so it's unit-tested on the build machine.
 */
object Classifier {
    enum class Verdict {
        /** Not political, or something we must never touch. */
        NONE,
        /** Clearly a campaign text: reply with its opt-out word (STOP if it names none). */
        OPT_OUT,
        /** Campaign-flavoured but with scam hallmarks ("restore your status", prizes, refunds): only noted, never answered. */
        SUSPECT,
    }

    class Result(val verdict: Verdict, val keyword: String?, val why: String)

    private val I = RegexOption.IGNORE_CASE

    private val DISCLAIMER = Regex(
        """\b(paid\s+for\s+by|pd\.?\s*(for\s+)?by|authorized\s+by\s+[^.\n]{0,80}(committee|campaign|for\s+(congress|senate|governor|president))|not\s+authorized\s+by\s+any\s+candidate)""", I)

    // "Reply STOP to opt out", "Text END to quit", "Txt STOP2END", "reply with QUIT", "STOP to end", "Stop=End"
    private val OPT_OUT = Regex(
        """\b(?:reply|text|txt|send|respond)(?:\s+with)?\s*[:\-]?\s*["'“‘]?(stop2end|stop2quit|stopall|stop|end|quit|cancel|unsubscribe|optout)\b""", I)
    private val OPT_OUT2 = Regex("""\b(stop2end|stop2quit|stopall|stop|end|quit)\s*(?:=|to)\s*(?:opt[\s-]?out|end|quit|unsub\w*|cancel|stop)\b""", I)

    private val POLITICAL = Regex(
        """\b(donat\w*|chip\s+in|contribut\w*|campaign\w*|candidate\w*|election\w*|elect\b|re-?elect\w*|vote\w*|voting|ballot\w*|congress\w*|senat\w*|democrat\w*|republican\w*|gop|dnc|rnc|nrcc|dccc|nrsc|dscc|pac|super\s?pac|primary|primaries|midterms?|caucus\w*|governor|president\w*|polls?|petition|grassroots|matched?|matching|deadline|fundrais\w*|patriots?|liberals?|conservatives?|progressives?)\b""", I)

    // where campaigns take money: a link to one of these is as good as a disclaimer
    private val DONATION_LINK = Regex("""\b(winred\.com|secure\.winred|actblue\.com|secure\.actblue|anedot\.com|ngpvan\.com|revv\.co)\b""", I)
    private val MONEY_ASK = Regex("""\b(donat\w*|chip\s+in|contribut\w*|fundrais\w*|match(ed|ing)?)\b""", I)

    // what scammers wearing campaign clothes say that real campaigns don't
    private val SCAM = Regex(
        """\b(restore|reactivat\w*|suspend\w*|your\s+(status|account|registration)\s+(is|has|was|will)|claim\s+your|prize|winner|gift\s?card|refund|rebate|stimulus|crypto|bitcoin|wire|zelle|cash\s?app|venmo)\b""", I)

    // texts we never act on, whatever else they say: codes, health, money and deliveries
    private val SENSITIVE = Regex(
        """\b(code|otp|passcode|pin|verification|verify|2fa|one[-\s]?time|pharmacy|prescription|rx|appointment|doctor|fraud|bank\w*|balance|delivery|delivered|tracking|order\s+#|password|log\s?in)\b""", I)

    fun classify(body: String): Result {
        val text = body.replace(Regex("\\s+"), " ")
        if (SENSITIVE.containsMatchIn(text)) return Result(Verdict.NONE, null, "sensitive wording")
        val word = (OPT_OUT.find(text)?.groupValues?.get(1) ?: OPT_OUT2.find(text)?.groupValues?.get(1))?.uppercase()
        val disclaimer = DISCLAIMER.containsMatchIn(text)
        val political = POLITICAL.findAll(text).map { it.value.lowercase() }.toSet().size
        val link = DONATION_LINK.containsMatchIn(text)
        val campaign = disclaimer || link || (word != null && political >= 2) || (political >= 3 && MONEY_ASK.containsMatchIn(text))
        return when {
            !campaign -> Result(Verdict.NONE, null, "not political")
            SCAM.containsMatchIn(text) && !disclaimer -> Result(Verdict.SUSPECT, word, "campaign wording with scam hallmarks")
            else -> Result(Verdict.OPT_OUT, word ?: "STOP",
                when { disclaimer -> "disclaimer"; link -> "donation link"; word != null -> "campaign wording and opt-out"; else -> "campaign money ask" })
        }
    }

    private val CONFIRMATION = Regex(
        """(unsubscribed|opted[\s-]?out|no\s+(more|further)\s+(messages|msgs|texts)|will\s+(no\s+longer|not)\s+(receive|get)|removed\s+from)""", I)

    /** The sender's reply to our opt-out ("You have been unsubscribed…"). */
    fun isConfirmation(body: String) = CONFIRMATION.containsMatchIn(body)
}
