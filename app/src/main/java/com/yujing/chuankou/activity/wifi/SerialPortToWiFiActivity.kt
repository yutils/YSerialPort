package com.yujing.chuankou.activity.wifi

import android.app.AlertDialog
import android.content.DialogInterface
import android.graphics.Color
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import com.yujing.chuankou.activity.wifi.socket.Server
import com.yujing.chuankou.base.KBaseActivity
import com.yujing.chuankou.databinding.ActivitySerialportToWifiBinding
import com.yujing.chuankou.utils.Setting
import com.yujing.contract.YListener
import com.yujing.contract.YListener2
import com.yujing.utils.*
import com.yujing.yserialport.YSerialPort
import java.net.Socket
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*
import com.yujing.chuankou.R

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
        binding.editText.setText(YShared.get(this, SEND_STRING))
        binding.etHex.setText(YShared.get(this, SEND_HEX))
        binding.etHexWifi.setText(YShared.get(this, SEND_WIFI_HEX))
        binding.editText.setSelection(binding.editText.text!!.length)
        binding.tvPort.text = "端口：$wifiPort"
        //按钮,退出
        binding.rlBack.setOnClickListener { finish() }
        //按钮,发送hex
        binding.btHex.setOnClickListener { sendHexSerialPort() }
        //按钮,发送文本
        binding.button.setOnClickListener { sendStringSerialPort() }
        //按钮,发送hex，wifi
        binding.btHexWifi.setOnClickListener { sendHexWiFi() }
        //设置server端口
        binding.llWifiSet.setOnClickListener { wifiSetting() }
        //打开server
        binding.llWifiOpen.setOnClickListener { wifiOpen() }
        //清屏
        binding.llClearSerialPortResult.setOnClickListener { binding.tvResult.text = "" }
        binding.llClearSerialPortSend.setOnClickListener { binding.tvSend.text = "" }
        binding.llClearWifiResult.setOnClickListener { binding.tvResultWifi.text = "" }
        binding.llClearWifiSend.setOnClickListener { binding.tvSendWifi.text = "" }
        //显示本机ip地址
        binding.tvIp.isSelected = true
        binding.tvIp.text = "本机IP：" + YUtils.getIPv4().toTypedArray().contentToString()
        //设置
        Setting.setting(this, binding.includeSet) {
            if (YSerialPort.readDevice(this) != null && YSerialPort.readBaudRate(this) != null)
                ySerialPort?.reStart(
                    YSerialPort.readDevice(this),
                    YSerialPort.readBaudRate(this)
                )
            binding.tvResult.text = ""
            binding.tvSend.text= ""
        }
        //初始化串口
        ySerialPort = YSerialPort(this, YSerialPort.readDevice(this), YSerialPort.readBaudRate(this));

        //自定义组包
        ySerialPort?.setInputStreamReadListener { inputStream ->
            var count = 0
            while (count == 0) count = inputStream.available() //获取真正长度
            val bytes = ByteArray(count)
            var readCount = 0 // 已经成功读取的字节的个数
            while (readCount < count) readCount += inputStream.read(
                bytes, readCount, count - readCount
            )
            return@setInputStreamReadListener bytes
        }

        //收到串口数据
        ySerialPort?.addDataListener { hexString: String, byteArray: ByteArray ->
            //转发给WiFi
            Thread { sendHexWiFi(byteArray) }.start()
            //显示
            if (binding.tvResult.text.toString().length > 10000)
                binding.tvResult.text = binding.tvResult.text.toString().substring(0, 2000)
            binding.tvResult.text =
                "HEX ${simpleDateFormat.format(Date())}：${YConvert.bytesToHexString(byteArray)}\n${binding.tvResult.text}"
        }
        ySerialPort?.start()


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
            //转发给串口
            Thread { sendHexSerialPort(byteArray) }.start()
            //显示
            if (binding.tvResultWifi.text.toString().length > 10000)
                binding.tvResultWifi.text =
                    binding.tvResultWifi.text.toString().substring(0, 2000)
            binding.tvResultWifi.text =
                "HEX ${simpleDateFormat.format(Date())}：${YConvert.bytesToHexString(byteArray)}\n${binding.tvResultWifi.text}"
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
            showSpeak("端口不正确")
        }
    }

    //设置wifi串口
    private fun wifiSetting() {
        if (server.server != null && !server.server!!.isClosed) {
            YToast.show("请先关闭服务")
            return
        }
        val editText = EditText(this)
        editText.setPadding(
            YScreenUtil.dp2px(YApp.get(), 10F),
            YScreenUtil.dp2px(YApp.get(), 10F),
            YScreenUtil.dp2px(YApp.get(), 10F),
            YScreenUtil.dp2px(YApp.get(), 10F)
        )
        editText.inputType = EditorInfo.TYPE_CLASS_NUMBER
        editText.maxLines = 1
        editText.maxLines = 5
        editText.setBackgroundColor(Color.parseColor("#EEEEEE"))
        editText.setText(wifiPort)
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setIcon(android.R.drawable.ic_menu_info_details)
        builder.setCancelable(true)
        builder.setTitle("请输入端口号")
        builder.setMessage("\n提示：端口 应该在1-65535之间")
        builder.setView(editText)
        builder.setPositiveButton("确定", null)
        builder.setNegativeButton("取消") { dialogInterface, which -> dialogInterface.dismiss() }
        val alertDialog: AlertDialog = builder.create()
        alertDialog.show()

        val button = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE)
        button.setOnClickListener {
            alertDialog.dismiss()
            val str = editText.text.toString().trim()
            if (YCheck.isPort(str)) {
                if (str != wifiPort) {
                    wifiPort = str
                    binding.tvPort.text = "端口：$wifiPort"
                }
            } else {
                showSpeak("端口不正确")
            }
        }
    }

    //发送16进制给串口
    private fun sendHexSerialPort() {
        val str: String = binding.etHex.text.toString().replace("\n", "").replace(" ", "")
        if (str.isEmpty()) {
            showSpeak("未输入内容")
            return
        }
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
        runOnUiThread {
            //显示
            if (binding.tvSend.text.toString().length > 10000)
                binding.tvSend.text = binding.tvSend.text.toString().substring(0, 2000)
            binding.tvSend.text =
                "HEX ${simpleDateFormat.format(Date())}：${YConvert.bytesToHexString(byteArray)}\n${binding.tvSend.text}"
        }
    }

    //发送文本进制给串口
    private fun sendStringSerialPort() {
        val str = binding.editText.text.toString()
        if (str.isEmpty()) {
            showSpeak("未输入内容")
            return
        }

        //保存数据，下次打开页面直接填写历史记录
        YShared.write(applicationContext, SEND_STRING, str)

        ySerialPort?.send(
            str.toByteArray(Charset.forName("GB18030"))
        ) { value: Boolean? ->
            if (!value!!) showSpeak("串口异常")
        }

        //显示
        if (binding.tvSend.text.toString().length > 10000)
            binding.tvSend.text = binding.tvSend.text.toString().substring(0, 2000)
        binding.tvSend.text =
            "STR ${simpleDateFormat.format(Date())}：$str\n${binding.tvSend.text}"
    }

    //发送16进制给wifi
    private fun sendHexWiFi() {
        if (server.socketList.isEmpty()) {
            showSpeak("没有已连接的设备")
            return
        }
        val str: String = binding.etHexWifi.text.toString().replace("\n", "").replace(" ", "")
        if (str.isEmpty()) {
            showSpeak("未输入内容")
            return
        }
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
            runOnUiThread {
                //显示
                if (binding.tvSendWifi.text.toString().length > 10000)
                    binding.tvSendWifi.text = binding.tvSendWifi.text.toString().substring(0, 2000)
                binding.tvSendWifi.text =
                    "HEX ${simpleDateFormat.format(Date())}：${YConvert.bytesToHexString(byteArray)}\n${binding.tvSendWifi.text}"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ySerialPort?.onDestroy()
        server.close()
    }
}
