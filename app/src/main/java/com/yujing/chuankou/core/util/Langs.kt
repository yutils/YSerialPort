package com.yujing.chuankou.core.util

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import com.yujing.chuankou.App

/**
 * 多语言取词辅助：为非 Activity/Fragment 环境（SerialManager / TcpBridge 等）提供跟随应用语言的 Context。
 *
 * 原因：AppCompatDelegate.setApplicationLocales 在 Android 13 以下只影响 AppCompatActivity 的资源，
 * Application 的 resources 不会切换，直接 getString 会拿到默认语言。
 */
object Langs {

    private var cached: Context? = null
    private var cachedTag: String = ""
    private val lock = Any()

    /** 跟随应用语言的 Context；跟随系统时直接返回 Application（其资源本就是系统语言） */
    fun context(): Context {
        val app = App.get()
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return app
        val tag = locales.toLanguageTags()
        synchronized(lock) {
            if (cached != null && cachedTag == tag) return cached!!
            val config = Configuration(app.resources.configuration)
            config.setLocale(locales[0])
            val ctx = app.createConfigurationContext(config)
            cached = ctx
            cachedTag = tag
            return ctx
        }
    }

    fun str(@StringRes id: Int): String = context().getString(id)

    fun str(@StringRes id: Int, vararg args: Any): String = context().getString(id, *args)
}
