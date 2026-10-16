package com.yujing.chuankou.core.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.yujing.chuankou.core.util.Hex
import org.json.JSONObject
import android.annotation.SuppressLint
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 数据仓库：收发历史 / 快捷指令 / 自动应答规则
 */
@SuppressLint("StaticFieldLeak") // 单例仓库，持有的是 applicationContext（见 init()），不会泄漏 Activity
object Store {

    private lateinit var app: Context
    private val handler = Handler(Looper.getMainLooper())

    // ---------------- 历史 ----------------
    private val history = ArrayDeque<HistoryItem>()
    private val historyListeners = mutableListOf<() -> Unit>()
    private var persistScheduled = false
    private var historyNotifyScheduled = false

    private val persistRunnable = Runnable { persistHistoryNow() }

    // ---------------- 快捷指令 ----------------
    private val _commands = mutableListOf<QuickCommand>()
    val commands: List<QuickCommand> get() = _commands
    private val commandListeners = mutableListOf<() -> Unit>()

    // ---------------- 应答规则 ----------------
    private val _rules = mutableListOf<AutoReplyRule>()
    val rules: List<AutoReplyRule> get() = _rules
    private val ruleListeners = mutableListOf<() -> Unit>()

    // 时间格式化：模式均为纯数字，用 Locale.ROOT 保证与系统语言无关。
    // SimpleDateFormat 不是线程安全的（列表刷新在 UI 线程、导出在 IO 线程），故统一加锁访问。
    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)

    // ==================================================================
    fun init(context: Context) {
        app = context.applicationContext
        Prefs.init(app)
        loadHistory()
        _commands.clear()
        _commands.addAll(QuickCommand.listFromJson(Prefs.str(K_COMMANDS)))
        if (_commands.isEmpty()) _commands.addAll(defaultCommands())
        _rules.clear()
        _rules.addAll(AutoReplyRule.listFromJson(Prefs.str(K_RULES)))
    }

    fun appContext(): Context = app

    // ==================================================================
    // 历史
    // ==================================================================
    fun historyList(): List<HistoryItem> = ArrayList(history)

    val historySize: Int get() = history.size

    fun addHistory(item: HistoryItem) {
        history.addLast(item)
        val limit = Prefs.historyLimit.coerceIn(100, 50000)
        while (history.size > limit) history.removeFirst()
        notifyHistory()
        schedulePersist()
    }

    fun clearHistory() {
        history.clear()
        notifyHistory()
        persistHistoryNow()
    }

    fun removeHistory(item: HistoryItem) {
        history.remove(item)
        notifyHistory()
        schedulePersist()
    }

    fun addHistoryListener(l: () -> Unit) {
        if (!historyListeners.contains(l)) historyListeners.add(l)
    }

    fun removeHistoryListener(l: () -> Unit) {
        historyListeners.remove(l)
    }

    private val historyNotifyRunnable = Runnable {
        historyNotifyScheduled = false
        ArrayList(historyListeners).forEach { runCatching { it() } }
    }

    /** 历史列表刷新限流，避免高频收发时反复刷新界面 */
    private fun notifyHistory() {
        if (historyNotifyScheduled) return
        historyNotifyScheduled = true
        handler.postDelayed(historyNotifyRunnable, 150L)
    }

    private fun schedulePersist() {
        if (persistScheduled) return
        persistScheduled = true
        handler.removeCallbacks(persistRunnable)
        handler.postDelayed(persistRunnable, 2500)
    }

    private fun persistHistoryNow() {
        persistScheduled = false
        val file = historyFile()
        val tmp = File(file.parentFile, file.name + ".tmp")
        runCatching {
            tmp.bufferedWriter().use { w ->
                history.forEach { w.write(it.toJson().toString()); w.newLine() }
            }
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        }
    }

    private fun loadHistory() {
        history.clear()
        val file = historyFile()
        if (!file.exists()) return
        runCatching {
            file.forEachLine { line ->
                if (line.isNotBlank()) {
                    runCatching { history.addLast(HistoryItem.fromJson(JSONObject(line))) }
                }
            }
        }
        val limit = Prefs.historyLimit.coerceIn(100, 50000)
        while (history.size > limit) history.removeFirst()
    }

    private fun historyFile(): File {
        val dir = File(app.filesDir, "data")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "history.jsonl")
    }

    /**
     * 生成历史导出文本（历史页 / 更多页共用，避免两处各写一份且列数不一致）。
     * 内部使用局部 formatter，天然线程安全，可从任意线程调用。
     */
    fun buildExportText(items: List<HistoryItem>): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
        val sb = StringBuilder(items.size * 64 + 128)
        sb.append("# YSerialPort 串口收发记录\n")
        sb.append("# 导出时间：${fmt.format(Date())}\n")
        sb.append("# 方向\t时间\t类型\t长度\tHEX\t文本\n")
        items.forEach { h ->
            val dir = when (h.dir) {
                Dir.RX -> "接收"
                Dir.TX -> "发送"
                else -> "系统"
            }
            val type = when (h.type) {
                DataType.ASCII -> "ASCII"
                DataType.HEX -> "HEX"
                DataType.FILE -> "FILE"
                else -> "-"
            }
            sb.append("$dir\t${fmt.format(Date(h.time))}\t$type\t${h.size}\t${h.hex}\t${h.text.replace("\n", "\\n")}\n")
        }
        return sb.toString()
    }

    fun formatTime(t: Long): String = synchronized(timeFmt) { timeFmt.format(Date(t)) }

    // ==================================================================
    // 快捷指令
    // ==================================================================
    fun addCommand(c: QuickCommand) {
        _commands.add(c)
        saveCommands()
    }

    fun updateCommand(c: QuickCommand) {
        val i = _commands.indexOfFirst { it.id == c.id }
        if (i >= 0) _commands[i] = c else _commands.add(c)
        saveCommands()
    }

    fun removeCommand(c: QuickCommand) {
        _commands.removeAll { it.id == c.id }
        saveCommands()
    }

    fun moveCommand(from: Int, to: Int) {
        if (from in _commands.indices && to in _commands.indices) {
            val item = _commands.removeAt(from)
            _commands.add(to, item)
            saveCommands()
        }
    }

    private fun saveCommands() {
        Prefs.put(K_COMMANDS, QuickCommand.listToJson(_commands))
        handler.post { ArrayList(commandListeners).forEach { runCatching { it() } } }
    }

    fun addCommandListener(l: () -> Unit) {
        if (!commandListeners.contains(l)) commandListeners.add(l)
    }

    fun removeCommandListener(l: () -> Unit) {
        commandListeners.remove(l)
    }

    private fun defaultCommands(): List<QuickCommand> = listOf(
        QuickCommand(name = "AT 指令", type = DataType.ASCII, content = "AT", appendNewline = 1, charset = "UTF-8"),
        QuickCommand(name = "Modbus 读保持寄存器", type = DataType.HEX, content = "01 03 00 00 00 0A C5 CD"),
        QuickCommand(name = "握手帧", type = DataType.HEX, content = "AA 55 01 00 55 AA"),
        QuickCommand(name = "复位", type = DataType.HEX, content = "FF FF 00 00 00 00 00 00"),
        QuickCommand(name = "回车换行", type = DataType.HEX, content = "0D 0A")
    )

    // ==================================================================
    // 自动应答
    // ==================================================================
    fun addRule(r: AutoReplyRule) {
        _rules.add(r)
        saveRules()
    }

    fun updateRule(r: AutoReplyRule) {
        val i = _rules.indexOfFirst { it.id == r.id }
        if (i >= 0) _rules[i] = r else _rules.add(r)
        saveRules()
    }

    fun removeRule(r: AutoReplyRule) {
        _rules.removeAll { it.id == r.id }
        saveRules()
    }

    fun toggleRule(r: AutoReplyRule, enabled: Boolean) {
        val i = _rules.indexOfFirst { it.id == r.id }
        if (i >= 0) {
            _rules[i].enabled = enabled
            saveRules()
        }
    }

    fun moveRule(from: Int, to: Int) {
        if (from in _rules.indices && to in _rules.indices) {
            val item = _rules.removeAt(from)
            _rules.add(to, item)
            saveRules()
        }
    }

    private fun saveRules() {
        Prefs.put(K_RULES, AutoReplyRule.listToJson(_rules))
        handler.post { ArrayList(ruleListeners).forEach { runCatching { it() } } }
    }

    fun addRuleListener(l: () -> Unit) {
        if (!ruleListeners.contains(l)) ruleListeners.add(l)
    }

    fun removeRuleListener(l: () -> Unit) {
        ruleListeners.remove(l)
    }

    // ==================================================================
    /** 发送历史（最近内容） */
    fun pushSendHistory(text: String) {
        if (text.isBlank()) return
        val list = Prefs.sendHistory.split("\u0001").filter { it.isNotBlank() }.toMutableList()
        list.remove(text)
        list.add(0, text)
        while (list.size > 50) list.removeAt(list.size - 1)
        Prefs.sendHistory = list.joinToString("\u0001")
    }

    fun sendHistory(): List<String> =
        Prefs.sendHistory.split("\u0001").filter { it.isNotBlank() }

    /** 复制到剪贴板的通用方法 */
    fun hexLine(bytes: ByteArray): String = Hex.toHexString(bytes, " ")

    private const val K_COMMANDS = "quick_commands_json"
    private const val K_RULES = "auto_reply_rules_json"
}
