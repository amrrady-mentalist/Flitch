package com.amr.imageswap

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the status bar and keep its icons light on the dark glass.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContent {
            GlassTheme {
                App()
            }
        }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    // One list position per effect, so you return to where you were after setting a code.
    val listStates = remember { mutableMapOf<String, LazyListState>() }

    // Back steps out one level, except on the black screen, which handles Back itself.
    BackHandler(enabled = screen !is Screen.Home && screen !is Screen.Black) {
        screen = when (val s = screen) {
            is Screen.Assign -> Screen.Codes(s.effect)
            is Screen.PhotoMark -> Screen.Text
            else -> Screen.Home
        }
    }

    when (val s = screen) {
        is Screen.Home -> HomeScreen(prefs) { screen = it }
        is Screen.Settings -> SettingsScreen(prefs) { screen = Screen.Home }
        is Screen.Mail -> MailScreen(prefs) { screen = Screen.Home }
        is Screen.Text -> ComposeScreen(prefs, onMark = { screen = Screen.PhotoMark }, onBack = { screen = Screen.Home })
        is Screen.PhotoMark -> PhotoMarkScreen(prefs) { screen = Screen.Text }
        is Screen.Codes -> CodesScreen(
            prefs = prefs,
            effect = s.effect,
            listState = listStates.getOrPut(s.effect) { LazyListState() },
            onEdit = { file -> screen = Screen.Assign(s.effect, file) },
            onBack = { screen = Screen.Home }
        )
        is Screen.Assign -> AssignScreen(prefs, s.effect, s.file) { screen = Screen.Codes(s.effect) }
        is Screen.Black -> BlackScreen(prefs) { screen = Screen.Home }
    }
}
