package org.chronopaper.chronopaper.data.parser

import org.chronopaper.chronopaper.domain.model.WallpaperMode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import kotlin.math.PI
import kotlin.math.cos

data class HeicSolarEntry(
    val frameIndex: Int,
    val altitude: Double,
    val azimuth: Double
)

data class HeicAppleMetadata(
    val mode: WallpaperMode,
    val solarEntries: List<HeicSolarEntry>,
    val lightIndex: Int?,
    val darkIndex: Int?
)

sealed interface PlistValue {
    data object Null : PlistValue
    data class BooleanVal(val value: Boolean) : PlistValue
    data class IntVal(val value: Long) : PlistValue
    data class DoubleVal(val value: Double) : PlistValue
    data class StringVal(val value: String) : PlistValue
    data class DataVal(val value: ByteArray) : PlistValue {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as DataVal
            return value.contentEquals(other.value)
        }
        override fun hashCode(): Int = value.contentHashCode()
    }

    data class ArrayVal(val items: List<PlistValue>) : PlistValue
    data class DictVal(val entries: Map<String, PlistValue>) : PlistValue

    fun asInt(): Int? = when (this) {
        is IntVal -> value.toInt()
        is DoubleVal -> value.toInt()
        else -> null
    }

    fun asDouble(): Double? = when (this) {
        is DoubleVal -> value
        is IntVal -> value.toDouble()
        else -> null
    }

    fun asDict(): Map<String, PlistValue>? = (this as? DictVal)?.entries
    fun asArray(): List<PlistValue>? = (this as? ArrayVal)?.items
}

object BinaryPlistDecoder {

    private const val HEADER_MAGIC = "bplist00"
    private const val TRAILER_SIZE = 32

    fun decodeAppleMetadata(plistBytes: ByteArray): HeicAppleMetadata {
        val root = decode(plistBytes).asDict()
            ?: throw IllegalArgumentException("Root of Apple plist must be a dictionary")

        val solarList = (root["si"] ?: root["ap"])?.asArray()
        val aprDict = root["o"]?.asDict() ?: root["apr"]?.asDict() ?: root
        val timeList = root["ti"]?.asArray()

        val lightIndex = aprDict["d"]?.asInt()
        val darkIndex = aprDict["n"]?.asInt()

        if (!solarList.isNullOrEmpty()) {
            val entries = solarList.mapNotNull { item ->
                val dict = item.asDict() ?: return@mapNotNull null
                val index = dict["i"]?.asInt() ?: return@mapNotNull null
                val altitude = dict["a"]?.asDouble() ?: return@mapNotNull null
                val azimuth = dict["z"]?.asDouble() ?: return@mapNotNull null
                HeicSolarEntry(
                    frameIndex = index,
                    altitude = altitude,
                    azimuth = azimuth
                )
            }
            if (entries.isNotEmpty()) {
                return HeicAppleMetadata(
                    mode = WallpaperMode.SOLAR,
                    solarEntries = entries,
                    lightIndex = lightIndex ?: entries.maxByOrNull { it.altitude }?.frameIndex,
                    darkIndex = darkIndex ?: entries.minByOrNull { it.altitude }?.frameIndex
                )
            }
        }

        if (!timeList.isNullOrEmpty()) {
            val entries = timeList.mapNotNull { item ->
                val dict = item.asDict() ?: return@mapNotNull null
                val index = dict["i"]?.asInt() ?: return@mapNotNull null
                val timeFraction = dict["t"]?.asDouble()?.coerceIn(0.0, 1.0) ?: return@mapNotNull null
                HeicSolarEntry(
                    frameIndex = index,
                    altitude = -cos(timeFraction * 2.0 * PI) * 60.0,
                    azimuth = (timeFraction * 360.0).mod(360.0)
                )
            }
            if (entries.isNotEmpty()) {
                return HeicAppleMetadata(
                    mode = WallpaperMode.TIME,
                    solarEntries = entries,
                    lightIndex = lightIndex ?: entries.maxByOrNull { it.altitude }?.frameIndex,
                    darkIndex = darkIndex ?: entries.minByOrNull { it.altitude }?.frameIndex
                )
            }
        }

        if (lightIndex != null || darkIndex != null) {
            return HeicAppleMetadata(
                mode = WallpaperMode.THEME,
                solarEntries = emptyList(),
                lightIndex = lightIndex ?: darkIndex,
                darkIndex = darkIndex ?: lightIndex
            )
        }

        throw IllegalArgumentException("Unsupported Apple desktop plist structure: keys=${root.keys}")
    }

