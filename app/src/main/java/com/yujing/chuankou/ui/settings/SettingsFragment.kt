package com.yujing.chuankou.ui.settings

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import com.yujing.chuankou.App
import com.yujing.chuankou.R
import com.yujing.chuankou.core.data.Newline
import com.yujing.chuankou.core.data.Prefs
import com.yujing.chuankou.core.data.Store
import com.yujing.chuankou.core.serial.SerialConfig
import com.yujing.chuankou.core.serial.SerialManager
import com.yujing.chuankou.core.util.Charsets2
import com.yujing.chuankou.databinding.FragmentSettingsBinding
import com.yujing.chuankou.databinding.ViewSettingRowBinding
import com.yujing.chuankou.ui.MainActivity
import com.yujing.chuankou.ui.base.BaseFragment
import com.yujing.chuankou.ui.widget.Dialogs
import com.yujing.yserialport.YSerialPort

/**
 * 设置页：串口全参数 + 收发配置 + 外观
 */
class SettingsFragment : BaseFragment<FragmentSettingsBinding>(FragmentSettingsBinding::inflate),
    SerialManager.Listener {

    private var config: SerialConfig = SerialConfig()

    override fun onInit(view: View, savedInstanceState: Bundle?) {
        config = SerialConfig.fromPrefs()
        initSerialSection()
        initRxSection()
        initTxSection()
        initAppearanceSection()
        refreshAll()
        SerialManager.addListener(this)
    }

    override fun onRelease() {
        SerialManager.removeListener(this)
    }

    override fun onPageResume() {
        config = SerialConfig.fromPrefs()
        refreshAll()
    }

    // ==================================================================
    private fun refreshAll() {
        if (view == null) return
        refreshSerialSection()
        refreshRxSection()
        refreshTxSection()
        refreshAppearanceSection()
    }

    // ==================================================================
    // 串口
    // ==================================================================
    private fun initSerialSection() {
        binding.rowDevice.root.setOnClickListener {
            val paths = YSerialPort.getDevices() ?: emptyArray()
            val names = YSerialPort.getSerialPortFinder().allDevices ?: emptyArray()
            if (paths.isEmpty()) {
                snack("未扫描到任何串口设备")
                return@setOnClickListener
            }
            val items = paths.mapIndexed { i, p -> "${names.getOrElse(i) { p }}\n$p" }
            val checked = paths.indexOfFirst { it == config.device }
            Dialogs.singleChoice(requireContext(), "选择串口", items, checked) { which ->
                config = config.copy(device = paths[which])
                SerialConfig.save(config)
                refreshSerialSection()
                restartIfOpen()
            }
        }

        binding.rowBaud.root.setOnClickListener {
            val list = SerialConfig.BAUD_RATES.map { it.toString() }
            val checked = SerialConfig.BAUD_RATES.indexOfFirst { it == config.baudRate }
            Dialogs.singleChoice(requireContext(), "选择波特率", list, checked) { which ->
                config = config.copy(baudRate = SerialConfig.BAUD_RATES[which])
                SerialConfig.save(config)
                refreshSerialSection()
                restartIfOpen()
            }
        }

        binding.rowDataBits.root.setOnClickListener {
            val list = SerialConfig.DATA_BITS.map { "$it 位" }
            Dialogs.singleChoice(requireContext(), "数据位", list, SerialConfig.DATA_BITS.indexOf(config.dataBits)) { which ->
                config = config.copy(dataBits = SerialConfig.DATA_BITS[which])
                SerialConfig.save(config)
                refreshSerialSection()
                restartIfOpen()
            }
        }

        binding.rowStopBits.root.setOnClickListener {
            Dialogs.singleChoice(requireContext(), "停止位", SerialConfig.STOP_BITS_NAMES, SerialConfig.STOP_BITS.indexOf(config.stopBits)) { which ->
                config = config.copy(stopBits = SerialConfig.STOP_BITS[which])
                SerialConfig.save(config)
                refreshSerialSection()
                restartIfOpen()
            }
        }

        binding.rowParity.root.setOnClickListener {
            Dialogs.singleChoice(requireContext(), "校验位", SerialConfig.PARITY_NAMES, config.parity) { which ->
                config = config.copy(parity = which)
                SerialConfig.save(config)
                refreshSerialSection()
                restartIfOpen()
            }
        }

        binding.rowFlow.root.setOnClickListener {
            Dialogs.singleChoice(requireContext(), "流控", SerialConfig.FLOW_NAMES, config.flowControl) { which ->
                config = config.copy(flowControl = which)
                SerialConfig.save(config)
                refreshSerialSection()
                restartIfOpen()
            }
        }

        binding.rowFrameGap.root.setOnClickListener {
            Dialogs.input(
                requireContext(), "断帧组包间隔", "毫秒，0 = 按波特率自动计算",
                config.frameGapMs.toString(), "收到数据后静默该时间即认为一帧结束", numeric = true
            ) { text ->
                val v = text.toIntOrNull() ?: 0
                config = config.copy(frameGapMs = v.coerceIn(0, 5000))
                SerialConfig.save(config)
                refreshSerialSection()
                restartIfOpen()
            }
        }

        binding.rowScan.root.setOnClickListener {
            // 「刷新串口」已并入本行：每次点击重新扫描并展示
            val paths = YSerialPort.getDevices() ?: emptyArray()
            val names = YSerialPort.getSerialPortFinder().allDevices ?: emptyArray()
            refreshSerialSection()
            if (paths.isEmpty()) {
                Dialogs.info(requireContext(), "串口列表", "未扫描到串口设备。\n请确认设备已开放串口权限（/dev/ttyS*）。")
            } else {
                Dialogs.info(
                    requireContext(), "串口列表（共 ${paths.size} 个）",
                    paths.mapIndexed { i, p -> "${names.getOrElse(i) { "" }}\n$p" }.joinToString("\n\n")
                )
            }
        }

        // 开关串口：与右上角电源图标同一个开关，就近提供一份
        binding.chipSerialToggle.setOnClickListener {
            if (SerialManager.isOpen) {
                SerialManager.close()
            } else {
                if (config.device.isBlank()) {
                    snack("请先选择串口")
                    return@setOnClickListener
                }
                SerialConfig.save(config)
                SerialManager.open(config)
            }
        }
    }

    private fun refreshSerialSection() {
        setValue(binding.rowDevice, "串口设备", config.device.ifBlank { "未选择" }, "如 /dev/ttyS1，列表来自系统串口扫描")
        setValue(binding.rowBaud, "波特率", config.baudRate.toString(), "常见：9600 / 115200 / 921600")
        setValue(binding.rowDataBits, "数据位", "${config.dataBits} 位", "可选 5 ~ 8 位")
        setValue(binding.rowStopBits, "停止位", SerialConfig.STOP_BITS_NAMES.getOrElse(SerialConfig.STOP_BITS.indexOf(config.stopBits)) { "1" })
        setValue(binding.rowParity, "校验位", SerialConfig.PARITY_NAMES.getOrElse(config.parity) { "无校验" })
        setValue(binding.rowFlow, "流控", SerialConfig.FLOW_NAMES.getOrElse(config.flowControl) { "无流控" })
        setValue(binding.rowFrameGap, "断帧组包间隔", if (config.frameGapMs <= 0) "自动" else "${config.frameGapMs} ms")
        setValue(binding.rowScan, "串口列表", "已发现 ${YSerialPort.getDevices()?.size ?: 0} 个", "点击重新扫描并查看设备名称与路径")

        val open = SerialManager.isOpen
        binding.chipSerialToggle.text = if (open) "关闭串口" else "打开串口"
        binding.chipSerialToggle.setBackgroundResource(if (open) R.drawable.bg_status_open else R.drawable.bg_status_closed)
    }

    private fun restartIfOpen() {
        if (SerialManager.isOpen) {
            SerialManager.open(config)
            snack("已按新参数重新连接")
        }
    }

    // ==================================================================
    // 接收
    // ==================================================================
    private fun initRxSection() {
        binding.rowRxCharset.root.setOnClickListener {
            val names = Charsets2.SUPPORTED.map { it.first }
            Dialogs.singleChoice(requireContext(), "接收字符集", names, names.indexOfFirst { Charsets2.byDisplay(it) == Prefs.rxCharset }) { which ->
                Prefs.rxCharset = Charsets2.SUPPORTED[which].second
                refreshRxSection()
            }
        }
        bindSwitch(binding.rowRxTime, "显示时间戳", { Prefs.rxShowTime }, { Prefs.rxShowTime = it }, sub = "关闭自动换行时时间戳会被忽略")
        bindSwitch(binding.rowRxWrap, "自动换行", { Prefs.rxAutoWrap }, { Prefs.rxAutoWrap = it }, sub = "关闭后数据连续拼接，不插入任何换行")
        binding.rowRxSep.root.setOnClickListener {
            val names = listOf("空格", "无分隔", "逗号", "横线")
            Dialogs.singleChoice(requireContext(), "HEX 分隔符", names, Prefs.rxHexSeparator) { which ->
                Prefs.rxHexSeparator = which
                refreshRxSection()
            }
        }
        binding.rowRxMax.root.setOnClickListener {
            Dialogs.input(requireContext(), "最大显示字符数", "超出后自动从头部裁剪", Prefs.rxMaxChars.toString(), numeric = true) { text ->
                val v = text.toIntOrNull() ?: 20000
                Prefs.rxMaxChars = v.coerceIn(1000, 500000)
                refreshRxSection()
            }
        }
        bindSwitch(binding.rowRxEcho, "接收区回显发送内容", { Prefs.rxEchoTx }, { Prefs.rxEchoTx = it })
    }

    private fun refreshRxSection() {
        setValue(
            binding.rowRxCharset, "字符集", Charsets2.displayOf(Prefs.rxCharset),
            "用于 ASCII 模式解码；解不出来的字节显示为 \\xNN（如 \\xA1），而不是替换符"
        )
        setSwitchValue(binding.rowRxTime, "显示时间戳", Prefs.rxShowTime)
        setSwitchValue(binding.rowRxWrap, "自动换行", Prefs.rxAutoWrap)
        setValue(binding.rowRxSep, "HEX 分隔符", listOf("空格", "无分隔", "逗号", "横线").getOrElse(Prefs.rxHexSeparator) { "空格" })
        setValue(binding.rowRxMax, "最大显示字符数", "${Prefs.rxMaxChars}")
        setSwitchValue(binding.rowRxEcho, "接收区回显发送内容", Prefs.rxEchoTx)
    }

    // ==================================================================
    // 发送
    // ==================================================================
    private fun initTxSection() {
        binding.rowTxCharset.root.setOnClickListener {
            val names = Charsets2.SUPPORTED.map { it.first }
            Dialogs.singleChoice(requireContext(), "发送字符集", names, names.indexOfFirst { Charsets2.byDisplay(it) == Prefs.txCharset }) { which ->
                Prefs.txCharset = Charsets2.SUPPORTED[which].second
                refreshTxSection()
            }
        }
        bindSwitch(binding.rowTxEscape, "解析转义字符", { Prefs.txEscape }, { Prefs.txEscape = it }, sub = "支持 \\r \\n \\t \\0 \\xNN \\uHHHH")
        binding.rowTxNewline.root.setOnClickListener {
            Dialogs.singleChoice(requireContext(), "默认结束符", Newline.NAMES, Prefs.txAppendNewline) { which ->
                Prefs.txAppendNewline = which
                refreshTxSection()
            }
        }
        binding.rowTxChunk.root.setOnClickListener {
            Dialogs.input(requireContext(), "文件分包大小", "字节，1 ~ 4096", Prefs.txFileChunk.toString(), numeric = true) { text ->
                Prefs.txFileChunk = (text.toIntOrNull() ?: 512).coerceIn(1, 4096)
                refreshTxSection()
            }
        }
        binding.rowTxDelay.root.setOnClickListener {
            Dialogs.input(requireContext(), "文件包间延时", "毫秒", Prefs.txFileDelay.toString(), numeric = true) { text ->
                Prefs.txFileDelay = (text.toIntOrNull() ?: 5).coerceIn(0, 5000)
                refreshTxSection()
            }
        }
    }

    private fun refreshTxSection() {
        setValue(binding.rowTxCharset, "字符集", Charsets2.displayOf(Prefs.txCharset))
        setSwitchValue(binding.rowTxEscape, "解析转义字符", Prefs.txEscape)
        setValue(binding.rowTxNewline, "默认结束符", Newline.NAMES.getOrElse(Prefs.txAppendNewline) { "无" })
        setValue(binding.rowTxChunk, "文件分包大小", "${Prefs.txFileChunk} 字节")
        setValue(binding.rowTxDelay, "文件包间延时", "${Prefs.txFileDelay} ms")
    }

    // ==================================================================
    // 外观
    // ==================================================================
    private fun initAppearanceSection() {
        binding.rowTheme.root.setOnClickListener {
            val names = listOf("跟随系统", "深色", "浅色")
            Dialogs.singleChoice(requireContext(), "主题", names, Prefs.themeMode) { which ->
                Prefs.themeMode = which
                (requireActivity().application as App).applyTheme(which)
                refreshAppearanceSection()
            }
        }
        binding.rowBg.root.setOnClickListener {
            Dialogs.singleChoice(requireContext(), "背景图", listOf("无背景", "内置壁纸", "自定义（更多页选择图片）"), Prefs.bgMode) { which ->
                Prefs.bgMode = which
                (activity as? MainActivity)?.applyBackground()
                refreshAppearanceSection()
            }
        }
        binding.rowBgScrim.root.setOnClickListener {
            Dialogs.input(requireContext(), "背景遮罩", "0 ~ 100，越大背景越暗（推荐 70 ~ 90）", Prefs.bgScrim.toString(), numeric = true) { text ->
                Prefs.bgScrim = (text.toIntOrNull() ?: 78).coerceIn(0, 100)
                (activity as? MainActivity)?.applyBackground()
                refreshAppearanceSection()
            }
        }
        binding.rowFont.root.setOnClickListener {
            Dialogs.input(requireContext(), "终端字号", "单位 sp，9 ~ 24", Prefs.terminalFontSize.toString(), numeric = true) { text ->
                Prefs.terminalFontSize = (text.toIntOrNull() ?: 13).coerceIn(9, 24)
                refreshAppearanceSection()
            }
        }
        bindSwitch(binding.rowKeepScreen, "保持屏幕常亮", { Prefs.keepScreenOn }, { Prefs.keepScreenOn = it })
        binding.rowHistoryLimit.root.setOnClickListener {
            Dialogs.input(requireContext(), "历史记录上限", "条，100 ~ 50000", Prefs.historyLimit.toString(), numeric = true) { text ->
                Prefs.historyLimit = (text.toIntOrNull() ?: 2000).coerceIn(100, 50000)
                refreshAppearanceSection()
            }
        }
    }

    private fun refreshAppearanceSection() {
        setValue(binding.rowTheme, "主题", listOf("跟随系统", "深色", "浅色").getOrElse(Prefs.themeMode) { "深色" })
        setValue(binding.rowBg, "背景图", listOf("无背景", "内置壁纸", "自定义").getOrElse(Prefs.bgMode) { "无背景" })
        setValue(binding.rowBgScrim, "背景遮罩", "${Prefs.bgScrim} %")
        setValue(binding.rowFont, "终端字号", "${Prefs.terminalFontSize} sp")
        setSwitchValue(binding.rowKeepScreen, "保持屏幕常亮", Prefs.keepScreenOn)
        setValue(binding.rowHistoryLimit, "历史记录上限", "${Prefs.historyLimit} 条（当前 ${Store.historySize} 条）")
    }

    // ==================================================================
    // 行操作辅助
    // ==================================================================
    private fun setValue(row: ViewSettingRowBinding, title: String, value: String?, sub: String? = null) {
        row.tvTitle.text = title
        row.tvValue.text = value ?: ""
        row.tvValue.visibility = if (value.isNullOrEmpty()) View.GONE else View.VISIBLE
        row.ivArrow.visibility = View.VISIBLE
        row.swToggle.visibility = View.GONE
        if (sub != null) {
            row.tvSub.text = sub
            row.tvSub.visibility = View.VISIBLE
        } else {
            row.tvSub.visibility = View.GONE
        }
    }

    private fun setSwitchValue(row: ViewSettingRowBinding, title: String, checked: Boolean, sub: String? = null) {
        row.tvTitle.text = title
        row.tvValue.visibility = View.GONE
        row.ivArrow.visibility = View.GONE
        row.swToggle.visibility = View.VISIBLE
        row.swToggle.setOnCheckedChangeListener(null)
        row.swToggle.isChecked = checked
        if (sub != null) {
            row.tvSub.text = sub
            row.tvSub.visibility = View.VISIBLE
        } else {
            row.tvSub.visibility = View.GONE
        }
    }

    private fun bindSwitch(
        row: ViewSettingRowBinding,
        title: String,
        get: () -> Boolean,
        set: (Boolean) -> Unit,
        sub: String? = null
    ) {
        setSwitchValue(row, title, get(), sub)
        row.swToggle.setOnCheckedChangeListener { _, checked -> set(checked) }
        row.root.setOnClickListener { row.swToggle.isChecked = !row.swToggle.isChecked }
    }

    // ==================================================================
    override fun onState(state: SerialManager.State, message: String?) {
        if (view == null) return
        view?.post {
            refreshSerialSection()
            if (state == SerialManager.State.ERROR && !message.isNullOrBlank()) snack(message)
            if (state == SerialManager.State.OPEN) snack("串口已连接")
        }
    }
}
