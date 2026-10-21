package com.yujing.chuankou.ui.widget

import android.content.Context
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.yujing.chuankou.R
/**
 * 统一风格的对话框工具
 */
object Dialogs {

    /** 单选列表 */
    fun singleChoice(
        context: Context,
        title: String,
        items: List<String>,
        checked: Int = -1,
        onPick: (Int) -> Unit
    ) {
        val builder = MaterialAlertDialogBuilder(context).setTitle(title)
        if (checked in items.indices) {
            builder.setSingleChoiceItems(items.toTypedArray(), checked) { d, which ->
                d.dismiss()
                onPick(which)
            }
        } else {
            builder.setItems(items.toTypedArray()) { d, which ->
                d.dismiss()
                onPick(which)
            }
        }
        builder.setNegativeButton(context.getString(R.string.common_cancel), null)
        builder.show()
    }

    /** 文本输入 */
    fun input(
        context: Context,
        title: String,
        hint: String = "",
        initial: String = "",
        helper: String? = null,
        numeric: Boolean = false,
        onOk: (String) -> Unit
    ) {
        val pad = (16 * context.resources.displayMetrics.density).toInt()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }
        val et = EditText(context).apply {
            setText(initial)
            this.hint = hint
            setSingleLine(!numeric)
            inputType = if (numeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
            setTextColor(context.getThemeColor(com.google.android.material.R.attr.colorOnSurface))
            setHintTextColor(context.getThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
        }
        container.addView(et)
        if (helper != null) {
            container.addView(TextView(context).apply {
                text = helper
                textSize = 11f
                setPadding(0, pad / 3, 0, 0)
                setTextColor(context.getThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            })
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(container)
            .setPositiveButton(context.getString(R.string.common_ok)) { _, _ -> onOk(et.text.toString()) }
            .setNegativeButton(context.getString(R.string.common_cancel), null)
            .show()
    }

    /** 多行文本输入 */
    fun inputMultiline(
        context: Context,
        title: String,
        hint: String = "",
        initial: String = "",
        helper: String? = null,
        onOk: (String) -> Unit
    ) {
        val pad = (16 * context.resources.displayMetrics.density).toInt()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }
        val et = EditText(context).apply {
            setText(initial)
            this.hint = hint
            minLines = 3
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setTextColor(context.getThemeColor(com.google.android.material.R.attr.colorOnSurface))
            setHintTextColor(context.getThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
        }
        container.addView(et)
        if (helper != null) {
            container.addView(TextView(context).apply {
                text = helper
                textSize = 11f
                setPadding(0, pad / 3, 0, 0)
                setTextColor(context.getThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            })
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(container)
            .setPositiveButton(context.getString(R.string.common_ok)) { _, _ -> onOk(et.text.toString()) }
            .setNegativeButton(context.getString(R.string.common_cancel), null)
            .show()
    }

    /**
     * 确认框
     * @param danger true 时把确认按钮染成错误色（用于「清空 / 恢复默认 / 退出」这类破坏性操作）
     */
    fun confirm(
        context: Context,
        title: String,
        message: String,
        okText: String = context.getString(R.string.common_ok),
        danger: Boolean = false,
        onOk: () -> Unit
    ) {
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(okText) { _, _ -> onOk() }
            .setNegativeButton(context.getString(R.string.common_cancel), null)
            .create()
        if (danger) {
            // 按钮文字颜色只能在 show 之后取到，故用 OnShowListener
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(
                    context.getThemeColor(com.google.android.material.R.attr.colorError)
                )
            }
        }
        dialog.show()
    }

    /** 信息框 */
    fun info(context: Context, title: String, message: String) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(context.getString(R.string.common_got_it), null)
            .show()
    }

    /**
     * 列表动作选择：用于「复制地址 / 浏览器打开」这类并列操作。
     *
     * 坑：AlertDialog 的 message 与 items 共用同一个内容槽，同时设置会顶掉列表，
     * 所以副标题（如要复制的 URL）走自定义标题视图，而不是 setMessage。
     */
    fun actions(
        context: Context,
        title: String,
        subtitle: String? = null,
        items: List<String>,
        onPick: (Int) -> Unit
    ) {
        val builder = MaterialAlertDialogBuilder(context)
        if (subtitle.isNullOrBlank()) {
            builder.setTitle(title)
        } else {
            builder.setCustomTitle(buildTitleView(context, title, subtitle))
        }
        builder.setItems(items.toTypedArray()) { d, which ->
            d.dismiss()
            onPick(which)
        }
        builder.setNegativeButton(context.getString(R.string.common_cancel), null)
        builder.show()
    }

    /** 对话框自定义标题：主标题 + 可长按复制的小字副标题 */
    private fun buildTitleView(context: Context, title: String, subtitle: String): View {
        val pad = (24 * context.resources.displayMetrics.density).toInt()
        val padBottom = (12 * context.resources.displayMetrics.density).toInt()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, padBottom)
            addView(TextView(context).apply {
                text = title
                textSize = 19f
                setTextColor(context.getThemeColor(com.google.android.material.R.attr.colorOnSurface))
            })
            addView(TextView(context).apply {
                text = subtitle
                textSize = 12f
                setPadding(0, padBottom, 0, 0)
                setTextColor(context.getThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                setTextIsSelectable(true)
            })
        }
    }

    /** 自定义 View 弹窗 */
    fun custom(
        context: Context,
        title: String,
        view: View,
        okText: String = context.getString(R.string.common_ok),
        cancelText: String? = context.getString(R.string.common_cancel),
        onOk: (AlertDialog) -> Unit
    ): AlertDialog {
        val builder = MaterialAlertDialogBuilder(context).setTitle(title).setView(view)
        builder.setPositiveButton(okText) { d, _ -> onOk(d as AlertDialog) }
        if (cancelText != null) builder.setNegativeButton(cancelText, null)
        return builder.show()
    }

    private fun Context.getThemeColor(attr: Int): Int {
        val tv = android.util.TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) androidx.core.content.ContextCompat.getColor(this, tv.resourceId) else tv.data
    }
}
