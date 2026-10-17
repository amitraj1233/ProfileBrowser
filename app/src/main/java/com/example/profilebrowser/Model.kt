package com.example.profilebrowser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.random.Random

const val MAX_PROFILES = 10
const val CHROME_VER = "131"

class Preset(
    val ua: String,
    val platform: String,
    val uaPlatform: String,
    val platformVersion: String,
    val gpus: List<Pair<String, String>>,
    val cores: List<Int>,
    val mem: List<Int>
)

val PRESETS: Map<String, Preset> = linkedMapOf(
    "Windows" to Preset(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$CHROME_VER.0.0.0 Safari/537.36",
        "Win32", "Windows", "10.0.0",
        listOf(
            "Google Inc. (NVIDIA)" to "ANGLE (NVIDIA, NVIDIA GeForce GTX 1660 Direct3D11 vs_5_0 ps_5_0, D3D11)",
            "Google Inc. (Intel)" to "ANGLE (Intel, Intel(R) UHD Graphics 630 Direct3D11 vs_5_0 ps_5_0, D3D11)",
            "Google Inc. (AMD)" to "ANGLE (AMD, AMD Radeon RX 580 Direct3D11 vs_5_0 ps_5_0, D3D11)"
        ),
        listOf(4, 6, 8, 12), listOf(8, 16)
    ),
    "macOS" to Preset(
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$CHROME_VER.0.0.0 Safari/537.36",
        "MacIntel", "macOS", "14.5.0",
        listOf(
            "Google Inc. (Apple)" to "ANGLE (Apple, ANGLE Metal Renderer: Apple M1, Unspecified Version)",
            "Google Inc. (Apple)" to "ANGLE (Apple, ANGLE Metal Renderer: Apple M2, Unspecified Version)"
        ),
        listOf(8, 10), listOf(8)
    ),
    "Linux" to Preset(
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$CHROME_VER.0.0.0 Safari/537.36",
        "Linux x86_64", "Linux", "6.5.0",
        listOf(
            "Google Inc. (NVIDIA Corporation)" to "ANGLE (NVIDIA Corporation, NVIDIA GeForce GTX 1050/PCIe/SSE2, OpenGL 4.5.0)",
            "Google Inc. (Intel)" to "ANGLE (Intel, Mesa Intel(R) UHD Graphics 620 (KBL GT2), OpenGL 4.6)",
            "Google Inc. (AMD)" to "ANGLE (AMD, AMD Radeon RX 570 Series (polaris10, LLVM 15.0.7, DRM 3.49), OpenGL 4.6)"
        ),
        listOf(4, 8, 16), listOf(8, 16)
    )
)

data class Profile(
    val id: String,
    var name: String,
    var os: String,
    var proxy: String,
    var url: String,
    var script: String,
    var deviceId: String,
    var seed: Long,
    var cores: Int,
    var memory: Int,
    var vendor: String,
    var renderer: String
) {
    /** webview profile name (alphanumeric only) */
    val webProfileName: String get() = "p$id"

    fun reroll() {
        val p = PRESETS.getValue(os)
        val gpu = p.gpus.random()
        deviceId = UUID.randomUUID().toString()
        seed = Random.nextLong(1, Int.MAX_VALUE.toLong())
        cores = p.cores.random()
        memory = p.mem.random()
        vendor = gpu.first
        renderer = gpu.second
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("os", os).put("proxy", proxy)
        .put("url", url).put("script", script).put("deviceId", deviceId)
        .put("seed", seed).put("cores", cores).put("memory", memory)
        .put("vendor", vendor).put("renderer", renderer)

    companion object {
        fun fromJson(o: JSONObject) = Profile(
            o.getString("id"), o.getString("name"), o.getString("os"), o.optString("proxy"),
            o.optString("url", "https://www.google.com"), o.optString("script"),
            o.getString("deviceId"), o.getLong("seed"), o.getInt("cores"), o.getInt("memory"),
            o.getString("vendor"), o.getString("renderer")
        )

        fun create(name: String, os: String, proxy: String, url: String, script: String): Profile {
            val p = Profile(
                UUID.randomUUID().toString().replace("-", "").take(10),
                name, os, proxy, url, script, "", 0, 0, 0, "", ""
            )
            p.reroll()
            return p
        }
    }
}

object Store {
    private const val PREF = "profiles"
    private const val KEY = "json"

    fun load(ctx: Context): MutableList<Profile> {
        val s = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(s)
        return MutableList(arr.length()) { Profile.fromJson(arr.getJSONObject(it)) }
    }

    fun save(ctx: Context, list: List<Profile>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    fun find(ctx: Context, id: String): Profile? = load(ctx).firstOrNull { it.id == id }
}

class ProxyInfo(val scheme: String, val user: String?, val pass: String?, val host: String, val port: Int) {
    /** rule string understood by androidx.webkit ProxyConfig */
    fun rule(): String = if (scheme.startsWith("socks")) "$scheme://$host:$port" else "$host:$port"
}

/** Accepts host:port, scheme://host:port, scheme://user:pass@host:port, user:pass@host:port. Returns null if blank. */
fun parseProxy(text: String): ProxyInfo? {
    var t = text.trim()
    if (t.isEmpty()) return null
    if (!t.contains("://")) t = "http://$t"
    val m = Regex("^(\\w+)://(?:([^:@]+):([^@]*)@)?([^:/]+):(\\d+)/?$").matchEntire(t)
        ?: throw IllegalArgumentException("Proxy format galat. Example: http://user:pass@1.2.3.4:8080")
    val (scheme, user, pass, host, port) = m.destructured
    return ProxyInfo(scheme.lowercase(), user.ifEmpty { null }, pass.ifEmpty { null }, host, port.toInt())
}
