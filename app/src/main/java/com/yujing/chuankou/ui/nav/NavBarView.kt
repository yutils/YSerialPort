package com.yujing.chuankou.ui.nav

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import com.yujing.chuankou.R
import com.yujing.chuankou.databinding.ViewNavItemBinding

/**
 * 自适应导航栏
 *
 * 横屏：竖向侧边栏（图标在上、文字在下，固定 92dp 宽）
 * 竖屏：横向底部栏（图标在上、文字在下，等分宽度，62dp 高）
 */
class NavBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    data class Item(
        val id: String,
        @param:DrawableRes val icon: Int,
        val label: String
    )

    private val items = mutableListOf<Item>()
    private val itemViews = mutableListOf<ViewNavItemBinding>()

    var onSelected: ((Int) -> Unit)? = null

    var landscape: Boolean = true
        private set

    private var selectedIndex = -1

    init {
        orientation = VERTICAL
        setWillNotDraw(true)
    }

    fun setItems(newItems: List<Item>) {
        removeAllViews()
        itemViews.clear()
        items.clear()
        items.addAll(newItems)
        val inflater = LayoutInflater.from(context)
        items.forEachIndexed { index, item ->
            val binding = ViewNavItemBinding.inflate(inflater, this, false)
            binding.ivIcon.setImageResource(item.icon)
            binding.tvLabel.text = item.label
            binding.root.setOnClickListener { select(index, true) }
            addView(binding.root)
            itemViews.add(binding)
        }
        applyLayout()
        if (selectedIndex < 0 && items.isNotEmpty()) select(0, false)
    }

    fun setLandscape(value: Boolean) {
        if (landscape == value) return
        landscape = value
        applyLayout()
    }

    private fun applyLayout() {
        orientation = if (landscape) VERTICAL else HORIZONTAL
        itemViews.forEachIndexed { i, b ->
            val lp = b.root.layoutParams as LayoutParams
            if (landscape) {
                lp.width = LayoutParams.MATCH_PARENT
                lp.height = 0
                lp.weight = 1f
            } else {
                lp.width = 0
                lp.height = LayoutParams.MATCH_PARENT
                lp.weight = 1f
            }
            b.root.layoutParams = lp
            // 竖屏时收紧内边距，避免 7 项过挤
            val h = if (landscape) dp(10) else dp(5)
            b.root.setPadding(0, h, 0, h)
        }
        // 侧栏在顶部留出 logo 空间
        invalidate()
    }

    fun select(index: Int, notify: Boolean = true) {
        if (index !in items.indices) return
        selectedIndex = index
        itemViews.forEachIndexed { i, b -> b.root.isSelected = i == index }
        if (notify) onSelected?.invoke(index)
    }

    fun currentIndex(): Int = selectedIndex

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    fun labelOf(index: Int): String = items.getOrNull(index)?.label ?: ""
    fun idOf(index: Int): String = items.getOrNull(index)?.id ?: ""
}
