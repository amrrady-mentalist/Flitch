package com.amr.imageswap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sets the swipe code for one image.
 * Top half: a preview of the image. Bottom half: the swipe area.
 */
@Composable
fun AssignScreen(prefs: Prefs, effect: String, fileName: String, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val minPx = with(LocalDensity.current) { 50.dp.toPx() }

    val allCodes = remember { prefs.getCodes(effect) }
    val currentCode = remember { allCodes.entries.firstOrNull { it.value == fileName }?.key }
    val others = remember { allCodes.filterValues { it != fileName } }

    var rec by remember { mutableStateOf("") }
    val trail = remember { mutableStateListOf<Offset>() }

    // Two codes must not start the same way, or the app could not tell them apart.
    val conflict = if (rec.isEmpty()) null
    else others.entries.firstOrNull { e -> e.key.startsWith(rec) || rec.startsWith(e.key) }

    val preview by produceState<ImageBitmap?>(initialValue = null, key1 = fileName) {
        value = withContext(Dispatchers.IO) {
            Storage.file(ctx, prefs.rootUri, effect, fileName)
                ?.let { Storage.bitmap(ctx, it.uri, 1000) }
                ?.asImageBitmap()
        }
    }

    fun save() {
        val updated = LinkedHashMap(allCodes)
        if (currentCode != null) updated.remove(currentCode)
        updated[rec] = fileName
        prefs.saveCodes(effect, updated)
        onDone()
    }

    fun remove() {
        val updated = LinkedHashMap(allCodes)
        if (currentCode != null) updated.remove(currentCode)
        prefs.saveCodes(effect, updated)
        onDone()
    }

    GlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ---------- top half: preview ----------
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
            ) {
                Spacer(Modifier.height(14.dp))
                GlassHeader(title = fileName, subtitle = effect, titleSize = 24.sp, onBack = onDone)
                Spacer(Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(30.dp))
                ) {
                    SectionLabel("PREVIEW", Modifier.padding(start = 18.dp, top = 14.dp))
                    val img = preview
                    if (img != null) {
                        Image(
                            bitmap = img,
                            contentDescription = "Preview of $fileName",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 14.dp, end = 14.dp, top = 38.dp, bottom = 14.dp)
                                .clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CodePill("CURRENT", currentCode, tinted = false, modifier = Modifier.weight(1f))
                    CodePill("NEW", if (rec.isEmpty()) null else rec, tinted = true, modifier = Modifier.weight(1f))
                }
                Box(modifier = Modifier.height(24.dp), contentAlignment = Alignment.CenterStart) {
                    if (conflict != null) {
                        Text(
                            "Clashes with ${conflict.value}: one code starts with the other.",
                            fontSize = 13.sp,
                            color = GlassColors.Amber
                        )
                    }
                }
            }

            // ---------- bottom half: swipe area ----------
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 22.dp, bottom = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(34.dp))
                        .swipesWithTrail(minPx, trail) { rec += it }
                ) {
                    SectionLabel("SWIPE HERE", Modifier.padding(start = 20.dp, top = 16.dp))
                    EdgeHint("\u2191", Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
                    EdgeHint("\u2193", Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
                    EdgeHint("\u2190", Modifier.align(Alignment.CenterStart).padding(start = 8.dp))
                    EdgeHint("\u2192", Modifier.align(Alignment.CenterEnd).padding(end = 8.dp))
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (trail.size > 1) {
                            val path = Path()
                            path.moveTo(trail[0].x, trail[0].y)
                            for (i in 1 until trail.size) path.lineTo(trail[i].x, trail[i].y)
                            drawPath(
                                path = path,
                                brush = SolidColor(Color.White.copy(alpha = 0.92f)),
                                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton("Clear", onClick = { rec = "" }, modifier = Modifier.weight(1f), enabled = rec.isNotEmpty())
                    GlassButton("Remove", onClick = { remove() }, modifier = Modifier.weight(1f), enabled = currentCode != null)
                    GlassButton(
                        "Save",
                        onClick = { save() },
                        modifier = Modifier.weight(1.3f),
                        tinted = true,
                        enabled = rec.isNotEmpty() && conflict == null
                    )
                }
            }
        }
    }
}

@Composable
private fun CodePill(label: String, code: String?, tinted: Boolean, modifier: Modifier) {
    Row(
        modifier = modifier
            .height(44.dp)
            .glass(CircleShape, tinted)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionLabel(label)
        Text(
            if (code == null) "-" else arrows(code),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun EdgeHint(glyph: String, modifier: Modifier) {
    Text(glyph, modifier = modifier.alpha(0.3f), fontSize = 28.sp, fontWeight = FontWeight.Bold)
}
