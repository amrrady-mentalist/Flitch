package com.amr.imageswap

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(prefs: Prefs, go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val rootUri = prefs.rootUri
    var effects by remember { mutableStateOf<List<String>>(emptyList()) }
    var active by remember { mutableStateOf(prefs.activeEffect) }

    LaunchedEffect(rootUri) {
        effects = if (rootUri.isBlank()) emptyList()
        else withContext(Dispatchers.IO) { Storage.effects(ctx, rootUri) }
        if (active !in effects && effects.isNotEmpty()) {
            active = effects.first()
            prefs.activeEffect = active
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("ImageSwap", style = MaterialTheme.typography.headlineMedium)

        if (rootUri.isBlank()) {
            Text("First open Settings: choose your images folder, then enter your server address and secret key.")
        } else if (effects.isEmpty()) {
            Text("No effect folders found. Put one folder per effect inside your chosen folder (for example Cards).")
        } else {
            Text("Effect:", style = MaterialTheme.typography.titleMedium)
            effects.forEach { e ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            active = e
                            prefs.activeEffect = e
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = e == active, onClick = {
                        active = e
                        prefs.activeEffect = e
                    })
                    Text(e)
                }
            }
        }

        Button(onClick = { go(Screen.Mail) }, modifier = Modifier.fillMaxWidth()) {
            Text("1. Write and send the email")
        }
        Button(
            onClick = { go(Screen.Black) },
            enabled = active.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("2. Black screen (swipe to send the image)")
        }
        OutlinedButton(
            onClick = { go(Screen.Codes(active)) },
            enabled = active.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Set swipe codes for this effect")
        }
        OutlinedButton(
            onClick = {
                if (prefs.workerUrl.isBlank()) {
                    Toast.makeText(ctx, "Enter the server address in Settings first", Toast.LENGTH_LONG).show()
                } else {
                    // Makes a fresh slot and opens it in the browser, to test a swap without an email.
                    prefs.currentSlot = newSlot()
                    val url = MailLauncher.slotUrl(prefs.workerUrl, prefs.currentSlot)
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Test: open a live image link in the browser")
        }
        OutlinedButton(onClick = { go(Screen.Settings) }, modifier = Modifier.fillMaxWidth()) {
            Text("Settings")
        }
    }
}
