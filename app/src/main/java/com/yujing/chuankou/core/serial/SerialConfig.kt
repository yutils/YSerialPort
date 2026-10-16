package com.yujing.chuankou.core.serial

import com.yujing.chuankou.core.data.Prefs

/**
 * 串口全参数配置
 */
data class SerialConfig(
    val device: String = "",
    val baudRate: Int = 115200,
    /** 5~8 */
    val dataBits: Int = 8,
    /** 1:1位 2:2位 3:1.5位（需5位数据位） */
    val stopBits: Int = 1,
    /** 0:NONE 1:ODD 2:EVEN 3:MARK 4:SPACE */
    val parity: Int = 0,
    /** 0:无 1:硬件RTS/CTS 2:软件XON/XOFF */
    val flowControl: Int = 0,
    /** 断帧组包间隔(ms)，0=按波特率自动 */
    val frameGapMs: Int = 0
) {
    val isValid: Boolean get() = device.isNotBlank() && baudRate > 0

    /** 形如 /dev/ttyS1 · 115200 · 8N1 */
    fun summary(): String = "$device · $baudRate · ${dataBits}${parityLetter()}${stopBitsText()}"

    fun frameSummary(): String {
        val flow = if (flowControl == 0) "" else " · ${FLOW_NAMES[flowControl]}"
        return "${dataBits}${parityLetter()}${stopBitsText()}$flow"
    }

    private fun parityLetter(): String = when (parity) {
        1 -> "O"
        2 -> "E"
        3 -> "M"
        4 -> "S"
        else -> "N"
    }

    private fun stopBitsText(): String = when (stopBits) {
        2 -> "2"
        3 -> "1.5"
        else -> "1"
    }

    companion object {
        val BAUD_RATES: List<Int> = listOf(
            300, 600, 1200, 2400, 4800, 7200, 9600, 14400, 19200, 28800, 38400, 56000,
            57600, 76800, 115200, 128000, 230400, 256000, 460800, 500000, 576000,
            921600, 1000000, 1152000, 1500000, 2000000, 2500000, 3000000, 3500000, 4000000
        )

        val DATA_BITS = listOf(5, 6, 7, 8)
        val STOP_BITS_NAMES = listOf("1", "2", "1.5")
        val STOP_BITS = listOf(1, 2, 3)
        val PARITY_NAMES = listOf("无校验 (None)", "奇校验 (Odd)", "偶校验 (Even)", "标记校验 (Mark)", "空格校验 (Space)")
        val FLOW_NAMES = listOf("无流控", "硬件流控 (RTS/CTS)", "软件流控 (XON/XOFF)")

        fun fromPrefs(): SerialConfig = SerialConfig(
            device = Prefs.device,
            baudRate = Prefs.baudRate.toIntOrNull() ?: 115200,
            dataBits = Prefs.dataBits,
            stopBits = Prefs.stopBits,
            parity = Prefs.parity,
            flowControl = Prefs.flowControl,
            frameGapMs = Prefs.frameGapMs
        )

        fun save(config: SerialConfig) {
            Prefs.device = config.device
            Prefs.baudRate = config.baudRate.toString()
            Prefs.dataBits = config.dataBits
            Prefs.stopBits = config.stopBits
            Prefs.parity = config.parity
            Prefs.flowControl = config.flowControl
            Prefs.frameGapMs = config.frameGapMs
        }
    }
}
