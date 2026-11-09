package org.chronopaper.chronopaper.data.parser

import org.junit.Test
import java.nio.charset.StandardCharsets

class BinaryPlistDecoderTest {

    @Test(expected = IllegalArgumentException::class)
    fun `decode too short byte array throws exception`() {
        BinaryPlistDecoder.decode(byteArrayOf(1, 2, 3))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decode invalid header magic throws exception`() {
        val bytes = ByteArray(40) { 0 }
        System.arraycopy("invalid!".toByteArray(StandardCharsets.US_ASCII), 0, bytes, 0, 8)
        BinaryPlistDecoder.decode(bytes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decode corrupted offset table throws exception`() {
        val bytes = ByteArray(40) { 0 }
        System.arraycopy("bplist00".toByteArray(StandardCharsets.US_ASCII), 0, bytes, 0, 8)
        BinaryPlistDecoder.decode(bytes)
    }
}