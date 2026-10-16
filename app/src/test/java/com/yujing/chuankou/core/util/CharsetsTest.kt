package com.yujing.chuankou.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 解码器行为测试
 *
 * 核心约定：字符集解不出来的字节输出 `\xNN`，**绝不**输出 U+FFFD 替换符「�」。
 */
class CharsetsTest {

    // ---------------- 一次性解码 ----------------

    @Test
    fun utf8ChineseDecodes() {
        val bytes = "串口调试".toByteArray(Charsets.UTF_8)
        assertEquals("串口调试", Charsets2.decode(bytes, "UTF-8"))
    }

    @Test
    fun utf8UndecodableByteBecomesEscape() {
        // 0xA1 不是合法的 UTF-8 起始字节
        assertEquals("\\xA1", Charsets2.decode(byteArrayOf(0xA1.toByte()), "UTF-8"))
    }

    @Test
    fun utf8MixedTextKeepsGoodPartsAndEscapesBad() {
        val bytes = "AB".toByteArray(Charsets.UTF_8) + byteArrayOf(0xA1.toByte()) +
                "CD".toByteArray(Charsets.UTF_8)
        assertEquals("AB\\xA1CD", Charsets2.decode(bytes, "UTF-8"))
    }

    @Test
    fun utf8ConsecutiveBadBytesAllEscaped() {
        val bytes = byteArrayOf(0xA1.toByte(), 0xA2.toByte(), 0xFF.toByte())
        assertEquals("\\xA1\\xA2\\xFF", Charsets2.decode(bytes, "UTF-8"))
    }

    @Test
    fun gbkChineseDecodes() {
        assertEquals("中文测试", Charsets2.decode("中文测试".toByteArray(charset("GBK")), "GBK"))
    }

    @Test
    fun usAsciiHighByteBecomesEscape() {
        assertEquals("A", Charsets2.decode(byteArrayOf(0x41), "US-ASCII"))
        assertEquals("\\xA1", Charsets2.decode(byteArrayOf(0xA1.toByte()), "US-ASCII"))
    }

    @Test
    fun latin1HasNoUndecodableByte() {
        // ISO-8859-1 每个字节都能映射，不应出现转义标记
        val out = Charsets2.decode(ByteArray(256) { it.toByte() }, "ISO-8859-1")
        assertEquals(256, out.length)
        assertFalse(out.contains("\\x"))
    }

    @Test
    fun oneShotDecodingEscapesTruncatedTail() {
        // 一次性解码没有"下一包"，末尾半个多字节序列必须转义输出，不能静默丢弃
        assertEquals("\\xE4\\xB8", Charsets2.decode(byteArrayOf(0xE4.toByte(), 0xB8.toByte()), "UTF-8"))
        assertEquals("\\xD6", Charsets2.decode(byteArrayOf(0xD6.toByte()), "GBK"))
    }

    @Test
    fun decodeNeverProducesReplacementChar() {
        // 0x80~0xFF 全量投喂 UTF-8，不应出现 U+FFFD
        val out = Charsets2.decode(ByteArray(128) { (0x80 + it).toByte() }, "UTF-8")
        assertFalse(out.contains('\uFFFD'))
        assertTrue(out.contains("\\x"))
    }

    // ---------------- 流式解码：断包续接 ----------------

    @Test
    fun utf8SplitAcrossPacketsIsReassembled() {
        val full = "串口".toByteArray(Charsets.UTF_8)
        val decoder = StreamingDecoder("UTF-8")
        // 每个字节单独喂一次，模拟极端断包
        val sb = StringBuilder()
        for (b in full) sb.append(decoder.decode(byteArrayOf(b)))
        assertEquals("串口", sb.toString())
    }

    @Test
    fun gbkSplitAcrossPacketsIsReassembled() {
        val full = "中文".toByteArray(charset("GBK"))
        val decoder = StreamingDecoder("GBK")
        val sb = StringBuilder()
        for (b in full) sb.append(decoder.decode(byteArrayOf(b)))
        assertEquals("中文", sb.toString())
    }

    @Test
    fun truncatedSequenceIsNotEmittedAsGarbage() {
        val decoder = StreamingDecoder("UTF-8")
        // 0xE4 是 3 字节序列的首字节，单独出现时应保留余留而不是输出转义
        assertEquals("", decoder.decode(byteArrayOf(0xE4.toByte())))
        // 补齐剩下的两个字节
        assertEquals("中", decoder.decode(byteArrayOf(0xB8.toByte(), 0xAD.toByte())))
    }

    @Test
    fun flushEmitsLeftoverAsEscape() {
        val decoder = StreamingDecoder("UTF-8")
        decoder.decode(byteArrayOf(0xE4.toByte()))
        assertEquals("\\xE4", decoder.flush())
        // flush 后再 flush 应为空
        assertEquals("", decoder.flush())
    }

    @Test
    fun resetDropsLeftover() {
        val decoder = StreamingDecoder("UTF-8")
        decoder.decode(byteArrayOf(0xE4.toByte()))
        decoder.reset()
        // 余留被丢弃后，补齐的两个字节应各自作为非法字节转义
        assertEquals("\\xB8\\xAD", decoder.decode(byteArrayOf(0xB8.toByte(), 0xAD.toByte())))
    }

    @Test
    fun utf16LeSplitAcrossPacketsIsReassembled() {
        val full = "AB".toByteArray(charset("UTF-16LE"))
        val decoder = StreamingDecoder("UTF-16LE")
        val sb = StringBuilder()
        for (b in full) sb.append(decoder.decode(byteArrayOf(b)))
        assertEquals("AB", sb.toString())
    }

    // ---------------- 流式解码：不可映射字节转义 ----------------

    @Test
    fun streamingEscapesUndecodableByte() {
        val decoder = StreamingDecoder("UTF-8")
        assertEquals("\\xA1", decoder.decode(byteArrayOf(0xA1.toByte())))
    }

    @Test
    fun streamingKeepsControlCharsIntact() {
        // 0x1B / 0x00 / 0x0D 在 UTF-8 下都是合法字符，解码器不应改写它们
        val decoder = StreamingDecoder("UTF-8")
        val out = decoder.decode(byteArrayOf(0x1B, 0x00, 0x0D, 0x0A))
        assertEquals("\u001B\u0000\r\n", out)
    }

    @Test
    fun streamingMixedValidAndInvalid() {
        val decoder = StreamingDecoder("UTF-8")
        val out = decoder.decode("OK".toByteArray(Charsets.UTF_8) + byteArrayOf(0xFE.toByte()) + "!".toByteArray(Charsets.UTF_8))
        assertEquals("OK\\xFE!", out)
    }

    @Test
    fun streamingNeverProducesReplacementChar() {
        val decoder = StreamingDecoder("UTF-8")
        val sb = StringBuilder()
        // 分 16 个包投喂 0x80~0xFF
        for (i in 0 until 16) {
            val chunk = ByteArray(8) { (0x80 + i * 8 + it).toByte() }
            sb.append(decoder.decode(chunk))
        }
        sb.append(decoder.flush())
        assertFalse(sb.toString().contains('\uFFFD'))
    }
}
