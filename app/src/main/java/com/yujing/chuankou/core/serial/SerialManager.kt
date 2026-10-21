package com.yujing.chuankou.core.serial

import android.content.Context
import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import com.yujing.chuankou.core.data.DataType
import com.yujing.chuankou.core.data.Dir
import com.yujing.chuankou.core.data.HistoryItem
import com.yujing.chuankou.core.data.Prefs
import com.yujing.chuankou.R
import com.yujing.chuankou.core.data.Store
import com.yujing.chuankou.core.util.Charsets2
import com.yujing.chuankou.core.util.Hex
import com.yujing.chuankou.core.util.Langs
import com.yujing.chuankou.core.util.StreamingDecoder
import com.yujing.serialport.SerialPort
import com.yujing.yserialport.ThreadMode
import com.yujing.yserialport.YSerialPort
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * 串口管理器（全局单例）
 *
 * 职责：
 * - 持有唯一的串口连接，跨页面 / 跨旋转存活
 * - 统一的发送队列（单线程串行，避免交错）
 * - 接收数据分发（原始字节 + 按字符集解码后的文本）
 * - 收发统计、历史记录、自动应答触发
 */
@SuppressLint("StaticFieldLeak") // 全局单例，YSerialPort 由 applicationContext 构造，不持有 Activity
object SerialManager {

    enum class State { CLOSED, OPENING, OPEN, CLOSING, ERROR }

    interface Listener {
        /** 连接状态变化 */
        fun onState(state: State, message: String?) {}

        /** 收到数据（主线程）。ascii 为按当前字符集解码后的文本 */
        fun onReceive(bytes: ByteArray, ascii: String, time: Long) {}

        /** 发出数据（主线程） */
        fun onSend(bytes: ByteArray, type: Int, time: Long) {}

        /** 统计变化（主线程） */
        fun onStats(rxBytes: Long, txBytes: Long, rxPackets: Long, txPackets: Long) {}

        /** 周期发送状态变化 */
        fun onLoopState(running: Boolean, sent: Int, total: Int) {}

        /** 文件发送进度 */
        fun onFileProgress(sent: Int, total: Int) {}
    }

