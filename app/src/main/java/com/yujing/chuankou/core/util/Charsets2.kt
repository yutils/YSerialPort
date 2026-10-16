package com.yujing.chuankou.core.util

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** 不可解码字节的转义前缀 */
private const val ESCAPE_PREFIX = "\\x"

/**
 * 增量解码：字符集解不出来的字节转义为 `\xNN` 输出，
 * 而不是平台默认的 U+FFFD 替换符「�」。
 *
 * 之所以不用 `CodingErrorAction.REPLACE`：REPLACE 会把非法字节悄悄吞成 U+FFFD，
 * 调用方再也拿不到原始字节；改用 REPORT 可以拿到出错字节长度，再自己转义。
 */
private fun decodeEscaping(
    bytes: ByteArray,
    offset: Int,
    length: Int,
    charset: Charset
): String {
    if (length <= 0) return ""
    // ISO-8859-1 每个字节都能映射到 U+0000~U+00FF，不存在解不出来的字节
    if (charset == Charsets.ISO_8859_1) return String(bytes, offset, length, charset)

    val decoder = charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    val inBuf = ByteBuffer.wrap(bytes, offset, length)
    val sb = StringBuilder(length + 16)
    while (inBuf.hasRemaining()) {
        // 输出缓冲按最多 2 char/byte 估算，不够就再循环一次
        val outBuf = CharBuffer.allocate(maxOf(8, inBuf.remaining() * 2))
        val result = decoder.decode(inBuf, outBuf, false)
        outBuf.flip()
        sb.append(outBuf)
        when {
            result.isUnderflow -> break
            result.isOverflow -> continue
            else -> {
                // 非法 / 不可映射：把对应的原始字节转义输出后继续解码
                val len = result.length().coerceAtLeast(1)
                for (i in 0 until len) {
                    if (!inBuf.hasRemaining()) break
                    sb.append(ESCAPE_PREFIX).append(Hex.byteToHex(inBuf.get().toInt() and 0xFF))
                }
            }
        }
    }
    return sb.toString()
}

/**
 * 字符集工具 + 流式解码器
 *
 * 串口按包读取时，UTF-8 / GB18030 等多字节字符可能被切断，
 * 通过保留余留字节的方式保证不出现乱码。
 */
object Charsets2 {

    /** 常用字符集清单（name 为展示名，charsetName 为 Java 字符集名） */
    val SUPPORTED: List<Pair<String, String>> = listOf(
        "UTF-8" to "UTF-8",
        "GB18030（国标）" to "GB18030",
        "GBK" to "GBK",
        "GB2312" to "GB2312",
        "BIG5（繁体）" to "Big5",
        "UTF-16LE（小端）" to "UTF-16LE",
        "UTF-16BE（大端）" to "UTF-16BE",
        "ISO-8859-1（Latin-1）" to "ISO-8859-1",
        "US-ASCII" to "US-ASCII",
        "Windows-1252" to "windows-1252",
        "Shift_JIS（日文）" to "Shift_JIS",
        "EUC-KR（韩文）" to "EUC-KR"
    )

    val DEFAULT = "UTF-8"

    fun charset(name: String): Charset =
        runCatching { Charset.forName(name) }.getOrDefault(Charsets.UTF_8)

    /** 翻译展示名 -> Java 名 */
    fun byDisplay(display: String): String =
        SUPPORTED.firstOrNull { it.first == display }?.second ?: display

    /** 翻译 Java 名 -> 展示名 */
    fun displayOf(charsetName: String): String =
        SUPPORTED.firstOrNull { it.second == charsetName }?.first ?: charsetName

    /** 按字符集编码 */
    fun encode(text: String, charsetName: String): ByteArray = text.toByteArray(charset(charsetName))

    /**
     * 按字符集解码（一次性）。解不出来的字节输出 `\xNN`，不输出 U+FFFD。
     */
    fun decode(bytes: ByteArray, charsetName: String): String =
        decode(bytes, 0, bytes.size, charsetName)

