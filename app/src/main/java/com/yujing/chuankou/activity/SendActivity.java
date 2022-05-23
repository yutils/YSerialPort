package com.yujing.chuankou.activity;

import com.yujing.chuankou.R;
import com.yujing.chuankou.base.KBaseActivity;
import com.yujing.chuankou.config.Config;
import com.yujing.chuankou.databinding.ActivitySendBinding;
import com.yujing.chuankou.utils.Setting;
import com.yujing.utils.YConvert;
import com.yujing.utils.YLog;
import com.yujing.utils.YShared;
import com.yujing.utils.YToast;
import com.yujing.yserialport.DataListener;
import com.yujing.yserialport.ThreadMode;
import com.yujing.yserialport.YSerialPort;

import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * @author yujing
 * 可以参考此类用法
 */
public class SendActivity extends KBaseActivity<ActivitySendBinding> {
    YSerialPort ySerialPort;
    final String SEND_STRING = "SEND_STRING";
    final String SEND_HEX = "SEND_HEX";
    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("[HH:mm:ss.SSS]", Locale.getDefault());


    public SendActivity() {
        super(R.layout.activity_send);
    }

    @Override
    protected void init() {
        //YReadInputStream.setShowLog(true);
        //非阻塞读取线程，轮询不休息，将增加cpu消耗
        //YReadInputStream.setSleep(false);
        //上次使用的数据
        binding.editText.setText(YShared.get(this, SEND_STRING));
        binding.etHex.setText(YShared.get(this, SEND_HEX));
        binding.editText.setSelection(binding.editText.getText().toString().length());
        binding.button.setOnClickListener(v -> sendString());
        binding.btHex.setOnClickListener(v -> sendHexString());
        //退出
        binding.rlBack.setOnClickListener(v -> finish());
        //清空
        binding.llClearSerialPortResult.setOnClickListener(v -> binding.tvResult.setText(""));
        binding.llClearSerialPortSend.setOnClickListener(v -> binding.tvSend.setText(""));

        //初始化串口
        ySerialPort = new YSerialPort(this, Config.getDevice(), Config.getBaudRate());
//      自定义组包
//        ySerialPort.setInputStreamReadListener(inputStream -> {
//            int count = 0;
//            while (count == 0)
//                count = inputStream.available();
//            byte[] bytes = new byte[count];
//            //readCount，已经成功读取的字节的个数，这儿需读取count个数据，不够则循环读取，如果采用inputStream.read(bytes);可能读不完
//            int readCount = 0;
//            while (readCount < count)
//                readCount += inputStream.read(bytes, readCount, count - readCount);
//            return bytes;
//        });
        //添加监听
        ySerialPort.addDataListener(dataListener);
        ySerialPort.setThreadMode(ThreadMode.MAIN);//设置回调线程为主线程
        if (Config.getDevice() != null && Config.getBaudRate() != null)
            ySerialPort.start();
        //设置
        Setting.setting(this, binding.includeSet, () -> {
            if (Config.getDevice() != null && Config.getBaudRate() != null)
                ySerialPort.reStart(Config.getDevice(), Config.getBaudRate());
            binding.tvResult.setText("");
            binding.tvSend.setText("");
        });
    }

    private void sendHexString() {
        String str = binding.etHex.getText().toString().replace("\n", "").replace(" ", "");
        if (str.isEmpty()) {
            YToast.show("未输入内容！");
            return;
        }
        //去空格后
        binding.etHex.setText(str);

        //保存数据，下次打开页面直接填写历史记录
        YShared.write(getApplicationContext(), SEND_HEX, str);

        //发送
        YLog.i("发送串口：" + ySerialPort.getDevice() + "\t\t波特率：" + ySerialPort.getBaudRate() + "\t\t内容：" + str);
        ySerialPort.send(YConvert.hexStringToByte(str));

        //显示
        if (binding.tvSend.getText().toString().length() > 10000)
            binding.tvSend.setText(binding.tvSend.getText().toString().substring(0, 2000));
        binding.tvSend.setText(
                "HEX " + simpleDateFormat.format(new Date()) + "：" + str + "\n" + binding.tvSend.getText().toString());
    }

    private void sendString() {
        String str = binding.editText.getText().toString();
        if (str.isEmpty()) {
            YToast.show("未输入内容！");
            return;
        }

        //保存数据，下次打开页面直接填写历史记录
        YShared.write(getApplicationContext(), SEND_STRING, str);

        //发送
        YLog.i("发送串口：" + ySerialPort.getDevice() + "\t\t波特率：" + ySerialPort.getBaudRate() + "\t\t内容：" + str);
        ySerialPort.send(str.getBytes(Charset.forName("GB18030")), value -> {
            if (!value) YToast.show("串口异常");
        });

        //显示
        if (binding.tvSend.getText().toString().length() > 10000)
            binding.tvSend.setText(binding.tvSend.getText().toString().substring(0, 2000));
        binding.tvSend.setText(
                "STR " + simpleDateFormat.format(new Date()) + "：" + str + "\n" + binding.tvSend.getText().toString());
    }

    DataListener dataListener = (hexString, bytes) -> {
        //显示
        if (binding.tvResult.getText().toString().length() > 10000)
            binding.tvResult.setText(binding.tvResult.getText().toString().substring(0, 2000));
        binding.tvResult.setText(
                "HEX " + simpleDateFormat.format(new Date()) + "：" + YConvert.bytesToHexString(bytes) + "\n" + binding.tvResult.getText().toString());
    };


    @Override
    public void onDestroy() {
        super.onDestroy();
        ySerialPort.onDestroy();
    }
}
