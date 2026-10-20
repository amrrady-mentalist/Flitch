package com.amr.imageswap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.provider.DocumentsContract
import android.util.LruCache
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
        if (rootUri.isBlank()) return emptyList()
        return try {
            val tree = Uri.parse(rootUri)
            children(context, tree, DocumentsContract.getTreeDocumentId(tree))
                .filter { it.mime == DocumentsContract.Document.MIME_TYPE_DIR }
                .map { it.name }
                .sortedBy { it.lowercase() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    class Img(val name: String, val uri: Uri)

    private class Child(val id: String, val name: String, val mime: String)

    /** One single query for all children of a folder (much faster than DocumentFile.listFiles). */
    private fun children(context: Context, tree: Uri, parentId: String): List<Child> {
        val out = ArrayList<Child>()
        val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        try {
            context.contentResolver.query(
                childUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    out.add(Child(c.getString(0) ?: continue, c.getString(1) ?: continue, c.getString(2) ?: ""))
                }
            }
        } catch (e: Exception) {
            // unreadable folder: return what we have
        }
        return out
    }

    private fun isImage(c: Child): Boolean {
        if (c.mime.startsWith("image/")) return true
        val n = c.name.lowercase()
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp")
    }

    private fun effectFolderId(context: Context, tree: Uri, effect: String): String? {
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        return children(context, tree, rootId)
            .firstOrNull { it.mime == DocumentsContract.Document.MIME_TYPE_DIR && it.name == effect }?.id
    }

    fun images(context: Context, rootUri: String, effect: String): List<Img> {
        if (rootUri.isBlank()) return emptyList()
        return try {
            val tree = Uri.parse(rootUri)
            val dirId = effectFolderId(context, tree, effect) ?: return emptyList()
            children(context, tree, dirId)
                .filter { it.mime != DocumentsContract.Document.MIME_TYPE_DIR && isImage(it) }
                .sortedBy { it.name.lowercase() }
                .map { Img(it.name, DocumentsContract.buildDocumentUriUsingTree(tree, it.id)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun file(context: Context, rootUri: String, effect: String, name: String): Img? =
        images(context, rootUri, effect).firstOrNull { it.name == name }

    private val thumbCache = object : LruCache<String, Bitmap>(48) {}

    /** Same as [bitmap] but remembers recent results, so scrolling the list is instant. */
    fun cachedBitmap(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        val key = "$uri@$maxSide"
        thumbCache.get(key)?.let { return it }
        val b = bitmap(context, uri, maxSide) ?: return null
        thumbCache.put(key, b)
        return b
    }

    /** Reads an image at a reduced size (for thumbnails and the big preview). */
    fun bitmap(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        return try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                null
            } else {
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Reads an image and returns a JPEG no bigger than ~1.8 MB, so it uploads
     * fast and fits the server limit. Transparent areas become white.
     */
    fun jpegFor(context: Context, file: Img, maxSide: Int = 1280): ByteArray? {
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
