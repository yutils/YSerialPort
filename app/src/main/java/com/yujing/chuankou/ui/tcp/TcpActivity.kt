package com.yujing.chuankou.ui.tcp

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.yujing.chuankou.App
import com.yujing.chuankou.R
import com.yujing.chuankou.core.data.Prefs
import com.yujing.chuankou.core.net.TcpBridge
import com.yujing.chuankou.core.serial.SerialManager
import com.yujing.chuankou.databinding.FragmentTcpBinding
import com.yujing.chuankou.ui.widget.Dialogs
import com.yujing.utils.YUtils
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 串口 ⇄ TCP 透传页
 */
class TcpActivity : AppCompatActivity(), TcpBridge.Listener {

    private lateinit var b: FragmentTcpBinding
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var mode = 0  // 0 服务端 / 1 客户端

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = FragmentTcpBinding.inflate(layoutInflater)
        setContentView(b.root)
        runCatching { YUtils.setFullScreen(this, true); YUtils.setImmersive(this, true) }

        b.segMode.setOptions(
            listOf(getString(R.string.tcp_mode_server), getString(R.string.tcp_mode_client)), mode
        )
        b.segMode.onSelected = { index ->
            mode = index
            b.llServer.visibility = if (index == 0) View.VISIBLE else View.GONE
            b.llClient.visibility = if (index == 1) View.VISIBLE else View.GONE
            updateState()
        }

        b.etPort.setText(Prefs.tcpPort)
        b.etHost.setText(Prefs.tcpHost)
        b.etRemotePort.setText(Prefs.tcpRemotePort)

        b.btnStartServer.setOnClickListener { toggleServer() }
        b.btnStartClient.setOnClickListener { toggleClient() }
        b.btnClearLog.setOnClickListener { b.tvLog.text = "" }
        b.btnBack.setOnClickListener { finish() }

        b.tvLocalIp.text = getString(R.string.tcp_local_ip_format, localIp())
        appendLog(getString(R.string.tcp_intro))
        updateState()
        TcpBridge.addListener(this)
    }

    override fun onResume() {
        super.onResume()
        TcpBridge.addListener(this)
        updateState()
    }

    override fun onDestroy() {
        TcpBridge.removeListener(this)
        super.onDestroy()
    }

    // ==================================================================
    private fun toggleServer() {
        if (TcpBridge.isRunning && TcpBridge.currentMode == TcpBridge.Mode.SERVER) {
            TcpBridge.stop()
            updateState()
            return
        }
        val port = b.etPort.text.toString().toIntOrNull()
        if (port == null || port !in 1..65535) {
            Dialogs.info(this, getString(R.string.tcp_port_invalid_title), getString(R.string.tcp_port_invalid_message))
            return
        }
        Prefs.tcpPort = port.toString()
        ensureSerialOpen()
        TcpBridge.startServer(port)
        updateState()
    }

    private fun toggleClient() {
        if (TcpBridge.isRunning && TcpBridge.currentMode == TcpBridge.Mode.CLIENT) {
            TcpBridge.stop()
            updateState()
            return
        }
        val host = b.etHost.text.toString().trim()
        val port = b.etRemotePort.text.toString().toIntOrNull()
        if (host.isEmpty()) {
            Dialogs.info(this, getString(R.string.tcp_host_invalid_title), getString(R.string.tcp_host_invalid_message))
            return
        }
        if (port == null || port !in 1..65535) {
            Dialogs.info(this, getString(R.string.tcp_port_invalid_title), getString(R.string.tcp_port_invalid_message))
            return
        }
        Prefs.tcpHost = host
        Prefs.tcpRemotePort = port.toString()
        ensureSerialOpen()
        appendLog(getString(R.string.tcp_connecting, host, port))
        TcpBridge.startClient(host, port)
        updateState()
    }

    /** 透传前确保串口已打开（未打开时按已保存配置自动打开） */
    private fun ensureSerialOpen() {
        if (SerialManager.isOpen) return
        val cfg = com.yujing.chuankou.core.serial.SerialConfig.fromPrefs()
        if (cfg.device.isBlank()) {
            appendLog(getString(R.string.tcp_serial_not_configured))
            return
        }
        appendLog(getString(R.string.tcp_serial_auto_opening, cfg.summary()))
        val ok = SerialManager.open(cfg)
        appendLog(
            if (ok) getString(R.string.tcp_serial_opened, cfg.summary())
            else getString(R.string.tcp_serial_open_failed, SerialManager.lastError ?: getString(R.string.tcp_unknown_error))
        )
    }

    private fun updateState() {
        val running = TcpBridge.isRunning
        val isServerMode = TcpBridge.currentMode == TcpBridge.Mode.SERVER
        val stateText = when {
            running && isServerMode -> getString(R.string.tcp_state_running_server, Prefs.tcpPort, TcpBridge.clientCount)
            running -> getString(R.string.tcp_state_running_client, Prefs.tcpHost, Prefs.tcpRemotePort)
            else -> getString(R.string.tcp_state_stopped)
        }
        b.tvState.text = stateText
        b.tvHeaderState.text = if (running) getString(R.string.tcp_state_running) else getString(R.string.tcp_state_stopped)
        b.tvHeaderState.setBackgroundResource(if (running) R.drawable.bg_status_open else R.drawable.bg_status_closed)
        b.btnStartServer.text = if (running && isServerMode) getString(R.string.tcp_stop) else getString(R.string.tcp_start)
        b.btnStartClient.text =
            if (running && !isServerMode && TcpBridge.currentMode == TcpBridge.Mode.CLIENT) getString(R.string.tcp_disconnect)
            else getString(R.string.tcp_connect)

        if (!running) {
            b.llServer.visibility = if (mode == 0) View.VISIBLE else View.GONE
            b.llClient.visibility = if (mode == 1) View.VISIBLE else View.GONE
        }
    }

    private fun appendLog(message: String) {
        val line = "[${fmt.format(Date())}] $message\n"
        val cur = b.tvLog.text?.toString() ?: ""
        val text = if (cur.length > 20000) cur.substring(cur.length - 10000) else cur
        b.tvLog.text = text + line
        b.svLog.post { b.svLog.fullScroll(View.FOCUS_DOWN) }
    }

    private fun localIp(): String = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
            ?.hostAddress ?: getString(R.string.tcp_ip_unavailable)
    }.getOrDefault(getString(R.string.tcp_ip_unavailable))

    // ==================================================================
    override fun onLog(message: String) = appendLog(message)

    override fun onStateChanged(mode: TcpBridge.Mode, running: Boolean, clients: Int) = updateState()
}
