package com.yujing.chuankou.core.data

import com.yujing.chuankou.core.util.Hex
import org.json.JSONArray
import org.json.JSONObject

/** 数据方向 */
object Dir {
    const val RX = 0
    const val TX = 1
    const val SYSTEM = 2
}

/** 数据类型 */
object DataType {
    const val ASCII = 0
    const val HEX = 1
    const val FILE = 2
    const val SYSTEM = 3
}

/** 匹配模式 */
object MatchMode {
    const val CONTAINS = 0
    const val EQUALS = 1
    const val PREFIX = 2
    const val SUFFIX = 3
    const val REGEX = 4
    val names = listOf("包含", "完全相等", "前缀", "后缀", "正则")

    fun name(v: Int) = names.getOrElse(v) { names[0] }
}

// ======================================================================
// 收发历史
// ======================================================================
data class HistoryItem(
    val time: Long,
    val dir: Int,
    val type: Int,
    val text: String,
    val hex: String,
    val size: Int,
    val note: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("t", time)
        put("d", dir)
        put("y", type)
        put("x", text)
        put("h", hex)
        put("s", size)
        put("n", note)
    }

    companion object {
        fun fromJson(o: JSONObject) = HistoryItem(
            time = o.optLong("t"),
            dir = o.optInt("d"),
            type = o.optInt("y"),
            text = o.optString("x"),
            hex = o.optString("h"),
            size = o.optInt("s"),
            note = o.optString("n")
        )

        fun of(time: Long, dir: Int, type: Int, bytes: ByteArray, text: String, note: String = "") =
            HistoryItem(time, dir, type, text, Hex.toHexString(bytes, " "), bytes.size, note)
    }
}

// ======================================================================
// 快捷指令
// ======================================================================
data class QuickCommand(
    var id: Long = System.currentTimeMillis() * 1000 + (Math.random() * 1000).toLong(),
    var name: String = "",
    var type: Int = DataType.HEX,
    var content: String = "",
    var charset: String = "UTF-8",
    var appendNewline: Int = 0,
    var escape: Boolean = false,
    var enabled: Boolean = true,
    var tag: Int = 0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("type", type)
        put("content", content)
        put("charset", charset)
        put("nl", appendNewline)
        put("esc", escape)
        put("en", enabled)
        put("tag", tag)
    }

    companion object {
        fun fromJson(o: JSONObject) = QuickCommand(
            id = o.optLong("id", System.currentTimeMillis()),
            name = o.optString("name"),
            type = o.optInt("type", DataType.HEX),
            content = o.optString("content"),
            charset = o.optString("charset", "UTF-8"),
            appendNewline = o.optInt("nl", 0),
            escape = o.optBoolean("esc", false),
            enabled = o.optBoolean("en", true),
            tag = o.optInt("tag", 0)
        )

        fun listToJson(list: List<QuickCommand>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun listFromJson(s: String): MutableList<QuickCommand> {
            val out = mutableListOf<QuickCommand>()
            if (s.isBlank()) return out
            runCatching {
                val arr = JSONArray(s)
                for (i in 0 until arr.length()) out.add(fromJson(arr.getJSONObject(i)))
            }
            return out
        }
    }
}

// ======================================================================
// 自动应答规则
// ======================================================================
data class AutoReplyRule(
    var id: Long = System.currentTimeMillis() * 1000 + (Math.random() * 1000).toLong() + 1,
    var enabled: Boolean = true,
    var name: String = "",
    var matchType: Int = DataType.HEX,
    var matchMode: Int = MatchMode.CONTAINS,
    var matchContent: String = "",
    var charset: String = "UTF-8",
    var replyType: Int = DataType.HEX,
    var replyContent: String = "",
    var replyAppendNewline: Int = 0,
    var replyEscape: Boolean = false,
    var delayMs: Long = 0,
    /** 命中一次后自动停用 */
    var once: Boolean = false,
    /** 已命中次数（运行时统计，不持久化） */
    var hitCount: Int = 0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("en", enabled)
        put("name", name)
        put("mt", matchType)
        put("mm", matchMode)
        put("mc", matchContent)
        put("cs", charset)
        put("rt", replyType)
        put("rc", replyContent)
        put("nl", replyAppendNewline)
        put("esc", replyEscape)
        put("delay", delayMs)
        put("once", once)
    }

    companion object {
        fun fromJson(o: JSONObject) = AutoReplyRule(
            id = o.optLong("id", System.currentTimeMillis()),
            enabled = o.optBoolean("en", true),
            name = o.optString("name"),
            matchType = o.optInt("mt", DataType.HEX),
            matchMode = o.optInt("mm", MatchMode.CONTAINS),
            matchContent = o.optString("mc"),
            charset = o.optString("cs", "UTF-8"),
            replyType = o.optInt("rt", DataType.HEX),
            replyContent = o.optString("rc"),
            replyAppendNewline = o.optInt("nl", 0),
            replyEscape = o.optBoolean("esc", false),
            delayMs = o.optLong("delay", 0),
            once = o.optBoolean("once", false)
        )

        fun listToJson(list: List<AutoReplyRule>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun listFromJson(s: String): MutableList<AutoReplyRule> {
            val out = mutableListOf<AutoReplyRule>()
            if (s.isBlank()) return out
            runCatching {
                val arr = JSONArray(s)
                for (i in 0 until arr.length()) out.add(fromJson(arr.getJSONObject(i)))
            }
            return out
        }
    }
}

// ======================================================================
// 发送结束符
// ======================================================================
object Newline {
    val NAMES = listOf("无", "\\r\\n", "\\n", "\\r")
    fun bytes(v: Int, charsetName: String): ByteArray = when (v) {
        1 -> byteArrayOf(0x0D, 0x0A)
        2 -> byteArrayOf(0x0A)
        3 -> byteArrayOf(0x0D)
        else -> ByteArray(0)
    }
}