    fun decode(bytes: ByteArray): PlistValue {
        require(bytes.size >= HEADER_MAGIC.length + TRAILER_SIZE) {
            "Input too short to be a valid bplist00 (${bytes.size} bytes)"
        }
        val header = String(bytes, 0, HEADER_MAGIC.length, StandardCharsets.US_ASCII)
        require(header == HEADER_MAGIC) {
            "Invalid bplist header: $header"
        }

        val trailerOffset = bytes.size - TRAILER_SIZE
        val offsetIntSize = bytes[trailerOffset + 6].toInt() and 0xFF
        val objectRefSize = bytes[trailerOffset + 7].toInt() and 0xFF
        val numObjectsLong = readUnsignedLong(bytes, trailerOffset + 8, 8)
        val topObjectLong = readUnsignedLong(bytes, trailerOffset + 16, 8)
        val offsetTableStartLong = readUnsignedLong(bytes, trailerOffset + 24, 8)

        require(offsetIntSize in 1..8 && objectRefSize in 1..8) {
            "Invalid trailer sizes: offsetIntSize=$offsetIntSize, objectRefSize=$objectRefSize"
        }
        require(numObjectsLong in 1..100_000 && topObjectLong in 0..<numObjectsLong) {
            "Invalid object counts: numObjects=$numObjectsLong, topObject=$topObjectLong"
        }
        val numObjects = numObjectsLong.toInt()
        val topObjectIndex = topObjectLong.toInt()
        val offsetTableStart = offsetTableStartLong.toInt()

        require(
            offsetTableStart >= HEADER_MAGIC.length &&
                    offsetTableStart + numObjects * offsetIntSize <= trailerOffset
        ) {
            "Corrupted offset table bounds in bplist00"
        }

        val offsets = IntArray(numObjects) { i ->
            val objOffset = readUnsignedLong(
                bytes = bytes,
                offset = offsetTableStart + i * offsetIntSize,
                length = offsetIntSize
            ).toInt()
            require(objOffset in HEADER_MAGIC.length..<trailerOffset) {
                "Object $i offset out of bounds:$objOffset"
            }
            objOffset
        }

        return parseObject(
            bytes = bytes,
            offsets = offsets,
            objectRefSize = objectRefSize,
            objIndex = topObjectIndex,
            visited = mutableSetOf()
        )
    }

