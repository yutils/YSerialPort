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
        binding.tvVersion.text = getString(
            R.string.about_version_format, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE
        )

        binding.rowTcp.root.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), TcpActivity::class.java))
        }
        setValue(
            binding.rowTcp,
            getString(R.string.more_tcp_title),
            getString(R.string.more_tcp_value),
            getString(R.string.more_tcp_sub)
        )

        binding.rowTheme.root.setOnClickListener {
            val names = listOf(
                getString(R.string.theme_follow_system),
                getString(R.string.theme_dark),
                getString(R.string.theme_light)
            )
            Dialogs.singleChoice(requireContext(), getString(R.string.settings_theme), names, Prefs.themeMode) { which ->
                Prefs.themeMode = which
                (requireActivity().application as App).applyTheme(which)
                refresh()
            }
        }

        binding.rowBgMode.root.setOnClickListener {
            val names = listOf(
                getString(R.string.bg_none),
                getString(R.string.bg_builtin),
                getString(R.string.more_bg_custom)
            )
            Dialogs.singleChoice(requireContext(), getString(R.string.settings_bg), names, Prefs.bgMode) { which ->
                Prefs.bgMode = which
                (activity as? MainActivity)?.applyBackground()
                refresh()
            }
        }

        binding.rowPickImage.root.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.rowBgScrim.root.setOnClickListener {
            Dialogs.input(
                requireContext(), getString(R.string.settings_bg_scrim), getString(R.string.more_bg_scrim_hint),
                Prefs.bgScrim.toString(), numeric = true
            ) { text ->
                Prefs.bgScrim = (text.toIntOrNull() ?: 78).coerceIn(0, 100)
                (activity as? MainActivity)?.applyBackground()
                refresh()
            }
        }

        binding.rowFont.root.setOnClickListener {
            Dialogs.input(
                requireContext(), getString(R.string.settings_font_size), "9 ~ 24 sp",
                Prefs.terminalFontSize.toString(), numeric = true
            ) { text ->
                Prefs.terminalFontSize = (text.toIntOrNull() ?: 13).coerceIn(9, 24)
                refresh()
            }
        }

        binding.rowExportHistory.root.setOnClickListener {
            if (Store.historySize == 0) {
                snack(getString(R.string.more_no_history))
                return@setOnClickListener
            }
            val file = AppFiles.saveText(requireContext(), "serial_history", buildHistoryText())
            if (file == null) snack(getString(R.string.more_export_failed))
            else snack(getString(R.string.more_exported_format, file.name), getString(R.string.more_share)) {
                AppFiles.share(requireContext(), file)
            }
        }

        binding.rowExportDir.root.setOnClickListener {
            val dir = AppFiles.exportDir(requireContext())
            Dialogs.info(
                requireContext(),
                getString(R.string.more_export_dir),
                getString(R.string.more_export_dir_msg, dir.absolutePath)
            )
        }

        binding.rowReset.root.setOnClickListener {
            Dialogs.confirm(
                requireContext(), getString(R.string.more_reset),
                getString(R.string.more_reset_msg),
                getString(R.string.more_reset_btn), true
            ) {
                Prefs.clearAll()
                (requireActivity().application as App).applyTheme(Prefs.themeMode)
                (activity as? MainActivity)?.applyBackground()
                refresh()
                snack(getString(R.string.more_reset_done))
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
        val themeNames = listOf(
            getString(R.string.theme_follow_system),
            getString(R.string.theme_dark),
            getString(R.string.theme_light)
        )
        setValue(binding.rowTheme, getString(R.string.settings_theme), themeNames.getOrElse(Prefs.themeMode) { getString(R.string.theme_dark) })
        val bgNames = listOf(
            getString(R.string.bg_none),
            getString(R.string.bg_builtin),
            getString(R.string.more_bg_custom)
        )
        setValue(binding.rowBgMode, getString(R.string.settings_bg), bgNames.getOrElse(Prefs.bgMode) { getString(R.string.bg_none) })
        setValue(
            binding.rowPickImage, getString(R.string.more_pick_image),
            if (Prefs.bgPath.isBlank()) getString(R.string.common_none_selected) else getString(R.string.more_selected),
            getString(R.string.more_pick_image_sub)
        )
        setValue(binding.rowBgScrim, getString(R.string.settings_bg_scrim), getString(R.string.settings_percent_format, Prefs.bgScrim))
        setValue(binding.rowFont, getString(R.string.settings_font_size), getString(R.string.settings_sp_format, Prefs.terminalFontSize))
        setValue(binding.rowExportHistory, getString(R.string.more_export_history), getString(R.string.more_count_format, Store.historySize))
        setValue(binding.rowExportDir, getString(R.string.more_export_dir), getString(R.string.more_view))
        setValue(binding.rowReset, getString(R.string.more_reset), getString(R.string.more_reset_careful))
        setValue(
            binding.rowExit, getString(R.string.more_exit),
            if (SerialManager.isOpen) getString(R.string.more_exit_serial_open) else getString(R.string.more_exit_normal)
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
            getString(R.string.more_source),
            SOURCE_URL,
            listOf(getString(R.string.more_copy_url), getString(R.string.more_open_browser))
        ) { which ->
            when (which) {
                0 -> copyToClipboard(SOURCE_URL, getString(R.string.more_url_copied))
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
        if (!ok) copyToClipboard(SOURCE_URL, getString(R.string.more_no_browser_copied))
    }

    // ==================================================================
    // 退出应用
    // ==================================================================
    private fun confirmExit() {
        val serialOpen = SerialManager.isOpen
        Dialogs.confirm(
            requireContext(), getString(R.string.more_exit),
            if (serialOpen) getString(R.string.more_exit_confirm_serial_open)
            else getString(R.string.more_exit_confirm_normal),
            getString(R.string.more_exit_btn), true
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
            snack(getString(R.string.more_bg_set))
        } else {
            snack(getString(R.string.more_bg_read_failed))
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
