package com.amr.imageswap

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(prefs: Prefs, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf(prefs.workerUrl) }
    var secret by remember { mutableStateOf(prefs.secret) }
    var showSecret by remember { mutableStateOf(false) }
    var idleText by remember { mutableStateOf((prefs.idleMs / 1000.0).toString().removeSuffix(".0")) }
    var apiUrl by remember { mutableStateOf(prefs.apiUrl) }
    var apiKey by remember { mutableStateOf(prefs.apiKey) }
    var folder by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var storageText by remember { mutableStateOf("") }
    var storageBusy by remember { mutableStateOf(false) }
    var cleanDaysText by remember { mutableStateOf("30") }
    var confirmDays by remember { mutableStateOf<Int?>(null) } // null = no dialog, 0 = delete all

    val rootUri = prefs.rootUri
    LaunchedEffect(rootUri) {
        folder = if (rootUri.isBlank()) "" else withContext(Dispatchers.IO) { Storage.folderName(ctx, rootUri) }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                // permission may already be held
            }
            prefs.rootUri = uri.toString()
            scope.launch {
                folder = withContext(Dispatchers.IO) { Storage.folderName(ctx, uri.toString()) }
            }
        }
    }

    GlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 22.dp)
        ) {
            Spacer(Modifier.height(14.dp))
            GlassHeader(title = "Settings", onBack = onBack)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                SectionLabel("IMAGES FOLDER", Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(26.dp))
                        .padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        SectionLabel("CHOSEN")
                        Text(
                            if (folder.isBlank()) "No folder yet" else folder,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                    GlassButton(
                        text = "Choose folder",
                        onClick = { picker.launch(null) },
                        tinted = true,
                        height = 44.dp,
                        fontSize = 15.sp
                    )
                }

                SectionLabel("SERVER", Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassField(
                        label = "SERVER ADDRESS",
                        value = url,
                        onValueChange = {
                            url = it
                            prefs.workerUrl = it
                        },
                        placeholder = "https://yourname.workers.dev",
                        keyboardType = KeyboardType.Uri,
                        modifier = Modifier.fillMaxWidth()
                    )
                    GlassField(
                        label = "SECRET KEY",
                        value = secret,
                        onValueChange = {
                            secret = it
                            prefs.secret = it
                        },
                        keyboardType = KeyboardType.Password,
                        visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                        trailing = {
                            Icon(
                                if (showSecret) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (showSecret) "Hide key" else "Show key",
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable { showSecret = !showSecret }
                                    .padding(7.dp)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    GlassField(
                        label = "SECONDS TO WAIT AFTER THE LAST SWIPE",
                        value = idleText,
                        onValueChange = {
                            idleText = it
                            val s = it.replace(',', '.').toDoubleOrNull()
                            if (s != null && s >= 0.5 && s <= 30) prefs.idleMs = (s * 1000).toLong()
                        },
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                SectionLabel("WORD API (OPTIONAL)", Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassField(
                        label = "API ADDRESS",
                        value = apiUrl,
                        onValueChange = {
                            apiUrl = it
                            prefs.apiUrl = it
                        },
                        placeholder = "https://11z.co/_w/12345/selection",
                        keyboardType = KeyboardType.Uri,
                        modifier = Modifier.fillMaxWidth()
                    )
                    GlassField(
                        label = "FIELD THAT HOLDS THE WORD",
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            prefs.apiKey = it
                        },
                        placeholder = "value",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(16.dp))
                GlassButton(
                    text = if (testing) "Testing..." else "Test server and key",
                    onClick = {
                        testing = true
                        status = ""
                        scope.launch {
                            status = Net.selfTest(prefs.workerUrl, prefs.secret)
                            testing = false
                        }
                    },
                    tinted = true,
                    enabled = url.isNotBlank() && secret.isNotBlank() && !testing,
                    height = 56.dp,
                    fontSize = 17.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                if (status.isNotBlank()) {
                    val ok = status.startsWith("Server OK and secret key OK")
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glass(CircleShape)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = if (ok) GlassColors.Green else GlassColors.Amber,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(status, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                SectionLabel("STORAGE ON THE SERVER", Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton(
                        text = if (storageBusy) "Working..." else "Check storage",
                        onClick = {
                            storageBusy = true
                            storageText = ""
                            scope.launch {
                                val r = Net.storage(prefs.workerUrl, prefs.secret)
                                val st = r.getOrNull()
                                storageText = if (st != null) {
                                    "${st.count} images, ${"%.1f".format(st.bytes / 1048576.0)} MB used of 1000 MB free"
                                } else {
                                    "Could not check: ${r.exceptionOrNull()?.message}"
                                }
                                storageBusy = false
                            }
                        },
                        enabled = url.isNotBlank() && secret.isNotBlank() && !storageBusy,
                        height = 52.dp,
                        fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    GlassField(
                        label = "DELETE IMAGES OLDER THAN (DAYS)",
                        value = cleanDaysText,
                        onValueChange = { cleanDaysText = it.filter { c -> c.isDigit() }.take(4) },
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassButton(
                            text = "Delete old",
                            onClick = { confirmDays = cleanDaysText.toIntOrNull()?.coerceAtLeast(1) ?: 30 },
                            enabled = url.isNotBlank() && secret.isNotBlank() && !storageBusy,
                            tinted = true,
                            height = 52.dp,
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f)
                        )
                        GlassButton(
                            text = "Delete all",
                            onClick = { confirmDays = 0 },
                            enabled = url.isNotBlank() && secret.isNotBlank() && !storageBusy,
                            height = 52.dp,
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (storageText.isNotBlank()) {
                        Text(storageText, fontSize = 14.sp, color = GlassColors.Dim, modifier = Modifier.padding(horizontal = 6.dp))
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    val days = confirmDays
    if (days != null) {
        AlertDialog(
            onDismissRequest = { confirmDays = null },
            title = { Text(if (days == 0) "Delete ALL images?" else "Delete images older than $days days?") },
            text = {
                Text(
                    "Emails that show a deleted image go back to blank when they are opened. " +
                        "This cannot be undone." + if (days == 0) " Do not do this just before a performance." else ""
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDays = null
                    storageBusy = true
                    storageText = "Deleting..."
                    scope.launch {
                        var total = 0
                        var failure: String? = null
                        var rounds = 0
                        while (rounds < 200) {
                            rounds++
                            val r = Net.clean(prefs.workerUrl, prefs.secret, days)
                            val p = r.getOrNull()
                            if (p == null) {
                                failure = r.exceptionOrNull()?.message
                                break
                            }
                            total += p.first
                            storageText = "Deleting... $total so far"
                            if (p.second == 0 || p.first == 0) break
                        }
                        storageText = if (failure != null) "Stopped after $total: $failure" else "Deleted $total images."
                        storageBusy = false
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDays = null }) { Text("Cancel") } }
        )
    }
}
