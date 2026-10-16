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

        binding.segTool.setOptions(listOf("校验计算器", "ASCII 码表"), 0)
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
            binding.etInput.hint = if (inputType == DataType.HEX) "HEX 例：01 03 00 00 00 0A"
            else "ASCII 例：123456789"
            binding.chipEscape.visibility = if (inputType == DataType.ASCII) View.VISIBLE else View.GONE
            updateInputInfo()
            calc()
        }
        binding.chipCharset.visibility = View.GONE
        binding.chipEscape.visibility = View.GONE

        binding.chipCharset.text = Charsets2.displayOf("UTF-8")
        binding.chipCharset.setOnClickListener {
            val names = Charsets2.SUPPORTED.map { it.first }
            Dialogs.singleChoice(requireContext(), "字符集", names, 0) { which ->
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
                requireContext(), "结果异或值", "支持 0x 前缀或十进制，例：0xFF",
                "0x" + java.lang.Long.toHexString(extraXor).uppercase(Locale.getDefault())
            ) { text ->
                extraXor = parseNumber(text)
                updateXorChip()
                calc()
            }
        }
        updateXorChip()

        binding.chipWidth.setOnClickListener {
            Dialogs.singleChoice(requireContext(), "输出位宽", listOf("跟随算法", "8 位", "16 位", "32 位"), widthIndex()) { which ->
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
            copyToClipboard(r.hex, "已复制校验值：${r.hex}")
        }

        binding.btnAppend.setOnClickListener {
            val r = lastResult ?: return@setOnClickListener
            SendBridge.fill(r.hex, DataType.HEX)
            (activity as? MainActivity)?.selectPage("terminal")
        }

        binding.btnSendResult.setOnClickListener {
            val r = lastResult ?: return@setOnClickListener
            if (!SerialManager.isOpen) {
                snack("串口未打开")
                return@setOnClickListener
            }
            val data = inputBytes() ?: ByteArray(0)
            val payload = data + r.bytes
            SerialManager.send(payload, DataType.HEX, "数据+${r.algo.name}")
            snack("已发送 ${payload.size} 字节（数据 + 校验）")
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
        binding.chipXor.text = "异或 0x" + java.lang.Long.toHexString(extraXor).uppercase(Locale.getDefault())
        binding.chipXor.isSelected = extraXor != 0L
    }

    private fun updateWidthChip() {
        binding.chipWidth.text = if (outWidth == 0) "位宽 跟随" else "位宽 $outWidth"
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
        Dialogs.singleChoice(requireContext(), "算法分类", groups, groups.indexOf(algo.group)) { gi ->
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
            "位宽 ${p.width} · 多项式 0x${java.lang.Long.toHexString(p.poly).uppercase(Locale.getDefault())} · " +
                    "初值 0x${java.lang.Long.toHexString(p.init).uppercase(Locale.getDefault())} · " +
                    "XorOut 0x${java.lang.Long.toHexString(p.xorout).uppercase(Locale.getDefault())} · " +
                    "RefIn=${p.refin} RefOut=${p.refout}" +
                    if (p.check >= 0) " · 标准校验值 0x${java.lang.Long.toHexString(p.check).uppercase(Locale.getDefault())}" else ""
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
        binding.tvInputInfo.text = if (bytes == null) "格式不正确" else "${bytes.size} 字节"
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
            binding.tvResultHex.text = "输入格式不正确"
            binding.tvResultDec.text = "HEX 输入仅允许 0-9 A-F 与分隔符"
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
            "点击任意单元格可复制 / 填入发送框（共 ${asciiItems.size} 项）"
        else
            "搜索「$asciiKeyword」命中 ${asciiItems.size} 项"
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
    private fun nameOf(code: Int): String = when {
        code in 0..31 -> CONTROL_DESC[code]
        code == 32 -> "空格"
        code == 127 -> "删除"
        else -> ASCII_NAMES.getOrElse(code) { "扩展字符" }
    }

    private fun showAsciiMenu(code: Int) {
        val hex = Hex.byteToHex(code)
        val c = if (code in 33..126) code.toChar().toString() else ""
        val options = mutableListOf("复制 HEX（$hex）", "发送该字节", "填入发送框（HEX）")
        if (c.isNotEmpty()) options.add(0, "复制字符「$c」")
        Dialogs.singleChoice(requireContext(), "0x$hex · DEC $code", options) { which ->
            val realWhich = if (c.isNotEmpty()) which else which + 1
            when (realWhich) {
                0 -> copyToClipboard(c, "已复制字符")
                1 -> copyToClipboard(hex, "已复制 HEX")
                2 -> {
                    if (!SerialManager.isOpen) snack("串口未打开")
                    else {
                        SerialManager.send(byteArrayOf(code.toByte()), DataType.HEX, "ASCII 码表")
                        snack("已发送 0x$hex")
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

        /** 0 ~ 31 控制字符说明 */
        val CONTROL_DESC = listOf(
            "空字符", "标题开始", "正文开始", "正文结束", "传输结束", "请求", "确认", "响铃",
            "退格", "水平制表", "换行", "垂直制表", "换页", "回车", "移出", "移入",
            "数据链路转义", "设备控制1", "设备控制2", "设备控制3", "设备控制4", "否定", "同步", "块传输结束",
            "取消", "介质结束", "替换", "转义", "文件分隔", "组分隔", "记录分隔", "单元分隔"
        )

        /** 0 ~ 255 名称表（0-31 与 32/127 由上面控制表处理，这里仅作搜索与回退） */
        val ASCII_NAMES: List<String> = buildList {
            addAll(CONTROL_DESC)
            add("空格")
            addAll(listOf("!", "\"", "#", "$", "%", "&", "'", "(", ")", "*", "+", ",", "-", ".", "/"))
            addAll((0..9).map { "数字 $it" })
            addAll(listOf(":", ";", "<", "=", ">", "?", "@"))
            addAll(('A'..'Z').map { "大写字母 $it" })
            addAll(listOf("[", "\\", "]", "^", "_", "`"))
            addAll(('a'..'z').map { "小写字母 $it" })
            addAll(listOf("{", "|", "}", "~"))
            add("删除")
            addAll(listOf(
                "欧元符号", "未定义", "单低引号", "花体 f", "双低引号", "省略号", "剑号", "双剑号",
                "修饰符抑扬", "千分号", "S 加抑扬", "单左尖括号", "OE 连字", "未定义", "Z 加抑扬", "未定义",
                "未定义", "左单引号", "右单引号", "左双引号", "右双引号", "项目符号", "短破折号", "长破折号",
                "修饰符波浪", "商标", "s 加抑扬", "单右尖括号", "oe 连字", "未定义", "z 加抑扬", "Y 加分音",
                "不换行空格", "倒叹号", "分币", "英镑", "货币符号", "日元", "断竖线", "章节号",
                "分音符号", "版权", "阴性序数", "左双角括号", "非", "软连字符", "注册商标", "长音符号",
                "度", "正负", "上标2", "上标3", "重音", "微", "段落", "中间点",
                "下加符", "上标1", "阳性序数", "右双角括号", "四分之一", "二分之一", "四分之三", "倒问号",
                "À", "Á", "Â", "Ã", "Ä", "Å", "Æ", "Ç", "È", "É", "Ê", "Ë", "Ì", "Í", "Î", "Ï",
                "Ð", "Ñ", "Ò", "Ó", "Ô", "Õ", "Ö", "乘号", "Ø", "Ù", "Ú", "Û", "Ü", "Ý", "Þ", "ß",
                "à", "á", "â", "ã", "ä", "å", "æ", "ç", "è", "é", "ê", "ë", "ì", "í", "î", "ï",
                "ð", "ñ", "ò", "ó", "ô", "õ", "ö", "除号", "ø", "ù", "ú", "û", "ü", "ý", "þ", "ÿ"
            ))
        }.let { list -> if (list.size >= 256) list.subList(0, 256) else list + List(256 - list.size) { "扩展字符" } }
    }
}
