package com.yujing.chuankou.ui.tool

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yujing.chuankou.R
import com.yujing.chuankou.core.data.DataType
import com.yujing.chuankou.core.data.SendBridge
import com.yujing.chuankou.core.serial.SerialManager
import com.yujing.chuankou.core.util.Charsets2
import com.yujing.chuankou.core.util.Checksum
import com.yujing.chuankou.core.util.Escape
import com.yujing.chuankou.core.util.Hex
import com.yujing.chuankou.databinding.FragmentToolBinding
import com.yujing.chuankou.databinding.ItemAsciiBinding
import com.yujing.chuankou.ui.MainActivity
import com.yujing.chuankou.ui.base.BaseFragment
import com.yujing.chuankou.ui.widget.Dialogs
import java.util.Locale

/**
 * 工具页：校验算法计算器 + ASCII 码表
 */
class ToolFragment : BaseFragment<FragmentToolBinding>(FragmentToolBinding::inflate) {

    // ---------------- 校验 ----------------
    private var inputType = DataType.HEX
    private var algo: Checksum.Algo = Checksum.findAlgo("crc16_modbus")
    private var highFirst = true
    private var refinOverride: Boolean? = null
    private var refoutOverride: Boolean? = null
    private var invert = false
    private var extraXor = 0L
    private var outWidth = 0
    private var lastResult: Checksum.Result? = null

    // ---------------- ASCII ----------------
    private var asciiRange = 0        // 0: 0-127, 1: 128-255
    private var asciiKeyword = ""
    private var asciiItems: List<Int> = emptyList()
    private lateinit var asciiAdapter: AsciiAdapter

    override fun onInit(view: View, savedInstanceState: Bundle?) {
        initChecksumPanel()
        initAsciiPanel()

        binding.segTool.setOptions(
            listOf(getString(R.string.tool_tab_checksum), getString(R.string.tool_tab_ascii)), 0
        )
        binding.segTool.onSelected = { index ->
            binding.panelChecksum.visibility = if (index == 0) View.VISIBLE else View.GONE
            binding.panelAscii.visibility = if (index == 1) View.VISIBLE else View.GONE
            if (index == 1) refreshAscii()
        }
    }

