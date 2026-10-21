package com.amr.imageswap

import android.app.Activity
import android.widget.Toast
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Colours every %IMG% / %TEXT% in the email text, so you can see where the live image goes. */
private object ImgTokenHighlight : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val s = text.text
        val styled = buildAnnotatedString {
            var i = 0
            while (i < s.length) {
                val m = Regex("%IMG%|%TEXT%").find(s, i)
                if (m == null) {
                    append(s.substring(i))
                    break
                }
                append(s.substring(i, m.range.first))
                withStyle(SpanStyle(background = GlassColors.Cyan.copy(alpha = 0.45f), fontWeight = FontWeight.Bold)) {
                    append(m.value)
                }
                i = m.range.last + 1
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

@Composable
fun MailScreen(prefs: Prefs, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var to by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf(prefs.mailSubject) }
    var mode by remember { mutableStateOf(prefs.liveMode) }
    // Each mode keeps its own message. In Text mode the message is the sentence that becomes the image.
    var bodyImages by remember { mutableStateOf(prefs.mailBody) }
    var bodyPhoto by remember { mutableStateOf(prefs.mailBodyPhoto) }
    var sentence by remember { mutableStateOf(prefs.textSentence) }
    val body = when (mode) {
        "text" -> sentence
        "photo" -> bodyPhoto
        else -> bodyImages
    }
    var opening by remember { mutableStateOf(false) }

    fun openMail() {
        if (prefs.workerUrl.isBlank() || prefs.secret.isBlank()) {
            Toast.makeText(ctx, "Enter the server address and secret key in Settings first", Toast.LENGTH_LONG).show()
            return
        }
        if (to.isBlank()) {
            Toast.makeText(ctx, "Enter the spectator's email address", Toast.LENGTH_SHORT).show()
            return
        }
        if (opening) return
        if (mode == "photo" && !Compositor.photoFile(ctx).exists()) {
            Toast.makeText(ctx, "Choose a photo in Live text first", Toast.LENGTH_LONG).show()
            return
        }
        // A brand-new slot for every email: it starts out blank.
        val slot = newSlot()
        prefs.currentSlot = slot
        opening = true
        scope.launch {
            val textH = Compositor.textBoxHeight(prefs.textSize, prefs.textLines)
            // Text and photo emails start from a matching blank, so nothing jumps when the real image arrives.
            val ext: String
            when (mode) {
                "text" -> {
                    ext = "png"
                    val png = withContext(Dispatchers.Default) {
                        Compositor.png(Compositor.renderText(ctx, "", "", prefs.textFont, prefs.textSize, prefs.textLines, prefs.mailTheme))
                    }
                    Net.put(prefs.workerUrl, prefs.secret, slot, png, "image/png")
                }
                "photo" -> {
                    ext = "jpg"
                    val blank = withContext(Dispatchers.Default) { Compositor.loadPhoto(ctx)?.let { Compositor.blankLike(it) } }
                    if (blank != null) Net.put(prefs.workerUrl, prefs.secret, slot, blank, "image/jpeg")
                }
                else -> ext = "jpg"
            }
            val html = MailLauncher.buildHtml(body, MailLauncher.slotUrl(prefs.workerUrl, slot, ext), mode, prefs.mailTheme, textH)
            try {
                MailLauncher.launch(ctx as Activity, to.trim(), subject, html)
            } catch (e: Exception) {
                Toast.makeText(ctx, "Could not open a mail app: ${e.message}", Toast.LENGTH_LONG).show()
            }
            opening = false
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
            GlassHeader(title = "Mail", onBack = onBack)
            Spacer(Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassField(
                    label = "SPECTATOR'S EMAIL ADDRESS",
                    value = to,
                    onValueChange = { to = it },
                    placeholder = "mail@domain.com",
                    keyboardType = KeyboardType.Email,
                    modifier = Modifier.fillMaxWidth()
                )
                GlassField(
                    label = "MAIL SUBJECT",
                    value = subject,
                    onValueChange = {
                        subject = it
                        prefs.mailSubject = it
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                GlassField(
                    label = if (mode == "text") "MESSAGE (%TEXT% = YOUR WORD)" else "CONTENT OF EMAIL",
                    value = body,
                    onValueChange = {
                        when (mode) {
                            "text" -> { sentence = it; prefs.textSentence = it }
                            "photo" -> { bodyPhoto = it; prefs.mailBodyPhoto = it }
                            else -> { bodyImages = it; prefs.mailBody = it }
                        }
                    },
                    singleLine = false,
                    minHeight = 150.dp,
                    visualTransformation = ImgTokenHighlight,
                    modifier = Modifier.fillMaxWidth()
                )
                SectionLabel("WHAT THE LIVE IMAGE SHOWS", Modifier.padding(start = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    GlassButton(
                        "Images",
                        onClick = { mode = "images"; prefs.liveMode = "images" },
                        tinted = mode == "images",
                        height = 46.dp,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(
                        "Text",
                        onClick = { mode = "text"; prefs.liveMode = "text" },
                        tinted = mode == "text",
                        height = 46.dp,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(
                        "Photo",
                        onClick = { mode = "photo"; prefs.liveMode = "photo" },
                        tinted = mode == "photo",
                        height = 46.dp,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    when (mode) {
                        "text" -> "The whole message is drawn as one transparent image with %TEXT% replaced by your word, so nothing else is added to the email."
                        "photo" -> "%IMG% becomes your photo with the word written on it. Set it up under Live text."
                        else -> "%IMG% is replaced by the live image when you send."
                    },
                    fontSize = 13.sp,
                    color = GlassColors.Dim,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                if (mode == "images") "After you send it from FairEmail, come back and open the Black screen."
                else "After you send it from FairEmail, come back and open Live text.",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = GlassColors.Dim,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            GlassButton(
                text = "Open in FairEmail",
                tinted = true,
                icon = Icons.Rounded.Email,
                height = 60.dp,
                fontSize = 18.sp,
                modifier = Modifier.fillMaxWidth(),
                onClick = { openMail() }
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
