package com.yujing.chuankou.core.util

import com.yujing.chuankou.core.data.DataType
import com.yujing.chuankou.core.data.Newline

/**
 * 发送载荷构建（统一 ASCII / HEX / 转义 / 结束符 处理）
 */
object Payload {

    /**
     * @param content 用户输入内容
     * @param type DataType.ASCII / DataType.HEX
     * @param charset 展示名或 Java 字符集名
     * @param appendNewline 0 无 / 1 \r\n / 2 \n / 3 \r
     * @param escape 是否解析转义字符
     */
    fun build(
        content: String,
        type: Int,
        charset: String = "UTF-8",
        appendNewline: Int = 0,
        escape: Boolean = false
    ): ByteArray {
        if (content.isEmpty()) return ByteArray(0)
        val cs = Charsets2.byDisplay(charset)
        val body: ByteArray = if (type == DataType.HEX) {
            Hex.parse(content) ?: ByteArray(0)
        } else {
            if (escape) Escape.parse(content, cs, true) else Charsets2.encode(content, cs)
        }
        if (body.isEmpty()) return body
        val nl = Newline.bytes(appendNewline, cs)
        return if (nl.isEmpty()) body else body + nl
    }

    /** 校验用户输入是否合法（HEX 非法返回 false） */
    fun validate(content: String, type: Int): Boolean {
        if (type != DataType.HEX) return true
        return Hex.parse(content) != null
    }

    /** 生成预览文本 */
    fun preview(content: String, type: Int, charset: String = "UTF-8", appendNewline: Int = 0, escape: Boolean = false): String {
        val bytes = build(content, type, charset, appendNewline, escape)
        if (bytes.isEmpty()) return ""
        return if (type == DataType.HEX || type == DataType.FILE) {
            Hex.toHexString(bytes, " ")
        } else {
            val s = Charsets2.decode(bytes, Charsets2.byDisplay(charset))
            Escape.visible(s)
        }
    }
}
