package com.yujing.chuankou.core.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 转义序列解析
 *
 * 重点覆盖：`\0` 与八进制的统一处理（\012 必须解析成八进制 10，而不是 NUL + "12"）。
 */
class EscapeTest {

    private fun parse(text: String, cs: String = "UTF-8"): List<Int> =
        Escape.parse(text, cs, true).map { it.toInt() and 0xFF }

    @Test
    fun plainTextPassesThrough() {
        assertEquals(listOf(0x41, 0x42), parse("AB"))
    }

    @Test
    fun commonEscapes() {
        assertEquals(listOf(0x0D, 0x0A, 0x09), parse("\\r\\n\\t"))
        assertEquals(listOf(0x07, 0x08, 0x0C, 0x0B, 0x1B), parse("\\a\\b\\f\\v\\e"))
    }

    @Test
    fun nulIsZero() {
        assertEquals(listOf(0x00), parse("\\0"))
        // \0 后面跟非八进制数字时，只吃掉 "0"
        assertEquals(listOf(0x00, 0x38), parse("\\08")) // '8' = 0x38
    }

    @Test
    fun leadingZeroIsOctalNotNul() {
        // 与 C / 多数串口工具一致：\012 = 八进制 012 = 0x0A
        assertEquals(listOf(0x0A), parse("\\012"))
        assertEquals(listOf(0x0A), parse("\\12"))
        assertEquals(listOf(0x41), parse("\\101")) // 'A'
    }

    @Test
    fun octalMaxThreeDigits() {
        // "\0101" → 取前 3 位 "010" = 0o010 = 0x08，剩 '1' = 0x31
        assertEquals(listOf(0x08, 0x31), parse("\\0101"))
    }

    @Test
    fun hexEscapes() {
        assertEquals(listOf(0xA1), parse("\\xA1"))
        assertEquals(listOf(0xA1), parse("\\xa1"))
        assertEquals(listOf(0xFF, 0x00), parse("\\xFF\\x00"))
        // 只有一位十六进制也接受
        assertEquals(listOf(0x0F), parse("\\xF"))
    }

    @Test
    fun backslashAndQuotes() {
        assertEquals(listOf(0x5C), parse("\\\\"))
        assertEquals(listOf(0x27, 0x22, 0x3F), parse("\\'\\\"\\?"))
    }

    @Test
    fun unicodeEscapeUsesCharset() {
        // \u4e2d = 中，按 UTF-8 编码为 E4 B8 AD
        assertEquals(listOf(0xE4, 0xB8, 0xAD), parse("\\u4e2d", "UTF-8"))
    }

    @Test
    fun escapedBytesNotRecoded() {
        // \xA1 在 UTF-8 下不是合法序列，必须原样输出一个字节，不能被再编码
        assertEquals(listOf(0x41, 0xA1, 0x42), parse("A\\xA1B"))
    }

    @Test
    fun loneTrailingBackslashKept() {
        assertEquals(listOf(0x41, 0x5C), parse("A\\"))
    }

    @Test
    fun unknownEscapeKeepsChar() {
        // \q 不是合法转义，保留 'q'
        assertEquals(listOf(0x71), parse("\\q"))
    }

    @Test
    fun disabledKeepsEverythingLiteral() {
        assertArrayEquals(
            "AB\\r\\n".toByteArray(Charsets.UTF_8),
            Escape.parse("AB\\r\\n", "UTF-8", false)
        )
    }

    @Test
    fun mixedTextAndEscapes() {
        assertEquals(
            listOf(0x68, 0x69, 0x0D, 0x0A),
            parse("hi\\r\\n")
        )
    }

    @Test
    fun visibleEscapesControlChars() {
        assertEquals("a\\rb\\nc\\td", Escape.visible("a\rb\nc\td"))
        assertEquals("\\x1B", Escape.visible("\u001B"))
        assertEquals("\\0", Escape.visible("\u0000"))
    }
}
