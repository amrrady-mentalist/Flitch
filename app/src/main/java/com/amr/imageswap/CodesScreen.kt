package com.amr.imageswap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CodesScreen(prefs: Prefs, effect: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var files by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    // code -> file name
    val codes = remember { mutableStateMapOf<String, String>().apply { putAll(prefs.getCodes(effect)) } }
    var editing by remember { mutableStateOf<String?>(null) }
    var confirmAuto by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(effect) {
        files = withContext(Dispatchers.IO) {
            Storage.images(ctx, prefs.rootUri, effect).mapNotNull { it.name }
        }
        loading = false
    }

    fun persist() = prefs.saveCodes(effect, codes.toMap())
    fun codeOf(file: String): String? = codes.entries.firstOrNull { it.value == file }?.key

    fun autoFill() {
        codes.clear()
        val n = files.size
        var len = 1
        var cap = 4
        while (cap < n) {
            cap *= 4
            len++
        }
        files.forEachIndexed { i, f ->
            var v = i
            val chars = CharArray(len)
            for (k in len - 1 downTo 0) {
                chars[k] = "UDLR"[v % 4]
                v /= 4
            }
            codes[chars.concatToString()] = f
        }
        persist()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Swipe codes: $effect", style = MaterialTheme.typography.headlineSmall)
        Text("Tap an image to set the swipes that send it.")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { confirmAuto = true }, enabled = files.isNotEmpty()) { Text("Auto-fill") }
            OutlinedButton(onClick = { confirmClear = true }, enabled = codes.isNotEmpty()) { Text("Clear all") }
            OutlinedButton(onClick = onBack) { Text("Back") }
        }

        when {
            loading -> Text("Loading images...")
            files.isEmpty() -> Text("No images found in this folder.")
            else -> LazyColumn(modifier = Modifier.weight(1f)) {
                items(files) { f ->
                    val c = codeOf(f)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editing = f }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(f, modifier = Modifier.weight(1f))
                        Text(if (c == null) "-" else arrows(c), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }

    val current = editing
    if (current != null) {
        AssignDialog(
            file = current,
            currentCode = codeOf(current),
            others = codes.filterValues { it != current },
            onSave = { newCode ->
                codeOf(current)?.let { codes.remove(it) }
                codes[newCode] = current
                persist()
                editing = null
            },
            onRemove = {
                codeOf(current)?.let { codes.remove(it) }
                persist()
                editing = null
            },
            onDismiss = { editing = null }
        )
    }

    if (confirmAuto) {
        AlertDialog(
            onDismissRequest = { confirmAuto = false },
            title = { Text("Auto-fill all codes?") },
            text = {
                Text("This gives every image a swipe code of the same length, in alphabetical order of the file names, and replaces the codes you have now.")
            },
            confirmButton = {
                TextButton(onClick = {
                    autoFill()
                    confirmAuto = false
                }) { Text("Do it") }
            },
            dismissButton = { TextButton(onClick = { confirmAuto = false }) { Text("Cancel") } }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Remove all codes for this effect?") },
            confirmButton = {
                TextButton(onClick = {
                    codes.clear()
                    persist()
                    confirmClear = false
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun AssignDialog(
    file: String,
    currentCode: String?,
    others: Map<String, String>,
    onSave: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    var rec by remember { mutableStateOf("") }
    val minPx = with(LocalDensity.current) { 50.dp.toPx() }
    val ctx = LocalContext.current

    // Two codes must not start the same way, or the app could not tell them apart.
    val conflict = if (rec.isEmpty()) null
    else others.entries.firstOrNull { (c, _) -> c.startsWith(rec) || rec.startsWith(c) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(file) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Current code: " + if (currentCode == null) "none" else arrows(currentCode))
                Text("New code: " + if (rec.isEmpty()) "swipe in the box below" else arrows(rec))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .size(200.dp)
                        .background(Color(0xFF222222), RoundedCornerShape(16.dp))
                        .swipes(minPx) {
                            rec += it
                            vibrate(ctx, Buzz.TICK)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Swipe here", color = Color.Gray)
                }
                if (conflict != null) {
                    Text(
                        "Clashes with ${conflict.value} (one code starts with the other).",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { rec = "" }) { Text("Clear") }
                    TextButton(onClick = onRemove, enabled = currentCode != null) { Text("Remove code") }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(rec) }, enabled = rec.isNotEmpty() && conflict == null) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
