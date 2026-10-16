package com.yujing.chuankou.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.yujing.chuankou.R

/**
 * 分段选择控件（Segmented Control）
 *
 * 一组互斥选项，选中项高亮为强调色，常用于 ASCII / HEX 切换等场景。
 */
class SegmentedControl @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val labels = mutableListOf<String>()
    private var views = mutableListOf<TextView>()

    var onSelected: ((Int) -> Unit)? = null

    var selectedIndex: Int = 0
        private set

    init {
        orientation = HORIZONTAL
        background = ContextCompat.getDrawable(context, R.drawable.bg_segment_group)
        val p = (3 * resources.displayMetrics.density).toInt()
        setPadding(p, p, p, p)
    }

    fun setOptions(options: List<String>, initial: Int = 0) {
        removeAllViews()
        labels.clear()
        labels.addAll(options)
        views.clear()
        val density = resources.displayMetrics.density
        val padH = (10 * density).toInt()
        val padV = (6 * density).toInt()
        options.forEachIndexed { index, label ->
            val tv = TextView(context).apply {
                text = label
                gravity = Gravity.CENTER
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(ContextCompat.getColorStateList(context, R.color.sel_seg_text))
                setPadding(padH, padV, padH, padV)
                isClickable = true
                isFocusable = true
                background = ContextCompat.getDrawable(context, R.drawable.bg_seg_item)
                setOnClickListener { select(index, true) }
            }
            val lp = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            lp.marginStart = if (index == 0) 0 else (3 * density).toInt()
            addView(tv, lp)
            views.add(tv)
        }
        select(initial.coerceIn(0, (options.size - 1).coerceAtLeast(0)), false)
    }

    fun select(index: Int, notify: Boolean = true) {
        if (index !in views.indices) return
        selectedIndex = index
        views.forEachIndexed { i, tv -> tv.isSelected = i == index }
        if (notify) onSelected?.invoke(index)
    }

    fun labelOf(index: Int): String = labels.getOrElse(index) { "" }
    val size: Int get() = views.size
}
