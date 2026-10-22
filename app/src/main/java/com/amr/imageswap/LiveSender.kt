package com.amr.imageswap

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Draws the word (as text or onto the photo) and uploads it to the current email's image slot. */
object LiveSender {

    /** Returns null when it worked, otherwise a short message saying what went wrong. */
    suspend fun send(ctx: Context, prefs: Prefs, value: String): String? {
        if (prefs.workerUrl.isBlank() || prefs.secret.isBlank() || prefs.currentSlot.isBlank()) {
            return "Send an email first (and fill in Settings)."
        }
        val loaded: Pair<ByteArray, String>? = withContext(Dispatchers.Default) {
            if (prefs.liveMode == "photo") {
                Compositor.loadPhoto(ctx)?.let {
                    Compositor.jpeg(Compositor.renderPhoto(ctx, it, prefs.photoQuad, value, prefs.photoFont, prefs.photoInk)) to "image/jpeg"
                }
            } else {
                Compositor.png(
                    Compositor.renderText(ctx, prefs.textSentence, value, prefs.textFont, prefs.textSize, prefs.textLines, prefs.mailTheme)
                ) to "image/png"
            }
        }
        val data = loaded ?: return "Choose a photo first."
        val r = Net.put(prefs.workerUrl, prefs.secret, prefs.currentSlot, data.first, data.second)
        return if (r.isSuccess) null else "Could not send: ${r.exceptionOrNull()?.message}"
    }
}
