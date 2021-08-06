package com.yujing.chuankou.activity.wifi.socket

import com.yujing.contract.YListener
import com.yujing.contract.YListener2
import com.yujing.utils.*
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

/**
 * 服务，广播服务
 * @author yujing 2021年8月6日15:30:40
 */
/*
用法
//服务
val server = Server()

//服务，连接监听，刷新显示列表
server.connectListener = YListener {
    var s = ""
    for (item in server.socketList) {
        s += item.socket.inetAddress.hostAddress
        s += "\n"
    }
    binding.tvConnectList.text = s
}

//服务，收到数据
server.readListener = YListener2<Socket, ByteArray> { socket, byteArray ->

}

if (YCheck.isPort(wifiPort)) {
    if (server.server == null || server.server!!.isClosed) {
        server.startListener = YListener {
         //服务状态：已打开
        }
        //打开服务
        server.start(wifiPort.toInt())
    } else {
        //关闭服务
        server.close()
    }
} else {
    showSpeak("端口不正确")
}


override fun onDestroy() {
    super.onDestroy()
    server.close()
}
 */
class Server {
    val socketList: MutableList<ClientSocket> = ArrayList()

    //读取数据监听
    var readListener: YListener2<Socket, ByteArray>? = null

    //连接或者关闭监听
    var connectListener: YListener? = null

    //启动成功监听
    var startListener: YListener? = null

    //server
    var server: ServerSocket? = null

    fun start(port: Int) {
        Thread {
            YLog.d("启动服务")
            try {
                server = ServerSocket(port)
                YLog.d("启动服务成功")
                YThread.runOnUiThread { startListener?.value() }
                while (!server!!.isClosed) {
                    val socket = server!!.accept()
                    connect(socket)
                }
            } catch (e: SocketException) {
                if (e.message == "Socket closed") {
                    YLog.d("服务已关闭")
                } else {
                    YLog.e("服务已关闭", e)
                    YThread.runOnUiThread {
                        YToast.show(YApp.get(), "服务已关闭")
                        YTts.getInstance().speak("服务已关闭")
                    }
                }
            } catch (e: Exception) {
                YLog.e("启动服务失败", e)
                YThread.runOnUiThread {
                    YToast.show(YApp.get(), "启动服务失败")
                    YTts.getInstance().speak("启动服务失败")
                }
            }
        }.start()
    }

    //新增一个连接
    private fun connect(socket: Socket) {
        Thread {
            val socketClient = ClientSocket(socket)
            socketList.add(socketClient)
            socketClient.init()
            //读取监听
            socketClient.readListener = YListener2<Socket, ByteArray> { socket, data ->
                YThread.runOnUiThread {
                    readListener?.value(socket, data)
                }
            }
            //关闭监听
            socketClient.closeListener = YListener {
                check()
            }
            check()
        }.start()
    }


    //检查当前列表
    @Synchronized
    private  fun check() {
        //删除——循环
        var i = 0
        while (i < socketList.size) {
            val next=socketList[i]
            if (next.socket.isClosed) {
                next.close()
                socketList.removeAt(i)
            }
            i++
        }

        YThread.runOnUiThread {
            connectListener?.value()
        }
    }

    //发送
    fun send(bytes: ByteArray) {
        var i = 0
        while (i < socketList.size) {
            val item=socketList[i]
            item.send(bytes)
            i++
        }
    }

    //关闭
    fun close() {
        for (item in socketList) {
            item.close()
        }
        socketList.clear()
        server?.close()
        server = null
    }
}