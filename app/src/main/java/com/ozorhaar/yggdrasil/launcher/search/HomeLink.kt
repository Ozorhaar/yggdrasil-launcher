package com.ozorhaar.yggdrasil.launcher.search

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Home Assistant devices in search. Talks to HA over HTTPS only, with an access token
 * that's encrypted by a key that lives in the phone's hardware keystore (it can't be
 * read out, backed up or copied to another phone).
 *
 * Only devices you've ticked in the launcher's settings show up (an allowlist, so a
 * relay that opens something can't sneak in), and it only does things that are
 * safe from an unlocked home screen: switch lights, plugs and fans, lock a lock, close
 * a cover (garage) and run a scene. It never unlocks, opens, or disarms anything.
 */
class HomeLink(context: Context) {
    private val prefs = context.getSharedPreferences("ygg_home_link", Context.MODE_PRIVATE)

    data class Device(val entityId: String, val name: String, val state: String) {
        val domain: String get() = entityId.substringBefore('.')

        /** The one safe action for this device right now, or null. */
        val action: Pair<String, String>?
            get() = when (domain) {
                "light", "switch", "fan", "input_boolean" ->
                    if (state == "on") "Turn off" to "turn_off" else "Turn on" to "turn_on"
                "lock" -> if (state == "unlocked" || state == "open") "Lock" to "lock" else null
                "cover" -> if (state == "open" || state == "opening") "Close" to "close_cover" else null
                "scene" -> "Activate" to "turn_on"
                else -> null
            }

        /** Why there's no button, for devices search will only ever move one way. */
        val oneWayHint: String?
            get() = if (action != null) null else when (domain) {
                "lock" -> "Can only lock from here"
                "cover" -> "Can only close from here"
                else -> null
            }
    }

    val url: String get() = prefs.getString("url", DEFAULT_URL) ?: DEFAULT_URL
    val configured: Boolean get() = prefs.contains("token")

    private var cache: List<Device> = emptyList()
    private var cachedAt = 0L

    /** Saves the address and token after checking HA accepts them. Throws with a reason if not. */
    fun connect(rawUrl: String, token: String) {
        val u = rawUrl.trim().trimEnd('/')
        require(u.startsWith("https://")) { "Use the https:// address" }
        require(token.isNotBlank()) { "Paste a token first" }
        val reply = request(u, token.trim(), "GET", "/api/", null)
        require(reply.contains("API running")) { "Home Assistant didn't answer as expected" }
        prefs.edit().putString("url", u).putString("token", encrypt(token.trim())).apply()
        cache = emptyList()
    }

    fun forget() {
        prefs.edit().remove("token").apply()
        cache = emptyList()
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(KEY_ALIAS) }
    }

    /** Devices matching [query] by name or id (blocking; call off the main thread). */
    fun find(query: String): List<Device> {
        val q = query.trim().lowercase()
        if (q.length < 2 || !configured) return emptyList()
        if (System.currentTimeMillis() - cachedAt > 60_000 || cache.isEmpty()) {
            cache = allDevices()
            cachedAt = System.currentTimeMillis()
        }
        val ok = allowed
        return cache.filter { it.entityId in ok && (q in it.name.lowercase() || q in it.entityId) }.take(6)
    }

    /** Every device HA has in the domains search can handle (blocking), for choosing which to allow. */
    fun allDevices(): List<Device> {
        val arr = JSONArray(request(url, token(), "GET", "/api/states", null))
        return (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { it.getString("entity_id").substringBefore('.') in DOMAINS }
            .map {
                Device(
                    it.getString("entity_id"),
                    it.optJSONObject("attributes")?.optString("friendly_name")?.takeIf { n -> n.isNotBlank() } ?: it.getString("entity_id"),
                    it.optString("state"),
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    /** Entity ids you've allowed in search. */
    val allowed: Set<String> get() = prefs.getStringSet("allowed", emptySet()) ?: emptySet()

    fun setAllowed(entityId: String, on: Boolean) {
        val now = allowed.toMutableSet()
        if (on) now += entityId else now -= entityId
        prefs.edit().putStringSet("allowed", now).apply()
    }

    private var chatId: String? = null
    private var chatAt = 0L

    /**
     * Asks Hafnir (HA's conversation agent) a typed question and returns what he'd say.
     * Follow-ups within two minutes stay in the same conversation. Blocking.
     */
    fun ask(text: String): String {
        // Same guard rail as Hafnir's own: voice/typing never unlocks, opens a door/garage/gate or disarms.
        if (Backstop.isForbidden(text)) return Backstop.REPLY
        val body = JSONObject().put("text", text).put("language", "en").put("agent_id", AGENT)
        chatId?.takeIf { System.currentTimeMillis() - chatAt < 120_000 }?.let { body.put("conversation_id", it) }
        val r = JSONObject(request(url, token(), "POST", "/api/conversation/process", body.toString()))
        chatId = r.optString("conversation_id").takeIf { it.isNotBlank() }
        chatAt = System.currentTimeMillis()
        return r.optJSONObject("response")?.optJSONObject("speech")?.optJSONObject("plain")?.optString("speech")
            ?.takeIf { it.isNotBlank() } ?: "No answer came back."
    }

    /** Runs the device's safe action. Returns its new state as HA reports it. */
    fun act(d: Device): String {
        val (_, service) = d.action ?: error("Nothing safe to do with ${d.name}")
        require(service in SAFE_SERVICES && d.entityId in allowed) // belt and braces: never anything else
        request(url, token(), "POST", "/api/services/${d.domain}/$service", JSONObject().put("entity_id", d.entityId).toString())
        cachedAt = 0L
        return runCatching {
            JSONObject(request(url, token(), "GET", "/api/states/${d.entityId}", null)).optString("state")
        }.getOrDefault("?")
    }

    /** The house's latitude/longitude from Home Assistant's config, or null if not connected. Blocking. */
    fun homeLocation(): Pair<Double, Double>? = runCatching {
        if (!configured) return null
        val j = org.json.JSONObject(request(url, token(), "GET", "/api/config", null))
        j.getDouble("latitude") to j.getDouble("longitude")
    }.getOrNull()

    private fun request(base: String, token: String, method: String, path: String, body: String?): String {
        val c = URL(base + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 4000
            c.readTimeout = if (path.startsWith("/api/conversation")) 30000 else 6000
            c.instanceFollowRedirects = false
            c.setRequestProperty("Authorization", "Bearer $token")
            c.setRequestProperty("Content-Type", "application/json")
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            if (code == 401) error("Home Assistant refused the token")
            if (code !in 200..299) error("Home Assistant answered $code")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    // ---------------------------------------------------------------- token at rest
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val out = c.iv + c.doFinal(plain.toByteArray())
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun token(): String {
        val raw = Base64.decode(prefs.getString("token", null) ?: error("Not connected"), Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12))
        return String(c.doFinal(raw, 12, raw.size - 12))
    }

    companion object {
        const val DEFAULT_URL = ""
        const val AGENT = "conversation.hafnir_mac"
        private const val KEY_ALIAS = "ygg_home_link"
        private val DOMAINS = setOf("light", "switch", "fan", "input_boolean", "lock", "cover", "scene")
        private val SAFE_SERVICES = setOf("turn_on", "turn_off", "lock", "close_cover")
    }
}
