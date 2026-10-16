package com.yujing.chuankou.ui.terminal

/**
 * 终端显示内容快照
 *
 * 主题切换 / 系统回收 会导致 Activity 重建，这里保留最近的接收内容，
 * 重建后自动恢复，避免用户丢失现场数据。
 */
object TerminalSnapshot {

    /** 最多保留的字符数 */
    private const val MAX = 20000

    @Volatile
    private var content: String = ""

    fun save(text: String) {
        content = if (text.length > MAX) text.substring(text.length - MAX) else text
    }

    fun load(): String = content

    fun clear() {
        content = ""
    }

    val isEmpty: Boolean get() = content.isEmpty()
}
