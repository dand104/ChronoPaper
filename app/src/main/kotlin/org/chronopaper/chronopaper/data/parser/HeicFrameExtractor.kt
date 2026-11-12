package org.chronopaper.chronopaper.data.parser

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object HeicFrameExtractor {

    private const val MAX_DECODE_DIMENSION = 3840
    private const val MAX_META_BOX_BYTES = 16 * 1024 * 1024

    data class HeicContainerLayout(
        val pitmBoxOffset: Long,
        val pitmHeaderSize: Int,
        val pitmVersion: Int,
        val originalPitmId: Int,
        val masterItemIds: List<Int>
    )

    fun inspectContainer(heicFile: File): HeicContainerLayout {
        RandomAccessFile(heicFile, "r").use { raf ->
            val fileLength = raf.length()
            var offset = 0L
            while (offset + 8L <= fileLength) {
                val (boxSize, headerSize) = readBoxHeader(raf, offset, fileLength)
                if (boxSize < headerSize || offset + boxSize > fileLength) break
                val boxType = readFourCc(raf, offset + 4L)

                if (boxType == "meta") {
                    val payloadStart = offset + headerSize + 4L
                    val metaEnd = offset + boxSize
                    val payloadSize = (metaEnd - payloadStart).toInt()
                    require(payloadSize in 8..MAX_META_BOX_BYTES) {
                        "Invalid or oversized 'meta' box ($payloadSize bytes)"
                    }
                    val metaBytes = ByteArray(payloadSize)
                    raf.seek(payloadStart)
                    raf.readFully(metaBytes)
                    return parseMetaBox(
                        heicBytes = metaBytes,
                        metaStart = 0,
                        metaEnd = metaBytes.size,
                        baseFileOffset = payloadStart
                    )
                }
                offset += boxSize
            }
        }
        throw IllegalArgumentException("Could not locate 'meta' box in HEIC container")
    }

    fun overwritePitmId(
        heicFile: File,
        pitmBoxOffset: Long,
        pitmHeaderSize: Int = 8,
        newItemId: Int
    ) {
        RandomAccessFile(heicFile, "rw").use { raf ->
            val versionOffset = pitmBoxOffset + pitmHeaderSize
            val idOffset = versionOffset + 4L
            require(versionOffset >= 0L && idOffset + 4L <= raf.length()) {
                "pitm offset out of bounds: $pitmBoxOffset"
            }

            raf.seek(versionOffset)
            val version = raf.readUnsignedByte()
            raf.seek(idOffset)
            if (version == 0) {
                raf.writeByte((newItemId ushr 8) and 0xFF)
                raf.writeByte(newItemId and 0xFF)
            } else {
                raf.writeInt(newItemId)
            }
        }
    }

    fun extractFrameToWebp(
        heicFile: File,
        layout: HeicContainerLayout,
        frameIndex: Int,
        targetWidth: Int,
        targetHeight: Int,
        outputFile: File
    ) {
        if (layout.masterItemIds.size > 1 || frameIndex > 0) {
            val targetItemId = layout.masterItemIds.getOrElse(frameIndex) { layout.originalPitmId }
            overwritePitmId(
                heicFile = heicFile,
                pitmBoxOffset = layout.pitmBoxOffset,
                pitmHeaderSize = layout.pitmHeaderSize,
                newItemId = targetItemId
            )
        }

        val source = ImageDecoder.createSource(heicFile)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val srcW = info.size.width
            val srcH = info.size.height
            val scale = computeOptimalScale(
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

        outputFile.parentFile?.mkdirs()
        val tempFile = File(outputFile.parentFile, "${outputFile.name}.tmp")
        try {
            FileOutputStream(tempFile).use { out ->
                val ok = bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 92, out)
                check(ok) { "Failed to compress frame $frameIndex to WebP" }
            }
            if (!tempFile.renameTo(outputFile)) {
                tempFile.copyTo(outputFile, overwrite = true)
                tempFile.delete()
            }
        } catch (t: Throwable) {
            tempFile.delete()
            throw t
        } finally {
            bitmap.recycle()
        }
    }

    internal fun computeOptimalScale(
        srcWidth: Int,
        srcHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): Float {
        if (srcWidth <= 0 || srcHeight <= 0) return 1f
        val maxSrcDim = max(srcWidth, srcHeight).toFloat()
        val hardwareLimitScale = (MAX_DECODE_DIMENSION.toFloat() / maxSrcDim).coerceAtMost(1f)

        if (targetWidth <= 0 || targetHeight <= 0) {
            return hardwareLimitScale
        }
        val coverScale = max(
            targetWidth.toFloat() / srcWidth.toFloat(),
            targetHeight.toFloat() / srcHeight.toFloat()
        ).coerceAtMost(1f)

        return min(coverScale, hardwareLimitScale)
    }

    private fun parseMetaBox(
        heicBytes: ByteArray,
        metaStart: Int,
        metaEnd: Int,
        baseFileOffset: Long
    ): HeicContainerLayout {
        var pitmOffset = -1L
        var pitmHeaderSize = 8
        var pitmVersion = 0
        var masterIds: List<Int> = emptyList()
        var originalPitmId = 1

        var offset = metaStart
        while (offset + 8 <= metaEnd) {
            val (boxSize, headerSize) = readBoxHeader(heicBytes, offset, metaEnd)
            if (boxSize < headerSize || offset + boxSize > metaEnd) break
            val boxType = readFourCc(heicBytes, offset + 4)

            when (boxType) {
                "pitm" -> {
                    if (offset + headerSize + 4 <= metaEnd) {
                        pitmOffset = baseFileOffset + offset.toLong()
                        pitmHeaderSize = headerSize
                        pitmVersion = heicBytes[offset + headerSize].toInt() and 0xFF
                        val idPos = offset + headerSize + 4
                        if (pitmVersion == 0 && idPos + 2 <= metaEnd) {
                            originalPitmId = ((heicBytes[idPos].toInt() and 0xFF) shl 8) or
                                    (heicBytes[idPos + 1].toInt() and 0xFF)
                        } else if (pitmVersion > 0 && idPos + 4 <= metaEnd) {
                            originalPitmId = ByteBuffer.wrap(heicBytes, idPos, 4)
                                .order(ByteOrder.BIG_ENDIAN)
                                .int
                        }
                    }
                }

                "iinf" -> {
                    masterIds = parseIinfBox(
                        heicBytes = heicBytes,
                        iinfOffset = offset,
                        iinfHeaderSize = headerSize,
                        iinfEnd = offset + boxSize
                    )
                }
            }
            offset += boxSize
        }

        require(pitmOffset != -1L) {
            "HEIC 'meta' box does not contain a valid 'pitm' box"
        }

        val resolvedMasterIds = masterIds.ifEmpty {
            if (originalPitmId > 0) listOf(originalPitmId) else listOf(1)
        }

        return HeicContainerLayout(
            pitmBoxOffset = pitmOffset,
            pitmHeaderSize = pitmHeaderSize,
            pitmVersion = pitmVersion,
            originalPitmId = originalPitmId,
            masterItemIds = resolvedMasterIds
        )
    }

    private fun parseIinfBox(
        heicBytes: ByteArray,
        iinfOffset: Int,
        iinfHeaderSize: Int,
        iinfEnd: Int
    ): List<Int> {
        if (iinfOffset + iinfHeaderSize + 6 > iinfEnd) return emptyList()
        val version = heicBytes[iinfOffset + iinfHeaderSize].toInt() and 0xFF
        val childrenStart = if (version == 0) {
            iinfOffset + iinfHeaderSize + 4 + 2
        } else {
            iinfOffset + iinfHeaderSize + 4 + 4
        }

        val gridIds = mutableListOf<Int>()
        val visibleCodecIds = mutableListOf<Int>()

        var offset = childrenStart

        while (offset + 8 <= iinfEnd) {
            val (boxSize, headerSize) = readBoxHeader(heicBytes, offset, iinfEnd)
            if (boxSize < headerSize || offset + boxSize > iinfEnd) break
            val boxType = readFourCc(heicBytes, offset + 4)

            if (boxType == "infe" && offset + headerSize + 4 <= offset + boxSize) {
                val infeVersion = heicBytes[offset + headerSize].toInt() and 0xFF
                val infeFlags = ((heicBytes[offset + headerSize + 1].toInt() and 0xFF) shl 16) or
                        ((heicBytes[offset + headerSize + 2].toInt() and 0xFF) shl 8) or
                        (heicBytes[offset + headerSize + 3].toInt() and 0xFF)
                val isHidden = (infeFlags and 0x01) != 0

                if (infeVersion == 2 && offset + headerSize + 12 <= offset + boxSize) {
                    val itemId = ((heicBytes[offset + headerSize + 4].toInt() and 0xFF) shl 8) or
                            (heicBytes[offset + headerSize + 5].toInt() and 0xFF)
                    val itemType = readFourCc(heicBytes, offset + headerSize + 8)
                    when {
                        !isHidden && itemType == "grid" -> gridIds.add(itemId)
                        !isHidden && (itemType == "hvc1" || itemType == "av01") ->
                            visibleCodecIds.add(itemId)
                    }
                } else if (infeVersion >= 3 && offset + headerSize + 14 <= offset + boxSize) {
                    val itemId = ByteBuffer.wrap(heicBytes, offset + headerSize + 4, 4)
                        .order(ByteOrder.BIG_ENDIAN)
                        .int
                    val itemType = readFourCc(heicBytes, offset + headerSize + 10)
                    when {
                        !isHidden && itemType == "grid" -> gridIds.add(itemId)
                        !isHidden && (itemType == "hvc1" || itemType == "av01") ->
                            visibleCodecIds.add(itemId)
                    }
                }
            }
            offset += boxSize
        }

        return gridIds.ifEmpty { visibleCodecIds }
    }

    private fun readBoxHeader(raf: RandomAccessFile, offset: Long, limit: Long): Pair<Long, Int> {
        if (offset + 8L > limit) return 0L to 8
        raf.seek(offset)
        val size32 = raf.readInt().toLong() and 0xFFFFFFFFL
        return when {
            size32 == 1L -> {
                if (offset + 16L > limit) return 0L to 16
                raf.seek(offset + 8L)
                val size64 = raf.readLong()
                if (size64 < 16L || size64 > (limit - offset)) return 0L to 16
                size64 to 16
            }

            size32 == 0L -> (limit - offset) to 8
            size32 < 8L || size32 > (limit - offset) -> 0L to 8
            else -> size32 to 8
        }
    }

    private fun readFourCc(raf: RandomAccessFile, offset: Long): String {
        val buf = ByteArray(4)
        raf.seek(offset)
        raf.readFully(buf)
        return String(buf, StandardCharsets.US_ASCII)
    }

    private fun readBoxHeader(bytes: ByteArray, offset: Int, limit: Int): Pair<Int, Int> {
        if (offset + 8 > limit) return 0 to 8
        val size32 = ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
        return when {
            size32 == 1L -> {
                if (offset + 16 > limit) return 0 to 16
                val size64 = ByteBuffer.wrap(bytes, offset + 8, 8).order(ByteOrder.BIG_ENDIAN).long
                if (size64 < 16L || size64 > (limit - offset).toLong()) return 0 to 16
                size64.toInt() to 16
            }

            size32 == 0L -> (limit - offset) to 8
            size32 < 8L || size32 > (limit - offset).toLong() -> 0 to 8
            else -> size32.toInt() to 8
        }
    }

    private fun readFourCc(bytes: ByteArray, offset: Int): String =
        String(bytes, offset, 4, StandardCharsets.US_ASCII)
}