package com.amr.imageswap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.ByteArrayOutputStream

/** Reads the effect folders and images from the folder the user picked. */
object Storage {

    private fun root(context: Context, rootUri: String): DocumentFile? {
        if (rootUri.isBlank()) return null
        return try {
            DocumentFile.fromTreeUri(context, Uri.parse(rootUri))
        } catch (e: Exception) {
            null
        }
    }

    fun folderName(context: Context, rootUri: String): String =
        root(context, rootUri)?.name ?: ""

    /** Each sub-folder of the root is one effect (Cards, Numbers, ...). */
    fun effects(context: Context, rootUri: String): List<String> {
        val r = root(context, rootUri) ?: return emptyList()
        return r.listFiles()
            .filter { it.isDirectory }
            .mapNotNull { it.name }
            .sortedBy { it.lowercase() }
    }

    private fun isImage(f: DocumentFile): Boolean {
        if (!f.isFile) return false
        if (f.type?.startsWith("image/") == true) return true
        val n = f.name?.lowercase() ?: return false
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp")
    }

    fun images(context: Context, rootUri: String, effect: String): List<DocumentFile> {
        val dir = root(context, rootUri)?.findFile(effect) ?: return emptyList()
        return dir.listFiles()
            .filter { isImage(it) }
            .sortedBy { it.name?.lowercase() }
    }

    fun file(context: Context, rootUri: String, effect: String, name: String): DocumentFile? {
        val dir = root(context, rootUri)?.findFile(effect) ?: return null
        return dir.findFile(name)
    }

    /**
     * Reads an image and returns a JPEG no bigger than ~1.8 MB, so it uploads
     * fast and fits the server limit. Transparent areas become white.
     */
    fun jpegFor(context: Context, file: DocumentFile, maxSide: Int = 1280): ByteArray? {
        return try {
            val resolver = context.contentResolver

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(file.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(file.uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null

            val scale = maxSide.toFloat() / maxOf(decoded.width, decoded.height)
            val sized = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).toInt().coerceAtLeast(1),
                    (decoded.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else decoded

            val flat = Bitmap.createBitmap(sized.width, sized.height, Bitmap.Config.ARGB_8888)
            Canvas(flat).apply {
                drawColor(Color.WHITE)
                drawBitmap(sized, 0f, 0f, null)
            }

            var quality = 88
            var bytes: ByteArray
            do {
                val out = ByteArrayOutputStream()
                flat.compress(Bitmap.CompressFormat.JPEG, quality, out)
                bytes = out.toByteArray()
                quality -= 10
            } while (bytes.size > 1_800_000 && quality > 30)
            bytes
        } catch (e: Exception) {
            null
        }
    }
}
