package com.amr.imageswap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    App()
                }
            }
        }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    // Back goes to the home screen everywhere except the black screen,
    // which handles Back itself.
    BackHandler(enabled = screen !is Screen.Home && screen !is Screen.Black) {
        screen = Screen.Home
    }

    when (val s = screen) {
        is Screen.Home -> HomeScreen(prefs) { screen = it }
        is Screen.Settings -> SettingsScreen(prefs) { screen = Screen.Home }
        is Screen.Mail -> MailScreen(prefs) { screen = Screen.Home }
        is Screen.Codes -> CodesScreen(prefs, s.effect) { screen = Screen.Home }
        is Screen.Black -> BlackScreen(prefs) { screen = Screen.Home }
    }
}
