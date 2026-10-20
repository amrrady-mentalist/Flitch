package com.amr.imageswap

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    GlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
        ) {
            Spacer(Modifier.height(14.dp))
            Text("Glitch", fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp)
            Spacer(Modifier.height(22.dp))
            SectionLabel("EFFECT")
            Spacer(Modifier.height(10.dp))

            if (rootUri.isBlank() || effects.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(26.dp))
                        .padding(20.dp)
                ) {
                    Text(
                        if (rootUri.isBlank()) {
                            "First open Settings: choose your images folder, then enter your server address and secret key."
                        } else {
                            "No effect folders found. Put one folder per effect inside your chosen folder, for example Cards."
                        },
                        fontSize = 15.sp,
                        lineHeight = 21.sp
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 230.dp)
                        .glass(RoundedCornerShape(26.dp))
                        .padding(6.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    effects.forEach { e ->
                        EffectRow(name = e, selected = e == active) {
                            active = e
                            prefs.activeEffect = e
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            StepCard(
                kicker = "STEP 1",
                title = "Write and send the email",
                subtitle = "Opens FairEmail with the live image",
                icon = Icons.Rounded.Email,
                tinted = false,
                onClick = { go(Screen.Mail) }
            )
            Spacer(Modifier.height(12.dp))
            StepCard(
                kicker = "STEP 2",
                title = "Black screen",
                subtitle = "Swipe the code to send the image",
                icon = Icons.Rounded.DarkMode,
                tinted = true,
                enabled = active.isNotBlank(),
                onClick = { go(Screen.Black) }
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile(
                    label = "Swipe codes",
                    icon = Icons.Rounded.OpenWith,
                    enabled = active.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { go(Screen.Codes(active)) }
                Tile(label = "Test link", icon = Icons.Rounded.Link, modifier = Modifier.weight(1f)) {
                    if (prefs.workerUrl.isBlank()) {
                        Toast.makeText(ctx, "Enter the server address in Settings first", Toast.LENGTH_LONG).show()
                    } else {
                        // A fresh slot opened in the browser, to test a swap without sending an email.
                        prefs.currentSlot = newSlot()
                        val url = MailLauncher.slotUrl(prefs.workerUrl, prefs.currentSlot)
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                }
                Tile(label = "Settings", icon = Icons.Rounded.Tune, modifier = Modifier.weight(1f)) {
                    go(Screen.Settings)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EffectRow(name: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .glass(shape, tinted = selected)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            name,
            modifier = Modifier.weight(1f),
            fontSize = 17.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
        if (selected) Icon(Icons.Rounded.Check, contentDescription = "Selected", modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun StepCard(
    kicker: String,
    title: String,
    subtitle: String,
    icon: ImageVector,
    tinted: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(98.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .glass(RoundedCornerShape(28.dp), tinted)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .size(54.dp)
                .glass(CircleShape),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(kicker, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp, color = GlassColors.Dim)
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(subtitle, fontSize = 13.sp, color = GlassColors.Dim, maxLines = 1)
        }
    }
}

@Composable
private fun Tile(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .height(92.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .glass(RoundedCornerShape(24.dp))
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
