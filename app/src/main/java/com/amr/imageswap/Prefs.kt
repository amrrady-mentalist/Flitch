package com.amr.imageswap

import android.content.Context
import org.json.JSONObject

/** Everything the app remembers: server address, key, folder, codes. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("imageswap", Context.MODE_PRIVATE)

    var workerUrl: String
        get() = sp.getString("workerUrl", "") ?: ""
        set(v) {
            sp.edit().putString("workerUrl", v.trim().trimEnd('/')).apply()
        }

    var secret: String
        get() = sp.getString("secret", "") ?: ""
        set(v) {
            sp.edit().putString("secret", v.trim()).apply()
        }

    /** The root folder chosen with the folder picker (a content:// address). */
    var rootUri: String
        get() = sp.getString("rootUri", "") ?: ""
        set(v) {
            sp.edit().putString("rootUri", v).apply()
        }

    var activeEffect: String
        get() = sp.getString("activeEffect", "") ?: ""
        set(v) {
            sp.edit().putString("activeEffect", v).apply()
        }

    /** How long to wait after the last swipe before the image is sent. */
    var idleMs: Long
        get() = sp.getLong("idleMs", 2000L)
        set(v) {
            sp.edit().putLong("idleMs", v).apply()
        }

    /** The image slot used by the latest email. */
    var currentSlot: String
        get() = sp.getString("currentSlot", "") ?: ""
        set(v) {
            sp.edit().putString("currentSlot", v).apply()
        }

    var mailSubject: String
        get() = sp.getString("mailSubject", "\u26A0\uFE0F Don't open this Email \u26A0\uFE0F") ?: ""
        set(v) {
            sp.edit().putString("mailSubject", v).apply()
        }

    var mailBody: String
        get() = sp.getString("mailBody", "It's a game of luck \uD83E\uDD1E:\n\n%IMG%") ?: ""
        set(v) {
            sp.edit().putString("mailBody", v).apply()
        }

    /** Swipe code (like "UDL") -> image file name, for one effect folder. */
    fun getCodes(effect: String): Map<String, String> {
        val s = sp.getString("codes_$effect", null) ?: return emptyMap()
        return try {
            val o = JSONObject(s)
            val out = LinkedHashMap<String, String>()
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                out[k] = o.getString(k)
            }
            out
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun saveCodes(effect: String, codes: Map<String, String>) {
        sp.edit().putString("codes_$effect", JSONObject(codes).toString()).apply()
    }
}
