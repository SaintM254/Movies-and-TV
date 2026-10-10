package com.vidsource.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Caches website favicons (the "profile picture" of each source channel)
 * and generates a letter avatar when no icon is available yet.
 */
class FaviconStore(private val context: Context) {

    private val dir: File = File(context.filesDir, "favicons").apply { mkdirs() }

    fun fileFor(url: String): File {
        val host = try {
            Uri.parse(url).host ?: "source"
        } catch (e: Exception) {
            "source"
        }
        val safe = host.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(dir, "$safe.png")
    }

    fun save(bitmap: Bitmap, forUrl: String) {
        try {
            FileOutputStream(fileFor(forUrl)).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            // Non-fatal: placeholder avatar will be used.
        }
    }

    fun load(forUrl: String): Bitmap? = try {
        val file = fileFor(forUrl)
        if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    } catch (e: Exception) {
        null
    }

    /** Best-effort download of https://host/favicon.ico for the source switcher. */
    suspend fun fetchRemoteIcon(pageUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val host = Uri.parse(pageUrl).host ?: return@withContext null
            val conn = URL("https://$host/favicon.ico").openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.instanceFollowRedirects = true
            val bitmap = if (conn.responseCode == 200) {
                conn.inputStream.use { BitmapFactory.decodeStream(it) }
            } else {
                null
            }
            conn.disconnect()
            if (bitmap != null) save(bitmap, pageUrl)
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    /** Circular avatar with the source's first letter. */
    fun letterAvatar(name: String, forUrl: String): Bitmap {
        load(forUrl)?.let { return it }
        val size = 192
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = avatarColor(name)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = size * 0.42f
        val letter = name.trim().take(1).uppercase()
        val baseline = size / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(letter, size / 2f, baseline, paint)
        return bmp
    }

    /** Round bitmap from a captured favicon. */
    fun circleCrop(bitmap: Bitmap): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = android.graphics.Path()
        path.addCircle(size / 2f, size / 2f, size / 2f, android.graphics.Path.Direction.CW)
        canvas.clipPath(path)
        val left = (bitmap.width - size) / 2f
        val top = (bitmap.height - size) / 2f
        canvas.drawBitmap(bitmap, -left, -top, paint)
        return out
    }

    private fun avatarColor(name: String): Int {
        val colors = listOf(
            "#C62828", "#6A1B9A", "#283593", "#00695C",
            "#EF6C00", "#4527A0", "#AD1457", "#2E7D32"
        )
        val idx = (name.hashCode() % colors.size + colors.size) % colors.size
        return Color.parseColor(colors[idx])
    }
}
