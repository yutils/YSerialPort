package com.yujing.chuankou

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import com.tencent.bugly.crashreport.CrashReport
import com.yujing.utils.*
import java.io.File
import java.util.Date

class App : Application() {
    //单列
    companion object {
        private var instance: App? = null
        fun get(): App {
            return instance!!
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        YUtils.init(this)
        //保存日志开
        YLog.saveOpen(YPath.getFilePath(this, "log"))
        YLog.setLogSaveListener { type, tag, msg -> return@setLogSaveListener type != YLog.DEBUG }
        //保存最近30天日志
        YLog.delDaysAgo(30)

        //本地记录，kotlin
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)) else packageManager.getPackageInfo(packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode
        val name= info.versionName
        val strategy = CrashReport.UserStrategy(this)
        strategy.setCrashHandleCallback(object : CrashReport.CrashHandleCallback() {
            override fun onCrashHandleStart(crashType: Int, errorType: String?, errorMessage: String?, errorStack: String?): MutableMap<String, String> {
                //0：Java crash; 1：Java caught exception; 2：Native crash; 3：Unity error; 4：ANR; 5：Cocos JS error; 6：Cocos Lua error
                val str = "包名：${packageName}\n版本：verCode=${code}\tverName=${name}\n异常类型：$crashType\n错误类型：$errorType\n错误原因：$errorMessage\n$errorStack"
                val path = "${YPath.get()}/crash/${YDate.date2String(Date(), "yyyy_MM_dd_HH_mm_ss")}.txt"
                //存本地磁盘，路径Android/data/包名/files/crash/
                YFileUtil.stringToFile(File(path), str)
                //提交bugly额外信息
                val map = super.onCrashHandleStart(crashType, errorType, errorMessage, errorStack) ?: mutableMapOf("异常保存路径" to path)
                //重启APP，startActivity(packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                return map
            }
        })
        strategy.deviceID = YUtils.getAndroidId() //设置id
        strategy.appPackageName = packageName //App的包名
        CrashReport.setIsDevelopmentDevice(this, BuildConfig.DEBUG) //是否是debug
        CrashReport.initCrashReport(this, "a365f21e2f", YUtils.isDebug(this), strategy) //初始化Bugly
    }
}