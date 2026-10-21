package com.amr.imageswap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.hypot
import kotlin.math.max

/** The handwriting fonts that ship inside the app (all open-licence Google Fonts). */
object Fonts {
    class Face(val id: String, val label: String, val asset: String?)

    val all = listOf(
        Face("sans", "Plain", null),
        Face("caveat", "Caveat", "fonts/caveat.ttf"),
        Face("shadows", "Shadows", "fonts/shadows.ttf"),
        Face("reenie", "Reenie", "fonts/reenie.ttf"),
        Face("patrick", "Patrick", "fonts/patrick.ttf"),
        Face("indie", "Indie", "fonts/indie.ttf"),
        Face("covered", "Covered", "fonts/covered.ttf"),
        Face("nothing", "Nothing", "fonts/nothing.ttf")
    )

    private val cache = HashMap<String, Typeface>()

    fun typeface(context: Context, id: String): Typeface = cache.getOrPut(id) {
        val asset = all.firstOrNull { it.id == id }?.asset
        if (asset == null) Typeface.SANS_SERIF
        else try {
            Typeface.createFromAsset(context.assets, asset)
        } catch (e: Exception) {
            Typeface.SANS_SERIF
        }
    }
}

/** Draws the live text or the edited photo that gets uploaded to the email's image slot. */
object Compositor {

    /** Width of the text image in the email, in email (CSS) pixels. */
    const val TEXT_W = 360
    private const val SCALE = 3

    const val DEFAULT_QUAD = "0.3,0.4;0.7,0.4;0.7,0.6;0.3,0.6"

    /** Height reserved in the email for a text image, in email pixels. */
    fun textBoxHeight(sizePx: Int, lines: Int): Int = (lines * sizePx * 1.4f).toInt() + 8

    private fun layout(text: CharSequence, paint: TextPaint, width: Int, align: Layout.Alignment): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(align)
            .setLineSpacing(0f, 1.1f)
            .build()

