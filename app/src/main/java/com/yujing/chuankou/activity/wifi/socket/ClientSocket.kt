package com.yujing.chuankou.activity.wifi.socket

import com.yujing.contract.YListener
import com.yujing.contract.YListener1
import com.yujing.contract.YListener2
import com.yujing.utils.YConvert
import com.yujing.utils.YLog
import com.yujing.utils.YReadInputStream
import java.net.Socket

class ClientSocket(val socket: Socket) {
    //线程
    var readThread: Thread? = null

    //读取数据监听
    var readListener: YListener2<Socket, ByteArray>? = null

    //关闭监听
    var closeListener: YListener? = null

    //读取
    fun init() {
        readThread?.interrupt()
        readThread = Thread {
            while (!Thread.interrupted()) {
                try {
                    val inputStream = socket.getInputStream()
                    val bytes = YReadInputStream.readOnce(inputStream)
                    YLog.d("接收到的原始数据：" + YConvert.bytesToHexString(bytes))
                    readListener?.value(socket,bytes)
                } catch (e: Exception) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
            YLog.d("退出soket读取线程："+socket.inetAddress.hostAddress)
            closeListener?.value()
        }
        readThread?.start()
    }

    //发送
    fun send(bytes: ByteArray) {
        Thread {
            try {
                val os = socket.getOutputStream()
                os.write(bytes)
                os.flush()
            } catch (e: Exception) {
                //连接已经断开
                YLog.d("设备已断开")
                socket.close()
                close()
                closeListener?.value()
            }
        }.start()
    }

    fun close() {
        readThread?.interrupt()
        socket.close()
    }
}