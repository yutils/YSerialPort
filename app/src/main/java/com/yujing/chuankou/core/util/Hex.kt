package com.yujing.chuankou.core.util

/**
 * 十六进制工具
 */
object Hex {

    private val HEX_UPPER = "0123456789ABCDEF".toCharArray()
    private val HEX_LOWER = "0123456789abcdef".toCharArray()

    /** 单字节转两位十六进制 */
    fun byteToHex(b: Int, upper: Boolean = true): String {
        val t = if (upper) HEX_UPPER else HEX_LOWER
        val v = b and 0xFF
        return "${t[v ushr 4]}${t[v and 0x0F]}"
    }

    /** 字节数组转十六进制字符串 */
    fun toHexString(bytes: ByteArray, separator: String = " ", upper: Boolean = true): String {
        if (bytes.isEmpty()) return ""
        val sb = StringBuilder(bytes.size * 3)
        for (i in bytes.indices) {
            if (i > 0 && separator.isNotEmpty()) sb.append(separator)
            sb.append(byteToHex(bytes[i].toInt(), upper))
        }
        return sb.toString()
    }

    fun toHexString(bytes: ByteArray, offset: Int, length: Int, separator: String = " ", upper: Boolean = true): String {
        if (bytes.isEmpty() || length <= 0) return ""
        val end = (offset + length).coerceAtMost(bytes.size)
        val sb = StringBuilder((end - offset) * 3)
        var first = true
        for (i in offset until end) {
            if (!first && separator.isNotEmpty()) sb.append(separator)
            sb.append(byteToHex(bytes[i].toInt(), upper))
            first = false
        }
        return sb.toString()
    }

    /**
     * 宽松解析十六进制字符串。
     * 允许空白、逗号、冒号、横线、下划线、0x/0X 前缀、\x 前缀。
     * 非法字符返回 null；长度为奇数时自动在末尾补 0。
     */
    fun parse(input: String): ByteArray? {
        if (input.isBlank()) return ByteArray(0)
        val sb = StringBuilder(input.length)
        var i = 0
        val s = input
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() || c == ',' || c == ':' || c == '-' || c == '_' || c == ';' -> i++
                (c == '0' && i + 1 < s.length && (s[i + 1] == 'x' || s[i + 1] == 'X')) -> i += 2
                (c == '\\' && i + 1 < s.length && (s[i + 1] == 'x' || s[i + 1] == 'X')) -> i += 2
                c.isHexDigit() -> {
                    sb.append(c)
                    i++
                }
                else -> return null
            }
        }
        if (sb.isEmpty()) return ByteArray(0)
        // 奇数长度补零
        if (sb.length % 2 != 0) sb.append('0')
        val out = ByteArray(sb.length / 2)
        for (k in out.indices) {
            val hi = Character.digit(sb[k * 2], 16)
            val lo = Character.digit(sb[k * 2 + 1], 16)
            if (hi < 0 || lo < 0) return null
            out[k] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    /** 是否为合法十六进制输入（允许分隔符） */
    fun isValid(input: String): Boolean = parse(input) != null

    /** 是否含有非法字符 */
    fun hasInvalidChar(input: String): Boolean = parse(input) == null

    /** 格式化：去掉多余空格后按字节分组显示 */
    fun normalize(input: String): String {
        val bytes = parse(input) ?: return input
        return toHexString(bytes, " ", true)
    }

    /** 二进制字符串 */
    fun toBinary(b: Int, bits: Int = 8): String {
        val sb = StringBuilder(bits)
        for (i in bits - 1 downTo 0) sb.append(if ((b shr i) and 1 == 1) '1' else '0')
        return sb.toString()
    }

    /** 字节数组转二进制字符串（空格分隔） */
    fun toBinaryString(bytes: ByteArray): String = bytes.joinToString(" ") { toBinary(it.toInt() and 0xFF) }

    /** 把 long 按位宽转成字节数组 */
    fun longToBytes(value: Long, byteCount: Int, highFirst: Boolean = true): ByteArray {
        val out = ByteArray(byteCount)
        for (i in 0 until byteCount) {
            val shift = if (highFirst) (byteCount - 1 - i) * 8 else i * 8
            out[i] = ((value shr shift) and 0xFF).toByte()
        }
        return out
    }

    /** 判断字符是否十六进制数字 */
    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    /** 可打印字符显示（不可打印用 . 代替） */
    fun toPrintable(bytes: ByteArray, placeholder: Char = '.'): String {
        val sb = StringBuilder(bytes.size)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(if (v in 32..126) v.toChar() else placeholder)
        }
        return sb.toString()
    }

    /** 计算 ASCII 转义展示：把不可见字符转义 */
    fun escapeForDisplay(bytes: ByteArray, charsetName: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < bytes.size) {
            val v = bytes[i].toInt() and 0xFF
            when {
                v == 0x0D -> sb.append("\\r")
                v == 0x0A -> sb.append("\\n")
                v == 0x09 -> sb.append("\\t")
                v == 0x00 -> sb.append("\\0")
                v < 0x20 || v == 0x7F -> sb.append("\\x").append(byteToHex(v, true))
                else -> {
                    // 收集连续可打印字节，按字符集解码
                    var j = i
                    while (j < bytes.size) {
                        val u = bytes[j].toInt() and 0xFF
                        if (u < 0x20 || u == 0x7F) break
                        j++
                    }
                    sb.append(Charsets2.decode(bytes, i, j - i, charsetName))
                    i = j
                    continue
                }
            }
            i++
        }
        return sb.toString()
    }
}
