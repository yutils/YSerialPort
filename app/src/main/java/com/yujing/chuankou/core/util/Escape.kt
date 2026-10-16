package com.yujing.chuankou.core.util

/**
 * 转义字符解析
 *
 * 支持：\r \n \t \0 \a \b \f \v \e \\ \' \" \xHH \uHHHH \OOO(八进制)
 */
object Escape {

    /** 解析为字节数组。普通文本按 charsetName 编码，转义序列按原义字节写入。 */
    fun parse(text: String, charsetName: String, enable: Boolean = true): ByteArray {
        if (!enable || text.indexOf('\\') < 0) {
            return Charsets2.encode(text, charsetName)
        }
        val out = java.io.ByteArrayOutputStream(text.length + 16)
        val pending = StringBuilder()
        var i = 0
        fun flushPending() {
            if (pending.isNotEmpty()) {
                out.write(Charsets2.encode(pending.toString(), charsetName))
                pending.setLength(0)
            }
        }
        while (i < text.length) {
            val c = text[i]
            if (c != '\\') {
                pending.append(c)
                i++
                continue
            }
            if (i + 1 >= text.length) {
                pending.append('\\')
                break
            }
            val n = text[i + 1]
            var consumed = 2
            var raw: Byte? = null
            when (n) {
                'r' -> raw = 0x0D
                'n' -> raw = 0x0A
                't' -> raw = 0x09
                '0' -> raw = 0x00
                'a' -> raw = 0x07
                'b' -> raw = 0x08
                'f' -> raw = 0x0C
                'v' -> raw = 0x0B
                'e' -> raw = 0x1B
                '\\' -> raw = 0x5C
                '\'' -> raw = 0x27
                '"' -> raw = 0x22
                '?' -> raw = 0x3F
                'x', 'X' -> {
                    var hex = ""
                    var k = i + 2
                    while (k < text.length && hex.length < 2 && isHex(text[k])) {
                        hex += text[k]; k++
                    }
                    if (hex.isNotEmpty()) {
                        raw = hex.toInt(16).toByte()
                        consumed = k - i
                    } else {
                        pending.append(n); consumed = 2
                    }
                }
                'u', 'U' -> {
                    var hex = ""
                    var k = i + 2
                    while (k < text.length && hex.length < 4 && isHex(text[k])) {
                        hex += text[k]; k++
                    }
                    if (hex.length == 4) {
                        flushPending()
                        out.write(Charsets2.encode(hex.toInt(16).toChar().toString(), charsetName))
                        consumed = k - i
                    } else {
                        pending.append(n); consumed = 2
                    }
                }
                in '1'..'7' -> {
                    var oct = ""
                    var k = i + 1
                    while (k < text.length && oct.length < 3 && text[k] in '0'..'7') {
                        oct += text[k]; k++
                    }
                    raw = oct.toInt(8).toByte()
                    consumed = k - i
                }
                else -> {
                    pending.append(n); consumed = 2
                }
            }
            if (raw != null) {
                flushPending()
                out.write(raw.toInt())
            }
            i += consumed
        }
        flushPending()
        return out.toByteArray()
    }

    /** 展示用：转义可见化 */
    fun visible(text: String): String {
        val sb = StringBuilder(text.length + 8)
        for (c in text) {
            when (c) {
                '\r' -> sb.append("\\r")
                '\n' -> sb.append("\\n")
                '\t' -> sb.append("\\t")
                '\u0000' -> sb.append("\\0")
                else -> if (c.code < 0x20) sb.append("\\x").append(Hex.byteToHex(c.code)) else sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun isHex(c: Char): Boolean =
        c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
}