    // ==================================================================
    // 校验计算器
    // ==================================================================
    private fun initChecksumPanel() {
        binding.splitChecksum.configure(
            landscape = isLandscape,
            landscapeWeights = listOf(46f, 54f),
            portraitWeights = listOf(40f, 60f),
            gapPx = dp(8)
        )
        binding.splitChecksum.setLandscape(isLandscape)

        binding.segInputType.setOptions(listOf("HEX", "ASCII"), 0)
        binding.segInputType.onSelected = { index ->
            inputType = if (index == 0) DataType.HEX else DataType.ASCII
            binding.chipCharset.visibility = if (inputType == DataType.ASCII) View.VISIBLE else View.GONE
            binding.etInput.hint = if (inputType == DataType.HEX) getString(R.string.tool_input_hint_hex)
            else getString(R.string.tool_input_hint_ascii)
            binding.chipEscape.visibility = if (inputType == DataType.ASCII) View.VISIBLE else View.GONE
            updateInputInfo()
            calc()
        }
        binding.chipCharset.visibility = View.GONE
        binding.chipEscape.visibility = View.GONE

        binding.chipCharset.text = Charsets2.displayOf("UTF-8")
        binding.chipCharset.setOnClickListener {
            val names = Charsets2.SUPPORTED.map { it.first }
            Dialogs.singleChoice(requireContext(), getString(R.string.settings_charset), names, 0) { which ->
                binding.chipCharset.text = names[which]
                updateInputInfo()
                calc()
            }
        }
        binding.chipEscape.isSelected = false
        binding.chipEscape.setOnClickListener {
            binding.chipEscape.isSelected = !binding.chipEscape.isSelected
            updateInputInfo()
            calc()
        }

        binding.etInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                updateInputInfo()
                calc()
            }
        })

        binding.chipAlgo.text = algo.name
        binding.chipAlgo.setOnClickListener { pickAlgo() }

        binding.chipHighFirst.isSelected = highFirst
        binding.chipHighFirst.setOnClickListener {
            highFirst = !highFirst
            binding.chipHighFirst.isSelected = highFirst
            calc()
        }

        binding.chipRefin.isSelected = effectiveRefin()
        binding.chipRefin.setOnClickListener {
            refinOverride = !effectiveRefin()
            binding.chipRefin.isSelected = effectiveRefin()
            calc()
        }

        binding.chipRefout.isSelected = effectiveRefout()
        binding.chipRefout.setOnClickListener {
            refoutOverride = !effectiveRefout()
            binding.chipRefout.isSelected = effectiveRefout()
            calc()
        }

        binding.chipInvert.isSelected = invert
        binding.chipInvert.setOnClickListener {
            invert = !invert
            binding.chipInvert.isSelected = invert
            calc()
        }

        binding.chipXor.setOnClickListener {
            Dialogs.input(
                requireContext(), getString(R.string.tool_xor_value), getString(R.string.tool_xor_value_hint),
                "0x" + java.lang.Long.toHexString(extraXor).uppercase(Locale.getDefault())
            ) { text ->
                extraXor = parseNumber(text)
                updateXorChip()
                calc()
            }
        }
        updateXorChip()

        binding.chipWidth.setOnClickListener {
            Dialogs.singleChoice(
                requireContext(), getString(R.string.tool_output_width),
                listOf(
                    getString(R.string.tool_width_opt_follow),
                    getString(R.string.tool_width_opt_8),
                    getString(R.string.tool_width_opt_16),
                    getString(R.string.tool_width_opt_32)
                ),
                widthIndex()
            ) { which ->
                outWidth = when (which) {
                    1 -> 8
                    2 -> 16
                    3 -> 32
                    else -> 0
                }
                updateWidthChip()
                calc()
            }
        }
        updateWidthChip()

        binding.btnCalc.setOnClickListener { calc() }

        binding.btnCopyResult.setOnClickListener {
            val r = lastResult ?: return@setOnClickListener
            copyToClipboard(r.hex, getString(R.string.tool_copied_checksum_format, r.hex))
        }

        binding.btnAppend.setOnClickListener {
            val r = lastResult ?: return@setOnClickListener
            SendBridge.fill(r.hex, DataType.HEX)
            (activity as? MainActivity)?.selectPage("terminal")
        }

        binding.btnSendResult.setOnClickListener {
            val r = lastResult ?: return@setOnClickListener
            if (!SerialManager.isOpen) {
                snack(getString(R.string.tool_serial_not_open))
                return@setOnClickListener
            }
            val data = inputBytes() ?: ByteArray(0)
            val payload = data + r.bytes
            SerialManager.send(payload, DataType.HEX, getString(R.string.tool_note_checksum_format, r.algo.name))
            snack(getString(R.string.tool_sent_with_checksum, payload.size))
        }

        // 默认示例数据，便于直接体验
        binding.etInput.setText("01 03 00 00 00 0A")
        applyAlgoUi()
        calc()
    }

    private fun effectiveRefin(): Boolean = refinOverride ?: algo.crc?.refin ?: false
    private fun effectiveRefout(): Boolean = refoutOverride ?: algo.crc?.refout ?: false

    private fun widthIndex(): Int = when (outWidth) {
        8 -> 1
        16 -> 2
        32 -> 3
        else -> 0
    }

    private fun updateXorChip() {
        binding.chipXor.text = getString(
            R.string.tool_xor_chip_format,
            java.lang.Long.toHexString(extraXor).uppercase(Locale.getDefault())
        )
        binding.chipXor.isSelected = extraXor != 0L
    }

    private fun updateWidthChip() {
        binding.chipWidth.text = if (outWidth == 0) getString(R.string.tool_width_chip_follow)
        else getString(R.string.tool_width_chip_format, outWidth)
        binding.chipWidth.isSelected = outWidth != 0
    }

    private fun parseNumber(text: String): Long {
        val t = text.trim().replace("_", "")
        return try {
            if (t.startsWith("0x", true)) {
                java.lang.Long.parseLong(t.substring(2), 16)
            } else if (t.isEmpty()) {
                0L
            } else {
                t.toLong()
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun pickAlgo() {
        val groups = Checksum.ALGOS.map { it.group }.distinct()
        Dialogs.singleChoice(requireContext(), getString(R.string.tool_algo_group), groups, groups.indexOf(algo.group)) { gi ->
            val list = Checksum.ALGOS.filter { it.group == groups[gi] }
            Dialogs.singleChoice(requireContext(), groups[gi], list.map { it.name }, list.indexOfFirst { it.id == algo.id }) { ai ->
                algo = list[ai]
                refinOverride = null
                refoutOverride = null
                outWidth = 0
                binding.chipAlgo.text = algo.name
                binding.chipRefin.isSelected = effectiveRefin()
                binding.chipRefout.isSelected = effectiveRefout()
                updateWidthChip()
                applyAlgoUi()
                calc()
            }
        }
    }

    private fun applyAlgoUi() {
        binding.chipRefin.visibility = if (algo.kind == Checksum.Kind.CRC) View.VISIBLE else View.GONE
        binding.chipRefout.visibility = if (algo.kind == Checksum.Kind.CRC) View.VISIBLE else View.GONE
        binding.tvDesc.text = algoDescription(algo)
        binding.chipAlgo.text = algo.name
    }

    private fun algoDescription(a: Checksum.Algo): String {
        val p = a.crc
        return if (p != null) {
            getString(
                R.string.tool_algo_desc_crc, p.width,
                java.lang.Long.toHexString(p.poly).uppercase(Locale.getDefault()),
                java.lang.Long.toHexString(p.init).uppercase(Locale.getDefault()),
                java.lang.Long.toHexString(p.xorout).uppercase(Locale.getDefault()),
                p.refin, p.refout
            ) + if (p.check >= 0) getString(
                R.string.tool_algo_desc_check,
                java.lang.Long.toHexString(p.check).uppercase(Locale.getDefault())
            ) else ""
        } else {
            a.desc.ifBlank { a.name }
        }
    }

    private fun currentCharset(): String =
        Charsets2.byDisplay(binding.chipCharset.text.toString())

    private fun inputBytes(): ByteArray? {
        val text = binding.etInput.text?.toString() ?: return null
        if (text.isBlank()) return ByteArray(0)
        return if (inputType == DataType.HEX) {
            Hex.parse(text)
        } else {
            if (binding.chipEscape.isSelected) Escape.parse(text, currentCharset(), true)
            else Charsets2.encode(text, currentCharset())
        }
    }

    private fun updateInputInfo() {
        if (view == null) return
        val bytes = inputBytes()
        binding.tvInputInfo.text = if (bytes == null) getString(R.string.tool_input_invalid)
        else getString(R.string.settings_bytes_format, bytes.size)
        binding.tvInputInfo.setTextColor(
            androidx.core.content.ContextCompat.getColor(
                requireContext(),
                if (bytes == null) R.color.c_error else R.color.c_text_tertiary
            )
        )
    }

    private fun calc() {
        if (view == null) return
        val bytes = inputBytes()
        if (bytes == null) {
            lastResult = null
            binding.tvResultHex.text = getString(R.string.tool_input_format_invalid)
            binding.tvResultDec.text = getString(R.string.tool_hex_rule)
            binding.tvResultBin.text = ""
            return
        }
        val options = Checksum.Options(
            highByteFirst = highFirst,
            refin = refinOverride,
            refout = refoutOverride,
            extraXor = extraXor,
            invert = invert,
            outWidth = outWidth
        )
        val r = Checksum.calculate(bytes, algo, options)
        lastResult = r
        binding.tvResultHex.text = r.hex
        binding.tvResultDec.text = "DEC: ${r.dec}    HEX: 0x${java.lang.Long.toHexString(r.value).uppercase(Locale.getDefault())}"
        binding.tvResultBin.text = "BIN: ${r.bin}"
    }

    // ==================================================================
    // ASCII 码表
    // ==================================================================
    private fun initAsciiPanel() {
        asciiAdapter = AsciiAdapter()
        binding.rvAscii.layoutManager = GridLayoutManager(requireContext(), if (isLandscape) 10 else 5)
        binding.rvAscii.adapter = asciiAdapter

        binding.segAsciiRange.setOptions(listOf("0-127", "128-255"), 0)
        binding.segAsciiRange.onSelected = { index ->
            asciiRange = index
            refreshAscii()
        }

        binding.etAsciiSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                asciiKeyword = s?.toString()?.trim() ?: ""
                refreshAscii()
            }
        })

        refreshAscii()
    }

    private fun refreshAscii() {
        if (view == null) return
        val base = if (asciiRange == 0) (0..127).toList() else (128..255).toList()
        asciiItems = if (asciiKeyword.isEmpty()) base else base.filter { code ->
            val c = charOf(code)
            val name = nameOf(code)
            c.toString().contains(asciiKeyword, true) ||
                    code.toString() == asciiKeyword ||
                    Hex.byteToHex(code).equals(asciiKeyword, true) ||
                    ("0x" + Hex.byteToHex(code)).equals(asciiKeyword, true) ||
                    name.contains(asciiKeyword, true)
        }
        asciiAdapter.notifyDataSetChanged()
        binding.tvAsciiHint.text = if (asciiKeyword.isEmpty())
            getString(R.string.tool_ascii_hint_count_format, asciiItems.size)
        else
            getString(R.string.tool_ascii_hint_search_format, asciiKeyword, asciiItems.size)
    }

    /** 大号显示：控制字符直接显示助记符，避免出现缺字方框 */
    private fun charOf(code: Int): String = when {
        code in 0..31 -> CONTROL_MNEMONIC[code]
        code == 32 -> "SP"
        code in 33..126 -> code.toChar().toString()
        code == 127 -> "DEL"
        else -> {
            val c = runCatching {
                String(byteArrayOf(code.toByte()), Charsets2.charset("windows-1252"))
            }.getOrDefault("")
            if (c.isNotEmpty() && c[0].code in 0xA0..0xFF) c else "—"
        }
    }

    /** 名称行 */
    private fun nameOf(code: Int): String = when (code) {
        in 0..31 -> getString(CONTROL_DESC_RES[code])
        32 -> getString(R.string.tool_ascii_space)
        in 33..47 -> SYMBOLS_33[code - 33]
        in 48..57 -> getString(R.string.tool_ascii_digit_format, (code - 48).toString())
        in 58..64 -> SYMBOLS_58[code - 58]
        in 65..90 -> getString(R.string.tool_ascii_upper_format, code.toChar().toString())
        in 91..96 -> SYMBOLS_91[code - 91]
        in 97..122 -> getString(R.string.tool_ascii_lower_format, code.toChar().toString())
        in 123..126 -> SYMBOLS_123[code - 123]
        127 -> getString(R.string.tool_ascii_del)
        in 128..191 -> getString(EXT_DESC_RES[code - 128])
        215 -> getString(R.string.tool_ext_multiply)
        247 -> getString(R.string.tool_ext_divide)
        else -> code.toChar().toString()
    }

    private fun showAsciiMenu(code: Int) {
        val hex = Hex.byteToHex(code)
        val c = if (code in 33..126) code.toChar().toString() else ""
        val options = mutableListOf(
            getString(R.string.tool_menu_copy_hex_format, hex),
            getString(R.string.tool_menu_send_byte),
            getString(R.string.tool_menu_fill_hex)
        )
        if (c.isNotEmpty()) options.add(0, getString(R.string.tool_menu_copy_char_format, c))
        Dialogs.singleChoice(requireContext(), "0x$hex · DEC $code", options) { which ->
            val realWhich = if (c.isNotEmpty()) which else which + 1
            when (realWhich) {
                0 -> copyToClipboard(c, getString(R.string.tool_copied_char))
                1 -> copyToClipboard(hex, getString(R.string.tool_copied_hex))
                2 -> {
                    if (!SerialManager.isOpen) snack(getString(R.string.tool_serial_not_open))
                    else {
                        SerialManager.send(byteArrayOf(code.toByte()), DataType.HEX, getString(R.string.tool_tab_ascii))
                        snack(getString(R.string.tool_sent_hex_format, hex))
                    }
                }
                3 -> {
                    SendBridge.fill(hex, DataType.HEX)
                    (activity as? MainActivity)?.selectPage("terminal")
                }
            }
        }
    }

    private inner class AsciiAdapter : RecyclerView.Adapter<AsciiAdapter.VH>() {
        inner class VH(val b: ItemAsciiBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemAsciiBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = asciiItems.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val code = asciiItems[position]
            val b = holder.b
            b.tvChar.text = charOf(code)
            b.tvDec.text = code.toString()
            b.tvHex.text = "0x" + Hex.byteToHex(code)
            b.tvName.text = nameOf(code)
            b.root.setOnClickListener { showAsciiMenu(code) }
        }
    }

    companion object {
        /** 0 ~ 31 控制字符助记符 */
        val CONTROL_MNEMONIC = listOf(
            "NUL", "SOH", "STX", "ETX", "EOT", "ENQ", "ACK", "BEL",
            "BS", "HT", "LF", "VT", "FF", "CR", "SO", "SI",
            "DLE", "DC1", "DC2", "DC3", "DC4", "NAK", "SYN", "ETB",
            "CAN", "EM", "SUB", "ESC", "FS", "GS", "RS", "US"
        )

        /** 0 ~ 31 控制字符说明（资源 id，下标 = code） */
        private val CONTROL_DESC_RES = intArrayOf(
            R.string.tool_ctrl_nul, R.string.tool_ctrl_soh, R.string.tool_ctrl_stx, R.string.tool_ctrl_etx,
            R.string.tool_ctrl_eot, R.string.tool_ctrl_enq, R.string.tool_ctrl_ack, R.string.tool_ctrl_bel,
            R.string.tool_ctrl_bs, R.string.tool_ctrl_ht, R.string.tool_ctrl_lf, R.string.tool_ctrl_vt,
            R.string.tool_ctrl_ff, R.string.tool_ctrl_cr, R.string.tool_ctrl_so, R.string.tool_ctrl_si,
            R.string.tool_ctrl_dle, R.string.tool_ctrl_dc1, R.string.tool_ctrl_dc2, R.string.tool_ctrl_dc3,
            R.string.tool_ctrl_dc4, R.string.tool_ctrl_nak, R.string.tool_ctrl_syn, R.string.tool_ctrl_etb,
            R.string.tool_ctrl_can, R.string.tool_ctrl_em, R.string.tool_ctrl_sub, R.string.tool_ctrl_esc,
            R.string.tool_ctrl_fs, R.string.tool_ctrl_gs, R.string.tool_ctrl_rs, R.string.tool_ctrl_us
        )

        /** 128 ~ 191 扩展字符说明（资源 id，下标 = code - 128） */
        private val EXT_DESC_RES = intArrayOf(
            R.string.tool_ext_euro, R.string.tool_ext_undefined, R.string.tool_ext_quote_low_single, R.string.tool_ext_florin,
            R.string.tool_ext_quote_low_double, R.string.tool_ext_ellipsis, R.string.tool_ext_dagger, R.string.tool_ext_dagger_double,
            R.string.tool_ext_circumflex, R.string.tool_ext_permille, R.string.tool_ext_s_caron, R.string.tool_ext_langle_single,
            R.string.tool_ext_oe_ligature, R.string.tool_ext_undefined, R.string.tool_ext_z_caron, R.string.tool_ext_undefined,
            R.string.tool_ext_undefined, R.string.tool_ext_quote_left_single, R.string.tool_ext_quote_right_single, R.string.tool_ext_quote_left_double,
            R.string.tool_ext_quote_right_double, R.string.tool_ext_bullet, R.string.tool_ext_dash_en, R.string.tool_ext_dash_em,
            R.string.tool_ext_tilde, R.string.tool_ext_trademark, R.string.tool_ext_s_caron_lower, R.string.tool_ext_rangle_single,
            R.string.tool_ext_oe_ligature_lower, R.string.tool_ext_undefined, R.string.tool_ext_z_caron_lower, R.string.tool_ext_y_diaeresis,
            R.string.tool_ext_nbsp, R.string.tool_ext_exclaim_inverted, R.string.tool_ext_cent, R.string.tool_ext_pound,
            R.string.tool_ext_currency, R.string.tool_ext_yen, R.string.tool_ext_broken_bar, R.string.tool_ext_section,
            R.string.tool_ext_diaeresis, R.string.tool_ext_copyright, R.string.tool_ext_ordinal_f, R.string.tool_ext_langle_double,
            R.string.tool_ext_not, R.string.tool_ext_shy, R.string.tool_ext_registered, R.string.tool_ext_macron,
            R.string.tool_ext_degree, R.string.tool_ext_plus_minus, R.string.tool_ext_superscript_two, R.string.tool_ext_superscript_three,
            R.string.tool_ext_acute, R.string.tool_ext_micro, R.string.tool_ext_paragraph, R.string.tool_ext_middle_dot,
            R.string.tool_ext_cedilla, R.string.tool_ext_superscript_one, R.string.tool_ext_ordinal_m, R.string.tool_ext_rangle_double,
            R.string.tool_ext_quarter, R.string.tool_ext_half, R.string.tool_ext_three_quarters, R.string.tool_ext_question_inverted
        )

        /** 33 ~ 47 符号 */
        private val SYMBOLS_33 = listOf("!", "\"", "#", "$", "%", "&", "'", "(", ")", "*", "+", ",", "-", ".", "/")

        /** 58 ~ 64 符号 */
        private val SYMBOLS_58 = listOf(":", ";", "<", "=", ">", "?", "@")

        /** 91 ~ 96 符号 */
        private val SYMBOLS_91 = listOf("[", "\\", "]", "^", "_", "`")

        /** 123 ~ 126 符号 */
        private val SYMBOLS_123 = listOf("{", "|", "}", "~")
    }
}
