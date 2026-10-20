package com.amr.imageswap

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Talks to your Cloudflare Worker. */
object Net {

    private fun connect(url: String, method: String, secret: String?): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 8000
        c.readTimeout = 15000
        c.useCaches = false
        if (secret != null) c.setRequestProperty("X-Key", secret)
        return c
    }

    /** Replaces the image in a slot. */
    suspend fun put(base: String, secret: String, slot: String, jpeg: ByteArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val c = connect("$base/i/$slot", "PUT", secret)
                try {
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", "image/jpeg")
                    c.setFixedLengthStreamingMode(jpeg.size)
                    c.outputStream.use { it.write(jpeg) }
                    val code = c.responseCode
                    if (code !in 200..299) error("HTTP $code")
                } finally {
                    c.disconnect()
                }
                Unit
            }
        }

    /** Puts a slot back to the blank image. */
    suspend fun delete(base: String, secret: String, slot: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val c = connect("$base/i/$slot", "DELETE", secret)
                try {
                    val code = c.responseCode
                    if (code !in 200..299) error("HTTP $code")
                } finally {
                    c.disconnect()
                }
                Unit
            }
        }

    /** Checks the server answers and that the secret key is accepted. */
    suspend fun selfTest(base: String, secret: String): String = withContext(Dispatchers.IO) {
        try {
            val c = connect("$base/", "GET", null)
            val body = try {
                if (c.responseCode != 200) return@withContext "Server answered HTTP ${c.responseCode}"
                c.inputStream.bufferedReader().readText().trim()
            } finally {
                c.disconnect()
            }
            if (body != "ok") return@withContext "Unexpected answer from the server"

            val p = connect("$base/i/selftest", "PUT", secret)
            val code = try {
                p.doOutput = true
                p.setRequestProperty("Content-Type", "image/jpeg")
                p.setFixedLengthStreamingMode(1)
                p.outputStream.use { it.write(1) }
                p.responseCode
            } finally {
                p.disconnect()
            }
            if (code == 403) return@withContext "Server is OK, but the secret key is WRONG"
            if (code !in 200..299) return@withContext "Upload test failed: HTTP $code"
            delete(base, secret, "selftest")
            "Server OK and secret key OK"
        } catch (e: Exception) {
            "Could not reach the server: ${e.message}"
        }
    }
}
