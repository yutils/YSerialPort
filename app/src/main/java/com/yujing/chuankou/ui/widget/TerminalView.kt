package com.yujing.chuankou.ui.widget

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.util.AttributeSet
import android.util.TypedValue
import android.view.ViewTreeObserver
import android.widget.ScrollView
import androidx.appcompat.widget.AppCompatTextView

/**
 * 终端显示视图
 *
 * - 最多保留 [maxChars] 个字符（超出按行从头部裁剪）
 * - 批量刷新：合并 60ms 内的数据后再刷新，避免高频重排
 * - 长按可选中复制（选中期间自动暂停刷新，避免选区被打断）
 * - 新数据到来时自动滚动到底部；用户手动上滑查看历史后暂停自动滚动，
 *   滑回底部自动恢复
 */
class TerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    /** 最大显示字符数 */
    var maxChars: Int = 20000

    /** 是否自动滚动到底部 */
    var autoScroll: Boolean = true

    /** 用户暂停显示（暂停期间数据进入缓冲区，不刷新界面） */
    var paused: Boolean = false

    var onFlush: (() -> Unit)? = null

    /** 用户是否离开底部的状态变化回调 */
    var onScrollAwayChanged: ((Boolean) -> Unit)? = null

    private val buffer = SpannableStringBuilder()
    private val pending = SpannableStringBuilder()
    private var flushScheduled = false

    /** 有待执行的"滚动到底部"请求，等布局完成后再执行 */
    private var pendingScrollToBottom = false

    /** 用户是否手动上滑离开了底部 */
    private var userScrolledAway = false

    /** 程序触发的滚动，不参与"用户已上滑"判定 */
    private var programmaticScroll = false

    private var scrollView: ScrollView? = null

    private var scrollListener: ViewTreeObserver.OnScrollChangedListener? = null

    private val flushRunnable = Runnable { flushInternal() }

    /** 兜底：正常情况下由 onPreDraw 处理，这里防止极少数场景下没有触发绘制 */
    private val applyScrollRunnable = Runnable {
        if (pendingScrollToBottom && isAttachedToWindow) {
            pendingScrollToBottom = false
            if (autoScroll && !userScrolledAway) scrollToBottomNow()
        }
    }

    init {
        typeface = Typeface.MONOSPACE
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setLineSpacing(dpf(2f), 1f)
        setTextColor(context.getColorCompat(com.yujing.chuankou.R.color.c_terminal_text))
        includeFontPadding = false
        setTextIsSelectable(true)
        text = buffer
        setPadding(dpInt(10f), dpInt(8f), dpInt(10f), dpInt(8f))
    }

    fun setFontSizeSp(sp: Int) {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp.toFloat())
    }

    /** 追加内容（会合并刷新） */
    fun appendText(cs: CharSequence) {
        if (cs.isEmpty()) return
        pending.append(cs)
        // 缓冲过大时直接丢弃超出部分，避免内存膨胀
        if (pending.length > maxChars * 2) {
            pending.delete(0, pending.length - maxChars)
        }
        scheduleFlush()
    }

    /** 当前显示内容的最后一个字符是否为空白（用于包间分隔判断） */
    fun endsWithWhitespace(): Boolean {
        if (pending.isNotEmpty()) return pending[pending.length - 1].isWhitespace()
        if (buffer.isNotEmpty()) return buffer[buffer.length - 1].isWhitespace()
        return true
    }

    fun clearAll() {
        pending.clear()
        buffer.clear()
        text = buffer
        userScrolledAway = false
        onScrollAwayChanged?.invoke(false)
        pendingScrollToBottom = true
        invalidate()
        post(applyScrollRunnable)
    }

    /** 当前全部文本 */
    fun contentText(): String = buildString {
        append(buffer)
        append(pending)
    }

    val pendingLength: Int get() = pending.length

    /** 用户是否离开了底部（用于界面提示） */
    val isScrolledAway: Boolean get() = userScrolledAway

    // ==================================================================
    // 滚动处理
    // ==================================================================
    /** 布局完成、正式绘制之前对齐到底部，避免出现"最后一行被裁一半"的中间帧 */
    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        if (pendingScrollToBottom) {
            pendingScrollToBottom = false
            if (autoScroll && !userScrolledAway) scrollToBottomNow()
        }
        true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attachScrollTracking()
        viewTreeObserver.addOnPreDrawListener(preDrawListener)
    }

    override fun onDetachedFromWindow() {
        runCatching {
            if (viewTreeObserver.isAlive) viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        }
        detachScrollTracking()
        super.onDetachedFromWindow()
    }

    private fun attachScrollTracking() {
        val sv = parent as? ScrollView
        if (sv == null || scrollView === sv) return
        detachScrollTracking()
        scrollView = sv
        val listener = ViewTreeObserver.OnScrollChangedListener {
            if (programmaticScroll) return@OnScrollChangedListener
            val v = scrollView ?: return@OnScrollChangedListener
            val away = !atBottom(v.scrollY)
            if (away != userScrolledAway) {
                userScrolledAway = away
                onScrollAwayChanged?.invoke(away)
            }
        }
        scrollListener = listener
        sv.viewTreeObserver.addOnScrollChangedListener(listener)
    }

    private fun detachScrollTracking() {
        val sv = scrollView ?: return
        val l = scrollListener
        if (l != null && sv.viewTreeObserver.isAlive) {
            runCatching { sv.viewTreeObserver.removeOnScrollChangedListener(l) }
        }
        scrollListener = null
        scrollView = null
    }

    /**
     * 内容底部在滚动坐标系中的位置。
     * TerminalView 是 ScrollView 的唯一子 View，其 height 已包含自身 padding，
     * 所以这里只能用 ScrollView 的 paddingTop（早期版本误用了本 View 的 paddingTop，
     * 会把底部判定线抬高 8dp）。
     */
    private fun contentBottom(sv: ScrollView): Int = sv.paddingTop + height + sv.paddingBottom

    /** 当前滚动位置是否已在底部 */
    private fun atBottom(scrollY: Int): Boolean {
        val sv = scrollView ?: return true
        return scrollY + sv.height >= contentBottom(sv) - dpInt(16f)
    }

    private fun scrollToBottomNow() {
        val sv = scrollView ?: parent as? ScrollView ?: return
        val contentBottom = contentBottom(sv)
        val target = (contentBottom - sv.height).coerceAtLeast(0)
        programmaticScroll = true
        runCatching { sv.scrollTo(sv.scrollX, target) }
        programmaticScroll = false
        if (userScrolledAway) {
            userScrolledAway = false
            onScrollAwayChanged?.invoke(false)
        }
    }

    /** 手动跳到底部（恢复自动滚动） */
    fun jumpToBottom() {
        userScrolledAway = false
        pendingScrollToBottom = true
        invalidate()
        post(applyScrollRunnable)
    }

    // ==================================================================
    // 刷新
    // ==================================================================
    private fun scheduleFlush() {
        if (flushScheduled) return
        flushScheduled = true
        postDelayed(flushRunnable, FLUSH_INTERVAL)
    }

    private fun flushInternal() {
        flushScheduled = false
        if (pending.isEmpty()) return
        if (paused) return
        // 用户正在选择文本时延后刷新，避免打断选区
        if (selectionStart >= 0 && selectionEnd > selectionStart) {
            scheduleFlush()
            return
        }
        buffer.append(pending)
        pending.clear()
        trim()
        text = buffer
        if (autoScroll && !userScrolledAway) pendingScrollToBottom = true
        onFlush?.invoke()
    }

    private fun trim() {
        if (buffer.length <= maxChars) return
        // 多裁一点，减少裁剪频率
        var cut = buffer.length - maxChars + maxChars / 10
        if (cut <= 0) return
        // 尽量在换行处裁剪，避免截断半行
        val nl = buffer.indexOf("\n", cut)
        if (nl in cut until (cut + 2048)) cut = nl + 1
        if (cut >= buffer.length) return
        runCatching { buffer.delete(0, cut) }
    }

    private fun dpf(v: Float): Float = v * resources.displayMetrics.density
    private fun dpInt(v: Float): Int = (v * resources.displayMetrics.density).toInt()

    private fun Context.getColorCompat(res: Int): Int =
        androidx.core.content.ContextCompat.getColor(this, res)

    companion object {
        private const val FLUSH_INTERVAL = 60L
    }
}
