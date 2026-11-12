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
    context: Context
) {
    private val appContext = context.applicationContext.createDeviceProtectedStorageContext()

    private val wallpapersRootDir: File
        get() = File(appContext.filesDir, "wallpapers").apply { mkdirs() }

    private val heicDir: File
        get() = File(wallpapersRootDir, "heic").apply { mkdirs() }
    private val pairsDir: File
        get() = File(wallpapersRootDir, "pairs").apply { mkdirs() }

    fun getHeicPackDirectory(packId: String): File =
        File(heicDir, packId).apply { mkdirs() }

    suspend fun copyAndOptimizeImages(
        sourceUris: List<Uri>,
        pairId: String,
        targetWidth: Int,
        targetHeight: Int
    ): List<File> = withContext(Dispatchers.IO) {
        require(sourceUris.isNotEmpty()) { "At least one image is required" }
        val savedFiles = mutableListOf<File>()
        try {
            sourceUris.forEachIndexed { index, uri ->
                val outFile = File(pairsDir, "${pairId}_frame_$index.webp")
                decodeAndCompressUriToFile(
                    sourceUri = uri,
                    outFile = outFile,
                    targetWidth = targetWidth,
                    targetHeight = targetHeight
                )
                savedFiles.add(outFile)
            }
            savedFiles
        } catch (t: Throwable) {
            savedFiles.forEach { it.delete() }
            throw t
        }
    }

    private fun decodeAndCompressUriToFile(
        sourceUri: Uri,
        outFile: File,
        targetWidth: Int,
        targetHeight: Int
    ): File {
        val tempFile = File(outFile.parentFile, "${outFile.name}.tmp")

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
        return outFile
    }

    suspend fun copyUriToTempFile(
        uri: Uri,
        prefix: String = "heic_import_"
    ): File = withContext(Dispatchers.IO) {
        val stagingDir = File(appContext.cacheDir, "heic_staging").apply { mkdirs() }
        val tempFile = File.createTempFile(prefix, ".heic", stagingDir)
        try {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            } ?: throw IllegalArgumentException("Unable to open InputStream for $uri")
            tempFile
        } catch (t: Throwable) {
            tempFile.delete()
            throw t
        }
    }

    suspend fun deleteSetFiles(paths: List<String>) = withContext(Dispatchers.IO) {
        paths.distinct().forEach { path -> File(path).delete() }
    }

    suspend fun deleteHeicPackFiles(packId: String) = withContext(Dispatchers.IO) {
        File(heicDir, packId).deleteRecursively()
    }
}