package com.yujing.chuankou.ui.widget

import android.content.Context
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

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
        builder.setNegativeButton("取消", null)
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
            .setPositiveButton("确定") { _, _ -> onOk(et.text.toString()) }
            .setNegativeButton("取消", null)
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
            .setPositiveButton("确定") { _, _ -> onOk(et.text.toString()) }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 确认框 */
    fun confirm(
        context: Context,
        title: String,
        message: String,
        okText: String = "确定",
        danger: Boolean = false,
        onOk: () -> Unit
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(okText) { _, _ -> onOk() }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 信息框 */
    fun info(context: Context, title: String, message: String) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("知道了", null)
            .show()
    }

    /** 自定义 View 弹窗 */
    fun custom(
        context: Context,
        title: String,
        view: View,
        okText: String = "确定",
        cancelText: String? = "取消",
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
