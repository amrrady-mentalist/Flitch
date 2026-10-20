package com.amr.imageswap

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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

/** Colours every %IMG% in the email text, so you can see where the live image goes. */
private object ImgTokenHighlight : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val s = text.text
        val styled = buildAnnotatedString {
            var i = 0
            while (i < s.length) {
                val j = s.indexOf("%IMG%", i)
                if (j < 0) {
                    append(s.substring(i))
                    break                }
                append(s.substring(i, j))
                withStyle(SpanStyle(background = GlassColors.Cyan.copy(alpha = 0.45f), fontWeight = FontWeight.Bold)) {
                    append("%IMG%")
                }
                i = j + 5
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

@Composable
fun MailScreen(prefs: Prefs, onBack: () -> Unit) {
    val ctx = LocalContext.current

    var to by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf(prefs.mailSubject) }
    var body by remember { mutableStateOf(prefs.mailBody) }

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
                    label = "CONTENT OF EMAIL",
                    value = body,
                    onValueChange = {
                        body = it
                        prefs.mailBody = it
                    },
                    singleLine = false,
                    minHeight = 190.dp,
                    visualTransformation = ImgTokenHighlight,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "%IMG% is replaced by the live image when you send.",
                    fontSize = 13.sp,
                    color = GlassColors.Dim,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                "After you send it from FairEmail, come back and open the Black screen.",
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
                onClick = {
                    when {
                        prefs.workerUrl.isBlank() || prefs.secret.isBlank() ->
                            Toast.makeText(ctx, "Enter the server address and secret key in Settings first", Toast.LENGTH_LONG).show()
                        to.isBlank() ->
                            Toast.makeText(ctx, "Enter the spectator's email address", Toast.LENGTH_SHORT).show()
                        else -> {
                            // A brand-new slot for every email: it starts out blank.
                            val slot = newSlot()
                            prefs.currentSlot = slot
                            val html = MailLauncher.buildHtml(body, MailLauncher.slotUrl(prefs.workerUrl, slot))
                            try {
                                MailLauncher.launch(ctx as Activity, to.trim(), subject, html)
                            } catch (e: Exception) {
                                Toast.makeText(ctx, "Could not open a mail app: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}
