package com.amr.imageswap

import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private object PlaceholderHighlight : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val s = text.text
        val styled = buildAnnotatedString {
            var i = 0
            while (i < s.length) {
                val j = s.indexOf("%TEXT%", i)
                if (j < 0) {
                    append(s.substring(i))
                    break
                }
                append(s.substring(i, j))
                withStyle(SpanStyle(background = GlassColors.Cyan.copy(alpha = 0.45f), fontWeight = FontWeight.Bold)) {
                    append("%TEXT%")
                }
                i = j + 6
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

private val InkColors = listOf(
    0xFF111111.toInt(), // black
    0xFF1B1B3A.toInt(), // dark blue-black
    0xFF1F3FBF.toInt(), // blue
    0xFFB01E1E.toInt(), // red
    0xFF3A3A3A.toInt()  // graphite
)

/**
 * Live text and live photo. Set up the look once, then during the show type the word
 * (or let it arrive from the word API) and it is drawn and sent to the email's image.
 */
@Composable
fun ComposeScreen(prefs: Prefs, onMark: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(prefs.liveMode) } // "images", "text" or "photo"
    var sentence by remember { mutableStateOf(prefs.textSentence) }
    var textFont by remember { mutableStateOf(prefs.textFont) }
    var size by remember { mutableIntStateOf(prefs.textSize) }
    var lines by remember { mutableIntStateOf(prefs.textLines) }
    var theme by remember { mutableStateOf(prefs.mailTheme) }
    var photoFont by remember { mutableStateOf(prefs.photoFont) }
    var ink by remember { mutableIntStateOf(prefs.photoInk) }
    var photoVersion by remember { mutableIntStateOf(0) }
    var hasPhoto by remember { mutableStateOf(Compositor.photoFile(ctx).exists()) }

    var word by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf(prefs.activeOn) }
    var locked by remember { mutableStateOf(prefs.currentSlot.isNotBlank() && prefs.lockedSlot == prefs.currentSlot) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { Compositor.savePhoto(ctx, uri) }
                hasPhoto = ok || hasPhoto
                photoVersion++
                status = if (ok) "Photo saved. Now mark where the writing goes." else "Could not read that photo."
            }
        }
    }

    val dark = theme == "dark"

    // What the email would show right now.
    val preview by produceState<ImageBitmap?>(
        null,
        mode, sentence, textFont, size, lines, theme, word, photoFont, ink, photoVersion
    ) {
        value = withContext(Dispatchers.Default) {
            val shown = word.ifBlank { "Example" }
            if (mode == "photo") {
                Compositor.loadPhoto(ctx)?.let {
                    Compositor.renderPhoto(ctx, it, prefs.photoQuad, shown, photoFont, ink).asImageBitmap()
                }
            } else {
                Compositor.renderText(ctx, sentence, shown, textFont, size, lines, theme).asImageBitmap()
            }
        }
    }

    // Draws and uploads. The first real word locks the email, so it keeps showing that word.
    val doSend: suspend (String) -> Boolean = { value ->
        val err = LiveSender.send(ctx, prefs, value)
        vibrate(ctx, if (err == null) Buzz.OK else Buzz.FAIL)
        status = err ?: "Sent: the email now shows it."
        if (err == null && value.isNotBlank()) {
            prefs.lockedSlot = prefs.currentSlot
            locked = true
        }
        err == null
    }

    // Follow what Active mode is doing in the background.
    LaunchedEffect(Unit) {
        var seen = prefs.lastStatus
        while (isActive) {
            active = prefs.activeOn
            locked = prefs.currentSlot.isNotBlank() && prefs.lockedSlot == prefs.currentSlot
            if (prefs.lastStatus != seen) {
                seen = prefs.lastStatus
                status = seen
            }
            delay(700)
        }
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        active = true
        ActiveService.start(ctx)
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
            GlassHeader(title = "Live text", subtitle = "Writes your word into the email's image", onBack = onBack)
            Spacer(Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton(
                        "Text",
                        onClick = { mode = "text"; prefs.liveMode = "text" },
                        tinted = mode == "text",
                        height = 46.dp,
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(
                        "Photo",
                        onClick = { mode = "photo"; prefs.liveMode = "photo" },
                        tinted = mode == "photo",
                        height = 46.dp,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (mode == "images") {
                    Text(
                        "The email is set to Images. Tap Text or Photo to switch it.",
                        fontSize = 13.sp,
                        color = GlassColors.Amber
                    )
                }

                // ---- preview ----
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(26.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        SectionLabel("PREVIEW")
                        Spacer(Modifier.height(8.dp))
                        val img = preview
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (mode == "text") (if (dark) Color.Black else Color.White) else Color.Black.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (img != null) {
                                Image(
                                    bitmap = img,
                                    contentDescription = "Preview",
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Text(
                                    if (mode == "photo") "Choose a photo below" else "...",
                                    modifier = Modifier.padding(24.dp),
                                    color = GlassColors.Dim
                                )
                            }
                        }
                    }
                }

                if (mode == "photo") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassButton(
                            "Choose photo",
                            onClick = { photoPicker.launch("image/*") },
                            height = 48.dp,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                        GlassButton(
                            "Mark writing area",
                            onClick = onMark,
                            tinted = true,
                            enabled = hasPhoto,
                            height = 48.dp,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    SectionLabel("HANDWRITING")
                    FontRow(selected = photoFont) { photoFont = it; prefs.photoFont = it }
                    SectionLabel("INK")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        InkColors.forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .glass(CircleShape, tinted = c == ink)
                                    .clickable { ink = c; prefs.photoInk = c },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Color(c))
                                )
                            }
                        }
                    }
                } else {
                    GlassField(
                        label = "SENTENCE (%TEXT% = YOUR WORD)",
                        value = sentence,
                        onValueChange = { sentence = it; prefs.textSentence = it },
                        singleLine = false,
                        minHeight = 90.dp,
                        visualTransformation = PlaceholderHighlight,
                        modifier = Modifier.fillMaxWidth()
                    )
                    SectionLabel("FONT")
                    FontRow(selected = textFont) { textFont = it; prefs.textFont = it }
                    Stepper("TEXT SIZE", size, min = 12, max = 30) { size = it; prefs.textSize = it }
                    Stepper("LINES", lines, min = 1, max = 4) { lines = it; prefs.textLines = it }
                    SectionLabel("TEXT COLOUR (MATCH THE SPECTATOR'S MAIL THEME)")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassButton(
                            "Black",
                            onClick = { theme = "light"; prefs.mailTheme = "light" },
                            tinted = theme == "light",
                            height = 44.dp,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                        GlassButton(
                            "Grey",
                            onClick = { theme = "grey"; prefs.mailTheme = "grey" },
                            tinted = theme == "grey",
                            height = 44.dp,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                        GlassButton(
                            "White",
                            onClick = { theme = "dark"; prefs.mailTheme = "dark" },
                            tinted = theme == "dark",
                            height = 44.dp,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // ---- the word ----
                GlassField(
                    label = "THE WORD",
                    value = word,
                    onValueChange = { word = it },
                    placeholder = "e.g. Queen of Hearts",
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton(
                        "Send",
                        onClick = {
                            busy = true
                            scope.launch {
                                doSend(word.trim())
                                busy = false
                            }
                        },
                        tinted = true,
                        enabled = word.isNotBlank() && !busy && !locked,
                        modifier = Modifier.weight(1.4f)
                    )
                    GlassButton(
                        "Make empty",
                        onClick = {
                            busy = true
                            scope.launch {
                                doSend("")
                                busy = false
                            }
                        },
                        enabled = !busy && !locked,
                        modifier = Modifier.weight(1f)
                    )
                }
                GlassButton(
                    if (active) "Active: waiting for the word (tap to stop)" else "Active",
                    onClick = {
                        if (active) {
                            ActiveService.stop(ctx)
                            active = false
                        } else if (android.os.Build.VERSION.SDK_INT >= 33 &&
                            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            active = true
                            ActiveService.start(ctx)
                        }
                    },
                    tinted = true,
                    enabled = prefs.apiUrl.isNotBlank() && (!locked || active),
                    height = 56.dp,
                    fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                if (prefs.apiUrl.isBlank()) {
                    Text("Add your word API address in Settings to use Active mode.", fontSize = 13.sp, color = GlassColors.Dim)
                } else if (active) {
                    Text(
                        "Checking the API every 2 seconds, even if you leave the app. The first new word is sent, then Active switches itself off.",
                        fontSize = 13.sp,
                        color = GlassColors.Dim
                    )
                }
                if (locked) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "This email already shows its word and is locked, so it cannot change again.",
                            fontSize = 13.sp,
                            color = GlassColors.Amber,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(10.dp))
                        GlassButton(
                            "Unlock",
                            onClick = { prefs.lockedSlot = ""; locked = false },
                            height = 40.dp,
                            fontSize = 14.sp
                        )
                    }
                }
                if (status.isNotBlank()) Text(status, fontSize = 14.sp, color = GlassColors.Dim)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun FontRow(selected: String, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Fonts.all.forEach { f ->
            Box(
                modifier = Modifier
                    .height(50.dp)
                    .glass(CircleShape, tinted = f.id == selected)
                    .clickable { onPick(f.id) }
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${f.label} Aa",
                    fontFamily = FontFamily(Fonts.typeface(ctx, f.id)),
                    fontSize = 20.sp
                )
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(24.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            SectionLabel(label)
            Text("$value", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        GlassButton("-", onClick = { if (value > min) onChange(value - 1) }, height = 42.dp, modifier = Modifier.width(60.dp))
        Spacer(Modifier.width(10.dp))
        GlassButton("+", onClick = { if (value < max) onChange(value + 1) }, height = 42.dp, modifier = Modifier.width(60.dp))
    }
}
