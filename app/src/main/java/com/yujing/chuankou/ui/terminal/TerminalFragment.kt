package com.yujing.chuankou.ui.terminal

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.yujing.chuankou.R
import com.yujing.chuankou.core.data.DataType
import com.yujing.chuankou.core.data.Newline
import com.yujing.chuankou.core.data.Prefs
import com.yujing.chuankou.core.data.Store
import com.yujing.chuankou.core.serial.SerialManager
import com.yujing.chuankou.core.util.AppFiles
import com.yujing.chuankou.core.util.Charsets2
import com.yujing.chuankou.core.util.Escape
import com.yujing.chuankou.core.util.Hex
import com.yujing.chuankou.databinding.FragmentTerminalBinding
import com.yujing.chuankou.ui.base.BaseFragment
import com.yujing.chuankou.ui.widget.Dialogs
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.Executors

/**
 * 终端页：串口收发主界面
 */
class TerminalFragment : BaseFragment<FragmentTerminalBinding>(FragmentTerminalBinding::inflate),
    SerialManager.Listener {

    private val ui = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "terminal-io").apply { isDaemon = true } }
    // 时间格式是纯数字，必须锁 Locale.ROOT：否则阿拉伯语等 locale 下会输出非 ASCII 数字
    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)

    private var rxMode = 0      // 0 ASCII / 1 HEX
    private var txMode = 0      // 0 ASCII / 1 HEX / 2 FILE
    private var rxPaused = false
    private var loopEnabled = false

    private var fileBytes: ByteArray? = null
    private var fileName: String = ""

    private var statsScheduled = false
    private val statsRunnable = Runnable { refreshStats() }

    // ---------------- 文件选择 ----------------
    private lateinit var pickFileLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>

    // ==================================================================
    override fun onCreateReady(savedInstanceState: Bundle?) {
        pickFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) loadFile(uri)
        }
    }

    override fun onInit(view: View, savedInstanceState: Bundle?) {
        rxMode = Prefs.rxMode.coerceIn(0, 1)
        txMode = Prefs.txMode.coerceIn(0, 2)

        binding.split.configure(
            landscape = isLandscape,
            landscapeWeights = listOf(56f, 44f),
            portraitWeights = listOf(46f, 54f),
            gapPx = dp(8)
        )

        initRxPanel()
        initTxPanel()
        initEditor()

        SerialManager.addListener(this)
        refreshStats()
        updateSerialHint()
        applyRxModeUi()
        applyTxModeUi()
        restoreSnapshot()
    }

    /** 恢复上一次的接收内容（主题切换 / Activity 重建后） */
    private fun restoreSnapshot() {
        if (TerminalSnapshot.isEmpty) return
        val text = TerminalSnapshot.load()
        if (text.isNotEmpty()) binding.terminalRx.appendText(text)
    }

    /** 处理其它页面（历史记录 / ASCII 码表）回填或一键发送的请求 */
    private fun handlePendingSend() {
        val payload = com.yujing.chuankou.core.data.SendBridge.consume() ?: return
        val typeIndex = if (payload.type == DataType.ASCII) 0 else 1
        binding.segTxMode.select(typeIndex, true)
        binding.etSend.setText(payload.text)
        binding.etSend.setSelection(payload.text.length)
        persistEditor()
        if (payload.autoSend) {
            binding.etSend.post { onSendClicked() }
        } else {
            snack(getString(R.string.terminal_filled))
        }
    }

    override fun onRelease() {
        runCatching { TerminalSnapshot.save(binding.terminalRx.contentText()) }
        SerialManager.removeListener(this)
        ui.removeCallbacksAndMessages(null)
    }

    // ==================================================================
    // 接收面板
    // ==================================================================
    private fun initRxPanel() {
        binding.terminalRx.maxChars = Prefs.rxMaxChars
        binding.terminalRx.setFontSizeSp(Prefs.terminalFontSize)
        binding.terminalRx.onFlush = { updateScrollHint() }
        binding.terminalRx.onScrollAwayChanged = { updateScrollHint() }
        binding.chipBackToBottom.setOnClickListener {
            binding.terminalRx.jumpToBottom()
            updateScrollHint()
        }
        updateScrollHint()

        binding.segRxMode.setOptions(listOf("ASCII", "HEX"), rxMode)
        binding.segRxMode.onSelected = { index ->
            rxMode = index
            Prefs.rxMode = index
            applyRxModeUi()
        }

        binding.btnRxClear.setOnClickListener {
            binding.terminalRx.clearAll()
            TerminalSnapshot.clear()
            updateScrollHint()
            snack(getString(R.string.terminal_rx_cleared))
        }

        binding.btnRxPause.setOnClickListener {
            rxPaused = !rxPaused
            binding.terminalRx.paused = false
            binding.btnRxPause.setIconResource(if (rxPaused) R.drawable.ic_play else R.drawable.ic_pause)
            binding.btnRxPause.iconTint = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), if (rxPaused) R.color.c_warn else R.color.c_text_secondary)
            )
            snack(if (rxPaused) getString(R.string.terminal_display_paused) else getString(R.string.terminal_display_resumed))
        }

        binding.btnRxSave.setOnClickListener {
            val text = binding.terminalRx.contentText()
            if (text.isEmpty()) {
                snack(getString(R.string.terminal_nothing_to_save))
                return@setOnClickListener
            }
            io.execute {
                val file = AppFiles.saveText(Store.appContext(), "serial_rx", text)
                ui.post {
                    if (file == null) {
                        snack(getString(R.string.terminal_save_failed))
                    } else {
                        snack(getString(R.string.terminal_saved, file.absolutePath), getString(R.string.terminal_share)) {
                            AppFiles.share(requireContext(), file)
                        }
                    }
                }
            }
        }

        // 字符集
        updateCharsetChip()
        binding.chipCharset.setOnClickListener {
            val names = Charsets2.SUPPORTED.map { it.first }
            val current = names.indexOfFirst { Charsets2.byDisplay(it) == Prefs.rxCharset }
            Dialogs.singleChoice(requireContext(), getString(R.string.settings_rx_charset_dialog), names, current) { which ->
                Prefs.rxCharset = Charsets2.SUPPORTED[which].second
                updateCharsetChip()
                snack(getString(R.string.terminal_rx_charset_set, Charsets2.SUPPORTED[which].first))
            }
        }

        binding.chipRxTime.isSelected = Prefs.rxShowTime
        binding.chipRxTime.setOnClickListener {
            Prefs.rxShowTime = !Prefs.rxShowTime
            binding.chipRxTime.isSelected = Prefs.rxShowTime
            updateRxWrapHint()
        }

        binding.chipRxWrap.isSelected = Prefs.rxAutoWrap
        binding.chipRxWrap.setOnClickListener {
            Prefs.rxAutoWrap = !Prefs.rxAutoWrap
            binding.chipRxWrap.isSelected = Prefs.rxAutoWrap
            updateRxWrapHint()
        }

        binding.chipRxEcho.isSelected = Prefs.rxEchoTx
        binding.chipRxEcho.setOnClickListener {
            Prefs.rxEchoTx = !Prefs.rxEchoTx
            binding.chipRxEcho.isSelected = Prefs.rxEchoTx
        }

        binding.chipHexSep.setOnClickListener {
            val names = hexSepNames()
            Dialogs.singleChoice(requireContext(), getString(R.string.settings_rx_sep), names, Prefs.rxHexSeparator) { which ->
                Prefs.rxHexSeparator = which
                updateHexSepChip()
            }
        }
        updateHexSepChip()
        updateRxWrapHint()
    }

    private fun applyRxModeUi() {
        binding.chipHexSep.visibility = if (rxMode == 1) View.VISIBLE else View.GONE
        binding.terminalRx.autoScroll = true
    }

    private fun updateCharsetChip() {
        binding.chipCharset.text = Charsets2.displayOf(Prefs.rxCharset)
    }

    private fun hexSepNames() = listOf(
        getString(R.string.sep_space), getString(R.string.sep_none),
        getString(R.string.sep_comma), getString(R.string.sep_dash)
    )

    private fun updateHexSepChip() {
        binding.chipHexSep.text = hexSepNames()
            .getOrElse(Prefs.rxHexSeparator) { getString(R.string.sep_space) }
    }

    private fun updateRxWrapHint() {
        // 关闭自动换行时，时间戳会破坏「无限拼接」的连续性，这里做联动提示
        val wrap = Prefs.rxAutoWrap
        binding.chipRxTime.alpha = if (wrap) 1f else 0.45f
        binding.chipHexSep.alpha = 1f
        updateHexSepChip()
    }

    private fun hexSeparator(): String = when (Prefs.rxHexSeparator) {
        1 -> ""
        2 -> ","
        3 -> "-"
        else -> " "
    }

    // ==================================================================
    // 发送面板
    // ==================================================================
    private fun initTxPanel() {
        binding.segTxMode.setOptions(listOf("ASCII", "HEX", getString(R.string.terminal_mode_file)), txMode)
        binding.segTxMode.onSelected = { index ->
            txMode = index
            Prefs.txMode = index
            applyTxModeUi()
        }

        binding.btnTxClear.setOnClickListener {
            binding.etSend.setText("")
            if (txMode == 2) {
                fileBytes = null
                fileName = ""
                updateFileInfo()
            }
        }

        binding.chipTxCharset.setOnClickListener {
            val names = Charsets2.SUPPORTED.map { it.first }
            val current = names.indexOfFirst { Charsets2.byDisplay(it) == Prefs.txCharset }
            Dialogs.singleChoice(requireContext(), getString(R.string.settings_tx_charset_dialog), names, current) { which ->
                Prefs.txCharset = Charsets2.SUPPORTED[which].second
                updateTxCharsetChip()
            }
        }
        updateTxCharsetChip()

        binding.chipTxEscape.isSelected = Prefs.txEscape
        binding.chipTxEscape.setOnClickListener {
            Prefs.txEscape = !Prefs.txEscape
            binding.chipTxEscape.isSelected = Prefs.txEscape
            snack(if (Prefs.txEscape) getString(R.string.terminal_escape_on) else getString(R.string.terminal_escape_off))
        }

        binding.chipTxNewline.setOnClickListener {
            Dialogs.singleChoice(requireContext(), getString(R.string.terminal_newline), Newline.names(), Prefs.txAppendNewline) { which ->
                Prefs.txAppendNewline = which
                updateNewlineChip()
            }
        }
        updateNewlineChip()

        binding.chipHistory.setOnClickListener { showSendHistory() }

        binding.chipTxLoop.isSelected = false
        binding.chipTxLoop.setOnClickListener {
            loopEnabled = !loopEnabled
            binding.chipTxLoop.isSelected = loopEnabled
            binding.llLoopConfig.visibility = if (loopEnabled) View.VISIBLE else View.GONE
            if (!loopEnabled) SerialManager.stopLoop()
            updateSendButton()
        }

        binding.btnPickFile.setOnClickListener {
            pickFileLauncher.launch(arrayOf("*/*"))
        }

        binding.etChunk.setText(Prefs.txFileChunk.toString())
        binding.etFileDelay.setText(Prefs.txFileDelay.toString())
        binding.etLoopInterval.setText(Prefs.txLoopInterval.toString())
        binding.etLoopCount.setText(Prefs.txLoopCount.toString())

        binding.btnSend.setOnClickListener { onSendClicked() }
        updateFileInfo()
    }

    private fun applyTxModeUi() {
        val isFile = txMode == 2
        binding.llTxEditor.visibility = if (isFile) View.GONE else View.VISIBLE
        binding.llTxFile.visibility = if (isFile) View.VISIBLE else View.GONE
        binding.chipTxCharset.visibility = if (txMode == 1) View.GONE else View.VISIBLE
        binding.chipTxEscape.visibility = View.VISIBLE
        binding.chipTxNewline.visibility = if (txMode == 2) View.GONE else View.VISIBLE
        binding.etSend.isEnabled = true
        binding.chipTxLoop.visibility = if (isFile) View.GONE else View.VISIBLE
        if (isFile && loopEnabled) {
            loopEnabled = false
            binding.chipTxLoop.isSelected = false
            binding.llLoopConfig.visibility = View.GONE
            SerialManager.stopLoop()
        }
        updateSendButton()
    }

    private fun updateTxCharsetChip() {
        binding.chipTxCharset.text = Charsets2.displayOf(Prefs.txCharset)
    }

    private fun updateNewlineChip() {
        binding.chipTxNewline.text = getString(R.string.terminal_newline_format, Newline.name(Prefs.txAppendNewline))
    }

    private fun updateSendButton() {
        when {
            SerialManager.isLooping -> {
                binding.btnSend.text = getString(R.string.terminal_btn_stop)
                binding.btnSend.setIconResource(R.drawable.ic_stop)
            }
            loopEnabled -> {
                binding.btnSend.text = getString(R.string.terminal_btn_loop)
                binding.btnSend.setIconResource(R.drawable.ic_repeat)
            }
            else -> {
                binding.btnSend.text = getString(R.string.terminal_send)
                binding.btnSend.setIconResource(R.drawable.ic_play)
            }
        }
    }

    private fun initEditor() {
        binding.etSend.setText(if (txMode == 1) Prefs.lastSendHex else Prefs.lastSendText)
        binding.etSend.setSelection(binding.etSend.text?.length ?: 0)
        binding.etSend.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) persistEditor()
        }
    }

    private fun persistEditor() {
        val text = binding.etSend.text?.toString() ?: ""
        if (txMode == 1) Prefs.lastSendHex = text else if (txMode == 0) Prefs.lastSendText = text
    }

    private fun currentInput(): String = binding.etSend.text?.toString() ?: ""

    // ==================================================================
    // 发送
    // ==================================================================
    private fun onSendClicked() {
        if (SerialManager.isLooping) {
            SerialManager.stopLoop()
            return
        }
        if (!SerialManager.isOpen) {
            snack(getString(R.string.terminal_serial_not_open))
            return
        }
        val bytes = buildPayload()
        if (bytes == null || bytes.isEmpty()) {
            snack(if (txMode == 2) getString(R.string.terminal_pick_file_first) else getString(R.string.terminal_input_first))
            return
        }
        if (loopEnabled) {
            val interval = binding.etLoopInterval.text.toString().toIntOrNull() ?: 1000
            val count = binding.etLoopCount.text.toString().toIntOrNull() ?: 0
            Prefs.txLoopInterval = interval
            Prefs.txLoopCount = count
            SerialManager.startLoop(bytes, interval, count, dataTypeOf(txMode))
            snack(
                if (count > 0) getString(R.string.terminal_loop_started_count, interval, count)
                else getString(R.string.terminal_loop_started_infinite, interval)
            )
        } else {
            SerialManager.send(bytes, dataTypeOf(txMode), getString(R.string.terminal_source_manual))
        }
        if (txMode != 2) {
            persistEditor()
            Store.pushSendHistory(currentInput())
        }
    }

    private fun dataTypeOf(mode: Int) = when (mode) {
        0 -> DataType.ASCII
        1 -> DataType.HEX
        else -> DataType.FILE
    }

    /** 构建待发送数据 */
    private fun buildPayload(): ByteArray? {
        if (txMode == 2) return fileBytes
        val text = currentInput()
        if (text.isEmpty()) return ByteArray(0)
        val charset = Charsets2.byDisplay(Prefs.txCharset)
        val nl = Newline.bytes(Prefs.txAppendNewline, charset)
        return if (txMode == 1) {
            val body = Hex.parse(text) ?: return null
            if (nl.isEmpty()) body else body + nl
        } else {
            val body = if (Prefs.txEscape) Escape.parse(text, charset, true)
            else Charsets2.encode(text, charset)
            if (nl.isEmpty()) body else body + nl
        }
    }

    private fun showSendHistory() {
        val list = Store.sendHistory()
        if (list.isEmpty()) {
            snack(getString(R.string.terminal_no_history))
            return
        }
        Dialogs.singleChoice(requireContext(), getString(R.string.terminal_recent_sends), list) { which ->
            val text = list[which]
            binding.etSend.setText(text)
            binding.etSend.setSelection(text.length)
            persistEditor()
        }
    }

    // ==================================================================
    // 文件
    // ==================================================================
    private fun loadFile(uri: Uri) {
        io.execute {
            val result = runCatching {
                val ctx = Store.appContext()
                val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                val name = queryName(uri)
                bytes to name
            }.getOrNull()
            ui.post {
                val data = result?.first
                if (data == null) {
                    snack(getString(R.string.terminal_read_file_failed))
                    return@post
                }
                fileBytes = data
                fileName = result.second ?: getString(R.string.terminal_unknown_file)
                Prefs.lastFilePath = uri.toString()
                updateFileInfo()
                snack(getString(R.string.terminal_loaded, fileName, data.size))
            }
        }
    }

    private fun queryName(uri: Uri): String? = runCatching {
        val ctx = Store.appContext()
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }.getOrNull()

    private fun updateFileInfo() {
        val bytes = fileBytes
        binding.tvFileInfo.text = if (bytes == null) {
            getString(R.string.terminal_no_file)
        } else {
            getString(R.string.terminal_file_info, fileName, bytes.size, formatSize(bytes.size.toLong()))
        }
    }

    private fun formatSize(size: Long): String = when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> String.format(Locale.ROOT, "%.1f KB", size / 1024f)
        else -> String.format(Locale.ROOT, "%.2f MB", size / 1024f / 1024f)
    }

    // ==================================================================
    // 显示
    // ==================================================================
    private fun color(res: Int) = ContextCompat.getColor(requireContext(), res)

    private fun appendReceive(bytes: ByteArray, ascii: String, time: Long) {
        if (rxPaused || view == null) return
        val wrap = Prefs.rxAutoWrap
        val sb = SpannableStringBuilder()
        if (Prefs.rxShowTime && wrap) {
            val prefix = "[${timeFmt.format(Date(time))}] RX→ "
            sb.append(prefix)
            sb.setSpan(ForegroundColorSpan(color(R.color.c_terminal_meta)), 0, prefix.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        var body = if (rxMode == 1) Hex.toHexString(bytes, hexSeparator()) else sanitizeAscii(ascii)
        // 自动换行时若数据本身以换行结尾，去掉一个，避免出现多余空行
        if (wrap) {
            body = when {
                body.endsWith("\r\n") -> body.dropLast(2)
                body.endsWith("\n") || body.endsWith("\r") -> body.dropLast(1)
                else -> body
            }
        }
        if (body.isNotEmpty()) {
            // 关闭自动换行时，包与包之间补一个空格，避免两包首尾相连
            if (!wrap && !binding.terminalRx.endsWithWhitespace()) sb.append(' ')
            val bodyStart = sb.length
            sb.append(body)
            sb.setSpan(ForegroundColorSpan(color(R.color.c_rx)), bodyStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (wrap) sb.append("\n")
        binding.terminalRx.appendText(sb)
    }

    private fun appendSendEcho(bytes: ByteArray, type: Int, time: Long) {
        if (!Prefs.rxEchoTx || view == null) return
        val wrap = Prefs.rxAutoWrap
        val sb = SpannableStringBuilder()
        if (Prefs.rxShowTime && wrap) {
            val prefix = "[${timeFmt.format(Date(time))}] TX→ "
            sb.append(prefix)
            sb.setSpan(ForegroundColorSpan(color(R.color.c_terminal_meta)), 0, prefix.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val body = when (type) {
            DataType.ASCII -> Charsets2.decode(bytes, Charsets2.byDisplay(Prefs.txCharset))
            DataType.FILE -> getString(R.string.terminal_file_echo, bytes.size)
            else -> Hex.toHexString(bytes, hexSeparator())
        }
        if (body.isNotEmpty() && !wrap && !binding.terminalRx.endsWithWhitespace()) sb.append(' ')
        val bodyStart = sb.length
        sb.append(body)
        sb.setSpan(ForegroundColorSpan(color(R.color.c_tx)), bodyStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (wrap) sb.append("\n")
        binding.terminalRx.appendText(sb)
    }

    /**
     * ASCII 显示时把不可见控制字符转义为 \xNN，保留 \r \n \t，
     * 避免二进制数据在接收区显示为"空行"。
     */
    private fun sanitizeAscii(text: String): String {
        if (text.isEmpty()) return text
        var need = false
        for (c in text) {
            if ((c.code < 0x20 && c != '\r' && c != '\n' && c != '\t') || c.code == 0x7F) {
                need = true; break
            }
        }
        if (!need) return text
        val sb = StringBuilder(text.length + 16)
        for (c in text) {
            when {
                c == '\r' || c == '\n' || c == '\t' -> sb.append(c)
                c.code < 0x20 || c.code == 0x7F -> sb.append("\\x").append(Hex.byteToHex(c.code))
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /** 用户上滑离开底部时提示"回到底部" */
    private fun updateScrollHint() {
        if (view == null) return
        val away = binding.terminalRx.isScrolledAway
        binding.chipBackToBottom.visibility = if (away) View.VISIBLE else View.GONE
    }

    private fun refreshStats() {
        if (view == null) return
        binding.tvRxStats.text = getString(R.string.terminal_rx_stats, formatSize(SerialManager.rxBytes), SerialManager.rxPackets)
        binding.tvTxStats.text = getString(R.string.terminal_tx_stats, formatSize(SerialManager.txBytes), SerialManager.txPackets)
        updateSerialHint()
    }

    private fun updateSerialHint() {
        if (view == null) return
        if (!SerialManager.isOpen) {
            binding.etSend.isEnabled = false
            binding.etSend.hint = getString(R.string.terminal_serial_not_open)
        } else {
            binding.etSend.isEnabled = true
            binding.etSend.hint = if (txMode == 1) getString(R.string.terminal_hint_hex)
            else getString(R.string.terminal_hint_input)
        }
    }

    private fun scheduleStats() {
        if (statsScheduled) return
        statsScheduled = true
        ui.postDelayed({ statsScheduled = false; refreshStats() }, 200)
    }

    // ==================================================================
    // SerialManager.Listener
    // ==================================================================
    override fun onState(state: SerialManager.State, message: String?) {
        ui.post {
            if (state == SerialManager.State.CLOSED) {
                SerialManager.stopLoop()
                updateSendButton()
            }
            if (state == SerialManager.State.ERROR && !message.isNullOrBlank()) {
                snack(message)
            }
            refreshStats()
        }
    }

    override fun onReceive(bytes: ByteArray, ascii: String, time: Long) {
        appendReceive(bytes, ascii, time)
        scheduleStats()
    }

    override fun onSend(bytes: ByteArray, type: Int, time: Long) {
        appendSendEcho(bytes, type, time)
        scheduleStats()
    }

    override fun onStats(rxBytes: Long, txBytes: Long, rxPackets: Long, txPackets: Long) = scheduleStats()

    override fun onLoopState(running: Boolean, sent: Int, total: Int) {
        ui.post {
            updateSendButton()
            if (view != null) {
                binding.tvTxStats.text = if (total > 0) getString(R.string.terminal_loop_progress, sent, total) else getString(R.string.terminal_sent_times, sent)
                if (!running && sent > 0) snack(getString(R.string.terminal_loop_finished, sent))
            }
        }
    }

    override fun onFileProgress(sent: Int, total: Int) {
        ui.post {
            if (view == null) return@post
            binding.pbFile.visibility = View.VISIBLE
            binding.tvFileProgress.visibility = View.VISIBLE
            // sent * 100 用 Long 计算：大文件（>21MB）下 Int 会溢出成负数
            binding.pbFile.progress = if (total > 0) (sent.toLong() * 100 / total).toInt() else 0
            binding.tvFileProgress.text = getString(R.string.terminal_file_progress, sent, total)
            if (sent >= total) {
                ui.postDelayed({
                    if (view != null) {
                        binding.pbFile.visibility = View.GONE
                        binding.tvFileProgress.visibility = View.GONE
                    }
                }, 1500)
            }
        }
    }

    /** 页面切换回来时刷新 */
    override fun onPageResume() {
        if (view == null) return
        refreshStats()
        updateSendButton()
        handlePendingSend()
    }
}
