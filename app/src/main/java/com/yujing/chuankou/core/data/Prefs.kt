package com.yujing.chuankou.core.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * 全局配置持久化（SharedPreferences）
 */
object Prefs {

    private const val NAME = "yserialport_prefs"
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    }

    // ------------------------------------------------------------------
    // 基础读写
    // ------------------------------------------------------------------
    fun str(key: String, def: String = ""): String = runCatching { sp.getString(key, def) ?: def }.getOrDefault(def)
    fun strOrNull(key: String): String? = runCatching { sp.getString(key, null) }.getOrNull()
    fun put(key: String, v: String?) = sp.edit { if (v == null) remove(key) else putString(key, v) }
    fun int(key: String, def: Int = 0): Int = runCatching { sp.getInt(key, def) }.getOrDefault(def)
    fun put(key: String, v: Int) = sp.edit { putInt(key, v) }
    fun bool(key: String, def: Boolean = false): Boolean = runCatching { sp.getBoolean(key, def) }.getOrDefault(def)
    fun put(key: String, v: Boolean) = sp.edit { putBoolean(key, v) }
    fun long(key: String, def: Long = 0L): Long = runCatching { sp.getLong(key, def) }.getOrDefault(def)
    fun put(key: String, v: Long) = sp.edit { putLong(key, v) }
    fun remove(key: String) = sp.edit { remove(key) }
    fun clearAll() = sp.edit { clear() }
    fun contains(key: String): Boolean = runCatching { sp.contains(key) }.getOrDefault(false)

    // ------------------------------------------------------------------
    // 串口参数
    // ------------------------------------------------------------------
    var device: String
        get() = str(K_DEVICE, "")
        set(v) = put(K_DEVICE, v)

    var baudRate: String
        get() = str(K_BAUD, "115200")
        set(v) = put(K_BAUD, v)

    /** 5~8 */
    var dataBits: Int
        get() = int(K_DATA_BITS, 8)
        set(v) = put(K_DATA_BITS, v)

    /** 1 / 2 */
    var stopBits: Int
        get() = int(K_STOP_BITS, 1)
        set(v) = put(K_STOP_BITS, v)

    /** 0 NONE / 1 ODD / 2 EVEN */
    var parity: Int
        get() = int(K_PARITY, 0)
        set(v) = put(K_PARITY, v)

    /** 0 无 / 1 硬件(RTS/CTS) / 2 软件(XON/XOFF) */
    var flowControl: Int
        get() = int(K_FLOW, 0)
        set(v) = put(K_FLOW, v)

    /** 断帧组包间隔（ms），0 表示按波特率自动计算 */
    var frameGapMs: Int
        get() = int(K_FRAME_GAP, 0)
        set(v) = put(K_FRAME_GAP, v)

    // ------------------------------------------------------------------
    // 接收配置
    // ------------------------------------------------------------------
    /** 0 ASCII / 1 HEX */
    var rxMode: Int
        get() = int(K_RX_MODE, 0)
        set(v) = put(K_RX_MODE, v)

    var rxCharset: String
        get() = str(K_RX_CHARSET, "UTF-8")
        set(v) = put(K_RX_CHARSET, v)

    /** 每条数据自动换行；关闭则无限拼接 */
    var rxAutoWrap: Boolean
        get() = bool(K_RX_WRAP, true)
        set(v) = put(K_RX_WRAP, v)

    var rxShowTime: Boolean
        get() = bool(K_RX_TIME, true)
        set(v) = put(K_RX_TIME, v)

    /** HEX 分隔符：0 空格 / 1 无 / 2 逗号 / 3 横线 */
    var rxHexSeparator: Int
        get() = int(K_RX_SEP, 0)
        set(v) = put(K_RX_SEP, v)

    /** 最多显示字符数 */
    var rxMaxChars: Int
        get() = int(K_RX_MAX, 20000)
        set(v) = put(K_RX_MAX, v)

    /** 接收区显示发送的数据（回显） */
    var rxEchoTx: Boolean
        get() = bool(K_RX_ECHO_TX, false)
        set(v) = put(K_RX_ECHO_TX, v)

    // ------------------------------------------------------------------
    // 发送配置
    // ------------------------------------------------------------------
    /** 0 ASCII / 1 HEX / 2 文件 */
    var txMode: Int
        get() = int(K_TX_MODE, 0)
        set(v) = put(K_TX_MODE, v)

    var txCharset: String
        get() = str(K_TX_CHARSET, "UTF-8")
        set(v) = put(K_TX_CHARSET, v)

    /** 0 无 / 1 \r\n / 2 \n / 3 \r */
    var txAppendNewline: Int
        get() = int(K_TX_NL, 0)
        set(v) = put(K_TX_NL, v)

    /** 解析转义字符 */
    var txEscape: Boolean
        get() = bool(K_TX_ESCAPE, false)
        set(v) = put(K_TX_ESCAPE, v)

    /** 周期发送间隔 ms */
    var txLoopInterval: Int
        get() = int(K_TX_LOOP, 1000)
        set(v) = put(K_TX_LOOP, v)

    /** 周期发送次数，0=无限 */
    var txLoopCount: Int
        get() = int(K_TX_LOOP_COUNT, 0)
        set(v) = put(K_TX_LOOP_COUNT, v)

    /** 文件发送分包大小(字节) */
    var txFileChunk: Int
        get() = int(K_TX_CHUNK, 512)
        set(v) = put(K_TX_CHUNK, v)

    /** 文件发送包间延时(ms) */
    var txFileDelay: Int
        get() = int(K_TX_FILE_DELAY, 5)
        set(v) = put(K_TX_FILE_DELAY, v)

    var lastSendText: String
        get() = str(K_LAST_TEXT)
        set(v) = put(K_LAST_TEXT, v)

    var lastSendHex: String
        get() = str(K_LAST_HEX)
        set(v) = put(K_LAST_HEX, v)

    var lastFilePath: String
        get() = str(K_LAST_FILE)
        set(v) = put(K_LAST_FILE, v)

    /** 发送历史（最近发送内容，换行分隔，最多 50 条） */
    var sendHistory: String
        get() = str(K_SEND_HISTORY)
        set(v) = put(K_SEND_HISTORY, v)

    // ------------------------------------------------------------------
    // 外观
    // ------------------------------------------------------------------
    /** 0 跟随系统 / 1 深色 / 2 浅色 */
    var themeMode: Int
        get() = int(K_THEME, 1)
        set(v) = put(K_THEME, v)

    /** 0 无背景图 / 1 内置 / 2 自定义 */
    var bgMode: Int
        get() = int(K_BG_MODE, 0)
        set(v) = put(K_BG_MODE, v)

    var bgPath: String
        get() = str(K_BG_PATH)
        set(v) = put(K_BG_PATH, v)

    /** 背景图遮罩透明度 0~100 */
    var bgScrim: Int
        get() = int(K_BG_SCRIM, 78)
        set(v) = put(K_BG_SCRIM, v)

    /** 终端字号 sp */
    var terminalFontSize: Int
        get() = int(K_TERM_FS, 13)
        set(v) = put(K_TERM_FS, v)

    /** 常亮屏幕 */
    var keepScreenOn: Boolean
        get() = bool(K_KEEP_SCREEN, true)
        set(v) = put(K_KEEP_SCREEN, v)

    // ------------------------------------------------------------------
    // 其他
    // ------------------------------------------------------------------
    var autoReplyEnabled: Boolean
        get() = bool(K_AUTO_REPLY, true)
        set(v) = put(K_AUTO_REPLY, v)

    var historyLimit: Int
        get() = int(K_HISTORY_LIMIT, 2000)
        set(v) = put(K_HISTORY_LIMIT, v)

    var tcpPort: String
        get() = str(K_TCP_PORT, "8888")
        set(v) = put(K_TCP_PORT, v)

    var tcpHost: String
        get() = str(K_TCP_HOST, "192.168.1.100")
        set(v) = put(K_TCP_HOST, v)

    var tcpRemotePort: String
        get() = str(K_TCP_REMOTE_PORT, "8888")
        set(v) = put(K_TCP_REMOTE_PORT, v)

    // ------------------------------------------------------------------
    private const val K_DEVICE = "device"
    private const val K_BAUD = "baudRate"
    private const val K_DATA_BITS = "dataBits"
    private const val K_STOP_BITS = "stopBits"
    private const val K_PARITY = "parity"
    private const val K_FLOW = "flowControl"
    private const val K_FRAME_GAP = "frameGap"
    private const val K_RX_MODE = "rxMode"
    private const val K_RX_CHARSET = "rxCharset"
    private const val K_RX_WRAP = "rxAutoWrap"
    private const val K_RX_TIME = "rxShowTime"
    private const val K_RX_SEP = "rxHexSep"
    private const val K_RX_MAX = "rxMaxChars"
    private const val K_RX_ECHO_TX = "rxEchoTx"
    private const val K_TX_MODE = "txMode"
    private const val K_TX_CHARSET = "txCharset"
    private const val K_TX_NL = "txNewline"
    private const val K_TX_ESCAPE = "txEscape"
    private const val K_TX_LOOP = "txLoopInterval"
    private const val K_TX_LOOP_COUNT = "txLoopCount"
    private const val K_TX_CHUNK = "txFileChunk"
    private const val K_TX_FILE_DELAY = "txFileDelay"
    private const val K_LAST_TEXT = "lastSendText"
    private const val K_LAST_HEX = "lastSendHex"
    private const val K_LAST_FILE = "lastFilePath"
    private const val K_SEND_HISTORY = "sendHistory"
    private const val K_THEME = "themeMode"
    private const val K_BG_MODE = "bgMode"
    private const val K_BG_PATH = "bgPath"
    private const val K_BG_SCRIM = "bgScrim"
    private const val K_TERM_FS = "terminalFontSize"
    private const val K_KEEP_SCREEN = "keepScreenOn"
    private const val K_AUTO_REPLY = "autoReplyEnabled"
    private const val K_HISTORY_LIMIT = "historyLimit"
    private const val K_TCP_PORT = "tcpPort"
    private const val K_TCP_HOST = "tcpHost"
    private const val K_TCP_REMOTE_PORT = "tcpRemotePort"
}
