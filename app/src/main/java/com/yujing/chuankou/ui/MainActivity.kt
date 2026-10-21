package com.yujing.chuankou.ui

import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.kotlinx.appUpdate.AppUpdate
import com.yujing.chuankou.App
import com.yujing.chuankou.R
import com.yujing.chuankou.core.data.Prefs
import com.yujing.chuankou.core.serial.SerialConfig
import com.yujing.chuankou.core.serial.SerialManager
import com.yujing.chuankou.databinding.ActivityMainBinding
import com.yujing.chuankou.ui.autoreply.AutoReplyFragment
import com.yujing.chuankou.ui.base.BaseFragment
import com.yujing.chuankou.ui.command.CommandFragment
import com.yujing.chuankou.ui.history.HistoryFragment
import com.yujing.chuankou.ui.more.MoreFragment
import com.yujing.chuankou.ui.nav.NavBarView
import com.yujing.chuankou.ui.settings.SettingsFragment
import com.yujing.chuankou.ui.terminal.TerminalFragment
import com.yujing.chuankou.ui.tool.ToolFragment
import com.yujing.utils.YUtils
import java.io.File

/**
 * 主界面（自适应外壳）
 * 横屏：左侧竖向导航 + 右侧内容；竖屏：上方内容 + 底部横向导航
 */
class MainActivity : AppCompatActivity(), SerialManager.Listener {

    private lateinit var binding: ActivityMainBinding
    private val fragments = LinkedHashMap<String, Fragment>()
    private var currentId: String? = null

    private data class Page(
        val id: String,
        val titleRes: Int,
        val subtitleRes: Int,
        val icon: Int,
        val create: () -> Fragment
    )

    private val pages: List<Page> = listOf(
        Page("terminal", R.string.nav_terminal, R.string.nav_terminal_sub, R.drawable.ic_terminal) { TerminalFragment() },
        Page("history", R.string.nav_history, R.string.nav_history_sub, R.drawable.ic_history) { HistoryFragment() },
        Page("command", R.string.nav_command, R.string.nav_command_sub, R.drawable.ic_command) { CommandFragment() },
        Page("autoreply", R.string.nav_autoreply, R.string.nav_autoreply_sub, R.drawable.ic_reply) { AutoReplyFragment() },
        Page("tool", R.string.nav_tool, R.string.nav_tool_sub, R.drawable.ic_tool) { ToolFragment() },
        Page("settings", R.string.nav_settings, R.string.nav_settings_sub, R.drawable.ic_settings) { SettingsFragment() },
        Page("more", R.string.nav_more, R.string.nav_more_sub, R.drawable.ic_more) { MoreFragment() }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        initNav()
        applyOrientation(resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)

        binding.topbar.btnPower.setOnClickListener { toggleSerial() }
        binding.topbar.llStatus.setOnClickListener { selectPage("settings") }
        binding.topbar.btnTheme.setOnClickListener { toggleTheme() }

        SerialManager.addListener(this)
        updateSerialUi()
        applyBackground()
        updateThemeIcon()

        // Activity 重建（如切换主题）后恢复当前页面
        val startId = savedInstanceState?.getString(KEY_PAGE)
            ?: intent?.getStringExtra(EXTRA_PAGE)
            ?: "terminal"
        selectPage(startId)

        //检查更新
        AppUpdate.baseUrl = "http://apk.kotlinx.com:9999"
        AppUpdate.showCheckUpdateErrorToast = false
        AppUpdate().checkAndUpdate(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_PAGE, currentId)
    }

    override fun onResume() {
        super.onResume()
        setImmersive()
        SerialManager.addListener(this)
        updateSerialUi()
        applyBackground()
        updateThemeIcon()
        (fragments[currentId] as? BaseFragment<*>)?.onPageResume()
    }

    override fun onPause() {
        super.onPause()
        (fragments[currentId] as? BaseFragment<*>)?.onPagePause()
    }

    override fun onDestroy() {
        SerialManager.removeListener(this)
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setImmersive()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyOrientation(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)
    }

    // ==================================================================
    private fun setImmersive() {
        runCatching {
            YUtils.setFullScreen(this, true)
            YUtils.setImmersive(this, true)
        }
    }

    private fun initNav() {
        binding.nav.setItems(pages.map { NavBarView.Item(it.id, it.icon, getString(it.titleRes)) })
        binding.nav.onSelected = { index -> selectPage(pages[index].id) }
    }

    fun selectPage(id: String) {
        val index = pages.indexOfFirst { it.id == id }
        if (index < 0) return
        binding.nav.select(index, false)
        showFragment(index)
        binding.topbar.tvPageTitle.text = getString(pages[index].titleRes)
        binding.topbar.tvPageSub.text = getString(pages[index].subtitleRes)
    }