    /**
     * Text on a fully transparent background, so only the writing shows in the email.
     * [sentence] holds %TEXT%, which is replaced by [value]. A blank value gives an empty image of the same size.
     */
    fun renderText(
        context: Context,
        sentence: String,
        value: String,
        fontId: String,
        sizePx: Int,
        lines: Int,
        theme: String
    ): Bitmap {
        val w = TEXT_W * SCALE
        val h = textBoxHeight(sizePx, lines) * SCALE
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        if (value.isBlank()) return bmp

        val text = sentence.replace("%TEXT%", value)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when (theme) {
                "dark" -> Color.WHITE
                "grey" -> Color.rgb(140, 140, 140)
                else -> Color.BLACK
            }
            typeface = Fonts.typeface(context, fontId)
            textSize = sizePx * SCALE.toFloat()
        }
        val pad = 6
        var l = layout(text, paint, w - pad * 2, Layout.Alignment.ALIGN_NORMAL)
        // Long text shrinks until it fits the reserved space.
        while (l.height > h - pad * 2 && paint.textSize > 18f) {
            paint.textSize -= 2f
            l = layout(text, paint, w - pad * 2, Layout.Alignment.ALIGN_NORMAL)
        }
        val c = Canvas(bmp)
        c.translate(pad.toFloat(), pad.toFloat())
        l.draw(c)
        return bmp
    }

    // ---------------------------------------------------------------- photo

    fun photoFile(context: Context) = File(context.filesDir, "template_photo.jpg")

    fun loadPhoto(context: Context): Bitmap? {
        val f = photoFile(context)
        return if (f.exists()) android.graphics.BitmapFactory.decodeFile(f.absolutePath) else null
    }

    /** Copies the chosen photo into the app (upright, at most 1600 px) so it stays ready between shows. */
    fun savePhoto(context: Context, uri: Uri): Boolean {
        return try {
            var bmp = Storage.bitmap(context, uri, 1600) ?: return false
            val orientation = context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees != 0f) {
                val m = Matrix().apply { postRotate(degrees) }
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            photoFile(context).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** "x,y;x,y;x,y;x,y" (top-left, top-right, bottom-right, bottom-left) as 0..1 fractions of the photo. */
    fun parseQuad(s: String): FloatArray {
        val out = FloatArray(8)
        try {
            val parts = s.split(";")
            require(parts.size == 4)
            parts.forEachIndexed { i, p ->
                val xy = p.split(",")
                out[i * 2] = xy[0].toFloat().coerceIn(0f, 1f)
                out[i * 2 + 1] = xy[1].toFloat().coerceIn(0f, 1f)
            }
        } catch (e: Exception) {
            return parseQuad(DEFAULT_QUAD)
        }
        return out
    }

    fun formatQuad(q: FloatArray): String =
        (0 until 4).joinToString(";") { "${q[it * 2]},${q[it * 2 + 1]}" }

    /**
     * Writes [text] onto the marked four-sided area of the photo. The writing is bent to follow the area
     * (so it lies flat on a tilted card) and blended in like ink.
     */
    fun renderPhoto(context: Context, photo: Bitmap, quadSpec: String, text: String, fontId: String, ink: Int): Bitmap {
        val out = photo.copy(Bitmap.Config.ARGB_8888, true)
        if (text.isBlank()) return out

        val q = parseQuad(quadSpec)
        val dst = FloatArray(8) { if (it % 2 == 0) q[it] * out.width else q[it] * out.height }
        fun d(a: Int, b: Int) = hypot(dst[a * 2] - dst[b * 2], dst[a * 2 + 1] - dst[b * 2 + 1])
        val fw = ((d(0, 1) + d(3, 2)) / 2f).coerceAtLeast(20f)
        val fh = ((d(0, 3) + d(1, 2)) / 2f).coerceAtLeast(20f)

        // Draw the writing flat first, at a comfortable resolution.
        val k = max(1f, 700f / fw).coerceAtMost(4f)
        val flatW = (fw * k).toInt().coerceAtLeast(1)
        val flatH = (fh * k).toInt().coerceAtLeast(1)
        val flat = Bitmap.createBitmap(flatW, flatH, Bitmap.Config.ARGB_8888)

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            typeface = Fonts.typeface(context, fontId)
            textSize = flatH * 0.7f
        }
        val width = (flatW * 0.94f).toInt().coerceAtLeast(1)
        var l = layout(text, paint, width, Layout.Alignment.ALIGN_CENTER)
        while (l.height > flatH * 0.92f && paint.textSize > 6f) {
            paint.textSize *= 0.93f
            l = layout(text, paint, width, Layout.Alignment.ALIGN_CENTER)
        }
        val fc = Canvas(flat)
        fc.translate((flatW - width) / 2f, (flatH - l.height) / 2f)
        l.draw(fc)

        val src = floatArrayOf(0f, 0f, flatW.toFloat(), 0f, flatW.toFloat(), flatH.toFloat(), 0f, flatH.toFloat())
        val m = Matrix()
        m.setPolyToPoly(src, 0, dst, 0, 4)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
            alpha = 235
        }
        Canvas(out).drawBitmap(flat, m, p)
        return out
    }

    // ---------------------------------------------------------------- output

    fun png(b: Bitmap): ByteArray {
        val o = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.PNG, 100, o)
        return o.toByteArray()
    }

    /** A JPEG small enough for the server (about 1.8 MB at most). */
    fun jpeg(b: Bitmap): ByteArray {
        val flat = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply {
            drawColor(Color.WHITE)
            drawBitmap(b, 0f, 0f, null)
        }
        var quality = 90
        var bytes: ByteArray
        do {
            val o = ByteArrayOutputStream()
            flat.compress(Bitmap.CompressFormat.JPEG, quality, o)
            bytes = o.toByteArray()
            quality -= 10
        } while (bytes.size > 1_800_000 && quality > 30)
        return bytes
    }

    /** A small white picture with the same shape as the photo: the "blank" the email starts with. */
    fun blankLike(photo: Bitmap): ByteArray {
        val w = 64
        val h = (64f * photo.height / photo.width).toInt().coerceAtLeast(1)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        b.eraseColor(Color.WHITE)
        return jpeg(b)
    }
}
