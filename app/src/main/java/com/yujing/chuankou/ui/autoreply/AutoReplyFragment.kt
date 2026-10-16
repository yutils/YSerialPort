package com.yujing.chuankou.ui.autoreply

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.yujing.chuankou.R
import com.yujing.chuankou.core.data.AutoReplyRule
import com.yujing.chuankou.core.data.DataType
import com.yujing.chuankou.core.data.MatchMode
import com.yujing.chuankou.core.data.Newline
import com.yujing.chuankou.core.data.Prefs
import com.yujing.chuankou.core.data.Store
import com.yujing.chuankou.core.serial.AutoReplyEngine
import com.yujing.chuankou.core.util.Charsets2
import com.yujing.chuankou.core.util.Hex
import com.yujing.chuankou.core.util.Payload
import com.yujing.chuankou.databinding.DialogRuleBinding
import com.yujing.chuankou.databinding.FragmentAutoreplyBinding
import com.yujing.chuankou.databinding.ItemRuleBinding
import com.yujing.chuankou.ui.base.BaseFragment
import com.yujing.chuankou.ui.widget.Dialogs

/**
 * 自动应答页
 */
class AutoReplyFragment : BaseFragment<FragmentAutoreplyBinding>(FragmentAutoreplyBinding::inflate) {

    private lateinit var adapter: Adapter
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var refreshScheduled = false

    private val ruleListener: () -> Unit = { refresh() }
    private val hitListener: (AutoReplyRule, ByteArray) -> Unit = { _, _ -> uiRefresh() }

    override fun onInit(view: View, savedInstanceState: Bundle?) {
        adapter = Adapter()
        binding.rv.layoutManager = GridLayoutManager(requireContext(), if (isLandscape) 2 else 1)
        binding.rv.adapter = adapter

        binding.btnAdd.setOnClickListener { editRule(null) }
        binding.swMaster.isChecked = Prefs.autoReplyEnabled
        binding.swMaster.setOnCheckedChangeListener { _, checked ->
            Prefs.autoReplyEnabled = checked
            if (!checked) AutoReplyEngine.reset()
            snack(if (checked) "自动应答已启用" else "自动应答已停用")
        }

        Store.addRuleListener(ruleListener)
        AutoReplyEngine.addHitListener(hitListener)
        refresh()
    }

    override fun onRelease() {
        Store.removeRuleListener(ruleListener)
        AutoReplyEngine.removeHitListener(hitListener)
        handler.removeCallbacksAndMessages(null)
    }

    override fun onPageResume() = refresh()

    /** 命中回调限流：高频循环应答时不反复刷新列表，保证开关可点 */
    private fun uiRefresh() {
        if (view == null || refreshScheduled) return
        refreshScheduled = true
        handler.postDelayed(refreshRunnable, 400L)
    }

    private val refreshRunnable = Runnable {
        refreshScheduled = false
        if (view != null) adapter.notifyDataSetChanged()
    }

    private fun refresh() {
        if (view == null) return
        adapter.notifyDataSetChanged()
        val n = Store.rules.size
        val enabled = Store.rules.count { it.enabled }
        binding.tvCount.text = "$n 条规则 · 启用 $enabled 条 · 收到匹配数据自动回复"
        binding.swMaster.isChecked = Prefs.autoReplyEnabled
        binding.llEmpty.visibility = if (n == 0) View.VISIBLE else View.GONE
        binding.rv.visibility = if (n == 0) View.GONE else View.VISIBLE
    }

    // ==================================================================
    private fun showMenu(r: AutoReplyRule, position: Int) {
        val options = listOf(
            if (r.enabled) "停用此规则" else "启用此规则",
            "编辑", "测试应答（立即发送一次）", "复制一份", "上移", "下移", "删除"
        )
        Dialogs.singleChoice(requireContext(), r.name.ifBlank { "规则" }, options) { which ->
            when (which) {
                0 -> {
                    Store.toggleRule(r, !r.enabled)
                    AutoReplyEngine.reset()
                    snack(if (r.enabled) "已启用「${r.name}」" else "已停用「${r.name}」")
                }
                1 -> editRule(r)
                2 -> {
                    val bytes = AutoReplyEngine.buildReply(r)
                    if (bytes.isEmpty()) snack("应答内容为空或格式不正确")
                    else {
                        com.yujing.chuankou.core.serial.SerialManager.send(bytes, r.replyType, "测试应答")
                        snack("已发送测试应答 ${bytes.size} 字节")
                    }
                }
                3 -> Store.addRule(
                    r.copy(
                        id = System.currentTimeMillis() * 1000 + (Math.random() * 1000).toLong() + 2,
                        name = r.name + " 副本"
                    )
                )
                4 -> Store.moveRule(position, position - 1)
                5 -> Store.moveRule(position, position + 1)
                6 -> Dialogs.confirm(requireContext(), "删除规则", "确认删除「${r.name}」？", "删除", true) {
                    Store.removeRule(r)
                }
            }
        }
    }

