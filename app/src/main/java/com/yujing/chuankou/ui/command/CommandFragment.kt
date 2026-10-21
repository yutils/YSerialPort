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
        binding.tvCount.text = getString(R.string.command_count_format, n)
        binding.llEmpty.visibility = if (n == 0) View.VISIBLE else View.GONE
        binding.rv.visibility = if (n == 0) View.GONE else View.VISIBLE
    }

    // ==================================================================
    private fun sendCommand(c: QuickCommand) {
        if (!SerialManager.isOpen) {
            snack(getString(R.string.command_serial_not_open))
            return
        }
        val bytes = Payload.build(c.content, c.type, c.charset, c.appendNewline, c.escape)
        if (bytes.isEmpty()) {
            snack(getString(R.string.command_content_invalid))
            return
        }
        val note = if (c.name.isBlank()) {
            getString(R.string.command_note)
        } else {
            getString(R.string.command_note_name, c.name)
        }
        SerialManager.send(bytes, c.type, note)
        snack(getString(R.string.command_sent_bytes, bytes.size))
    }

    private fun showMenu(c: QuickCommand, position: Int) {
        val options = listOf(
            getString(R.string.command_menu_edit),
            getString(R.string.command_menu_duplicate),
            getString(R.string.command_menu_move_up),
            getString(R.string.command_menu_move_down),
            getString(R.string.command_delete)
        )
        Dialogs.singleChoice(requireContext(), c.name.ifBlank { getString(R.string.command_unnamed) }, options) { which ->
            when (which) {
                0 -> editCommand(c)
                1 -> Store.addCommand(c.copy(id = System.currentTimeMillis() * 1000 + (Math.random() * 1000).toLong(), name = getString(R.string.command_copy_format, c.name)))
                2 -> Store.moveCommand(position, position - 1)
                3 -> Store.moveCommand(position, position + 1)
                4 -> Dialogs.confirm(requireContext(), getString(R.string.command_delete_title), getString(R.string.command_delete_confirm, c.name), getString(R.string.command_delete), true) {
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
            db.etContent.hint = if (type == DataType.HEX) getString(R.string.command_hint_hex) else getString(R.string.command_hint_ascii)
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
            Dialogs.singleChoice(requireContext(), getString(R.string.settings_charset), names, current) { which ->
                charset = Charsets2.SUPPORTED[which].second
                db.chipCharset.text = Charsets2.displayOf(charset)
            }
        }

        db.chipNewline.text = getString(R.string.command_newline_chip, Newline.name(newline))
        db.chipNewline.setOnClickListener {
            Dialogs.singleChoice(requireContext(), getString(R.string.command_newline_title), Newline.names(), newline) { which ->
                newline = which
                db.chipNewline.text = getString(R.string.command_newline_chip, Newline.name(which))
            }
        }

        db.chipEscape.isSelected = escape
        db.chipEscape.setOnClickListener {
            escape = !escape
            db.chipEscape.isSelected = escape
            snack(if (escape) getString(R.string.command_escape_on) else getString(R.string.command_escape_off))
        }

        applyType()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (origin == null) getString(R.string.command_add_title) else getString(R.string.command_edit_title))
            .setView(db.root)
            .setPositiveButton(getString(R.string.command_save), null)
            .setNegativeButton(getString(R.string.common_cancel), null)
            .create()
        dialog.show()
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = db.etName.text.toString().trim()
            val content = db.etContent.text.toString().trim()
            if (content.isEmpty()) {
                snack(getString(R.string.command_error_content_empty))
                return@setOnClickListener
            }
            if (type == DataType.HEX && Hex.parse(content) == null) {
                snack(getString(R.string.command_error_hex_invalid))
                return@setOnClickListener
            }
            editing.name = name.ifBlank { if (type == DataType.HEX) getString(R.string.command_default_name_hex) else getString(R.string.command_default_name_ascii) }
            editing.type = type
            editing.content = content
            editing.charset = charset
            editing.appendNewline = newline
            editing.escape = escape
            if (origin == null) Store.addCommand(editing) else Store.updateCommand(editing)
            dialog.dismiss()
            snack(getString(R.string.command_saved))
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
            b.tvName.text = c.name.ifBlank { getString(R.string.command_item_unnamed) }
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
                    requireContext(), c.name.ifBlank { getString(R.string.command_fallback_name) },
                    listOf(
                        getString(R.string.command_action_send_now),
                        getString(R.string.command_action_fill),
                        getString(R.string.command_menu_edit),
                        getString(R.string.command_delete)
                    )
                ) { which ->
                    when (which) {
                        0 -> sendCommand(c)
                        1 -> {
                            com.yujing.chuankou.core.data.SendBridge.fill(c.content, c.type)
                            (activity as? com.yujing.chuankou.ui.MainActivity)?.selectPage("terminal")
                        }
                        2 -> editCommand(c)
                        3 -> Dialogs.confirm(requireContext(), getString(R.string.command_delete_title), getString(R.string.command_delete_confirm, c.name), getString(R.string.command_delete), true) {
                            Store.removeCommand(c)
                        }
                    }
                }
            }
        }
    }
}