    private fun parseObject(
        bytes: ByteArray,
        offsets: IntArray,
        objectRefSize: Int,
        objIndex: Int,
        visited: MutableSet<Int>
    ): PlistValue {
        require(objIndex in offsets.indices) {
            "Object index out of bounds: $objIndex"
        }
        require(visited.add(objIndex)) {
            "Circular reference in binary plist at object $objIndex"
        }
        try {
            val offset = offsets[objIndex]
            val marker = bytes[offset].toInt() and 0xFF
            val objType = (marker and 0xF0) shr 4
            val objInfo = marker and 0x0F

            return when (objType) {
                0x0 -> when (objInfo) {
                    0x0 -> PlistValue.Null
                    0x8 -> PlistValue.BooleanVal(false)
                    0x9 -> PlistValue.BooleanVal(true)
                    else -> PlistValue.Null
                }

                0x1 -> {
                    val byteCount = 1 shl objInfo
                    require(offset + 1 + byteCount <= bytes.size) {
                        "Int object out of bounds"
                    }
                    PlistValue.IntVal(readSignedLong(bytes, offset + 1, byteCount))
                }

                0x2 -> {
                    val byteCount = 1 shl objInfo
                    require(offset + 1 + byteCount <= bytes.size) {
                        "Real object out of bounds"
                    }
                    val buffer = ByteBuffer.wrap(bytes, offset + 1, byteCount)
                        .order(ByteOrder.BIG_ENDIAN)
                    val doubleVal = when (byteCount) {
                        4 -> buffer.float.toDouble()
                        8 -> buffer.double
                        else -> error("Unsupported real byteCount=$byteCount")
                    }
                    PlistValue.DoubleVal(doubleVal)
                }

                0x4 -> {
                    val (length, dataStart) = readLengthAndOffset(bytes, offset, objInfo)
                    require(length >= 0 && dataStart + length <= bytes.size) {
                        "Data object out of bounds"
                    }
                    PlistValue.DataVal(bytes.copyOfRange(dataStart, dataStart + length))
                }

                0x5 -> {
                    val (length, strStart) = readLengthAndOffset(bytes, offset, objInfo)
                    require(length >= 0 && strStart + length <= bytes.size) {
                        "ASCII string out of bounds"
                    }
                    PlistValue.StringVal(String(bytes, strStart, length, StandardCharsets.US_ASCII))
                }

                0x6 -> {
                    val (charCount, strStart) = readLengthAndOffset(bytes, offset, objInfo)
                    val byteLength = charCount * 2
                    require(byteLength >= 0 && strStart + byteLength <= bytes.size) {
                        "UTF-16 string out of bounds"
                    }
                    PlistValue.StringVal(
                        String(bytes, strStart, byteLength, StandardCharsets.UTF_16BE)
                    )
                }

                0xA -> {
                    val (count, refsStart) = readLengthAndOffset(bytes, offset, objInfo)
                    require(count >= 0 && refsStart + count * objectRefSize <= bytes.size) {
                        "Array object out of bounds"
                    }
                    val items = List(count) { i ->
                        val ref = readUnsignedLong(
                            bytes = bytes,
                            offset = refsStart + i * objectRefSize,
                            length = objectRefSize
                        ).toInt()
                        parseObject(bytes, offsets, objectRefSize, ref, visited)
                    }
                    PlistValue.ArrayVal(items)
                }

                0xD -> {
                    val (count, keysStart) = readLengthAndOffset(bytes, offset, objInfo)
                    val valsStart = keysStart + count * objectRefSize
                    require(count >= 0 && valsStart + count * objectRefSize <= bytes.size) {
                        "Dictionary object out of bounds"
                    }
                    val map = LinkedHashMap<String, PlistValue>(count)
                    for (i in 0..<count) {
                        val keyRef = readUnsignedLong(
                            bytes = bytes,
                            offset = keysStart + i * objectRefSize,
                            length = objectRefSize
                        ).toInt()
                        val valRef = readUnsignedLong(
                            bytes = bytes,
                            offset = valsStart + i * objectRefSize,
                            length = objectRefSize
                        ).toInt()
                        val keyObj = parseObject(bytes, offsets, objectRefSize, keyRef, visited)
                        val keyStr = (keyObj as? PlistValue.StringVal)?.value ?: keyObj.toString()
                        map[keyStr] = parseObject(bytes, offsets, objectRefSize, valRef, visited)
                    }
                    PlistValue.DictVal(map)
                }

                else -> PlistValue.Null
            }
        } finally {
            visited.remove(objIndex)
        }
    }

    private fun readLengthAndOffset(
        bytes: ByteArray,
        markerOffset: Int,
        objInfo: Int
    ): Pair<Int, Int> {
        if (objInfo != 0x0F) {
            return objInfo to (markerOffset + 1)
        }
        require(markerOffset + 2 <= bytes.size) { "Length marker out of bounds" }
        val intMarker = bytes[markerOffset + 1].toInt() and 0xFF
        val intByteCount = 1 shl (intMarker and 0x0F)
        require(markerOffset + 2 + intByteCount <= bytes.size) { "Length value out of bounds" }
        val length = readUnsignedLong(bytes, markerOffset + 2, intByteCount).toInt()
        return length to (markerOffset + 2 + intByteCount)
    }

    private fun readUnsignedLong(bytes: ByteArray, offset: Int, length: Int): Long {
        var result = 0L
        for (i in 0..<length) {
            result = (result shl 8) or (bytes[offset + i].toLong() and 0xFFL)
        }
        return result
    }

    private fun readSignedLong(bytes: ByteArray, offset: Int, length: Int): Long {
        return when (length) {
            1 -> bytes[offset].toLong()
            2 -> ((bytes[offset].toInt() shl 8) or (bytes[offset + 1].toInt() and 0xFF))
                .toShort()
                .toLong()
            4 -> ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.BIG_ENDIAN).int.toLong()
            8 -> ByteBuffer.wrap(bytes, offset, 8).order(ByteOrder.BIG_ENDIAN).long
            16 -> ByteBuffer.wrap(bytes, offset + 8, 8).order(ByteOrder.BIG_ENDIAN).long
            else -> readUnsignedLong(bytes, offset, length)
        }
    }
}