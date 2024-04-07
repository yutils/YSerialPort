package com.yujing.chuankou.activity.wifi

import android.app.AlertDialog
import android.content.DialogInterface
import android.graphics.Color
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import com.yujing.chuankou.R
import com.yujing.chuankou.activity.wifi.socket.Server
import com.yujing.chuankou.base.KBaseActivity
import com.yujing.chuankou.config.Config
import com.yujing.chuankou.databinding.ActivitySerialportToWifiBinding
import com.yujing.chuankou.utils.Setting
import com.yujing.contract.YListener
import com.yujing.contract.YListener2
import com.yujing.utils.YApp
import com.yujing.utils.YCheck
import com.yujing.utils.YConvert
import com.yujing.utils.YLog
import com.yujing.utils.YSave
import com.yujing.utils.YScreenUtil
import com.yujing.utils.YShared
import com.yujing.utils.YToast
import com.yujing.utils.YUtils
import com.yujing.yserialport.YSerialPort
import java.net.Socket
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 串口转TCP（WiFi）
 * @author 2021年8月6日15:34:13
 */
class SerialPortToWiFiActivity :
    KBaseActivity<ActivitySerialportToWifiBinding>(R.layout.activity_serialport_to_wifi) {
    var ySerialPort: YSerialPort? = null
    val SEND_STRING = "SEND_STRING"
    val SEND_HEX = "SEND_HEX"
    val SEND_WIFI_HEX = "SEND_WIFI_HEX"

    //服务
    val server = Server()

    //wifiPort
    var wifiPort: String
        get() = YSave.get(YApp.get(), "wifiPort", String::class.java, "8888")
        set(value) = YSave.put(YApp.get(), "wifiPort", value)

    var simpleDateFormat = SimpleDateFormat("[HH:mm:ss.SSS]", Locale.getDefault())

    override fun init() {
        //上次使用的数据
        binding.run {
            editText.setText(YShared.get(YApp.get(), SEND_STRING))
            etHex.setText(YShared.get(YApp.get(), SEND_HEX))
            etHexWifi.setText(YShared.get(YApp.get(), SEND_WIFI_HEX))
            editText.setSelection(editText.text!!.length)
            tvPort.text = "端口：$wifiPort"
            //按钮,退出
            rlBack.setOnClickListener { finish() }
            //按钮,发送hex
            btHex.setOnClickListener { sendHexSerialPort() }
            //按钮,发送文本
            button.setOnClickListener { sendStringSerialPort() }
            //按钮,发送hex，wifi
            btHexWifi.setOnClickListener { sendHexWiFi() }
            //设置server端口
            llWifiSet.setOnClickListener { wifiSetting() }
            //打开server
            llWifiOpen.setOnClickListener { wifiOpen() }
            //清屏
            llClearSerialPortResult.setOnClickListener { tvResult.text = "" }
            llClearSerialPortSend.setOnClickListener { tvSend.text = "" }
            llClearWifiResult.setOnClickListener { tvResultWifi.text = "" }
            llClearWifiSend.setOnClickListener { tvSendWifi.text = "" }
            //显示本机ip地址
            tvIp.isSelected = true
            tvIp.text = "本机IP：" + YUtils.getIPv4().toTypedArray().contentToString()
        }

        //初始化串口
        ySerialPort = YSerialPort(this, Config.device, Config.baudRate).apply {
            //自定义组包
            setInputStreamReadListener { inputStream ->
                var count = 0
                while (count == 0) count = inputStream.available() //获取真正长度
                val bytes = ByteArray(count)
                var readCount = 0 // 已经成功读取的字节的个数
                while (readCount < count) readCount += inputStream.read(bytes, readCount, count - readCount)
                return@setInputStreamReadListener bytes
            }
            //收到串口数据
            addDataListener { hexString: String, byteArray: ByteArray ->
                //转发给WiFi
                Thread { sendHexWiFi(byteArray) }.start()
                showByteArray(binding.tvResult, byteArray)
            }
            start()
        }

        //设置
        Setting.setting(this, binding.includeSet) {
            if (Config.device != null && Config.baudRate != null) ySerialPort?.reStart(Config.device, Config.baudRate)
            binding.tvResult.text = ""
            binding.tvSend.text = ""
        }

        //设置TCP服务
        server.run {
            //服务，连接监听，刷新显示列表
            connectListener = YListener {
                var s = ""
                for (item in socketList) s += item.socket.inetAddress.hostAddress + "\n"
                binding.tvConnectList.text = s
            }

            //服务，收到数据
            readListener = YListener2<Socket, ByteArray> { socket, byteArray ->
                //转发给串口
                Thread { sendHexSerialPort(byteArray) }.start()
                showByteArray(binding.tvResultWifi, byteArray)
            }
        }

        //默认打开服务
        wifiOpen()
    }

    //打开wifi
    private fun wifiOpen() {
        if (YCheck.isPort(wifiPort)) {
            if (server.server == null || server.server!!.isClosed) {
                server.startListener = YListener {
                    binding.tvServerStatus.text = "服务状态：已打开"
                    binding.tvServerStatusTips.text = "单击关闭服务"
                }
                server.start(wifiPort.toInt())
            } else {
                server.close()
                binding.tvServerStatus.text = "服务状态：已关闭"
                binding.tvServerStatusTips.text = "单击打开服务"
            }
        } else {
            YToast.showSpeak("端口不正确")
        }
    }

    //设置wifi串口
    private fun wifiSetting() {
        if (server.server != null && !server.server!!.isClosed) return YToast.show("请先关闭服务")

        val editText = EditText(this).apply {
            val dp10 = YScreenUtil.dp2px(10F)
            setPadding(dp10, dp10, dp10, dp10)
            inputType = EditorInfo.TYPE_CLASS_NUMBER
            maxLines = 1
            maxLines = 5
            setBackgroundColor(Color.parseColor("#EEEEEE"))
            setText(wifiPort)
        }

        AlertDialog.Builder(this).apply {
            setIcon(android.R.drawable.ic_menu_info_details)
            setCancelable(true)
            setTitle("请输入端口号")
            setMessage("\n提示：端口 应该在1-65535之间")
            setView(editText)
            setPositiveButton("确定", null)
            setNegativeButton("取消") { dialogInterface, which -> dialogInterface.dismiss() }
        }.create().apply {
            show()
            getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                dismiss()
                val str = editText.text.toString().trim()
                if (YCheck.isPort(str)) {
                    if (str != wifiPort) {
                        wifiPort = str
                        binding.tvPort.text = "端口：$wifiPort"
                    }
                } else {
                    YToast.showSpeak("端口不正确")
                }
            }
        }
    }

    //发送16进制给串口
    private fun sendHexSerialPort() {
        val str: String = binding.etHex.text.toString().replace("\n", "").replace(" ", "")
        if (str.isEmpty()) return YToast.showSpeak("未输入内容")

        //去空格后
        binding.etHex.setText(str)

        //保存数据，下次打开页面直接填写历史记录
        YShared.write(applicationContext, SEND_HEX, str)
        YLog.d(ySerialPort?.device + " " + ySerialPort?.baudRate + " " + str)

        sendHexSerialPort(YConvert.hexStringToByte(str))
    }

    //发送给串口
    private fun sendHexSerialPort(byteArray: ByteArray) {
        ySerialPort?.send(byteArray)
        showByteArray(binding.tvSend, byteArray)
    }

    //显示 不超过2000字符
    private fun showByteArray(tv: TextView, byteArray: ByteArray) {
        runOnUiThread {
            tv.run {
                if (text.toString().length > 10000) text = text.toString().substring(0, 2000)
                text = "HEX ${simpleDateFormat.format(Date())}：${YConvert.bytesToHexString(byteArray)}\n${text}"
            }
        }
    }

    //发送文本进制给串口
    private fun sendStringSerialPort() {
        val str = binding.editText.text.toString()
        if (str.isEmpty()) return YToast.showSpeak("未输入内容")

        //保存数据，下次打开页面直接填写历史记录
        YShared.write(applicationContext, SEND_STRING, str)

        ySerialPort?.send(
            str.toByteArray(Charset.forName("GB18030"))
        ) { value: Boolean? ->
            if (!value!!) YToast.showSpeak("串口异常")
        }
        //显示
        binding.tvSend.run {
            if (text.toString().length > 10000) text = text.toString().substring(0, 2000)
            text = "STR ${simpleDateFormat.format(Date())}：$str\n${text}"
        }
    }

    //发送16进制给wifi
    private fun sendHexWiFi() {
        if (server.socketList.isEmpty()) return YToast.showSpeak("没有已连接的设备")
        val str: String = binding.etHexWifi.text.toString().replace("\n", "").replace(" ", "")
        if (str.isEmpty()) return YToast.showSpeak("未输入内容")

        //去空格后
        binding.etHexWifi.setText(str)

        //保存数据，下次打开页面直接填写历史记录
        YShared.write(applicationContext, SEND_WIFI_HEX, str)
        sendHexWiFi(YConvert.hexStringToByte(str))
    }

    //发送给wifi
    private fun sendHexWiFi(byteArray: ByteArray) {
        //有wifi连接才转发
        if (server.socketList.size > 0) {
            //转发给wifi
            server.send(byteArray)
            showByteArray(binding.tvSendWifi, byteArray)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ySerialPort?.onDestroy()
        server.close()
    }
}