    // ==================================================================
    private fun editRule(origin: AutoReplyRule?) {
        val db = DialogRuleBinding.inflate(layoutInflater)
        val rule = origin ?: AutoReplyRule()

        var matchType = rule.matchType
        var matchMode = rule.matchMode
        var replyType = rule.replyType
        var newline = rule.replyAppendNewline
        var escape = rule.replyEscape
        var once = rule.once

        db.etName.setText(rule.name)
        db.etMatch.setText(rule.matchContent)
        db.etReply.setText(rule.replyContent)
        db.etDelay.setText(rule.delayMs.toString())

        db.segMatchType.setOptions(listOf("HEX", "ASCII"), if (matchType == DataType.HEX) 0 else 1)
        db.segMatchType.onSelected = { i ->
            matchType = if (i == 0) DataType.HEX else DataType.ASCII
            db.etMatch.hint = if (matchType == DataType.HEX) "HEX 支持通配：01 03 ?? ?? 00 0A"
            else "匹配的文本内容"
        }
        db.etMatch.hint = if (matchType == DataType.HEX) "HEX 支持通配：01 03 ?? ?? 00 0A" else "匹配的文本内容"

        fun updatePreview() {
            val p = Payload.preview(
                db.etReply.text.toString(), replyType, rule.charset, newline, escape
            )
            db.tvPreview.text = if (p.isBlank()) "应答预览：（空）" else "应答预览：$p"
            db.chipNewline.text = "结束符: " + Newline.NAMES.getOrElse(newline) { "无" }
        }

        db.segReplyType.setOptions(listOf("HEX", "ASCII"), if (replyType == DataType.HEX) 0 else 1)
        db.segReplyType.onSelected = { i ->
            replyType = if (i == 0) DataType.HEX else DataType.ASCII
            updatePreview()
        }

        db.chipMatchMode.text = MatchMode.name(matchMode)
        db.chipMatchMode.setOnClickListener {
            Dialogs.singleChoice(requireContext(), "匹配模式", MatchMode.names, matchMode) { which ->
                matchMode = which
                db.chipMatchMode.text = MatchMode.name(which)
            }
        }

        db.chipNewline.setOnClickListener {
            Dialogs.singleChoice(requireContext(), "结束符", Newline.NAMES, newline) { which ->
                newline = which
                updatePreview()
            }
        }

        db.chipEscape.isSelected = escape
        db.chipEscape.setOnClickListener {
            escape = !escape
            db.chipEscape.isSelected = escape
            updatePreview()
        }

        db.chipOnce.isSelected = once
        db.chipOnce.setOnClickListener {
            once = !once
            db.chipOnce.isSelected = once
        }

        db.etReply.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) = updatePreview()
        })
        updatePreview()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (origin == null) "新增应答规则" else "编辑应答规则")
            .setView(db.root)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .create()
        dialog.show()
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val match = db.etMatch.text.toString().trim()
            val reply = db.etReply.text.toString().trim()
            if (match.isEmpty()) {
                snack("请填写匹配内容")
                return@setOnClickListener
            }
            if (reply.isEmpty()) {
                snack("请填写应答内容")
                return@setOnClickListener
            }
            if (matchType == DataType.HEX && AutoReplyEngine.parseHexPattern(match) == null) {
                snack("匹配内容 HEX 格式不正确")
                return@setOnClickListener
            }
            if (replyType == DataType.HEX && Hex.parse(reply) == null) {
                snack("应答内容 HEX 格式不正确")
                return@setOnClickListener
            }
            rule.name = db.etName.text.toString().trim().ifBlank { "应答规则" }
            rule.matchType = matchType
            rule.matchMode = matchMode
            rule.matchContent = match
            rule.replyType = replyType
            rule.replyContent = reply
            rule.replyAppendNewline = newline
            rule.replyEscape = escape
            rule.delayMs = db.etDelay.text.toString().toLongOrNull() ?: 0L
            rule.once = once
            if (origin == null) Store.addRule(rule) else Store.updateRule(rule)
            dialog.dismiss()
            snack("已保存")
        }
    }

    // ==================================================================
    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        inner class VH(val b: ItemRuleBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = Store.rules.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val r = Store.rules[position]
            val b = holder.b
            b.tvName.text = r.name.ifBlank { "未命名规则" }
            b.tvHits.text = "命中 ${r.hitCount}"
            if (b.swEnabled.isChecked != r.enabled) {
                b.swEnabled.setOnCheckedChangeListener(null)
                b.swEnabled.isChecked = r.enabled
            }
            b.swEnabled.setOnCheckedChangeListener { _, checked ->
                if (r.enabled != checked) {
                    Store.toggleRule(r, checked)
                    AutoReplyEngine.reset()
                }
            }
            b.tvMatch.text = matchDisplay(r)
            b.tvReply.text = replyDisplay(r)
            b.btnMore.setOnClickListener { showMenu(r, holder.bindingAdapterPosition) }
            b.root.setOnClickListener { editRule(r) }
        }

        private fun matchDisplay(r: AutoReplyRule): String {
            val mode = if (r.matchType == DataType.ASCII) "[${MatchMode.name(r.matchMode)}] " else ""
            return mode + r.matchContent
        }

        private fun replyDisplay(r: AutoReplyRule): String {
            val p = Payload.preview(r.replyContent, r.replyType, r.charset, r.replyAppendNewline, r.replyEscape)
            val text = p.ifBlank { r.replyContent }
            return if (r.delayMs > 0) "$text   (+${r.delayMs}ms)" else text
        }
    }
}
