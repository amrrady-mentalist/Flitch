package com.amr.imageswap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The colours of the Glitch look. */
object GlassColors {
    val Navy = Color(0xFF090D20)
    val Cyan = Color(0xFF3DD6F5)
    val Violet = Color(0xFF8B5CF6)
    val Pink = Color(0xFFFF5FA2)
    val Green = Color(0xFF5BE3A0)
    val Amber = Color(0xFFFFC857)
    val Dim = Color.White.copy(alpha = 0.78f)
}

@Composable
fun GlassTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = GlassColors.Cyan,
            background = GlassColors.Navy,
            surface = GlassColors.Navy,
            onSurface = Color.White,
            onBackground = Color.White
        )
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            content()
        }
    }
}

/** Deep navy with soft cyan, violet and pink glows. */
@Composable
fun GlassBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GlassColors.Navy)
            .drawBehind {
                val w = size.width
                val h = size.height
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(GlassColors.Cyan.copy(alpha = 0.42f), Color.Transparent),
                        center = Offset(w * 0.10f, h * 0.02f),
                        radius = w * 0.95f
                    ),
                    radius = w * 0.95f,
                    center = Offset(w * 0.10f, h * 0.02f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(GlassColors.Violet.copy(alpha = 0.50f), Color.Transparent),
                        center = Offset(w, h * 0.55f),
                        radius = w * 1.0f
                    ),
                    radius = w * 1.0f,
                    center = Offset(w, h * 0.55f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(GlassColors.Pink.copy(alpha = 0.34f), Color.Transparent),
                        center = Offset(w * 0.15f, h),
                        radius = w * 0.9f
                    ),
                    radius = w * 0.9f,
                    center = Offset(w * 0.15f, h)
                )
            },
        content = content
    )
}

/** Frosted glass: a see-through diagonal sheen with a bright rim on the top-left edge. */
fun Modifier.glass(shape: Shape, tinted: Boolean = false): Modifier {
    val fill = if (tinted) {
        Brush.linearGradient(
            listOf(
                GlassColors.Cyan.copy(alpha = 0.58f),
                GlassColors.Violet.copy(alpha = 0.58f)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.17f), Color.White.copy(alpha = 0.06f))
        )
    }
    val rim = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = if (tinted) 0.60f else 0.42f),
            Color.White.copy(alpha = 0.06f),
            Color.White.copy(alpha = 0.18f)
        )
    )
    return this
        .clip(shape)
        .background(fill, shape)
        .border(1.dp, rim, shape)
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.1.sp,
        color = GlassColors.Dim
    )
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tinted: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    height: Dp = 54.dp,
    fontSize: TextUnit = 16.sp
) {
    Row(
        modifier = modifier
            .height(height)
            .alpha(if (enabled) 1f else 0.45f)
            .glass(CircleShape, tinted)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = fontSize, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun GlassCircleButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .glass(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(22.dp))
    }
}

/** A screen title row: round back button, title and an optional small line under it. */
@Composable
fun GlassHeader(title: String, subtitle: String? = null, titleSize: TextUnit = 26.sp, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GlassCircleButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            description = "Back",
            onClick = onBack
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, fontSize = titleSize, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, maxLines = 1)
            if (subtitle != null) {
                Text(subtitle, fontSize = 13.sp, color = GlassColors.Dim, maxLines = 1)
            }
        }
    }
}

/** A glass text field with a small caps label on top. */
@Composable
fun GlassField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minHeight: Dp = 0.dp,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    Column(
        modifier = modifier
            .then(if (minHeight > 0.dp) Modifier.heightIn(min = minHeight) else Modifier)
            .glass(RoundedCornerShape(24.dp))
            // A tap anywhere on the card focuses the field and opens the keyboard.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focus.requestFocus()
                keyboard?.show()
            }
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 15.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SectionLabel(label)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = singleLine,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 24.sp
                    ),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    visualTransformation = visualTransformation,
                    decorationBox = { inner ->
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(
                                placeholder,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.55f)
                            )
                        }
                        inner()
                    }
                )
            }
            if (trailing != null) trailing()
        }
    }
}

/** The small pill that shows a swipe code as arrows. */
@Composable
fun CodeChip(code: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .glass(CircleShape)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        if (code == null) {
            Text("none", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.7f))
        } else {
            Text(arrows(code), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
        }
    }
}
