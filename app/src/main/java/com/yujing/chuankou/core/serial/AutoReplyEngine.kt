package com.yujing.chuankou.core.serial

import android.os.Handler
import android.os.Looper
import com.yujing.chuankou.R
import com.yujing.chuankou.core.data.AutoReplyRule
import com.yujing.chuankou.core.data.DataType
import com.yujing.chuankou.core.data.MatchMode
import com.yujing.chuankou.core.data.Newline
import com.yujing.chuankou.core.data.Prefs
import com.yujing.chuankou.core.data.Store
import com.yujing.chuankou.core.util.Charsets2
import com.yujing.chuankou.core.util.Escape
import com.yujing.chuankou.core.util.Hex
import com.yujing.chuankou.core.util.Langs

/**
 * 自动应答引擎
 *
 * 收到串口数据时，用滚动缓冲匹配规则；命中后按规则内容应答。
 * 同一批数据只会触发一轮应答（避免重复应答）。
 */
object AutoReplyEngine {

    private const val MAX_BUFFER = 4096

    private val handler = Handler(Looper.getMainLooper())
    private var byteBuffer = ByteArray(0)
    private val textBuffer = StringBuilder()

    /** 命中回调（主线程） */
    private val hitListeners = mutableListOf<(AutoReplyRule, ByteArray) -> Unit>()

    fun addHitListener(l: (AutoReplyRule, ByteArray) -> Unit) {
        if (!hitListeners.contains(l)) hitListeners.add(l)
    }

    fun removeHitListener(l: (AutoReplyRule, ByteArray) -> Unit) {
        hitListeners.remove(l)
    }

    fun reset() {
        byteBuffer = ByteArray(0)
        textBuffer.setLength(0)
    }

    fun onReceive(bytes: ByteArray, ascii: String) {
        if (bytes.isEmpty()) return
        byteBuffer = (byteBuffer + bytes).let {
            if (it.size > MAX_BUFFER) it.copyOfRange(it.size - MAX_BUFFER, it.size) else it
        }
        textBuffer.append(ascii)
        if (textBuffer.length > MAX_BUFFER) textBuffer.delete(0, textBuffer.length - MAX_BUFFER)

        val rules = Store.rules
        if (rules.isEmpty()) return

        var anyHit = false
        for (rule in rules) {
            if (!rule.enabled) continue
            if (match(rule)) {
                anyHit = true
                // 走 Store 的节流落盘，直接 rule.hitCount++ 只改内存，重启后计数会归零
                Store.bumpRuleHit(rule)
                deliver(rule)
                if (rule.once) Store.toggleRule(rule, false)
            }
        }
        // 命中后清空缓冲，防止同一批数据反复触发
        if (anyHit) {
            byteBuffer = ByteArray(0)
            textBuffer.setLength(0)
        }
    }

    // ==================================================================
    private fun match(rule: AutoReplyRule): Boolean {
        if (rule.matchContent.isBlank()) return false
        return if (rule.matchType == DataType.HEX) {
            val pattern = parseHexPattern(rule.matchContent) ?: return false
            if (pattern.isEmpty()) return false
            indexOfPattern(byteBuffer, pattern) >= 0
        } else {
            val target = rule.matchContent
            val hay = textBuffer.toString()
            when (rule.matchMode) {
                MatchMode.EQUALS -> hay.trimEnd() == target
                MatchMode.PREFIX -> hay.trimStart().startsWith(target)
                MatchMode.SUFFIX -> hay.trimEnd().endsWith(target)
                MatchMode.REGEX -> runCatching { Regex(target).containsMatchIn(hay) }.getOrDefault(false)
                else -> hay.contains(target)
            }
        }
    }

    private fun deliver(rule: AutoReplyRule) {
        val payload = buildReply(rule)
        if (payload.isEmpty()) return
        if (rule.delayMs <= 0) {
            // 无延时：直接发送。不再往主线程消息队列 post，
            // 否则高频循环应答时会把主线程压满，导致界面（含开关）无法响应。
            sendNow(rule, payload)
        } else {
            handler.postDelayed({ sendNow(rule, payload) }, rule.delayMs)
        }
    }

    /** 真正发送应答，发送前复核规则开关，保证关闭后立刻生效 */
    private fun sendNow(rule: AutoReplyRule, payload: ByteArray) {
        if (!rule.enabled) return
        if (!Prefs.autoReplyEnabled) return
        val note = if (rule.name.isBlank()) {
            Langs.str(R.string.autoreply_note)
        } else {
            Langs.str(R.string.autoreply_note_name, rule.name)
        }
        SerialManager.send(payload, rule.replyType, note)
        hitListeners.toList().forEach { runCatching { it(rule, payload) } }
    }

    /** 按规则构建应答字节数组（供预览与发送复用） */
    fun buildReply(rule: AutoReplyRule): ByteArray {
        val body = if (rule.replyType == DataType.HEX) {
            Hex.parse(rule.replyContent) ?: ByteArray(0)
        } else {
            val cs = Charsets2.byDisplay(rule.charset)
            if (rule.replyEscape) {
                Escape.parse(rule.replyContent, cs, true)
            } else {
                Charsets2.encode(rule.replyContent, cs)
            }
        }
        if (body.isEmpty()) return body
        val nl = Newline.bytes(rule.replyAppendNewline, Charsets2.byDisplay(rule.charset))
        return if (nl.isEmpty()) body else body + nl
    }

    /**
     * 解析带通配符的 HEX 模式
     * 支持：`01 02 ??`、`01 02 ?`、`01 02 xx`、`01 02 **`
     * 返回 IntArray（0~255 为具体值，-1 为通配）；非法返回 null
     */
    fun parseHexPattern(input: String): IntArray? {
        val tokens = tokenizePattern(input)
        if (tokens.isEmpty()) return null
        val out = IntArray(tokens.size)
        tokens.forEachIndexed { i, tk ->
            if (tk.length < 2) return null
            out[i] = if (tk.all { it == '?' || it == '*' || it == 'x' || it == 'X' }) {
                -1
            } else {
                tk.toIntOrNull(16) ?: return null
            }
        }
        return out
    }

    private fun tokenizePattern(input: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        val s = input
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() || c == ',' || c == ':' || c == '-' || c == '_' || c == ';' -> i++
                (c == '0' && i + 1 < s.length && (s[i + 1] == 'x' || s[i + 1] == 'X')) -> i += 2
                (c == '\\' && i + 1 < s.length && (s[i + 1] == 'x' || s[i + 1] == 'X')) -> i += 2
                c == '?' || c == '*' -> {
                    out.add("??")
                    i++
                    if (i < s.length && (s[i] == '?' || s[i] == '*')) i++
                }
                else -> {
                    if (i + 1 >= s.length) {
                        // 末尾只剩单个字符：按低半字节补 0（"3" → "03"）。
                        // 早期版本在这里直接 return，尾部字节被静默丢弃，
                        // 用户写的规则永远匹配不上又看不出原因。
                        out.add("0$c")
                        i++
                    } else {
                        out.add(s.substring(i, i + 2))
                        i += 2
                    }
                }
            }
        }
        return out
    }

    /** 朴素通配匹配查找 */
    private fun indexOfPattern(data: ByteArray, pattern: IntArray): Int {
        if (pattern.isEmpty() || data.size < pattern.size) return -1
        outer@ for (i in 0..(data.size - pattern.size)) {
            for (j in pattern.indices) {
                val p = pattern[j]
                if (p < 0) continue
                if ((data[i + j].toInt() and 0xFF) != p) continue@outer
            }
            return i
        }
        return -1
    }
}
