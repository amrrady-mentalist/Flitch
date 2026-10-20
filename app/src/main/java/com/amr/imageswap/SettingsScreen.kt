package com.amr.imageswap

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(prefs: Prefs, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf(prefs.workerUrl) }
    var secret by remember { mutableStateOf(prefs.secret) }
    var idleText by remember { mutableStateOf((prefs.idleMs / 1000.0).toString().removeSuffix(".0")) }
    var folder by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    // Show the name of the chosen folder.
    val rootUri = prefs.rootUri
    androidx.compose.runtime.LaunchedEffect(rootUri) {
        folder = if (rootUri.isBlank()) "" else withContext(Dispatchers.IO) { Storage.folderName(ctx, rootUri) }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // permission may already be held
            }
            prefs.rootUri = uri.toString()
            scope.launch {
                folder = withContext(Dispatchers.IO) { Storage.folderName(ctx, uri.toString()) }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Text("Images folder", style = MaterialTheme.typography.titleMedium)
        Text(if (folder.isBlank()) "No folder chosen" else "Chosen: $folder")
        Button(onClick = { picker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
            Text("Choose folder")
        }

        Text("Server", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = url,
            onValueChange = {
                url = it
                prefs.workerUrl = it
            },
            label = { Text("Server address, e.g. https://rady.amrrady4020.workers.dev") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = secret,
            onValueChange = {
                secret = it
                prefs.secret = it
            },
            label = { Text("Secret key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = idleText,
            onValueChange = {
                idleText = it
                val s = it.replace(',', '.').toDoubleOrNull()
                if (s != null && s >= 0.5 && s <= 30) prefs.idleMs = (s * 1000).toLong()
            },
            label = { Text("Seconds to wait after the last swipe (0.5 to 30)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                status = "Testing..."
                scope.launch { status = Net.selfTest(prefs.workerUrl, prefs.secret) }
            },
            enabled = url.isNotBlank() && secret.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Test server and key")
        }
        if (status.isNotBlank()) Text(status)

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}
