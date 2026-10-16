package com.yujing.chuankou.ui.command

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yujing.chuankou.R
import com.yujing.chuankou.core.data.DataType
import com.yujing.chuankou.core.data.Newline
import com.yujing.chuankou.core.data.QuickCommand
import com.yujing.chuankou.core.data.Store
import com.yujing.chuankou.core.serial.SerialManager
import com.yujing.chuankou.core.util.Charsets2
import com.yujing.chuankou.core.util.Hex
import com.yujing.chuankou.core.util.Payload
import com.yujing.chuankou.databinding.DialogCommandBinding
import com.yujing.chuankou.databinding.FragmentCommandBinding
import com.yujing.chuankou.databinding.ItemCommandBinding
import com.yujing.chuankou.ui.base.BaseFragment
import com.yujing.chuankou.ui.widget.Dialogs
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 快捷指令页
 */
class CommandFragment : BaseFragment<FragmentCommandBinding>(FragmentCommandBinding::inflate) {

    private lateinit var adapter: Adapter

    private val commandListener: () -> Unit = { refresh() }

    override fun onInit(view: View, savedInstanceState: Bundle?) {
        adapter = Adapter()
        binding.rv.layoutManager = GridLayoutManager(requireContext(), if (isLandscape) 2 else 1)
        binding.rv.adapter = adapter

        binding.btnAdd.setOnClickListener { editCommand(null) }
        binding.fabAdd.setOnClickListener { editCommand(null) }

        Store.addCommandListener(commandListener)
        refresh()
    }

    override fun onRelease() {
        Store.removeCommandListener(commandListener)
    }

    override fun onPageResume() {
        refresh()
    }

    private fun refresh() {
        if (view == null) return
        adapter.notifyDataSetChanged()
        val n = Store.commands.size
        binding.tvCount.text = "共 $n 条 · 点击「发送」即可一键下发"
        binding.llEmpty.visibility = if (n == 0) View.VISIBLE else View.GONE
        binding.rv.visibility = if (n == 0) View.GONE else View.VISIBLE
    }

    // ==================================================================
    private fun sendCommand(c: QuickCommand) {
        if (!SerialManager.isOpen) {
            snack("串口未打开，请先打开串口")
            return
        }
        val bytes = Payload.build(c.content, c.type, c.charset, c.appendNewline, c.escape)
        if (bytes.isEmpty()) {
            snack("指令内容为空或格式不正确")
            return
        }
        SerialManager.send(bytes, c.type, "快捷指令${if (c.name.isBlank()) "" else "：${c.name}"}")
        snack("已发送 ${bytes.size} 字节")
    }

    private fun showMenu(c: QuickCommand, position: Int) {
        val options = listOf("编辑", "复制一份", "上移", "下移", "删除")
        Dialogs.singleChoice(requireContext(), c.name.ifBlank { "未命名指令" }, options) { which ->
            when (which) {
                0 -> editCommand(c)
                1 -> Store.addCommand(c.copy(id = System.currentTimeMillis() * 1000 + (Math.random() * 1000).toLong(), name = c.name + " 副本"))
                2 -> Store.moveCommand(position, position - 1)
                3 -> Store.moveCommand(position, position + 1)
                4 -> Dialogs.confirm(requireContext(), "删除指令", "确认删除「${c.name}」？", "删除", true) {
                    Store.removeCommand(c)
                }
            }
        }
    }

