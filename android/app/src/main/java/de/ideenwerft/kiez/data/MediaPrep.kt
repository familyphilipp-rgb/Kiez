package de.ideenwerft.kiez.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// Ein ausgewähltes Foto oder Video, bereit zum Hochladen
data class PickedMedia(val uri: Uri, val mime: String, val isVideo: Boolean)

private const val MAX_SIDE = 1600

// Fotos verkleinern und neu als JPEG speichern; das entfernt auch EXIF-Daten wie den GPS-Ort
suspend fun prepareImage(context: Context, source: Uri): PickedMedia = withContext(Dispatchers.IO) {
    val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, source)) { decoder, info, _ ->
        val w = info.size.width; val h = info.size.height
        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(w, h))
        if (scale < 1f) decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
    val out = captureFile(context, "jpg")
    out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    bitmap.recycle()
    PickedMedia(Uri.fromFile(out), "image/jpeg", isVideo = false)
}

fun videoMedia(context: Context, uri: Uri): PickedMedia {
    val mime = context.contentResolver.getType(uri)?.takeIf { it.startsWith("video/") } ?: "video/mp4"
    return PickedMedia(uri, mime, isVideo = true)
}

fun captureFile(context: Context, ext: String): File {
    val dir = File(context.cacheDir, "capture").apply { mkdirs() }
    // Alte Aufnahmen aufräumen
    dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 3_600_000 }?.forEach { it.delete() }
    return File(dir, "kiez_${System.currentTimeMillis()}.$ext")
}

fun captureUri(context: Context, ext: String): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", captureFile(context, ext))
