package com.escribe.diario

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/** Portadas: se copian reducidas a la carpeta privada de la app. */
object Covers {
    private val cache = LruCache<String, ImageBitmap>(30)

    fun import(ctx: Context, uri: Uri): String? = runCatching {
        val cr = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
        var bmp = cr.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }!!
        val rot = runCatching {
            cr.openInputStream(uri)!!.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        }.getOrDefault(0f)
        if (rot != 0f) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot) }, true)
        val dir = File(ctx.filesDir, "covers").apply { mkdirs() }
        val out = File(dir, "${System.currentTimeMillis()}.jpg")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        out.absolutePath
    }.getOrNull()

    fun load(path: String?): ImageBitmap? {
        if (path == null) return null
        cache.get(path)?.let { return it }
        val f = File(path); if (!f.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1100) sample *= 2
        val img = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap() ?: return null
        cache.put(path, img)
        return img
    }

    fun delete(path: String?) { if (path != null) { cache.remove(path); File(path).delete() } }
}
