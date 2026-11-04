package com.ozorhaar.yggdrasil.launcher.search

/**
 * The launcher's copy of Hafnir's guard rail (keep the two in step): "Ask Hafnir" never
 * unlocks a door, opens a door, garage or gate, or disarms security, whatever Home Assistant
 * happens to expose. Commands are always refused; status questions ("is the garage open?")
 * are allowed.
 */
object Backstop {
    const val REPLY =
        "For security, unlocking doors, opening the garage or disarming the alarm has to be done from the phone app or at the door."

    private val commands = listOf(
        Regex("""\bun ?lock\b"""),
        Regex("""\bdisarm\b"""),
        Regex("""\b(open|raise|lift|pop)\b.*\b(garage|gate|gates|door|doors)\b"""),
        Regex("""\b(turn|switch|shut|deactivate|disable)\b.*\b(security|alarm system|alarm panel|house alarm|home alarm)\b"""),
    )
    private val phrasings = listOf(
        Regex("""\b(garage|gate|door|doors)\b.*\b(open|up)\b"""),
        Regex("""\b(security|alarm system|alarm panel|house alarm|home alarm)\b.*\b(off|disabled?|deactivated?)\b"""),
    )
    private val question = Regex("""^(is|are|was|were|did|does|do|has|have|check|tell me|what|whether|why|when|how|which)\b""")

    fun isForbidden(text: String): Boolean {
        val t = text.lowercase().replace(Regex("[^a-z0-9' ]"), " ").replace(Regex("\\s+"), " ").trim()
        if (commands.any { it.containsMatchIn(t) }) return true
        return !question.containsMatchIn(t) && phrasings.any { it.containsMatchIn(t) }
    }
}
