package com.amr.imageswap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot

/** Drag the four corners onto the blank card (or paper) in your photo. */
@Composable
fun PhotoMarkScreen(prefs: Prefs, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val photo by produceState<ImageBitmap?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { Compositor.loadPhoto(ctx)?.asImageBitmap() }
    }
    val pts = remember {
        mutableStateListOf<Offset>().apply {
            val q = Compositor.parseQuad(prefs.photoQuad)
            for (i in 0 until 4) add(Offset(q[i * 2], q[i * 2 + 1]))
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
            GlassHeader(title = "Mark the writing area", subtitle = "Drag the four dots onto the blank card", onBack = onDone)
            Spacer(Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val img = photo
                if (img == null) {
                    Text("No photo yet. Go back and choose one.", color = GlassColors.Dim)
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(img.width.toFloat() / img.height.toFloat())
                    ) {
                        Image(
                            bitmap = img,
                            contentDescription = "Photo",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    var active = -1
                                    detectDragGestures(
                                        onDragStart = { o ->
                                            var best = -1
                                            var bestD = 90f * 1f
                                            for (i in 0 until 4) {
                                                val d = hypot(pts[i].x * size.width - o.x, pts[i].y * size.height - o.y)
                                                if (d < bestD) {
                                                    bestD = d
                                                    best = i
                                                }
                                            }
                                            active = best
                                        },
                                        onDragEnd = { active = -1 },
                                        onDragCancel = { active = -1 },
                                        onDrag = { change, delta ->
                                            if (active >= 0) {
                                                change.consume()
                                                val p = pts[active]
                                                pts[active] = Offset(
                                                    (p.x + delta.x / size.width).coerceIn(0f, 1f),
                                                    (p.y + delta.y / size.height).coerceIn(0f, 1f)
                                                )
                                            }
                                        }
                                    )
                                }
                        ) {
                            val px = pts.map { Offset(it.x * size.width, it.y * size.height) }
                            val path = Path().apply {
                                moveTo(px[0].x, px[0].y)
                                lineTo(px[1].x, px[1].y)
                                lineTo(px[2].x, px[2].y)
                                lineTo(px[3].x, px[3].y)
                                close()
                            }
                            drawPath(path, Color(0xFF3DD6F5).copy(alpha = 0.18f))
                            drawPath(path, Color(0xFF3DD6F5), style = Stroke(width = 3.dp.toPx()))
                            px.forEach {
                                drawCircle(Color.White, radius = 13.dp.toPx(), center = it)
                                drawCircle(Color(0xFF3DD6F5), radius = 9.dp.toPx(), center = it)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton(
                    "Reset",
                    onClick = {
                        val q = Compositor.parseQuad(Compositor.DEFAULT_QUAD)
                        for (i in 0 until 4) pts[i] = Offset(q[i * 2], q[i * 2 + 1])
                    },
                    modifier = Modifier.weight(1f)
                )
                GlassButton(
                    "Save",
                    onClick = {
                        val q = FloatArray(8)
                        for (i in 0 until 4) {
                            q[i * 2] = pts[i].x
                            q[i * 2 + 1] = pts[i].y
                        }
                        prefs.photoQuad = Compositor.formatQuad(q)
                        onDone()
                    },
                    tinted = true,
                    enabled = photo != null,
                    modifier = Modifier.weight(1.4f)
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
