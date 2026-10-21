package com.yujing.chuankou.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.radiobutton.MaterialRadioButton
import com.yujing.chuankou.App
import com.yujing.chuankou.R
import com.yujing.chuankou.core.data.Newline
import com.yujing.chuankou.core.data.Prefs
import com.yujing.chuankou.core.data.Store
import com.yujing.chuankou.core.serial.SerialConfig
import com.yujing.chuankou.core.serial.SerialManager
import com.yujing.chuankou.core.util.Charsets2
import com.yujing.chuankou.core.util.Languages
import com.yujing.chuankou.databinding.FragmentSettingsBinding
import com.yujing.chuankou.databinding.ViewSettingRowBinding
import com.yujing.chuankou.ui.MainActivity
import com.yujing.chuankou.ui.base.BaseFragment
import com.yujing.chuankou.ui.widget.Dialogs
import com.yujing.yserialport.YSerialPort

/**
 * 设置页：语言 + 串口全参数 + 收发配置 + 外观
 */
class SettingsFragment : BaseFragment<FragmentSettingsBinding>(FragmentSettingsBinding::inflate),
    SerialManager.Listener {

    private var config: SerialConfig = SerialConfig()

    /** 最近一次扫描到的串口数量（-1 = 还没扫过）。避免每次 refresh 都遍历一遍 /dev */
    private var deviceCount: Int = -1

    override fun onInit(view: View, savedInstanceState: Bundle?) {
        config = SerialConfig.fromPrefs()
        initGeneralSection()
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
        refreshGeneralSection()
        refreshSerialSection()
        refreshRxSection()
        refreshTxSection()
        refreshAppearanceSection()
    }

    // ==================================================================
    // 通用（语言）
    // ==================================================================
    private fun initGeneralSection() {
        binding.rowLanguage.root.setOnClickListener { showLanguageDialog() }
    }

    /**
     * 语言选择弹窗：两列紧凑单选列表
     * （系统默认单选列表行高按 48dp 触控目标，10 种语言 + 跟随系统共 11 项会被对话框高度裁掉后几项，需滚动才能看到）
     */
    private fun showLanguageDialog() {
        val ctx = requireContext()
        val names = (0 until Languages.optionCount).map { Languages.displayNameAt(ctx, it) }
        val current = Languages.currentIndex()

        val columns = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        val half = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        val leftColumn = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; layoutParams = half }
        val rightColumn = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; layoutParams = half }
        columns.addView(leftColumn)
        columns.addView(rightColumn)

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.settings_language_dialog_title)
            .setView(columns)
            .setNegativeButton(R.string.common_cancel, null)
            .create()

        val perColumn = (names.size + 1) / 2
        names.forEachIndexed { index, name ->
            val radio = MaterialRadioButton(ctx).apply {
                text = name
                isChecked = index == current
                setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
                setOnClickListener {
                    // 切换语言：AppCompatDelegate 会自动持久化并重建 Activity
                    Languages.apply(index)
                    dialog.dismiss()
                }
            }
            if (index < perColumn) leftColumn.addView(radio) else rightColumn.addView(radio)
        }
        dialog.show()
    }

    private fun refreshGeneralSection() {
        setValue(
            binding.rowLanguage, getString(R.string.settings_language),
            Languages.displayNameAt(requireContext(), Languages.currentIndex()),
            getString(R.string.settings_language_sub)
        )
    }

    // ==================================================================
    // 串口
    // ==================================================================
    private fun initSerialSection() {
        binding.rowDevice.root.setOnClickListener {
            val paths = YSerialPort.getDevices() ?: emptyArray()
            val names = YSerialPort.getSerialPortFinder().allDevices ?: emptyArray()
            if (paths.isEmpty()) {
                snack(getString(R.string.serial_no_devices_found))
                return@setOnClickListener
            }
            val items = paths.mapIndexed { i, p -> "${names.getOrElse(i) { p }}\n$p" }
            val checked = paths.indexOfFirst { it == config.device }
            Dialogs.singleChoice(requireContext(), getString(R.string.serial_select_device), items, checked) { which ->
                config = config.copy(device = paths[which])
                SerialConfig.save(config)
                refreshSerialSection()
                restartIfOpen()
            }
        }

        binding.rowBaud.root.setOnClickListener {
            val list = SerialConfig.BAUD_RATES.map { it.toString() }
            val checked = SerialConfig.BAUD_RATES.indexOfFirst { it == config.baudRate }
            Dialogs.singleChoice(requireContext(), getString(R.string.serial_select_baud), list, checked) { which ->
                config = config.copy(baudRate = SerialConfig.BAUD_RATES[which])
                SerialConfig.save(config)
                refreshSerialSection()
                restartIfOpen()
            }
        }

        binding.rowDataBits.root.setOnClickListener {
            val list = SerialConfig.DATA_BITS.map { getString(R.string.settings_bits_format, it) }
            Dialogs.singleChoice(requireContext(), getString(R.string.serial_data_bits), list, SerialConfig.DATA_BITS.indexOf(config.dataBits)) { which ->
                config = config.copy(dataBits = SerialConfig.DATA_BITS[which])
                SerialConfig.save(config)
                refreshSerialSection()
                restartIfOpen()
            }
        }

        binding.rowStopBits.root.setOnClickListener {
            Dialogs.singleChoice(requireContext(), getString(R.string.serial_stop_bits), SerialConfig.STOP_BITS_NAMES, SerialConfig.STOP_BITS.indexOf(config.stopBits)) { which ->
                config = config.copy(stopBits = SerialConfig.STOP_BITS[which])
                SerialConfig.save(config)
                refreshSerialSection()
                restartIfOpen()
            }
        }

        binding.rowParity.root.setOnClickListener {
            val names = (0..4).map { SerialConfig.parityName(it) }
            Dialogs.singleChoice(requireContext(), getString(R.string.serial_parity), names, config.parity) { which ->
                config = config.copy(parity = which)
                SerialConfig.save(config)
                refreshSerialSection()
                restartIfOpen()
            }
        }

        binding.rowFlow.root.setOnClickListener {
            val names = (0..2).map { SerialConfig.flowName(it) }
            Dialogs.singleChoice(requireContext(), getString(R.string.serial_flow_control), names, config.flowControl) { which ->
                config = config.copy(flowControl = which)
                SerialConfig.save(config)
                refreshSerialSection()
                restartIfOpen()
            }
        }

        binding.rowFrameGap.root.setOnClickListener {
            Dialogs.input(
                requireContext(), getString(R.string.serial_frame_gap), getString(R.string.serial_frame_gap_hint),
                config.frameGapMs.toString(), getString(R.string.serial_frame_gap_sub), numeric = true
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
            deviceCount = paths.size
            refreshSerialSection()
            if (paths.isEmpty()) {
                Dialogs.info(requireContext(), getString(R.string.serial_device_list), getString(R.string.serial_scan_empty))
            } else {
                Dialogs.info(
                    requireContext(), getString(R.string.serial_list_count, paths.size),
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
                    snack(getString(R.string.serial_select_first))
                    return@setOnClickListener
                }
                SerialConfig.save(config)
                SerialManager.open(config)
            }
        }
    }

    private fun refreshSerialSection() {
        setValue(
            binding.rowDevice, getString(R.string.settings_serial_device),
            config.device.ifBlank { getString(R.string.common_none_selected) },
            getString(R.string.settings_serial_device_sub)
        )
        setValue(binding.rowBaud, getString(R.string.settings_baud_rate), config.baudRate.toString(), getString(R.string.settings_baud_sub))
        setValue(binding.rowDataBits, getString(R.string.serial_data_bits), getString(R.string.settings_bits_format, config.dataBits), getString(R.string.settings_data_bits_sub))
        setValue(binding.rowStopBits, getString(R.string.serial_stop_bits), SerialConfig.STOP_BITS_NAMES.getOrElse(SerialConfig.STOP_BITS.indexOf(config.stopBits)) { "1" })
        setValue(binding.rowParity, getString(R.string.serial_parity), SerialConfig.parityName(config.parity))
        setValue(binding.rowFlow, getString(R.string.serial_flow_control), SerialConfig.flowName(config.flowControl))
        setValue(
            binding.rowFrameGap, getString(R.string.serial_frame_gap),
            if (config.frameGapMs <= 0) getString(R.string.common_auto) else getString(R.string.common_ms_format, config.frameGapMs)
        )
        if (deviceCount < 0) deviceCount = runCatching { YSerialPort.getDevices()?.size ?: 0 }.getOrDefault(0)
        setValue(binding.rowScan, getString(R.string.serial_device_list), getString(R.string.settings_scan_count, deviceCount), getString(R.string.settings_scan_sub))

        val open = SerialManager.isOpen
        binding.chipSerialToggle.text = getString(if (open) R.string.serial_close else R.string.serial_open)
        binding.chipSerialToggle.setBackgroundResource(if (open) R.drawable.bg_status_open else R.drawable.bg_status_closed)
    }

    private fun restartIfOpen() {
        if (SerialManager.isOpen) {
            SerialManager.open(config)
            snack(getString(R.string.serial_reconnected))
        }
    }

    // ==================================================================
    // 接收
    // ==================================================================
    private fun hexSeparatorNames(): List<String> = listOf(
        getString(R.string.sep_space), getString(R.string.sep_none), getString(R.string.sep_comma), getString(R.string.sep_dash)
    )

    private fun initRxSection() {
        binding.rowRxCharset.root.setOnClickListener {
            val names = Charsets2.SUPPORTED.map { it.first }
            Dialogs.singleChoice(requireContext(), getString(R.string.settings_rx_charset_dialog), names, names.indexOfFirst { Charsets2.byDisplay(it) == Prefs.rxCharset }) { which ->
                Prefs.rxCharset = Charsets2.SUPPORTED[which].second
                refreshRxSection()
            }
        }
        bindSwitch(binding.rowRxTime, getString(R.string.settings_rx_time), { Prefs.rxShowTime }, { Prefs.rxShowTime = it }, sub = getString(R.string.settings_rx_time_sub))
        bindSwitch(binding.rowRxWrap, getString(R.string.settings_rx_wrap), { Prefs.rxAutoWrap }, { Prefs.rxAutoWrap = it }, sub = getString(R.string.settings_rx_wrap_sub))
        binding.rowRxSep.root.setOnClickListener {
            Dialogs.singleChoice(requireContext(), getString(R.string.settings_rx_sep), hexSeparatorNames(), Prefs.rxHexSeparator) { which ->
                Prefs.rxHexSeparator = which
                refreshRxSection()
            }
        }
        binding.rowRxMax.root.setOnClickListener {
            Dialogs.input(requireContext(), getString(R.string.settings_rx_max), getString(R.string.settings_rx_max_sub), Prefs.rxMaxChars.toString(), numeric = true) { text ->
                val v = text.toIntOrNull() ?: 20000
                Prefs.rxMaxChars = v.coerceIn(1000, 500000)
                refreshRxSection()
            }
        }
        bindSwitch(binding.rowRxEcho, getString(R.string.settings_rx_echo), { Prefs.rxEchoTx }, { Prefs.rxEchoTx = it })
    }

    private fun refreshRxSection() {
        setValue(
            binding.rowRxCharset, getString(R.string.settings_charset), Charsets2.displayOf(Prefs.rxCharset),
            getString(R.string.settings_charset_sub)
        )
        setSwitchValue(binding.rowRxTime, getString(R.string.settings_rx_time), Prefs.rxShowTime)
        setSwitchValue(binding.rowRxWrap, getString(R.string.settings_rx_wrap), Prefs.rxAutoWrap)
        setValue(binding.rowRxSep, getString(R.string.settings_rx_sep), hexSeparatorNames().getOrElse(Prefs.rxHexSeparator) { getString(R.string.sep_space) })
        setValue(binding.rowRxMax, getString(R.string.settings_rx_max), "${Prefs.rxMaxChars}")
        setSwitchValue(binding.rowRxEcho, getString(R.string.settings_rx_echo), Prefs.rxEchoTx)
    }

    // ==================================================================
    // 发送
    // ==================================================================
    private fun initTxSection() {
        binding.rowTxCharset.root.setOnClickListener {
            val names = Charsets2.SUPPORTED.map { it.first }
            Dialogs.singleChoice(requireContext(), getString(R.string.settings_tx_charset_dialog), names, names.indexOfFirst { Charsets2.byDisplay(it) == Prefs.txCharset }) { which ->
                Prefs.txCharset = Charsets2.SUPPORTED[which].second
                refreshTxSection()
            }
        }
        bindSwitch(binding.rowTxEscape, getString(R.string.settings_tx_escape), { Prefs.txEscape }, { Prefs.txEscape = it }, sub = getString(R.string.settings_tx_escape_sub))
        binding.rowTxNewline.root.setOnClickListener {
            Dialogs.singleChoice(requireContext(), getString(R.string.settings_tx_newline), Newline.names(), Prefs.txAppendNewline) { which ->
                Prefs.txAppendNewline = which
                refreshTxSection()
            }
        }
        binding.rowTxChunk.root.setOnClickListener {
            Dialogs.input(requireContext(), getString(R.string.settings_tx_chunk), getString(R.string.settings_tx_chunk_hint), Prefs.txFileChunk.toString(), numeric = true) { text ->
                Prefs.txFileChunk = (text.toIntOrNull() ?: 512).coerceIn(1, 4096)
                refreshTxSection()
            }
        }
        binding.rowTxDelay.root.setOnClickListener {
            Dialogs.input(requireContext(), getString(R.string.settings_tx_delay), getString(R.string.settings_ms_hint), Prefs.txFileDelay.toString(), numeric = true) { text ->
                Prefs.txFileDelay = (text.toIntOrNull() ?: 5).coerceIn(0, 5000)
                refreshTxSection()
            }
        }
    }

    private fun refreshTxSection() {
        setValue(binding.rowTxCharset, getString(R.string.settings_charset), Charsets2.displayOf(Prefs.txCharset))
        setSwitchValue(binding.rowTxEscape, getString(R.string.settings_tx_escape), Prefs.txEscape)
        setValue(binding.rowTxNewline, getString(R.string.settings_tx_newline), Newline.name(Prefs.txAppendNewline))
        setValue(binding.rowTxChunk, getString(R.string.settings_tx_chunk), getString(R.string.settings_bytes_format, Prefs.txFileChunk))
        setValue(binding.rowTxDelay, getString(R.string.settings_tx_delay), getString(R.string.common_ms_format, Prefs.txFileDelay))
    }

    // ==================================================================
    // 外观
    // ==================================================================
    private fun themeNames(): List<String> = listOf(
        getString(R.string.theme_follow_system), getString(R.string.theme_dark), getString(R.string.theme_light)
    )

    private fun bgNames(full: Boolean): List<String> = listOf(
        getString(R.string.bg_none), getString(R.string.bg_builtin),
        getString(if (full) R.string.bg_custom else R.string.bg_custom_short)
    )

    private fun initAppearanceSection() {
        binding.rowTheme.root.setOnClickListener {
            Dialogs.singleChoice(requireContext(), getString(R.string.settings_theme), themeNames(), Prefs.themeMode) { which ->
                Prefs.themeMode = which
                (requireActivity().application as App).applyTheme(which)
                refreshAppearanceSection()
            }
        }
        binding.rowBg.root.setOnClickListener {
            Dialogs.singleChoice(requireContext(), getString(R.string.settings_bg), bgNames(full = true), Prefs.bgMode) { which ->
                Prefs.bgMode = which
                (activity as? MainActivity)?.applyBackground()
                refreshAppearanceSection()
            }
        }
        binding.rowBgScrim.root.setOnClickListener {
            Dialogs.input(requireContext(), getString(R.string.settings_bg_scrim), getString(R.string.settings_bg_scrim_hint), Prefs.bgScrim.toString(), numeric = true) { text ->
                Prefs.bgScrim = (text.toIntOrNull() ?: 78).coerceIn(0, 100)
                (activity as? MainActivity)?.applyBackground()
                refreshAppearanceSection()
            }
        }
        binding.rowFont.root.setOnClickListener {
            Dialogs.input(requireContext(), getString(R.string.settings_font_size), getString(R.string.settings_font_hint), Prefs.terminalFontSize.toString(), numeric = true) { text ->
                Prefs.terminalFontSize = (text.toIntOrNull() ?: 13).coerceIn(9, 24)
                refreshAppearanceSection()
            }
        }
        bindSwitch(binding.rowKeepScreen, getString(R.string.settings_keep_screen), { Prefs.keepScreenOn }, { Prefs.keepScreenOn = it })
        binding.rowHistoryLimit.root.setOnClickListener {
            Dialogs.input(requireContext(), getString(R.string.settings_history_limit), getString(R.string.settings_history_limit_hint), Prefs.historyLimit.toString(), numeric = true) { text ->
                Prefs.historyLimit = (text.toIntOrNull() ?: 2000).coerceIn(100, 50000)
                refreshAppearanceSection()
            }
        }
    }

    private fun refreshAppearanceSection() {
        setValue(binding.rowTheme, getString(R.string.settings_theme), themeNames().getOrElse(Prefs.themeMode) { getString(R.string.theme_dark) })
        setValue(binding.rowBg, getString(R.string.settings_bg), bgNames(full = false).getOrElse(Prefs.bgMode) { getString(R.string.bg_none) })
        setValue(binding.rowBgScrim, getString(R.string.settings_bg_scrim), getString(R.string.settings_percent_format, Prefs.bgScrim))
        setValue(binding.rowFont, getString(R.string.settings_font_size), getString(R.string.settings_sp_format, Prefs.terminalFontSize))
        setSwitchValue(binding.rowKeepScreen, getString(R.string.settings_keep_screen), Prefs.keepScreenOn)
        setValue(binding.rowHistoryLimit, getString(R.string.settings_history_limit), getString(R.string.settings_history_count, Prefs.historyLimit, Store.historySize))
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
            if (state == SerialManager.State.OPEN) snack(getString(R.string.serial_connected_snack))
        }
    }
}
