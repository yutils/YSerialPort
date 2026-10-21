package com.yujing.chuankou.core.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.yujing.chuankou.R
import java.util.Locale

/**
 * 应用内语言切换（默认跟随系统）
 *
 * - 语言状态由 AppCompatDelegate.setApplicationLocales() 统一管理：
 *   - Android 13+：写入系统「应用语言」设置；
 *   - Android 13 以下：由 Manifest 中 autoStoreLocales 服务自动持久化与恢复。
 * - 空 LocaleList 表示跟随系统语言。
 */
object Languages {

    /** 选项列表：index 0 = 跟随系统（tag 为 null），其余为 BCP-47 语言标签 */
    private val TAGS = listOf<String?>(null, "zh", "en", "zh-TW", "ja", "ko", "ru", "de", "fr", "es", "pt")

    /** 各语言在自身语言下的名称（顺序与 TAGS[1..] 对应），展示时无需翻译 */
    private val NATIVE_NAMES = listOf(
        "简体中文", "English", "繁體中文", "日本語", "한국어", "Русский", "Deutsch", "Français", "Español", "Português"
    )

    val optionCount: Int get() = TAGS.size

    /** 选项展示名：跟随系统（当前生效语言） / 各语言原生名 */
    fun displayNameAt(context: Context, index: Int): String {
        if (index <= 0) {
            val sys = LocaleListCompat.getAdjustedDefault().get(0)
                ?: LocaleListCompat.getDefault().get(0)
            return if (sys != null) {
                context.getString(R.string.lang_follow_system, sys.getDisplayName(sys))
            } else {
                context.getString(R.string.lang_follow_system_plain)
            }
        }
        return NATIVE_NAMES.getOrElse(index - 1) { "" }
    }

    /** 当前选中项 index（0 = 跟随系统） */
    fun currentIndex(): Int {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return 0
        val current = locales[0] ?: return 0
        var best = 0
        var bestScore = 0
        TAGS.forEachIndexed { i, tag ->
            if (tag == null) return@forEachIndexed
            val target = LocaleListCompat.forLanguageTags(tag)[0] ?: return@forEachIndexed
            var score = 0
            if (current.language == target.language) score += 1
            if (target.country.isNotEmpty() && current.country.equals(target.country, ignoreCase = true)) score += 2
            if (score > bestScore) {
                bestScore = score
                best = i
            }
        }
        return if (bestScore > 0) best else 0
    }

    /** 切换语言并持久化；tag 为 null 表示恢复跟随系统 */
    fun apply(index: Int) {
        val tag = TAGS.getOrElse(index) { null }
        val locales = if (tag == null) LocaleListCompat.getEmptyLocaleList()
        else LocaleListCompat.forLanguageTags(tag)
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
