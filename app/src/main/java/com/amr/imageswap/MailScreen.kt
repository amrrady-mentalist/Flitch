package com.amr.imageswap

import android.app.Activity
import android.widget.Toast
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun MailScreen(prefs: Prefs, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var to by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf(prefs.mailSubject) }
    var body by remember { mutableStateOf(prefs.mailBody) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Mail", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = to,
            onValueChange = { to = it },
            label = { Text("Spectator's email address") },
            placeholder = { Text("mail@domain.com") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = subject,
            onValueChange = {
                subject = it
                prefs.mailSubject = it
            },
            label = { Text("Mail subject") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = body,
            onValueChange = {
                body = it
                prefs.mailBody = it
            },
            label = { Text("Content of email (%IMG% is where the live image goes)") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
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
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Open in FairEmail")
        }

        Text(
            "After you send the email from FairEmail, come back here, go to Home and open the Black screen."
        )

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}