    private fun showFragment(index: Int) {
        val page = pages[index]
        if (currentId == page.id) return
        currentId?.let { id -> (fragments[id] as? BaseFragment<*>)?.onPagePause() }
        val ft = supportFragmentManager.beginTransaction()
        fragments.values.forEach { ft.hide(it) }
        // 优先复用 FragmentManager 已恢复的实例，避免重建后出现重复 Fragment
        var target = fragments[page.id] ?: supportFragmentManager.findFragmentByTag(page.id)
        if (target == null) {
            target = page.create()
            fragments[page.id] = target
            ft.add(R.id.fragment_container, target, page.id)
        } else {
            fragments[page.id] = target
            ft.show(target)
        }
        ft.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
        ft.commitAllowingStateLoss()
        currentId = page.id
        (target as? BaseFragment<*>)?.onPageResume()
    }

    /** 横竖屏布局切换 */
    private fun applyOrientation(landscape: Boolean) {
        binding.mainRoot.orientation = if (landscape) android.widget.LinearLayout.HORIZONTAL
        else android.widget.LinearLayout.VERTICAL
        binding.nav.setLandscape(landscape)

        val density = resources.displayMetrics.density
        binding.mainRoot.removeAllViews()
        if (landscape) {
            val navLp = android.widget.LinearLayout.LayoutParams(
                (dimen(R.dimen.nav_rail_w)), android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            binding.mainRoot.addView(binding.nav, navLp)
            val rightLp = android.widget.LinearLayout.LayoutParams(
                0, android.view.ViewGroup.LayoutParams.MATCH_PARENT, 1f
            )
            binding.mainRoot.addView(binding.rightRoot, rightLp)
        } else {
            val rightLp = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
            binding.mainRoot.addView(binding.rightRoot, rightLp)
            val navLp = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, dimen(R.dimen.nav_bar_h)
            )
            binding.mainRoot.addView(binding.nav, navLp)
        }
    }

    private fun dimen(res: Int): Int = resources.getDimensionPixelSize(res)

    // ==================================================================
    // 串口控制
    // ==================================================================
    private fun toggleSerial() {
        if (SerialManager.isOpen) {
            SerialManager.close()
        } else {
            val cfg = SerialConfig.fromPrefs()
            if (!cfg.isValid) {
                toast(getString(R.string.serial_select_in_settings))
                selectPage("settings")
                return
            }
            SerialManager.open(cfg)
        }
    }

    private fun updateSerialUi() {
        val open = SerialManager.isOpen
        val tb = binding.topbar
        tb.llStatus.setBackgroundResource(if (open) R.drawable.bg_status_open else R.drawable.bg_status_closed)
        tb.tvStatus.text = when (SerialManager.currentState()) {
            SerialManager.State.OPEN -> SerialConfig.fromPrefs().summary().removePrefix(" · ")
            SerialManager.State.OPENING -> getString(R.string.serial_state_opening)
            SerialManager.State.CLOSING -> getString(R.string.serial_state_closing)
            SerialManager.State.ERROR -> SerialManager.lastError ?: getString(R.string.serial_state_error)
            SerialManager.State.CLOSED -> getString(R.string.serial_state_closed)
        }
        tb.btnPower.setIconResource(if (open) R.drawable.ic_power else R.drawable.ic_power)
        tb.btnPower.iconTint = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(this, if (open) R.color.c_primary else R.color.c_text_secondary)
        )
    }

    private fun toggleTheme() {
        (application as App).toggleTheme()
    }

    /** 深色时显示太阳（点击切浅色），浅色时显示月亮 */
    private fun updateThemeIcon() {
        val night = (application as App).isNightMode()
        binding.topbar.btnTheme.setIconResource(if (night) R.drawable.ic_sun else R.drawable.ic_moon)
    }

    // ==================================================================
    // 背景图
    // ==================================================================
    fun applyBackground() {
        val mode = Prefs.bgMode
        val scrim = Prefs.bgScrim.coerceIn(0, 100)
        binding.vScrim.setBackgroundColor(ContextCompat.getColor(this, R.color.c_bg))
        binding.vScrim.alpha = scrim / 100f
        binding.ivBg.alpha = 1f
        if (mode == 0) {
            binding.ivBg.visibility = View.GONE
            return
        }
        val bmp = when (mode) {
            1 -> BitmapFactory.decodeResource(resources, R.drawable.bg_builtin)
            2 -> {
                val f = File(Prefs.bgPath)
                if (f.exists()) decodeSampled(f) else null
            }
            else -> null
        }
        if (bmp == null) {
            binding.ivBg.visibility = View.GONE
        } else {
            binding.ivBg.visibility = View.VISIBLE
            binding.ivBg.setImageBitmap(bmp)
        }
    }

    private fun decodeSampled(file: File): android.graphics.Bitmap? = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        val max = 1600
        var sample = 1
        while (opts.outWidth / sample > max || opts.outHeight / sample > max) sample *= 2
        val opts2 = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(file.absolutePath, opts2)
    }.getOrNull()

    fun toast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    // ==================================================================
    // SerialManager.Listener
    // ==================================================================
    override fun onState(state: SerialManager.State, message: String?) {
        runOnUiThread { updateSerialUi() }
    }

    override fun onStats(rxBytes: Long, txBytes: Long, rxPackets: Long, txPackets: Long) = Unit

    companion object {
        const val EXTRA_PAGE = "page"
        private const val KEY_PAGE = "current_page_id"
    }
}
