package com.yujing.chuankou.ui.history

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yujing.chuankou.R
import com.yujing.chuankou.core.data.DataType
import com.yujing.chuankou.core.data.Dir
import com.yujing.chuankou.core.data.HistoryItem
import com.yujing.chuankou.core.data.SendBridge
import com.yujing.chuankou.core.data.Store
import com.yujing.chuankou.core.util.AppFiles
import com.yujing.chuankou.databinding.FragmentHistoryBinding
import com.yujing.chuankou.databinding.ItemHistoryBinding
import com.yujing.chuankou.ui.MainActivity
import com.yujing.chuankou.ui.base.BaseFragment
import com.yujing.chuankou.ui.widget.Dialogs
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 历史记录页：收发记录检索 / 复制 / 回填 / 导出
 */
class HistoryFragment : BaseFragment<FragmentHistoryBinding>(FragmentHistoryBinding::inflate) {

    private val ui = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "history-io").apply { isDaemon = true } }

    private var filter = 0            // 0 全部 / 1 接收 / 2 发送
    private var keyword = ""
    private var visible = true
    private var refreshScheduled = false

    private val allItems = mutableListOf<HistoryItem>()
    private val shown = mutableListOf<HistoryItem>()
    private lateinit var adapter: HistoryAdapter

    private val refreshRunnable = Runnable { doRefresh() }

    private val historyListener: () -> Unit = { scheduleRefresh() }

    override fun onInit(view: View, savedInstanceState: Bundle?) {
        adapter = HistoryAdapter()
        val span = if (isLandscape) 2 else 1
        binding.rv.layoutManager = GridLayoutManager(requireContext(), span)
        binding.rv.adapter = adapter

        binding.segFilter.setOptions(listOf("全部", "接收", "发送"), 0)
        binding.segFilter.onSelected = { index ->
            filter = index
            applyFilter()
        }

        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                keyword = s?.toString()?.trim() ?: ""
                binding.btnSearchClear.visibility = if (keyword.isEmpty()) View.GONE else View.VISIBLE
                applyFilter()
            }
        })

        binding.btnSearchClear.setOnClickListener { binding.etSearch.setText("") }

        binding.btnClear.setOnClickListener {
            if (allItems.isEmpty()) {
                snack("暂无记录")
                return@setOnClickListener
            }
            Dialogs.confirm(requireContext(), "清空记录", "将删除全部 ${allItems.size} 条收发记录，且不可恢复。") {
                Store.clearHistory()
                snack("已清空")
            }
        }

        binding.btnExport.setOnClickListener { export() }

        Store.addHistoryListener(historyListener)
        doRefresh()
    }

    override fun onRelease() {
        Store.removeHistoryListener(historyListener)
        ui.removeCallbacksAndMessages(null)
    }

    override fun onPageResume() {
        visible = true
        doRefresh()
    }

    override fun onPagePause() {
        visible = false
    }

    private fun scheduleRefresh() {
        if (!visible || refreshScheduled) return
        refreshScheduled = true
        ui.postDelayed({ refreshScheduled = false; doRefresh() }, 600)
    }

    private fun doRefresh() {
        if (view == null) return
        val list = Store.historyList()
        allItems.clear()
        allItems.addAll(list.asReversed())
        applyFilter()
    }

    private fun applyFilter() {
        if (view == null) return
        shown.clear()
        val kw = keyword.lowercase(Locale.getDefault())
        allItems.forEach { item ->
            val dirOk = when (filter) {
                1 -> item.dir == Dir.RX
                2 -> item.dir == Dir.TX
                else -> true
            }
            if (!dirOk) return@forEach
            if (kw.isNotEmpty()) {
                val hit = item.text.lowercase(Locale.getDefault()).contains(kw) ||
                        item.hex.lowercase(Locale.getDefault()).contains(kw) ||
                        item.note.lowercase(Locale.getDefault()).contains(kw)
                if (!hit) return@forEach
            }
            shown.add(item)
        }
        adapter.notifyDataSetChanged()
        binding.tvCount.text = "共 ${shown.size} 条记录（总计 ${allItems.size} 条）"
        binding.llEmpty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        binding.rv.visibility = if (shown.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun export() {
        if (shown.isEmpty()) {
            snack("暂无可导出的记录")
            return
        }
        io.execute {
            val file = AppFiles.saveText(Store.appContext(), "serial_history", buildExportText())
            ui.post {
                if (file == null) snack("导出失败") else snack("已导出：${file.name}", "分享") {
                    AppFiles.share(requireContext(), file)
                }
            }
        }
    }

    private fun buildExportText(): String = Store.buildExportText(shown.asReversed())

    private fun showItemMenu(item: HistoryItem) {
        val options = listOf(
            "复制 HEX", "复制文本", "回填到发送框（HEX）", "回填到发送框（ASCII）",
            "一键发送 HEX", "删除这条记录"
        )
        Dialogs.singleChoice(requireContext(), "记录操作", options) { which ->
            when (which) {
                0 -> copyToClipboard(item.hex, "已复制 HEX")
                1 -> copyToClipboard(item.text, "已复制文本")
                2 -> {
                    SendBridge.fill(item.hex, DataType.HEX)
                    goTerminal()
                }
                3 -> {
                    SendBridge.fill(item.text, DataType.ASCII)
                    goTerminal()
                }
                4 -> {
                    SendBridge.send(item.hex, DataType.HEX)
                    goTerminal()
                }
                5 -> {
                    Store.removeHistory(item)
                    doRefresh()
                    snack("已删除")
                }
            }
        }
    }

    private fun goTerminal() {
        (activity as? MainActivity)?.selectPage("terminal")
    }

    // ==================================================================
    private inner class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.VH>() {

        inner class VH(val b: ItemHistoryBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = shown.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = shown[position]
            val b = holder.b
            b.tvDir.text = when (item.dir) {
                Dir.RX -> "接收"
                Dir.TX -> "发送"
                else -> "系统"
            }
            val dirColor = when (item.dir) {
                Dir.RX -> R.color.c_rx
                Dir.TX -> R.color.c_tx
                else -> R.color.c_info
            }
            b.tvDir.setTextColor(ContextCompat.getColor(requireContext(), dirColor))
            b.tvType.text = when (item.type) {
                DataType.ASCII -> "ASCII"
                DataType.HEX -> "HEX"
                DataType.FILE -> "FILE"
                else -> "SYS"
            }
            b.tvTime.text = Store.formatTime(item.time)
            b.tvSize.text = "${item.size} B"
            b.tvContent.text = if (item.hex.isNotBlank() && item.type != DataType.ASCII) item.hex else item.text
            b.root.setOnClickListener { showItemMenu(item) }
            b.root.setOnLongClickListener { showItemMenu(item); true }
        }
    }
}
