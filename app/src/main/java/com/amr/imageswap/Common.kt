package com.amr.imageswap

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.Html
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import java.security.SecureRandom
import kotlin.math.abs

sealed interface Screen {
    object Home : Screen
    object Settings : Screen
    object Mail : Screen
    object Black : Screen
    class Codes(val effect: String) : Screen
    class Assign(val effect: String, val file: String) : Screen
}

private fun classify(dx: Float, dy: Float): Char =
    if (abs(dx) > abs(dy)) {
        if (dx > 0) 'R' else 'L'
    } else {
        if (dy > 0) 'D' else 'U'
    }

/** Detects up / down / left / right swipes and reports 'U', 'D', 'L' or 'R'. */
fun Modifier.swipes(minDistPx: Float, onSwipe: (Char) -> Unit): Modifier = composed {
    val callback = rememberUpdatedState(onSwipe)
    pointerInput(minDistPx) {
        var dx = 0f
        var dy = 0f
        detectDragGestures(
            onDragStart = {
                dx = 0f
                dy = 0f
            },
            onDrag = { change, amount ->
                change.consume()
                dx += amount.x
                dy += amount.y
            },
            onDragEnd = {
                if (maxOf(abs(dx), abs(dy)) >= minDistPx) {
                    callback.value(classify(dx, dy))
                }
            }
        )
    }
}

/** Same as [swipes], and also records the finger path in [trail] so it can be drawn. */
fun Modifier.swipesWithTrail(
    minDistPx: Float,
    trail: SnapshotStateList<Offset>,
    onSwipe: (Char) -> Unit
): Modifier = composed {
    val callback = rememberUpdatedState(onSwipe)
    pointerInput(minDistPx) {
        var dx = 0f
        var dy = 0f
        detectDragGestures(
            onDragStart = { start ->
                dx = 0f
                dy = 0f
                trail.clear()
                trail.add(start)
            },
            onDrag = { change, amount ->
                change.consume()
                dx += amount.x
                dy += amount.y
                trail.add(change.position)
            },
            onDragEnd = {
                if (maxOf(abs(dx), abs(dy)) >= minDistPx) {
                    callback.value(classify(dx, dy))
                }
                trail.clear()
            },
            onDragCancel = {
                trail.clear()
            }
        )
    }
}

fun arrows(code: String): String = code.map {
    when (it) {
        'U' -> '↑'
        'D' -> '↓'
        'L' -> '←'
        else -> '→'
    }
}.joinToString(" ")

object Buzz {
    val TICK = longArrayOf(0, 35)
    val OK = longArrayOf(0, 70, 90, 70)
    val FAIL = longArrayOf(0, 500)
}

fun vibrate(context: Context, pattern: LongArray) {
    try {
        val v = context.getSystemService(Vibrator::class.java) ?: return
        v.vibrate(VibrationEffect.createWaveform(pattern, -1))
    } catch (e: Exception) {
        // vibration is only feedback; ignore problems
    }
}

fun newSlot(): String {
    val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
    val r = SecureRandom()
    return (1..14).map { chars[r.nextInt(chars.length)] }.joinToString("")
}

object MailLauncher {
    fun slotUrl(base: String, slot: String) = "$base/i/$slot.jpg"

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** Turns your text into an HTML email, replacing %IMG% with the live image. */
    fun buildHtml(body: String, imageUrl: String): String {
        val img = "<img src=\"$imageUrl\" style=\"width:100%;max-width:600px;height:auto\">"
        val parts = body.split("%IMG%")
        val sb = StringBuilder("<html><body>")
        parts.forEachIndexed { i, p ->
            sb.append(esc(p).replace("\n", "<br>"))
            if (i < parts.size - 1) sb.append(img)
        }
        if (parts.size == 1) sb.append("<br>").append(img)
        sb.append("</body></html>")
        return sb.toString()
    }

    /** Opens FairEmail directly; if it is not installed, shows the app chooser. */
    fun launch(activity: Activity, to: String, subject: String, html: String) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY))
            putExtra(Intent.EXTRA_HTML_TEXT, html)
        }
        try {
            activity.startActivity(Intent(intent).setPackage("eu.faircode.email"))
        } catch (e: ActivityNotFoundException) {
            activity.startActivity(Intent.createChooser(intent, "Send with"))
        }
    }
}
