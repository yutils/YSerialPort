package com.yujing.chuankou.ui.widget

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout

/**
 * 自适应分栏容器
 *
 * 横屏：[landscapeWeights] 决定各面板的列宽比例（横向排列）
 * 竖屏：[portraitWeights] 决定各面板的行高比例（纵向排列）
 *
 * 子 View 中 id 以 "divider" 结尾的会被当作分隔占位（宽度/高度 = gap）
 */
class SplitLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var landscape: Boolean = true
        private set

    private var landscapeWeights: List<Float> = listOf(1f)
    private var portraitWeights: List<Float> = listOf(1f)
    private var gapPx: Int = 0

    init {
        orientation = HORIZONTAL
    }

    /**
     * 配置分栏
     * @param landscape 是否横屏
     * @param landscapeWeights 横屏各面板权重
     * @param portraitWeights 竖屏各面板权重（缺省时复用横屏权重）
     * @param gapPx 面板间距
     */
    fun configure(
        landscape: Boolean,
        landscapeWeights: List<Float>,
        portraitWeights: List<Float> = landscapeWeights,
        gapPx: Int = this.gapPx
    ) {
        this.landscape = landscape
        this.landscapeWeights = landscapeWeights
        this.portraitWeights = portraitWeights
        this.gapPx = gapPx
        apply()
    }

    /** 屏幕方向变化时调用（保持原权重配置） */
    fun setLandscape(value: Boolean) {
        if (landscape == value) return
        landscape = value
        apply()
    }

    /**
     * 根据自身宽高比自动切换分栏方向：
     * 宽度 >= 高度 视为横屏（左右分栏），否则为竖屏（上下分栏）。
     * 这样即使 Activity 声明了 configChanges 而不会重建，也能正确自适应。
     */
    private fun autoAdapt(w: Int, h: Int) {
        if (!autoOrientation || w == 0 || h == 0) return
        val shouldBeLandscape = w >= h
        if (shouldBeLandscape != landscape) {
            landscape = shouldBeLandscape
            post { if (isAttachedToWindow) apply() }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) autoAdapt(w, h)
    }

    /** 是否根据自身宽高比自动切换方向（默认开启） */
    var autoOrientation: Boolean = true

    private fun apply() {
        orientation = if (landscape) HORIZONTAL else VERTICAL
        val weights = if (landscape) landscapeWeights else portraitWeights
        var index = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val params = child.layoutParams as LayoutParams
            val isDivider = isDivider(child)
            if (isDivider) {
                if (landscape) {
                    params.width = gapPx
                    params.height = LayoutParams.MATCH_PARENT
                } else {
                    params.width = LayoutParams.MATCH_PARENT
                    params.height = gapPx
                }
                params.weight = 0f
            } else {
                val w = weights.getOrElse(index) { 1f }
                if (landscape) {
                    params.width = 0
                    params.height = LayoutParams.MATCH_PARENT
                } else {
                    params.width = LayoutParams.MATCH_PARENT
                    params.height = 0
                }
                params.weight = w
                index++
            }
            child.layoutParams = params
        }
    }

    private fun isDivider(child: View): Boolean {
        // 优先按类型判断（最可靠）
        if (child is SplitDivider) return true
        if (child.id == View.NO_ID) return false
        val name = runCatching { resources.getResourceEntryName(child.id) }.getOrNull() ?: return false
        return name.endsWith("divider")
    }

    companion object {
        fun isLandscape(context: Context): Boolean =
            context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        fun dp(context: Context, value: Int): Int =
            (value * context.resources.displayMetrics.density).toInt()
    }
}

/** 分栏分隔视图（供 SplitLayout 识别；id 需以 divider 结尾） */
class SplitDivider @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs)
