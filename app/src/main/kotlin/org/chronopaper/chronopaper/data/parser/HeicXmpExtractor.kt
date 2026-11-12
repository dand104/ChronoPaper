package org.chronopaper.chronopaper.data.parser

import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import kotlin.math.PI
import kotlin.math.cos

object HeicXmpExtractor {
    private val SOLAR_ATTR_REGEX = Regex(
        """apple_desktop:solar\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )
    private val SOLAR_TAG_REGEX = Regex(
        """<apple_desktop:solar>\s*([^<]+?)\s*</apple_desktop:solar>""",
        RegexOption.IGNORE_CASE
    )
    private val APR_ATTR_REGEX = Regex(
        """apple_desktop:apr\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )
    private val APR_TAG_REGEX = Regex(
        """<apple_desktop:apr>\s*([^<]+?)\s*</apple_desktop:apr>""",
        RegexOption.IGNORE_CASE
    )
    private val H24_ATTR_REGEX = Regex(
        """apple_desktop:h24\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )
    private val H24_TAG_REGEX = Regex(
        """<apple_desktop:h24>\s*([^<]+?)\s*</apple_desktop:h24>""",
        RegexOption.IGNORE_CASE
    )
    private val WHITESPACE_REGEX = Regex("""\s+""")

    fun extractMetadata(
        heicFile: File,
        fallbackFrameCount: Int = 1
    ): HeicAppleMetadata {
        val xmpXml = extractRawXmpXml(heicFile)
        val base64Payload = xmpXml?.let(::findBase64Payload)
        if (base64Payload != null) {
            runCatching {
                val cleanBase64 = base64Payload.replace(WHITESPACE_REGEX, "")
                val plistBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                BinaryPlistDecoder.decodeAppleMetadata(plistBytes)
            }.onSuccess { return it }
        }
        return createFallbackMetadata(fallbackFrameCount)
    }

    internal fun createFallbackMetadata(frameCount: Int): HeicAppleMetadata {
        val count = frameCount.coerceAtLeast(1)
        return when (count) {
            1 -> HeicAppleMetadata(
                solarEntries = emptyList(),
                lightIndex = 0,
                darkIndex = 0
            )

            2 -> HeicAppleMetadata(
                solarEntries = emptyList(),
                lightIndex = 0,
                darkIndex = 1
            )

            else -> {
                val entries = List(count) { index ->
                    val progress = index.toDouble() / count.toDouble()
                    HeicSolarEntry(
                        frameIndex = index,
                        altitude = -cos(progress * 2.0 * PI) * 60.0,
                        azimuth = (progress * 360.0).mod(360.0)
                    )
                }
                HeicAppleMetadata(
                    solarEntries = entries,
                    lightIndex = entries.maxByOrNull { it.altitude }?.frameIndex ?: (count / 2),
                    darkIndex = entries.minByOrNull { it.altitude }?.frameIndex ?: 0
                )
            }
        }
    }

    fun extractRawXmpXml(heicFile: File): String? {
        val exifXmp = runCatching {
            ExifInterface(heicFile).getAttribute(ExifInterface.TAG_XMP)
        }.getOrNull()

        if (!exifXmp.isNullOrBlank() && exifXmp.contains("apple_desktop", ignoreCase = true)) {
            return exifXmp
        }

        return runCatching {
            scanFileForXmpPacket(heicFile) ?: exifXmp
        }.getOrDefault(exifXmp)
    }

    private fun scanFileForXmpPacket(heicFile: File): String? {
        val startMarker = "<x:xmpmeta".toByteArray(StandardCharsets.US_ASCII)
        val endMarker = "</x:xmpmeta>".toByteArray(StandardCharsets.US_ASCII)
        val chunkSize = 64 * 1024
        val overlap = 64
        val maxXmpLength = 512 * 1024

        RandomAccessFile(heicFile, "r").use { raf ->
            val fileLen = raf.length()
            val buffer = ByteArray(chunkSize)
            var pos = 0L
            var startOffset = -1L

            while (pos < fileLen) {
                raf.seek(pos)
                val read = raf.read(buffer)
                if (read <= 0) break
                val slice = if (read == buffer.size) buffer else buffer.copyOf(read)

                if (startOffset == -1L) {
                    val idx = indexOfSubArray(slice, startMarker, 0)
                    if (idx != -1) {
                        startOffset = pos + idx
                        val afterStart = indexOfSubArray(slice, endMarker, idx + startMarker.size)
                        if (afterStart != -1) {
                            val endOffset = pos + afterStart + endMarker.size
                            val len = (endOffset - startOffset).toInt()
                            return String(slice, idx, len, StandardCharsets.UTF_8)
                        }
                    }
                } else {
                    val idx = indexOfSubArray(slice, endMarker, 0)
                    if (idx != -1) {
                        val endOffset = pos + idx + endMarker.size
                        val len = (endOffset - startOffset).toInt()
                        if (len in 1..maxXmpLength) {
                            val xmpBytes = ByteArray(len)
                            raf.seek(startOffset)
                            raf.readFully(xmpBytes)
                            return String(xmpBytes, StandardCharsets.UTF_8)
                        }
                        return null
                    }
                    if (pos - startOffset > maxXmpLength) return null
                }

                if (read < chunkSize) break
                pos += (chunkSize - overlap)
            }
        }
        return null
    }

    private fun findBase64Payload(xmpXml: String): String? {
        SOLAR_ATTR_REGEX.find(xmpXml)?.groupValues?.getOrNull(1)?.let { return it }
        SOLAR_TAG_REGEX.find(xmpXml)?.groupValues?.getOrNull(1)?.let { return it }
        APR_ATTR_REGEX.find(xmpXml)?.groupValues?.getOrNull(1)?.let { return it }
        APR_TAG_REGEX.find(xmpXml)?.groupValues?.getOrNull(1)?.let { return it }
        H24_ATTR_REGEX.find(xmpXml)?.groupValues?.getOrNull(1)?.let { return it }
        H24_TAG_REGEX.find(xmpXml)?.groupValues?.getOrNull(1)?.let { return it }
        return null
    }

    private fun indexOfSubArray(source: ByteArray, target: ByteArray, fromIndex: Int): Int {
        if (target.isEmpty() || source.size - fromIndex < target.size) return -1
        val first = target[0]
        val max = source.size - target.size
        var i = fromIndex.coerceAtLeast(0)
        while (i <= max) {
            if (source[i] != first) {
                while (++i <= max && source[i] != first) {
                    // scan until first byte match
                }
            }
            if (i <= max) {
                var j = i + 1
                val end = i + target.size
                var k = 1
                while (j < end && source[j] == target[k]) {
                    j++
                    k++
                }
                if (j == end) return i
            }
            i++
        }
        return -1
    }
}