package com.ozorhaar.yggdrasil.launcher.ui

import android.content.pm.ApplicationInfo
import com.ozorhaar.yggdrasil.launcher.data.AppEntry

/**
 * The drawer's halls: apps gathered by what they're for, each hall under a rune (only from the
 * launcher's safe set). An app goes to the first hall whose words match its name or package,
 * else by the category the app declares, else to the Folk.
 */
enum class Hall(val rune: String, val title: String, val words: List<String>) {
    HEARTH("ᛜ", "The Hearth", listOf(
        "homeassistant", "home assistant", "nest", "ring doorbell", "frigate", "vivint", "unifi", "sense", "smartthings",
        "hue", "alexa", "google home", "reolink", "tuya", "govee", "ecobee", "wyze", "traeger", "thermostat", "smart life",
    )),
    TREASURY("ᚠ", "The Treasury", listOf(
        "bank", "pay", "paypal", "wallet", "money", "credit", "chase", "fidelity", "venmo", "cash", "schwab", "robinhood",
        "crypto", "coinbase", "discover", "amex", "capital one", "ally", "budget", "ynab", "invest", "vanguard",
        "finance", "tax", "zelle", "loan", "mortgage", "insurance",
    )),
    SPEECH("ᚨ", "The Speaking Hall", listOf(
        "message", "messag", "chat", "mail", "proton", "discord", "slack", "teams", "whatsapp", "signal",
        "telegram", "dialer", "phone", "contacts", "huginn", "mimir", "muninn", "facebook", "instagram",
        "reddit", "linkedin", "threads", "bluesky", "mastodon", "zoom", "meet", "voicemail", "pagerduty",
    )),
    MEAD_HALL("ᚹ", "The Mead Hall", listOf(
        "music", "spotify", "youtube", "netflix", "plex", "jellyfin", "hulu", "disney", "twitch", "game", "games",
        "podcast", "radio", "prime video", "max", "paramount", "peacock", "pandora", "audible", "tidal",
        "monopoly", "steam", "xbox", "playstation", "nintendo", "gallery", "photos", "streamfab",
    )),
    LONGSHIP("ᚱ", "The Longship", listOf(
        "maps", "waze", "uber", "lyft", "airline", "delta", "united", "southwest", "american", "hotel", "airbnb",
        "expedia", "gas", "fuel", "parking", "transit", "trip", "travel", "android auto", "tesla", "ford",
        "toyota", "honda", "hyundai", "kia", "chevrolet", "gm", "onstar", "car",
        "bmw", "audi", "mercedes", "volkswagen", "subaru", "nissan", "volvo", "mazda", "jeep", "rivian", "porsche",
    )),
    LORE("ᛒ", "The Lore", listOf(
        "news", "book", "kindle", "read", "wiki", "learn", "duolingo", "weather", "health", "fitness", "fit",
        "strava", "recipe", "cook", "bible", "dictionary", "translate",
    )),
    FORGE("ᚲ", "The Forge", listOf(
        "bambu", "calculator", "notes", "files", "drive", "docs", "office", "word", "excel", "sheets", "calendar",
        "nine", "clock", "termux", "settings", "camera", "scanner", "keep", "authenticator", "vpn", "browser",
        "chrome", "brave", "firefox", "claude", "chatgpt", "gemini", "assist", "tool", "manager",
    )),
    FOLK("ᛗ", "The Folk", emptyList());

    companion object {
        fun of(app: AppEntry): Hall {
            val name = app.label.lowercase()
            val pkg = app.packageName.lowercase()
            fun matches(w: String) = if (w.length <= 3) name.split(' ', '-', '.').any { it == w } else (w in name || w.replace(" ", "") in pkg)
            entries.firstOrNull { h -> h.words.any(::matches) }?.let { return it }
            return when (app.info?.applicationInfo?.category) {
                ApplicationInfo.CATEGORY_GAME, ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO,
                ApplicationInfo.CATEGORY_IMAGE -> MEAD_HALL
                ApplicationInfo.CATEGORY_SOCIAL -> SPEECH
                ApplicationInfo.CATEGORY_NEWS -> LORE
                ApplicationInfo.CATEGORY_MAPS -> LONGSHIP
                ApplicationInfo.CATEGORY_PRODUCTIVITY, ApplicationInfo.CATEGORY_ACCESSIBILITY -> FORGE
                else -> FOLK
            }
        }

        /** The apps grouped into halls, in hall order, each hall alphabetical (empty halls left out). */
        fun gather(apps: List<AppEntry>): List<Pair<Hall, List<AppEntry>>> =
            apps.groupBy { of(it) }.toSortedMap(compareBy { it.ordinal })
                .map { (h, a) -> h to a.sortedBy { it.label.lowercase() } }
    }
}
