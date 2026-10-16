package com.yujing.chuankou

import android.app.Application
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.tencent.bugly.crashreport.CrashReport
import com.yujing.chuankou.core.data.Prefs
import com.yujing.chuankou.core.data.Store
import com.yujing.chuankou.core.serial.SerialManager
import com.yujing.utils.YDate
import com.yujing.utils.YFileUtil
import com.yujing.utils.YLog
import com.yujing.utils.YPath
import com.yujing.utils.YUtils
import java.io.File
import java.util.Date

class App : Application() {

    companion object {
        private var instance: App? = null

        /** 只能在 Application.onCreate 之后调用（所有组件都晚于它，正常不会为空） */
        fun get(): App = instance
            ?: error("App 尚未初始化：App.get() 只能在 Application.onCreate 之后调用")
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        YUtils.init(this)

        // 配置与数据仓库
        Prefs.init(this)
        Store.init(this)
        SerialManager.registerContext(this)

        // 日志（本地保存最近 30 天）
        runCatching {
            YLog.saveOpen(YPath.getFilePath(this, "log"))
            YLog.setLogSaveListener { type, _, _ -> type != YLog.DEBUG }
            YLog.delDaysAgo(30)
        }

        // 主题（默认深色工业风）
        applyTheme(Prefs.themeMode)

        initBugly()
    }

    /** 0 跟随系统 / 1 深色 / 2 浅色 */
    fun applyTheme(mode: Int) {
        val night = when (mode) {
            1 -> AppCompatDelegate.MODE_NIGHT_YES
            2 -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() != night) {
            AppCompatDelegate.setDefaultNightMode(night)
        }
    }

    /**
     * 当前是否处于深色模式
     * 注意：以保存的配置为准，Application 的 resources 不会反映 Activity 级别的夜间覆盖。
     */
    fun isNightMode(): Boolean = when (Prefs.themeMode) {
        1 -> true
        2 -> false
        else -> {
            val mask = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            mask == Configuration.UI_MODE_NIGHT_YES
        }
    }

    /** 在深色 / 浅色之间切换 */
    fun toggleTheme() {
        val target = if (isNightMode()) 2 else 1
        Prefs.themeMode = target
        applyTheme(target)
    }

    private fun initBugly() {
        runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            @Suppress("DEPRECATION")
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
            val name = info.versionName

            val strategy = CrashReport.UserStrategy(this)
            strategy.setCrashHandleCallback(object : CrashReport.CrashHandleCallback() {
                override fun onCrashHandleStart(
                    crashType: Int, errorType: String?, errorMessage: String?, errorStack: String?
                ): MutableMap<String, String> {
                    val str = "包名：${packageName}\n版本：verCode=$code\tverName=$name\n" +
                            "异常类型：$crashType\n错误类型：$errorType\n错误原因：$errorMessage\n$errorStack"
                    val path = "${YPath.get()}/crash/${YDate.date2String(Date(), "yyyy_MM_dd_HH_mm_ss")}.txt"
                    runCatching { YFileUtil.stringToFile(File(path), str) }
                    return super.onCrashHandleStart(crashType, errorType, errorMessage, errorStack)
                        ?: mutableMapOf("异常保存路径" to path)
                }
            })
            strategy.deviceID = YUtils.getAndroidId()
            strategy.appPackageName = packageName
            CrashReport.initCrashReport(this, "a365f21e2f", YUtils.isDebug(this), strategy)
        }
    }
}
