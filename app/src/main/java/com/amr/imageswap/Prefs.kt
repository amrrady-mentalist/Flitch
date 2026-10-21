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

    /** Colour of the live text: "light" = black text, "dark" = white text, "grey" = grey text. */
    var mailTheme: String
        get() = sp.getString("mailTheme", "light") ?: "light"
        set(v) {
            sp.edit().putString("mailTheme", v).apply()
        }

    /** What the email's live image holds: "images" (your folders), "text" or "photo". */
    var liveMode: String
        get() = sp.getString("liveMode", "images") ?: "images"
        set(v) {
            sp.edit().putString("liveMode", v).apply()
        }

    var textSentence: String
        get() = sp.getString("textSentence", "My prediction is %TEXT%") ?: ""
        set(v) {
            sp.edit().putString("textSentence", v).apply()
        }

    var textFont: String
        get() = sp.getString("textFont", "sans") ?: "sans"
        set(v) {
            sp.edit().putString("textFont", v).apply()
        }

    var textSize: Int
        get() = sp.getInt("textSize", 16)
        set(v) {
            sp.edit().putInt("textSize", v).apply()
        }

    var textLines: Int
        get() = sp.getInt("textLines", 2)
        set(v) {
            sp.edit().putInt("textLines", v).apply()
        }

    var photoFont: String
        get() = sp.getString("photoFont", "caveat") ?: "caveat"
        set(v) {
            sp.edit().putString("photoFont", v).apply()
        }

    var photoInk: Int
        get() = sp.getInt("photoInk", 0xFF1B1B3A.toInt())
        set(v) {
            sp.edit().putInt("photoInk", v).apply()
        }

    /** The four corners of the writing area on the photo. */
    var photoQuad: String
        get() = sp.getString("photoQuad", Compositor.DEFAULT_QUAD) ?: Compositor.DEFAULT_QUAD
        set(v) {
            sp.edit().putString("photoQuad", v).apply()
        }

    /** Address of a JSON page (like Inject's) that returns the word to show. */
    var apiUrl: String
        get() = sp.getString("apiUrl", "") ?: ""
        set(v) {
            sp.edit().putString("apiUrl", v.trim()).apply()
        }

    /** Name of the field in that JSON that holds the word. */
    var apiKey: String
        get() = sp.getString("apiKey", "value") ?: "value"
        set(v) {
            sp.edit().putString("apiKey", v.trim()).apply()
        }

    /** The email slot that already shows its final word. Live text will not change it again. */
    var lockedSlot: String
        get() = sp.getString("lockedSlot", "") ?: ""
        set(v) {
            sp.edit().putString("lockedSlot", v).apply()
        }

    /** True while Active mode is watching the word API. */
    var activeOn: Boolean
        get() = sp.getBoolean("activeOn", false)
        set(v) {
            sp.edit().putBoolean("activeOn", v).apply()
        }

    /** What Active mode last did, for the Live text screen to show. */
    var lastStatus: String
        get() = sp.getString("lastStatus", "") ?: ""
        set(v) {
            sp.edit().putString("lastStatus", v).apply()
        }

    /** Email body used in Photo mode. */
    var mailBodyPhoto: String
        get() = sp.getString("mailBodyPhoto", "%IMG%") ?: ""
        set(v) {
            sp.edit().putString("mailBodyPhoto", v).apply()
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
