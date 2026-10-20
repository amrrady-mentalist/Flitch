package com.amr.imageswap

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A plain black screen in immersive mode: no status bar, no navigation bar.
 * Swipe a code; after you stop swiping, the matching image is uploaded to the email's image slot.
 *
 * Buzz feedback: a tick for each swipe, two pulses when the image was sent,
 * one long buzz when something went wrong. Press Back to leave.
 */
@Composable
fun BlackScreen(prefs: Prefs, onExit: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as Activity
    val scope = rememberCoroutineScope()

    var code by remember { mutableStateOf("") }
    val minPx = with(LocalDensity.current) { 60.dp.toPx() }

    BackHandler { onExit() }

    DisposableEffect(Unit) {
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            // The status bar comes back on every other screen.
            controller.show(WindowInsetsCompat.Type.systemBars())
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    suspend fun send(submitted: String) {
        val effect = prefs.activeEffect
        val fileName = prefs.getCodes(effect)[submitted]
        val base = prefs.workerUrl
        val slot = prefs.currentSlot
        if (fileName == null || base.isBlank() || slot.isBlank() || prefs.secret.isBlank()) {
            vibrate(ctx, Buzz.FAIL)
            return
        }
        val jpeg = withContext(Dispatchers.IO) {
            Storage.file(ctx, prefs.rootUri, effect, fileName)?.let { Storage.jpegFor(ctx, it) }
        }
        if (jpeg == null) {
            vibrate(ctx, Buzz.FAIL)
            return
        }
        val result = Net.put(base, prefs.secret, slot, jpeg)
        vibrate(ctx, if (result.isSuccess) Buzz.OK else Buzz.FAIL)
    }

    // Every new swipe restarts this wait; when it runs out, the code is sent.
    LaunchedEffect(code) {
        if (code.isNotEmpty()) {
            delay(prefs.idleMs)
            val submitted = code
            code = ""
            scope.launch { send(submitted) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .swipes(minPx) {
                code += it
                vibrate(ctx, Buzz.TICK)
            }
    )
}
