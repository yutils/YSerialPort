package com.yujing.chuankou.core.util

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

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

    /** 按字符集解码（一次性，替换非法字符） */
    fun decode(bytes: ByteArray, charsetName: String): String =
        decode(bytes, 0, bytes.size, charsetName)

    fun decode(bytes: ByteArray, offset: Int, length: Int, charsetName: String): String {
        if (length <= 0) return ""
        return try {
            val dec = charset(charsetName).newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
            dec.decode(ByteBuffer.wrap(bytes, offset, length)).toString()
        } catch (e: Throwable) {
            String(bytes, offset, length, charset(charsetName))
        }
    }
}

/**
 * 流式解码器：缓存不完整的多字节序列，下一次继续解码。
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
        if (charset == Charsets.ISO_8859_1 || charset == Charsets.US_ASCII) {
            return String(bytes, charset)
        }
        val buffer = ByteArray(leftover.size + bytes.size)
        System.arraycopy(leftover, 0, buffer, 0, leftover.size)
        System.arraycopy(bytes, 0, buffer, leftover.size, bytes.size)

        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)

        var inBuf = ByteBuffer.wrap(buffer)
        val sb = StringBuilder(buffer.size + 16)
        // 输出缓冲按最多 2 char/byte 估算，循环处理溢出
        var outSize = buffer.size * 2 + 16
        while (true) {
            val outBuf = CharBuffer.allocate(outSize)
            val result = decoder.decode(inBuf, outBuf, false)
            outBuf.flip()
            sb.append(outBuf)
            when (result) {
                java.nio.charset.CoderResult.UNDERFLOW -> break
                else -> {
                    if (result.isOverflow) {
                        outSize *= 2
                    } else {
                        // 理论不会走到这里（已 REPLACE），兜底退出
                        break
                    }
                }
            }
        }
        // 余留未消费的字节
        val remain = inBuf.remaining()
        leftover = if (remain > 0) {
            ByteArray(remain).also { inBuf.get(it) }
        } else {
            EMPTY
        }
        // 余留字节过多说明不是本项目期望的编码，避免无限增长
        if (leftover.size > maxBytesPerChar && leftover.size > 8) {
            sb.append(String(leftover, charset))
            leftover = EMPTY
        }
        return sb.toString()
    }

    fun reset() {
        leftover = EMPTY
    }

    /** 冲刷余留字节（关闭串口时调用） */
    fun flush(): String {
        if (leftover.isEmpty()) return ""
        val s = runCatching { String(leftover, charset) }.getOrDefault("")
        leftover = EMPTY
        return s
    }

    private companion object {
        val EMPTY = ByteArray(0)
    }
}
