package com.ozorhaar.rada

import java.net.URI

/**
 * Reads a link the way a careful person would before tapping it: which site it really belongs to,
 * and the tricks scam links use (a brand's name on someone else's domain, look-alike letters,
 * raw IP addresses, cheap throwaway domains, hidden logins, short links that hide where they go).
 * Pure Kotlin and offline: it never visits the link.
 */
object LinkCheck {
    enum class Level { OK, CAUTION, DANGER }

    class Sign(val level: Level, val text: String)

    class Report(val url: String, val host: String, val site: String, val signs: List<Sign>) {
        val level: Level get() = signs.maxOfOrNull { it.level } ?: Level.OK
    }

    /** Real homes of brands scam links like to wear. */
    private val BRANDS = mapOf(
        "paypal" to setOf("paypal.com"), "chase" to setOf("chase.com"), "bankofamerica" to setOf("bankofamerica.com"),
        "wellsfargo" to setOf("wellsfargo.com"), "amazon" to setOf("amazon.com", "amazon.co.uk"), "apple" to setOf("apple.com", "icloud.com"),
        "icloud" to setOf("icloud.com", "apple.com"), "microsoft" to setOf("microsoft.com", "live.com", "office.com"),
        "google" to setOf("google.com", "youtube.com"), "usps" to setOf("usps.com"), "fedex" to setOf("fedex.com"),
        "ups" to setOf("ups.com"), "dhl" to setOf("dhl.com"), "irs" to setOf("irs.gov"), "netflix" to setOf("netflix.com"),
        "venmo" to setOf("venmo.com"), "zelle" to setOf("zellepay.com"), "cashapp" to setOf("cash.app"),
        "coinbase" to setOf("coinbase.com"), "facebook" to setOf("facebook.com", "fb.com"), "instagram" to setOf("instagram.com"),
        "proton" to setOf("proton.me"), "samsung" to setOf("samsung.com"), "tmobile" to setOf("t-mobile.com"),
        "verizon" to setOf("verizon.com"), "spectrum" to setOf("spectrum.net", "spectrum.com"), "costco" to setOf("costco.com"),
        "walmart" to setOf("walmart.com"), "target" to setOf("target.com"), "ebay" to setOf("ebay.com"),
        "ezpass" to setOf("e-zpassny.com", "ezpassoh.com"), "turnpike" to setOf("ohioturnpike.org"),
        "ohioturnpike" to setOf("ohioturnpike.org"), "bmw" to setOf("bmwusa.com", "bmw.com"), "dmv" to setOf(),
    )

    private val COMMON_WORDS = setOf("target", "apple", "spectrum", "google", "amazon", "proton")

    private val CHEAP_TLDS = setOf("top", "xyz", "click", "link", "vip", "shop", "online", "site", "live", "icu", "cfd",
        "sbs", "bond", "buzz", "rest", "monster", "cyou", "lol", "quest", "zip", "mov", "work", "support", "help", "tk", "ml", "ga", "cf", "gq")

    private val SHORTENERS = setOf("bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd", "buff.ly", "rebrand.ly",
        "cutt.ly", "shorturl.at", "rb.gy", "tiny.cc", "s.id", "qrco.de", "qr.codes", "l.ead.me", "bl.ink")

    private val TWO_PART_SUFFIXES = setOf("co.uk", "org.uk", "ac.uk", "gov.uk", "com.au", "net.au", "co.nz", "co.jp", "com.br", "com.mx", "co.in")

    /** The registrable part of a host: "secure.login.paypal.com" -> "paypal.com". */
    fun siteOf(host: String): String {
        val labels = host.trimEnd('.').lowercase().split('.')
        if (labels.size <= 2) return labels.joinToString(".")
        val lastTwo = labels.takeLast(2).joinToString(".")
        return if (lastTwo in TWO_PART_SUFFIXES) labels.takeLast(3).joinToString(".") else lastTwo
    }

    fun looksLikeLink(text: String): Boolean {
        val t = text.trim()
        return Regex("""^(https?://|www\.)\S+$""", RegexOption.IGNORE_CASE).matches(t)
    }

    fun check(raw: String): Report? {
        var text = raw.trim()
        if (text.startsWith("www.", ignoreCase = true)) text = "https://$text"
        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = (uri.host ?: Regex("""^https?://(?:[^@/]*@)?([^/:?#]+)""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1))
            ?.lowercase() ?: return null
        val signs = ArrayList<Sign>()
        val ip = Regex("""^\d{1,3}(\.\d{1,3}){3}$""").matches(host) || host.startsWith("[")
        val site = if (ip) host else siteOf(host)

        if (uri.rawUserInfo != null || Regex("""^https?://[^/?#]*@""", RegexOption.IGNORE_CASE).containsMatchIn(text))
            signs += Sign(Level.DANGER, "Has an '@' before the address: the real site is $host, not what's written before the @")
        if (ip) signs += Sign(Level.DANGER, "Goes to a bare IP address instead of a named site")
        if (host.split('.').any { it.startsWith("xn--") })
            signs += Sign(Level.DANGER, "Uses look-alike letters from other alphabets (the address is disguised)")
        if (scheme == "http") signs += Sign(Level.CAUTION, "Not encrypted (http, not https)")
        if (site in SHORTENERS) signs += Sign(Level.CAUTION, "A short link: it hides where it really goes, and Ráða can't follow it without visiting it")
        val tld = site.substringAfterLast('.')
        if (!ip && tld in CHEAP_TLDS) signs += Sign(Level.CAUTION, "Ends in .$tld, a cheap domain scammers favour")
        if (!ip) {
            val bare = host.replace("-", "").replace(".", "")
            for ((brand, homes) in BRANDS) {
                if (brand.length < 3 || !bare.contains(brand)) continue
                if (homes.any { site == it } || site.endsWith(".gov")) continue
                // "ups" inside an unrelated word (groups.io, targeted-ads) isn't a brand claim
                if ((brand.length <= 4 || brand in COMMON_WORDS) && !Regex("""(^|[.\-])$brand([.\-]|$)""").containsMatchIn(host)) continue
                signs += Sign(Level.DANGER, if (homes.isEmpty()) "Pretends to be the ${brand.uppercase()} but is really $site"
                    else "Mentions ${brand.replaceFirstChar { it.uppercase() }} but belongs to $site, not ${homes.first()}")
                break
            }
            if (host.count { it == '.' } >= 4) signs += Sign(Level.CAUTION, "Unusually deep address (${host.count { it == '.' } + 1} parts), a common disguise")
        }
        return Report(text, host, site, signs)
    }
}
