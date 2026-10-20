package com.amr.imageswap

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class ImageRef(val name: String, val uri: Uri)

@Composable
fun CodesScreen(
    prefs: Prefs,
    effect: String,
    listState: LazyListState,
    onEdit: (String) -> Unit,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    var files by remember { mutableStateOf<List<ImageRef>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    // swipe code -> file name
    val codes = remember { mutableStateMapOf<String, String>().apply { putAll(prefs.getCodes(effect)) } }
    var confirmAuto by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(effect) {
        files = withContext(Dispatchers.IO) {
            Storage.images(ctx, prefs.rootUri, effect).map { f -> ImageRef(f.name, f.uri) }
        }
        loading = false
    }

    fun persist() = prefs.saveCodes(effect, codes.toMap())

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
            codes[chars.concatToString()] = f.name
        }
        persist()
    }

    GlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
        ) {
            Spacer(Modifier.height(14.dp))
            GlassHeader(
                title = "Swipe codes",
                subtitle = if (loading) effect else "$effect, ${files.size} images",
                onBack = onBack
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton(
                    text = "Auto-fill",
                    onClick = { confirmAuto = true },
                    tinted = true,
                    enabled = files.isNotEmpty(),
                    icon = Icons.Rounded.Bolt,
                    height = 44.dp,
                    fontSize = 15.sp
                )
                GlassButton(
                    text = "Clear all",
                    onClick = { confirmClear = true },
                    enabled = codes.isNotEmpty(),
                    icon = Icons.Rounded.Delete,
                    height = 44.dp,
                    fontSize = 15.sp
                )
            }
            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(28.dp))
            ) {
                when {
                    loading -> Text("Loading images...", modifier = Modifier.padding(20.dp))
                    files.isEmpty() -> Text("No images found in this folder.", modifier = Modifier.padding(20.dp))
                    else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(files, key = { it.name }) { f ->
                            val code = codes.entries.firstOrNull { it.value == f.name }?.key
                            CodeRow(f, code) { onEdit(f.name) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmAuto) {
        AlertDialog(
            onDismissRequest = { confirmAuto = false },
            title = { Text("Auto-fill all codes?") },
            text = {
                Text("Every image gets a swipe code of the same length, in alphabetical order of the file names. This replaces the codes you have now.")
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
private fun CodeRow(f: ImageRef, code: String?, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Thumb(f.uri)
            Spacer(Modifier.width(14.dp))
            Text(
                f.name,
                modifier = Modifier.weight(1f),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            CodeChip(code)
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
    }
}

@Composable
private fun Thumb(uri: Uri) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(initialValue = null, key1 = uri) {
        value = withContext(Dispatchers.IO) { Storage.cachedBitmap(ctx, uri, 160)?.asImageBitmap() }
    }
    Box(
        modifier = Modifier
            .size(width = 36.dp, height = 50.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Color.White.copy(alpha = 0.12f))
    ) {
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}