    fun decode(bytes: ByteArray, offset: Int, length: Int, charsetName: String): String {
        if (length <= 0) return ""
        return try {
            decodeEscaping(bytes, offset, length, charset(charsetName))
        } catch (e: Throwable) {
            // 极端兜底：连解码器都构造失败时退回平台默认行为
            String(bytes, offset, length, charset(charsetName))
        }
    }
}

/** 把整段字节转义为 `\xNN` */
private fun escapeAll(bytes: ByteArray): String {
    val sb = StringBuilder(bytes.size * 4)
    for (b in bytes) sb.append(ESCAPE_PREFIX).append(Hex.byteToHex(b.toInt() and 0xFF))
    return sb.toString()
}

/**
 * 流式解码器：缓存不完整的多字节序列，下一次继续解码。
 *
 * 两件事：
 * 1. 断包续接——串口按包读取时 UTF-8 / GB18030 等多字节字符可能被切断，
 *    未消费的余留字节保留到下一次一起解码，不会出现半个字符的乱码。
 * 2. 不可映射字节转义——某个字节（或字节序列）在当前字符集下解不出来时，
 *    不再输出 U+FFFD「�」，而是输出可读的 `\xNN` 十六进制转义，
 *    便于判断到底是设备发错编码还是本机选错了字符集。
 *
 * 注意：非线程安全，每个串口连接持有一个实例。
 */
class StreamingDecoder(charsetName: String) {

    private val charset: Charset = Charsets2.charset(charsetName)
    private var leftover: ByteArray = EMPTY

    /** 单字节字符集无需缓存余留（除 UTF-16 等） */
    private val maxBytesPerChar: Int =
        runCatching { charset.newEncoder().maxBytesPerChar().toInt() }.getOrDefault(4)

    fun decode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        // ISO-8859-1 每个字节都能映射到 U+0000~U+00FF，不存在解不出来的字节
        if (charset == Charsets.ISO_8859_1) return String(bytes, charset)

        val buffer = ByteArray(leftover.size + bytes.size)
        System.arraycopy(leftover, 0, buffer, 0, leftover.size)
        System.arraycopy(bytes, 0, buffer, leftover.size, bytes.size)

        // 与一次性解码同一套规则：非法 / 不可映射字节转义为 \xNN
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)

        val inBuf = ByteBuffer.wrap(buffer)
        val sb = StringBuilder(buffer.size + 16)
        while (inBuf.hasRemaining()) {
            // 输出缓冲按最多 2 char/byte 估算，不够就再循环一次
            val outBuf = CharBuffer.allocate(maxOf(8, inBuf.remaining() * 2))
            val result = decoder.decode(inBuf, outBuf, false)
            outBuf.flip()
            sb.append(outBuf)
            when {
                result.isUnderflow -> break
                result.isOverflow -> continue
                else -> {
                    // 非法 / 不可映射：把对应的原始字节转义输出后继续解码
                    val len = result.length().coerceAtLeast(1)
                    for (i in 0 until len) {
                        if (!inBuf.hasRemaining()) break
                        sb.append(ESCAPE_PREFIX).append(Hex.byteToHex(inBuf.get().toInt() and 0xFF))
                    }
                }
            }
        }
        // 余留未消费的字节（不完整的多字节序列）
        val remain = inBuf.remaining()
        leftover = if (remain > 0) ByteArray(remain).also { inBuf.get(it) } else EMPTY
        // 余留过多说明不是当前字符集期望的编码（正常最多 3~4 字节），
        // 直接转义输出，避免余留无限增长
        if (leftover.size > maxBytesPerChar && leftover.size > 8) {
            sb.append(escapeAll(leftover))
            leftover = EMPTY
        }
        return sb.toString()
    }

    fun reset() {
        leftover = EMPTY
    }

    /** 冲刷余留字节（关闭串口 / 切换字符集时调用） */
    fun flush(): String {
        if (leftover.isEmpty()) return ""
        val s = escapeAll(leftover)
        leftover = EMPTY
        return s
    }

    private companion object {
        val EMPTY = ByteArray(0)
    }
}
