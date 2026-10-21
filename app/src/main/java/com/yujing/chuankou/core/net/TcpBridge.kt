package com.yujing.chuankou.core.net

import android.os.Handler
import android.os.Looper
import com.yujing.chuankou.R
import com.yujing.chuankou.core.data.DataType
import com.yujing.chuankou.core.serial.SerialManager
import com.yujing.chuankou.core.util.Langs
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * 串口 <-> TCP 透传桥
 *
 * 支持两种模式：
 *  - 服务端：监听端口，多客户端接入，串口数据广播给所有客户端
 *  - 客户端：连接远端，串口数据转发到远端
 *
 * 注意：Android 禁止在主线程进行网络读写（NetworkOnMainThreadException），
 *      因此所有 socket 操作都放在独立线程执行。
 */
object TcpBridge {

    enum class Mode { NONE, SERVER, CLIENT }

    interface Listener {
        fun onLog(message: String)
        fun onStateChanged(mode: Mode, running: Boolean, clients: Int)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<Listener>()

    /** 监听/连接线程（可能长期阻塞在 accept / read） */
    private val serverExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "tcp-server").apply { isDaemon = true }
    }

    /** 出站写入线程（串口 → TCP 方向） */
    private val writeExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "tcp-write").apply { isDaemon = true }
    }

    @Volatile private var mode: Mode = Mode.NONE
    @Volatile private var running = false
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var clientSocket: Socket? = null

    private val clients: MutableList<Client> = Collections.synchronizedList(mutableListOf())

    private val serialListener = object : SerialManager.Listener {
        override fun onReceive(bytes: ByteArray, ascii: String, time: Long) {
            broadcast(bytes)
        }
    }

    fun addListener(l: Listener) {
        if (!listeners.contains(l)) listeners.add(l)
    }

    fun removeListener(l: Listener) {
        listeners.remove(l)
    }

    val currentMode: Mode get() = mode
    val isRunning: Boolean get() = running
    val clientCount: Int get() = clients.size

    // ==================================================================
    // 服务端
    // ==================================================================
    fun startServer(port: Int) {
        stop()
        mode = Mode.SERVER
        serverExecutor.execute {
            try {
                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress(port))
                serverSocket = ss
                SerialManager.addListener(serialListener)
                running = true
                log(Langs.str(R.string.tcp_log_server_started, port))
                notifyState()
                acceptLoop(ss)
            } catch (e: Exception) {
                running = false
                mode = Mode.NONE
                SerialManager.removeListener(serialListener)
                log(Langs.str(R.string.tcp_log_start_failed, e.javaClass.simpleName, e.message ?: ""))
                notifyState()
            }
        }
    }

    private fun acceptLoop(ss: ServerSocket) {
        while (running && !ss.isClosed) {
            val socket = try {
                ss.accept()
            } catch (e: Exception) {
                if (running) log(Langs.str(R.string.tcp_log_accept_ended, e.message ?: e.javaClass.simpleName))
                break
            }
            try {
                socket.tcpNoDelay = true
                val c = Client(socket)
                clients.add(c)
                log(Langs.str(R.string.tcp_log_client_joined, socket.inetAddress?.hostAddress ?: "", socket.port, clients.size))
                notifyState()
                Thread({ readLoop(c) }, "tcp-client-${socket.port}").apply { isDaemon = true; start() }
            } catch (e: Exception) {
                log(Langs.str(R.string.tcp_log_client_init_failed, e.message ?: e.javaClass.simpleName))
                runCatching { socket.close() }
            }
        }
    }

    private fun readLoop(c: Client) {
        val buf = ByteArray(4096)
        try {
            while (running && !c.closed) {
                val n = c.input.read(buf)
                if (n < 0) break
                if (n == 0) continue
                val data = buf.copyOf(n)
                val ok = SerialManager.send(data, DataType.HEX, Langs.str(R.string.tcp_note), fromUser = false)
                logData(
                    if (ok) Langs.str(R.string.tcp_log_tcp_to_serial_ok, data.size)
                    else Langs.str(R.string.tcp_log_tcp_to_serial_failed, data.size)
                )
            }
        } catch (e: Exception) {
            if (running && !c.closed) log(Langs.str(R.string.tcp_log_read_ended, e.message ?: e.javaClass.simpleName))
        } finally {
            closeClient(c)
        }
    }

    // ==================================================================
    // 客户端
    // ==================================================================
    fun startClient(host: String, port: Int) {
        stop()
        mode = Mode.CLIENT
        serverExecutor.execute {
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(host, port), 5000)
                clientSocket = socket
                SerialManager.addListener(serialListener)
                running = true
                log(Langs.str(R.string.tcp_log_connected, host, port))
                notifyState()
                val buf = ByteArray(4096)
                val ins = socket.getInputStream()
                while (running && !socket.isClosed) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    if (n == 0) continue
                    val data = buf.copyOf(n)
                    val ok = SerialManager.send(data, DataType.HEX, Langs.str(R.string.tcp_note), fromUser = false)
                    logData(
                        if (ok) Langs.str(R.string.tcp_log_tcp_to_serial_ok, data.size)
                        else Langs.str(R.string.tcp_log_tcp_to_serial_failed, data.size)
                    )
                }
                if (running) log(Langs.str(R.string.tcp_log_remote_disconnected))
            } catch (e: Exception) {
                if (running) log(Langs.str(R.string.tcp_log_connect_ended, e.javaClass.simpleName, e.message ?: ""))
            } finally {
                runCatching { socket?.close() }
                if (running) {
                    running = false
                    SerialManager.removeListener(serialListener)
                    mode = Mode.NONE
                    notifyState()
                }
            }
        }
    }

    // ==================================================================
    // 停止
    // ==================================================================
    fun stop() {
        val wasActive = running || mode != Mode.NONE
        running = false
        SerialManager.removeListener(serialListener)
        runCatching { serverSocket?.close() }
        serverSocket = null
        runCatching { clientSocket?.close() }
        clientSocket = null
        clients.toList().forEach { c -> runCatching { c.socket.close() } }
        clients.clear()
        mode = Mode.NONE
        if (wasActive) log(Langs.str(R.string.tcp_log_stopped))
        notifyState()
    }

    // ==================================================================
    // 串口 → TCP（在写线程执行，避免主线程网络异常）
    // ==================================================================
    private fun broadcast(bytes: ByteArray) {
        if (bytes.isEmpty() || !running) return
        val currentMode = mode
        if (currentMode == Mode.NONE) return
        val payload = bytes.copyOf()
        writeExecutor.execute {
            if (!running) return@execute
            when (currentMode) {
                Mode.SERVER -> {
                    var sent = 0
                    clients.toList().forEach { c ->
                        if (c.closed) return@forEach
                        try {
                            c.output.write(payload)
                            c.output.flush()
                            sent++
                        } catch (e: Exception) {
                            log(Langs.str(R.string.tcp_log_write_client_failed, e.javaClass.simpleName, e.message ?: ""))
                            closeClient(c)
                        }
                    }
                    if (sent > 0) logData(Langs.str(R.string.tcp_log_serial_to_tcp_clients, payload.size, sent))
                }
                Mode.CLIENT -> {
                    val s = clientSocket ?: return@execute
                    try {
                        s.getOutputStream().write(payload)
                        s.getOutputStream().flush()
                        logData(Langs.str(R.string.tcp_log_serial_to_tcp, payload.size))
                    } catch (e: Exception) {
                        log(Langs.str(R.string.tcp_log_send_failed, e.javaClass.simpleName, e.message ?: ""))
                    }
                }
                Mode.NONE -> Unit
            }
        }
    }

    private fun closeClient(c: Client) {
        if (c.closed) return
        c.closed = true
        runCatching { c.socket.close() }
        clients.remove(c)
        log(Langs.str(R.string.tcp_log_client_left, clients.size))
        notifyState()
    }

    private fun log(msg: String) {
        handler.post { listeners.toList().forEach { runCatching { it.onLog(msg) } } }
    }

    /** 透传是高频路径，每包都往主线程 post 一条日志会把消息队列压满，这里做节流 */
    private val lastDataLogMs = java.util.concurrent.atomic.AtomicLong(0L)

    /** 数据转发日志：最多每 500ms 一条 */
    private fun logData(msg: String) {
        val now = System.currentTimeMillis()
        val last = lastDataLogMs.get()
        if (now - last < DATA_LOG_INTERVAL) return
        if (lastDataLogMs.compareAndSet(last, now)) log(msg)
    }

    private fun notifyState() {
        val m = mode
        val r = running
        val n = clients.size
        handler.post { listeners.toList().forEach { runCatching { it.onStateChanged(m, r, n) } } }
    }

    private class Client(val socket: Socket) {
        val input: InputStream = socket.getInputStream()
        val output: OutputStream = socket.getOutputStream()
        @Volatile var closed = false
    }

    private const val DATA_LOG_INTERVAL = 500L
}
