package com.yujing.chuankou.ui.more

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.yujing.chuankou.App
import com.yujing.chuankou.BuildConfig
import com.yujing.chuankou.R
import com.yujing.chuankou.core.data.Prefs
import com.yujing.chuankou.core.data.Store
import com.yujing.chuankou.core.net.TcpBridge
import com.yujing.chuankou.core.serial.SerialManager
import com.yujing.chuankou.core.util.AppFiles
import com.yujing.chuankou.databinding.FragmentMoreBinding
import com.yujing.chuankou.databinding.ViewSettingRowBinding
import com.yujing.chuankou.ui.MainActivity
import com.yujing.chuankou.ui.base.BaseFragment
import com.yujing.chuankou.ui.tcp.TcpActivity
import com.yujing.chuankou.ui.widget.Dialogs
import java.io.File

/**
 * 更多页：网络透传入口 / 个性化 / 数据维护 / 关于
 */
class MoreFragment : BaseFragment<FragmentMoreBinding>(FragmentMoreBinding::inflate) {

    private lateinit var pickImageLauncher: androidx.activity.result.ActivityResultLauncher<String>

    override fun onCreateReady(savedInstanceState: Bundle?) {
        pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) saveBackground(uri)
        }
    }

    override fun onInit(view: View, savedInstanceState: Bundle?) {
        binding.tvAppName.text = getString(R.string.app_name)
        binding.tvVersion.text = "版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

        binding.rowTcp.root.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), TcpActivity::class.java))
        }
        setValue(binding.rowTcp, "串口 ↔ TCP 透传", "服务端 / 客户端", "把串口数据转发到网络，或把网络数据写入串口")

        binding.rowTheme.root.setOnClickListener {
            val names = listOf("跟随系统", "深色", "浅色")
            Dialogs.singleChoice(requireContext(), "主题", names, Prefs.themeMode) { which ->
                Prefs.themeMode = which
                (requireActivity().application as App).applyTheme(which)
                refresh()
            }
        }

        binding.rowBgMode.root.setOnClickListener {
            val names = listOf("无背景", "内置壁纸", "自定义图片")
            Dialogs.singleChoice(requireContext(), "背景图", names, Prefs.bgMode) { which ->
                Prefs.bgMode = which
                (activity as? MainActivity)?.applyBackground()
                refresh()
            }
        }

        binding.rowPickImage.root.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.rowBgScrim.root.setOnClickListener {
            Dialogs.input(requireContext(), "背景遮罩", "0 ~ 100，越大背景越暗", Prefs.bgScrim.toString(), numeric = true) { text ->
                Prefs.bgScrim = (text.toIntOrNull() ?: 78).coerceIn(0, 100)
                (activity as? MainActivity)?.applyBackground()
                refresh()
            }
        }

        binding.rowFont.root.setOnClickListener {
            Dialogs.input(requireContext(), "终端字号", "9 ~ 24 sp", Prefs.terminalFontSize.toString(), numeric = true) { text ->
                Prefs.terminalFontSize = (text.toIntOrNull() ?: 13).coerceIn(9, 24)
                refresh()
            }
        }

        binding.rowExportHistory.root.setOnClickListener {
            if (Store.historySize == 0) {
                snack("暂无历史记录")
                return@setOnClickListener
            }
            val file = AppFiles.saveText(requireContext(), "serial_history", buildHistoryText())
            if (file == null) snack("导出失败")
            else snack("已导出 ${file.name}", "分享") { AppFiles.share(requireContext(), file) }
        }

        binding.rowExportDir.root.setOnClickListener {
            val dir = AppFiles.exportDir(requireContext())
            Dialogs.info(requireContext(), "导出目录", "${dir.absolutePath}\n\n可通过文件管理器或 adb pull 获取。")
        }

        binding.rowReset.root.setOnClickListener {
            Dialogs.confirm(
                requireContext(), "恢复默认设置",
                "将清空所有配置（串口参数、收发配置、外观等）。\n快捷指令与自动应答规则不会删除。",
                "恢复默认", true
            ) {
                Prefs.clearAll()
                (requireActivity().application as App).applyTheme(Prefs.themeMode)
                (activity as? MainActivity)?.applyBackground()
                refresh()
                snack("已恢复默认设置")
            }
        }

        // 开源地址：点击后选择「复制地址 / 浏览器打开」
        binding.rowSource.setOnClickListener { showSourceActions() }

        // 退出应用
        binding.rowExit.root.setOnClickListener { confirmExit() }

        refresh()
    }

    override fun onPageResume() = refresh()

    private fun refresh() {
        if (view == null) return
        setValue(binding.rowTheme, "主题", listOf("跟随系统", "深色", "浅色").getOrElse(Prefs.themeMode) { "深色" })
        setValue(binding.rowBgMode, "背景图", listOf("无背景", "内置壁纸", "自定义图片").getOrElse(Prefs.bgMode) { "无背景" })
        setValue(
            binding.rowPickImage, "选择自定义背景",
            if (Prefs.bgPath.isBlank()) "未选择" else "已选择",
            "从相册选择图片，建议横屏大图"
        )
        setValue(binding.rowBgScrim, "背景遮罩", "${Prefs.bgScrim} %")
        setValue(binding.rowFont, "终端字号", "${Prefs.terminalFontSize} sp")
        setValue(binding.rowExportHistory, "导出收发记录", "${Store.historySize} 条")
        setValue(binding.rowExportDir, "导出目录", "查看")
        setValue(binding.rowReset, "恢复默认设置", "慎用")
        setValue(
            binding.rowExit, "退出应用",
            if (SerialManager.isOpen) "先关串口再退出" else "释放资源后退出"
        )
        binding.rowExit.ivIcon.visibility = View.VISIBLE
        binding.rowExit.ivIcon.setImageResource(R.drawable.ic_exit)
        binding.rowExit.ivIcon.imageTintList =
            ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.c_error))
        binding.rowExit.tvTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.c_error))
        binding.rowExit.ivArrow.visibility = View.GONE
    }

    // ==================================================================
    // 开源地址
    // ==================================================================
    private fun showSourceActions() {
        Dialogs.actions(
            requireContext(),
            "开源地址",
            SOURCE_URL,
            listOf("复制地址", "浏览器打开")
        ) { which ->
            when (which) {
                0 -> copyToClipboard(SOURCE_URL, "地址已复制")
                1 -> openSourceUrl()
            }
        }
    }

    /** 打开浏览器；没有可用浏览器时回退为复制地址，避免点击后毫无反馈 */
    private fun openSourceUrl() {
        val ok = runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL)))
            true
        }.getOrDefault(false)
        if (!ok) copyToClipboard(SOURCE_URL, "无法打开浏览器，地址已复制")
    }

    // ==================================================================
    // 退出应用
    // ==================================================================
    private fun confirmExit() {
        val serialOpen = SerialManager.isOpen
        Dialogs.confirm(
            requireContext(), "退出应用",
            if (serialOpen) "将先关闭串口连接，保存收发记录后退出。\n确定退出吗？"
            else "将保存收发记录后退出应用。\n确定退出吗？",
            "退出", true
        ) {
            val act = activity ?: return@confirm
            // 先落盘（历史是 2.5s 防抖写入，直接退出会丢最后几条）
            runCatching { Store.flush() }
            // 停掉网络透传与串口，释放 fd
            runCatching { TcpBridge.stop() }
            runCatching { SerialManager.close() }
            // 先 removeTask 再 finishAffinity：否则「最近任务」会残留一张空卡片
            act.finishAndRemoveTask()
            act.finishAffinity()
        }
    }

    private fun saveBackground(uri: Uri) {
        val ok = runCatching {
            val dir = File(requireContext().filesDir, "bg").apply { if (!exists()) mkdirs() }
            val target = File(dir, "bg_custom.jpg")
            requireContext().contentResolver.openInputStream(uri)?.use { ins ->
                target.outputStream().use { out -> ins.copyTo(out) }
            }
            Prefs.bgPath = target.absolutePath
            Prefs.bgMode = 2
            true
        }.getOrDefault(false)
        if (ok) {
            (activity as? MainActivity)?.applyBackground()
            refresh()
            snack("背景图已设置")
        } else {
            snack("图片读取失败")
        }
    }

    private fun buildHistoryText(): String = Store.buildExportText(Store.historyList().asReversed())

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

    companion object {
        private const val SOURCE_URL = "https://github.com/yutils/YSerialPort"
    }
}
