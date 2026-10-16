package com.yujing.chuankou.core.data

/**
 * 页面间轻量通信（例如：从历史记录/ASCII 码表回填到终端发送框）
 */
object SendBridge {

    data class Payload(val text: String, val type: Int, val autoSend: Boolean = false, val stamp: Long = System.currentTimeMillis())

    @Volatile
    private var payload: Payload? = null

    /** 填入内容（不自动发送） */
    fun fill(text: String, type: Int) {
        payload = Payload(text, type, false)
    }

    /** 一键发送 */
    fun send(text: String, type: Int) {
        payload = Payload(text, type, true)
    }

    /** 取出并清空 */
    fun consume(): Payload? {
        val p = payload
        payload = null
        return p
    }

    fun peek(): Payload? = payload
}