    private val handler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<Listener>()
    private val txExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "YSerialPort-TX").apply { isDaemon = true }
    }

    @Volatile
    private var ySerialPort: YSerialPort? = null

    @Volatile
    private var state: State = State.CLOSED

    @Volatile
    var config: SerialConfig = SerialConfig()
        private set

    /** 接收文本解码器（流式，处理多字节断包） */
    private var decoder = StreamingDecoder(Prefs.rxCharset.toCharsetName())
    private var decoderCharset = Prefs.rxCharset.toCharsetName()

    // 统计
    @Volatile var rxBytes: Long = 0; private set
    @Volatile var txBytes: Long = 0; private set
    @Volatile var rxPackets: Long = 0; private set
    @Volatile var txPackets: Long = 0; private set
    @Volatile var lastError: String? = null; private set
    @Volatile var connectedAt: Long = 0; private set

    private var appContext: Context? = null

    // 周期发送
    private val loopRunnable = object : Runnable {
        override fun run() {
            if (!loopRunning) return
            val payload = loopPayload ?: return
            loopSent++
            sendInternal(payload.first, payload.second, Langs.str(R.string.serial_loop_note), false)
            val total = loopTotal
            notifyLoop(true, loopSent, total)
            if (total > 0 && loopSent >= total) {
                stopLoop()
            } else {
                handler.postDelayed(this, loopIntervalMs.coerceAtLeast(1).toLong())
            }
        }
    }
    @Volatile private var loopRunning = false
    @Volatile private var loopSent = 0
    @Volatile private var loopTotal = 0
    @Volatile private var loopIntervalMs = 1000
    @Volatile private var loopPayload: Pair<ByteArray, Int>? = null

    // 文件发送
    @Volatile private var fileSending = false

    // ==================================================================
    // 生命周期
    // ==================================================================
    fun registerContext(context: Context) {
        appContext = context.applicationContext
    }

    fun addListener(l: Listener) {
        if (!listeners.contains(l)) listeners.add(l)
    }

    fun removeListener(l: Listener) {
        listeners.remove(l)
    }

    fun currentState(): State = state
    val isOpen: Boolean get() = state == State.OPEN

    // ==================================================================
    // 打开 / 关闭
    // ==================================================================
    fun open(cfg: SerialConfig): Boolean {
        appContext ?: return false
        if (state == State.OPENING || state == State.OPEN) close()
        config = cfg
        setState(State.OPENING, null)
        resetDecoder()

        return try {
            val port = SerialPort.newBuilder(File(cfg.device), cfg.baudRate)
                .dataBits(cfg.dataBits)
                .parity(cfg.parity)
                .stopBits(cfg.stopBits)
                .flowControl(cfg.flowControl)
                .build()

            // 传跟随应用语言的 Context：库内错误提示按应用语言取词（Android 13 以下用 Application 取不到应用语言）
            val ysp = YSerialPort(Langs.context(), cfg.device, cfg.baudRate.toString())
            ysp.setThreadMode(ThreadMode.MAIN)
            ysp.setErrorListener { msg ->
                lastError = msg
                setState(State.ERROR, msg)
            }
            // 断帧组包：优先使用用户设置，否则按波特率自动推算
            if (cfg.frameGapMs > 0) ysp.setToAuto(cfg.frameGapMs) else ysp.setToAuto()
            ysp.setNoDataNotReturn(true)
            ysp.addDataListener { _, bytes -> handleReceive(bytes) }
            ysp.start(port)
            ySerialPort = ysp

            rxBytes = 0; txBytes = 0; rxPackets = 0; txPackets = 0
            connectedAt = System.currentTimeMillis()
            lastError = null
            setState(State.OPEN, cfg.summary())
            notifyStatsNow()
            true
        } catch (e: SecurityException) {
            // 底层异常文案（如 chmod 失败）由库打印到日志；界面只显示本地化提示，避免中英混杂
            setState(
                State.ERROR,
                Langs.str(R.string.serial_err_no_permission, Langs.str(R.string.serial_err_no_permission_hint))
            )
            false
        } catch (e: Exception) {
            setState(
                State.ERROR,
                Langs.str(R.string.serial_err_open_failed, e.message ?: e.javaClass.simpleName)
            )
            false
        }
    }

    fun close() {
        if (state == State.CLOSED && ySerialPort == null) return
        setState(State.CLOSING, null)
        stopLoop()
        fileSending = false
        val ysp = ySerialPort
        ySerialPort = null
        try {
            ysp?.onDestroy()
        } catch (e: Throwable) {
            // ignore
        }
        connectedAt = 0
        setState(State.CLOSED, null)
    }

    fun toggle(): Boolean = if (isOpen) {
        close(); false
    } else {
        open(SerialConfig.fromPrefs())
    }

    fun reStart(newConfig: SerialConfig): Boolean {
        close()
        return open(newConfig)
    }

    // ==================================================================
    // 发送
    // ==================================================================
    /** 发送原始字节（自动串行化） */
    fun send(bytes: ByteArray, type: Int = DataType.HEX, note: String = "", fromUser: Boolean = true): Boolean {
        if (bytes.isEmpty()) return false
        return sendInternal(bytes, type, note, fromUser)
    }

    private fun sendInternal(bytes: ByteArray, type: Int, note: String, fromUser: Boolean): Boolean {
        val ysp = ySerialPort
        if (ysp == null || state != State.OPEN) {
            if (fromUser) postError(Langs.str(R.string.serial_not_open))
            return false
        }
        txExecutor.execute {
            val ok = runCatching { ysp.sendSynchronization(bytes) }.getOrDefault(false)
            handler.post {
                if (ok) {
                    txBytes += bytes.size
                    txPackets++
                    val now = System.currentTimeMillis()
                    listeners.forEach { runCatching { it.onSend(bytes, type, now) } }
                    notifyStats()
                    val text = displayTextOf(bytes, type)
                    Store.addHistory(HistoryItem.of(now, Dir.TX, type, bytes, text, note))
                } else {
                    postError(Langs.str(R.string.serial_send_failed))
                }
            }
        }
        return true
    }

    /** 直接在主线程同步发送（不排队，供内部低延时场景使用） */
    fun sendSync(bytes: ByteArray): Boolean {
        val ysp = ySerialPort ?: return false
        return runCatching { ysp.sendSynchronization(bytes) }.getOrDefault(false)
    }

    // ==================================================================
    // 周期发送
    // ==================================================================
    fun startLoop(bytes: ByteArray, intervalMs: Int, count: Int, type: Int) {
        stopLoop()
        if (bytes.isEmpty()) return
        loopPayload = bytes to type
        loopIntervalMs = intervalMs.coerceAtLeast(1)
        loopTotal = count
        loopSent = 0
        loopRunning = true
        handler.postDelayed(loopRunnable, loopIntervalMs.toLong())
        notifyLoop(true, 0, count)
    }

    fun stopLoop() {
        if (!loopRunning) {
            handler.removeCallbacks(loopRunnable)
            return
        }
        loopRunning = false
        handler.removeCallbacks(loopRunnable)
        notifyLoop(false, loopSent, loopTotal)
    }

    val isLooping: Boolean get() = loopRunning

    // ==================================================================
    // 文件发送
    // ==================================================================
    fun sendFile(bytes: ByteArray, chunkSize: Int, delayMs: Int, type: Int = DataType.FILE) {
        if (bytes.isEmpty()) return
        if (fileSending) {
            postError(Langs.str(R.string.serial_file_busy))
            return
        }
        fileSending = true
        txExecutor.execute {
            val chunk = chunkSize.coerceIn(1, 4096)
            var sent = 0
            val ysp = ySerialPort
            while (sent < bytes.size && fileSending) {
                if (ysp == null || state != State.OPEN) break
                val len = minOf(chunk, bytes.size - sent)
                val part = bytes.copyOfRange(sent, sent + len)
                val ok = runCatching { ysp.sendSynchronization(part) }.getOrDefault(false)
                if (!ok) break
                sent += len
                val s = sent
                handler.post {
                    txBytes += len
                    listeners.forEach { runCatching { it.onFileProgress(s, bytes.size) } }
                    notifyStats()
                }
                if (delayMs > 0) Thread.sleep(delayMs.toLong())
            }
            val okAll = sent >= bytes.size
            fileSending = false
            handler.post {
                if (okAll) {
                    txPackets++
                    val now = System.currentTimeMillis()
                    listeners.forEach { runCatching { it.onSend(bytes, type, now) } }
                    Store.addHistory(
                        HistoryItem.of(
                            now, Dir.TX, type, bytes,
                            Langs.str(R.string.serial_file_bytes, bytes.size),
                            Langs.str(R.string.serial_file_send_note)
                        )
                    )
                } else {
                    postError(Langs.str(R.string.serial_file_interrupted, sent, bytes.size))
                }
                listeners.forEach { runCatching { it.onFileProgress(if (okAll) bytes.size else sent, bytes.size) } }
            }
        }
    }

    fun cancelFileSend() {
        fileSending = false
    }

    val isFileSending: Boolean get() = fileSending

    // ==================================================================
    // 接收
    // ==================================================================
    private fun handleReceive(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        // 字符集切换时重置解码器
        val cs = Prefs.rxCharset.toCharsetName()
        if (cs != decoderCharset) {
            decoderCharset = cs
            decoder = StreamingDecoder(cs)
        }
        val ascii = runCatching { decoder.decode(bytes) }.getOrDefault("")
        rxBytes += bytes.size
        rxPackets++
        val now = System.currentTimeMillis()
        listeners.forEach { runCatching { it.onReceive(bytes, ascii, now) } }
        notifyStats()
        // 历史
        val type = if (Prefs.rxMode == DataType.HEX) DataType.HEX else DataType.ASCII
        val text = if (type == DataType.HEX) Hex.toHexString(bytes, " ") else ascii
        Store.addHistory(HistoryItem.of(now, Dir.RX, type, bytes, text, Langs.str(R.string.serial_rx_note)))
        // 自动应答
        if (Prefs.autoReplyEnabled) {
            AutoReplyEngine.onReceive(bytes, ascii)
        }
    }

    /** 清空统计 */
    fun resetStats() {
        rxBytes = 0; txBytes = 0; rxPackets = 0; txPackets = 0
        notifyStatsNow()
    }

    // ==================================================================
    // 工具
    // ==================================================================
    fun displayTextOf(bytes: ByteArray, type: Int): String = when (type) {
        DataType.ASCII -> Charsets2.decode(bytes, Prefs.txCharset.toCharsetName())
        DataType.FILE -> Langs.str(R.string.serial_file_bytes, bytes.size)
        else -> Hex.toHexString(bytes, " ")
    }

    fun describeState(): String = when (state) {
        State.CLOSED -> Langs.str(R.string.serial_state_closed)
        State.OPENING -> Langs.str(R.string.serial_state_opening)
        State.OPEN -> Langs.str(R.string.serial_state_open)
        State.CLOSING -> Langs.str(R.string.serial_state_closing)
        State.ERROR -> Langs.str(R.string.serial_state_error_short)
    }

    fun uptimeMs(): Long = if (connectedAt > 0) System.currentTimeMillis() - connectedAt else 0

    private fun resetDecoder() {
        decoderCharset = Prefs.rxCharset.toCharsetName()
        decoder = StreamingDecoder(decoderCharset)
    }

    private fun setState(s: State, msg: String?) {
        state = s
        if (s == State.ERROR) lastError = msg
        handler.post {
            listeners.forEach { runCatching { it.onState(s, msg) } }
        }
    }

    private var statsScheduled = false
    private val statsRunnable = Runnable {
        statsScheduled = false
        listeners.forEach { runCatching { it.onStats(rxBytes, txBytes, rxPackets, txPackets) } }
    }

    /**
     * 统计回调限流：高频收发时每包都回调会压满主线程，
     * 这里合并到最多每 200ms 一次。
     */
    private fun notifyStats() {
        if (statsScheduled) return
        statsScheduled = true
        handler.postDelayed(statsRunnable, STATS_INTERVAL)
    }

    /** 需要立刻刷新的场景（打开/关闭串口、清零统计） */
    private fun notifyStatsNow() {
        handler.removeCallbacks(statsRunnable)
        statsScheduled = false
        listeners.forEach { runCatching { it.onStats(rxBytes, txBytes, rxPackets, txPackets) } }
    }

    private fun notifyLoop(running: Boolean, sent: Int, total: Int) {
        handler.post {
            listeners.forEach { runCatching { it.onLoopState(running, sent, total) } }
        }
    }

    private const val STATS_INTERVAL = 200L

    private fun postError(msg: String) {
        lastError = msg
        handler.post {
            listeners.forEach { runCatching { it.onState(state, msg) } }
        }
    }

    private fun String.toCharsetName(): String = Charsets2.byDisplay(this)
}
