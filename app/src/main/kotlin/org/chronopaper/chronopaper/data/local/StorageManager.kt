package org.chronopaper.chronopaper.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.chronopaper.chronopaper.data.parser.HeicFrameExtractor
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

class StorageManager(
    private val context: Context
) {
    private val appContext = context.applicationContext

    private val wallpapersRootDir: File
        get() = File(appContext.filesDir, "wallpapers").apply { mkdirs() }

    private val pairsDir: File
        get() = File(wallpapersRootDir, "pairs").apply { mkdirs() }

    private val heicDir: File
        get() = File(wallpapersRootDir, "heic").apply { mkdirs() }

    fun getHeicPackDirectory(packId: String): File =
        File(heicDir, packId).apply { mkdirs() }

    suspend fun copyAndOptimizeImage(
        sourceUri: Uri,
        pairId: String,
        isDark: Boolean,
        targetWidth: Int,
        targetHeight: Int
    ): File = withContext(Dispatchers.IO) {
        val suffix = if (isDark) "dark" else "light"
        val outFile = File(pairsDir, "${pairId}_$suffix.webp")
        val tempFile = File(pairsDir, "${pairId}_$suffix.webp.tmp")

        val source = ImageDecoder.createSource(appContext.contentResolver, sourceUri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val srcW = info.size.width
            val srcH = info.size.height
            val scale = HeicFrameExtractor.computeOptimalScale(
                srcWidth = srcW,
                srcHeight = srcH,
                targetWidth = targetWidth,
                targetHeight = targetHeight
            )
            if (scale < 1f) {
                decoder.setTargetSize(
                    (srcW * scale).roundToInt().coerceAtLeast(1),
                    (srcH * scale).roundToInt().coerceAtLeast(1)
                )
            }
        }

        try {
            FileOutputStream(tempFile).use { output ->
                val ok = bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 92, output)
                check(ok) { "Failed to compress image $sourceUri to WebP" }
            }
            if (!tempFile.renameTo(outFile)) {
                tempFile.copyTo(outFile, overwrite = true)
                tempFile.delete()
            }
        } catch (t: Throwable) {
            tempFile.delete()
            throw t
        } finally {
            bitmap.recycle()
        }
        outFile
    }

    suspend fun readUriBytes(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Unable to open InputStream for $uri")
    }

    suspend fun deletePairFiles(lightPath: String, darkPath: String) = withContext(Dispatchers.IO) {
        File(lightPath).delete()
        File(darkPath).delete()
    }

    suspend fun deleteHeicPackFiles(packId: String) = withContext(Dispatchers.IO) {
        File(heicDir, packId).deleteRecursively()
    }
}