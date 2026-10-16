package com.yujing.chuankou.ui.base

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import com.google.android.material.snackbar.Snackbar

/**
 * Fragment 基类：统一 ViewBinding 生命周期、横竖屏判断、常用交互
 */
abstract class BaseFragment<B : ViewBinding>(
    private val binder: (LayoutInflater, ViewGroup?, Boolean) -> B
) : Fragment() {

    private var _binding: B? = null
    protected val binding: B get() = _binding!!

    protected val isLandscape: Boolean
        get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ActivityResultLauncher 必须在此阶段注册
        onCreateReady(savedInstanceState)
    }

    /** 注册 ActivityResultLauncher 等只允许在 onCreate 阶段完成的工作 */
    protected open fun onCreateReady(savedInstanceState: Bundle?) {}

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = binder(inflater, container, false)
        return _binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        onInit(view, savedInstanceState)
    }

    protected abstract fun onInit(view: View, savedInstanceState: Bundle?)

    /** 释放监听 / 资源，在 onDestroyView 前调用 */
    protected open fun onRelease() {}

    override fun onDestroyView() {
        runCatching { onRelease() }
        super.onDestroyView()
        _binding = null
    }

    // ==================================================================
    protected fun toast(message: String) {
        if (!isAdded) return
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    protected fun snack(message: String, actionText: String = "确定", actionBlock: (() -> Unit)? = null) {
        if (!isAdded || view == null) return
        val bar = Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG)
        bar.setBackgroundTint(resolveColor(com.google.android.material.R.attr.colorSurfaceContainerHighest))
        bar.setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
        if (actionBlock != null) {
            bar.setAction(actionText) { actionBlock() }
            bar.setActionTextColor(resolveColor(com.google.android.material.R.attr.colorPrimary))
        }
        bar.show()
    }

    protected fun resolveColor(attrRes: Int): Int {
        val tv = android.util.TypedValue()
        requireContext().theme.resolveAttribute(attrRes, tv, true)
        return if (tv.resourceId != 0) androidx.core.content.ContextCompat.getColor(requireContext(), tv.resourceId) else tv.data
    }

    protected fun copyToClipboard(text: String, label: String = "已复制到剪贴板") {
        if (text.isEmpty()) return
        val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("YSerialPort", text))
        snack(label)
    }

    protected fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    protected fun dpFloat(value: Float): Float = value * resources.displayMetrics.density

    /** 供子类在页面切换时刷新（由 MainActivity 调用） */
    open fun onPageResume() {}

    /** 供子类在离开页面时调用 */
    open fun onPagePause() {}
}