    // ==================================================================
    private fun editCommand(origin: QuickCommand?) {
        val db = DialogCommandBinding.inflate(layoutInflater)
        val editing = origin ?: QuickCommand()

        var type = editing.type.takeIf { it == DataType.ASCII || it == DataType.HEX } ?: DataType.HEX
        var charset = editing.charset
        var newline = editing.appendNewline
        var escape = editing.escape

        db.etName.setText(editing.name)
        db.etContent.setText(editing.content)

        fun applyType() {
            db.llContentAscii.visibility = if (type == DataType.ASCII) View.VISIBLE else View.GONE
            db.etContent.hint = if (type == DataType.HEX) "HEX 例：01 03 00 00 00 0A C5 CD" else "文本内容，例：AT+RESET"
            db.chipCharset.text = Charsets2.displayOf(charset)
        }

        db.segType.setOptions(listOf("HEX", "ASCII"), if (type == DataType.HEX) 0 else 1)
        db.segType.onSelected = { index ->
            type = if (index == 0) DataType.HEX else DataType.ASCII
            applyType()
        }

        db.chipCharset.setOnClickListener {
            val names = Charsets2.SUPPORTED.map { it.first }
            val current = names.indexOfFirst { Charsets2.byDisplay(it) == charset }
            Dialogs.singleChoice(requireContext(), "字符集", names, current) { which ->
                charset = Charsets2.SUPPORTED[which].second
                db.chipCharset.text = Charsets2.displayOf(charset)
            }
        }

        db.chipNewline.text = "结束符: " + Newline.NAMES.getOrElse(newline) { "无" }
        db.chipNewline.setOnClickListener {
            Dialogs.singleChoice(requireContext(), "结束符", Newline.NAMES, newline) { which ->
                newline = which
                db.chipNewline.text = "结束符: " + Newline.NAMES[which]
            }
        }

        db.chipEscape.isSelected = escape
        db.chipEscape.setOnClickListener {
            escape = !escape
            db.chipEscape.isSelected = escape
            snack(if (escape) "发送时将解析 \\r \\n \\xNN 等转义" else "已关闭转义解析")
        }

        applyType()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (origin == null) "新增快捷指令" else "编辑快捷指令")
            .setView(db.root)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .create()
        dialog.show()
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = db.etName.text.toString().trim()
            val content = db.etContent.text.toString().trim()
            if (content.isEmpty()) {
                snack("请填写指令内容")
                return@setOnClickListener
            }
            if (type == DataType.HEX && Hex.parse(content) == null) {
                snack("HEX 格式不正确，请检查是否含非法字符")
                return@setOnClickListener
            }
            editing.name = name.ifBlank { if (type == DataType.HEX) "HEX 指令" else "文本指令" }
            editing.type = type
            editing.content = content
            editing.charset = charset
            editing.appendNewline = newline
            editing.escape = escape
            if (origin == null) Store.addCommand(editing) else Store.updateCommand(editing)
            dialog.dismiss()
            snack("已保存")
        }
    }

    // ==================================================================
    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        inner class VH(val b: ItemCommandBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemCommandBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = Store.commands.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val c = Store.commands[position]
            val b = holder.b
            b.tvName.text = c.name.ifBlank { "未命名" }
            b.tvType.text = if (c.type == DataType.HEX) "HEX" else "ASCII"
            b.vDot.background.setTint(
                androidx.core.content.ContextCompat.getColor(
                    requireContext(),
                    if (c.enabled) R.color.c_success else R.color.c_text_disabled
                )
            )
            b.tvContent.text = Payload.preview(c.content, c.type, c.charset, c.appendNewline, c.escape)
                .ifBlank { c.content }
            b.btnSend.setOnClickListener { sendCommand(c) }
            b.btnMore.setOnClickListener { showMenu(c, holder.bindingAdapterPosition) }
            b.root.setOnClickListener {
                Dialogs.singleChoice(
                    requireContext(), c.name.ifBlank { "指令" },
                    listOf("一键发送", "填入发送框", "编辑", "删除")
                ) { which ->
                    when (which) {
                        0 -> sendCommand(c)
                        1 -> {
                            com.yujing.chuankou.core.data.SendBridge.fill(c.content, c.type)
                            (activity as? com.yujing.chuankou.ui.MainActivity)?.selectPage("terminal")
                        }
                        2 -> editCommand(c)
                        3 -> Dialogs.confirm(requireContext(), "删除指令", "确认删除「${c.name}」？", "删除", true) {
                            Store.removeCommand(c)
                        }
                    }
                }
            }
        }
    }
}
