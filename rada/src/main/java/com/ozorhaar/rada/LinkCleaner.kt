package com.ozorhaar.rada

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Takes the tracking out of links: unwraps redirect wrappers (Google, Facebook, Instagram,
 * YouTube), drops tracking parameters, and trims Amazon product links to /dp/ASIN. Everything
 * else in the link (the page, its real parameters, the #fragment) is kept. Pure Kotlin, tested
 * on the build machine.
 */
object LinkCleaner {
    class Result(val text: String, val removed: Int)

    private val URL = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    // parameters that only identify the click, the campaign or the sharer
    private val ALWAYS = setOf(
        "fbclid", "gclid", "dclid", "gbraid", "wbraid", "msclkid", "yclid", "twclid", "ttclid", "li_fat_id",
        "mc_eid", "mc_cid", "igshid", "igsh", "_hsenc", "_hsmi", "mkt_tok", "oly_anon_id", "oly_enc_id",
        "vero_id", "vero_conv", "srsltid", "ref_src", "ref_url", "_branch_match_id", "_branch_referrer",
        "spm", "share_id", "sharer_id", "rdt", "epik",
    )
    // ones that only mean tracking on particular sites
    private val BY_HOST = mapOf(
        "youtube.com" to setOf("si", "feature", "pp"), "youtu.be" to setOf("si", "feature"),
        "spotify.com" to setOf("si", "context", "nd"), "x.com" to setOf("s", "t"), "twitter.com" to setOf("s", "t"),
        "instagram.com" to setOf("img_index", "utm_source"), "tiktok.com" to setOf("is_from_webapp", "sender_device", "web_id"),
        "reddit.com" to setOf("share_id", "rdt", "context"), "linkedin.com" to setOf("trk", "trackingId", "lipi"),
    )
    // wrappers whose real destination is in a parameter
    private val WRAPPERS = mapOf(
        "google.com" to listOf("/url" to listOf("q", "url")),
        "l.facebook.com" to listOf("/l.php" to listOf("u")), "lm.facebook.com" to listOf("/l.php" to listOf("u")),
        "l.instagram.com" to listOf("/" to listOf("u")),
        "youtube.com" to listOf("/redirect" to listOf("q")),
        "out.reddit.com" to listOf("/" to listOf("url")),
    )

    private fun hostMatches(host: String, base: String) = host == base || host.endsWith(".$base")

    fun clean(text: String): Result {
        var removed = 0
        val out = URL.replace(text) { m ->
            // keep sentence punctuation that the pattern swallowed
            var raw = m.value; var tail = ""
            while (raw.isNotEmpty() && raw.last() in ".,!?;:)]}") { tail = raw.last() + tail; raw = raw.dropLast(1) }
            val (url, n) = cleanUrl(raw)
            removed += n
            url + tail
        }
        return Result(out, removed)
    }

    fun cleanUrl(input: String, depth: Int = 0): Pair<String, Int> {
        val uri = runCatching { URI(input) }.getOrNull() ?: return input to 0
        val host = (uri.host ?: return input to 0).lowercase().removePrefix("www.").removePrefix("m.")
        val path = uri.rawPath ?: ""
        val params = parseQuery(uri.rawQuery)

        // a wrapper: go to where it points
        if (depth < 3) WRAPPERS.entries.firstOrNull { hostMatches(host, it.key) }?.value?.forEach { (p, keys) ->
            if (path == p || (p == "/" && (path.isEmpty() || path == "/"))) {
                val target = keys.firstNotNullOfOrNull { k -> params.firstOrNull { it.first == k }?.second }?.let { decode(it) }
                if (target != null && target.startsWith("http", ignoreCase = true)) {
                    val (inner, n) = cleanUrl(target, depth + 1)
                    return inner to n + 1
                }
            }
        }

        // Amazon: the product is all that's needed
        if (hostMatches(host, "amazon.com") || host.startsWith("amazon.") || host.contains(".amazon.")) {
            Regex("""/(?:dp|gp/product|gp/aw/d)/([A-Z0-9]{10})""").find(path)?.let { m ->
                val clean = "https://${uri.host}/dp/${m.groupValues[1]}"
                return if (clean == input) input to 0 else clean to 1
            }
        }

        val siteKeys = BY_HOST.entries.filter { hostMatches(host, it.key) }.flatMap { it.value }.toSet()
        val kept = params.filterNot { (k, _) -> val kl = k.lowercase(); kl.startsWith("utm_") || kl in ALWAYS || k in siteKeys }
        val n = params.size - kept.size
        if (n == 0) return input to 0
        val q = if (kept.isEmpty()) "" else "?" + kept.joinToString("&") { (k, v) -> if (v == null) k else "$k=$v" }
        val frag = uri.rawFragment?.let { "#$it" } ?: ""
        val auth = uri.rawAuthority ?: uri.host
        return "${uri.scheme}://$auth$path$q$frag" to n
    }

    /** Raw (still-encoded) key/value pairs, in order. */
    private fun parseQuery(q: String?): List<Pair<String, String?>> =
        q?.split('&')?.filter { it.isNotEmpty() }?.map { p ->
            val i = p.indexOf('=')
            if (i < 0) p to null else p.substring(0, i) to p.substring(i + 1)
        } ?: emptyList()

    private fun decode(s: String) = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
    @Suppress("unused") private fun encode(s: String) = URLEncoder.encode(s, "UTF-8")
}
