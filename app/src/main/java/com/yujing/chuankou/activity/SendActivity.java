
package com.yujing.chuankou.activity;

import com.yujing.chuankou.R;
import com.yujing.chuankou.base.BaseActivity;
import com.yujing.chuankou.databinding.ActivitySendBinding;
import com.yujing.chuankou.utils.Setting;
import com.yujing.utils.YConvert;
import com.yujing.utils.YLog;
import com.yujing.utils.YSharedPreferencesUtils;
import com.yujing.utils.YToast;
import com.yujing.yserialport.DataListener;
import com.yujing.yserialport.YSerialPort;

import java.nio.charset.Charset;

/**
 * @author yujing
 * 2020年8月11日13:15:16
 * 可以参考此类用法
 */
public class SendActivity extends BaseActivity<ActivitySendBinding> {
    YSerialPort ySerialPort;
    final String SEND_STRING = "SEND_STRING";
    final String SEND_HEX = "SEND_HEX";

    public SendActivity() {
        super(R.layout.activity_send);
    }

    @Override
    protected void init() {
        //上次使用的数据
        binding.editText.setText(YSharedPreferencesUtils.get(this, SEND_STRING));
        binding.etHex.setText(YSharedPreferencesUtils.get(this, SEND_HEX));
        binding.editText.setSelection(binding.editText.getText().length());
        binding.button.setOnClickListener(v -> sendString());
        binding.btHex.setOnClickListener(v -> sendHexString());
        //初始化串口
        ySerialPort = new YSerialPort(this);
        ySerialPort.addDataListener(dataListener);
//        //自定义组包
//        ySerialPort.setInputStreamReadListener(inputStream -> {
//            // 网络传输时候，这样获取真正长度
//            int count;
//            do {
//                count = inputStream.available();
//            } while (count == 0);
//            byte[] bytes = new byte[count];
//            // 一定要读取count个数据，如果inputStream.read(bytes);可能读不完
//            int readCount = 0; // 已经成功读取的字节的个数
//            while (readCount < count) {
//                readCount += inputStream.read(bytes, readCount, count - readCount);
//            }
//            return bytes;
//        });
        ySerialPort.start();
        //设置
        Setting.setting(this, binding.includeSet, () -> {
            if (YSerialPort.readDevice(this) != null && YSerialPort.readBaudRate(this) != null)
                ySerialPort.reStart(YSerialPort.readDevice(this), YSerialPort.readBaudRate(this));
            binding.tvResult.setText("");
        });
        //退出
        binding.ButtonQuit.setOnClickListener(v -> finish());
    }

    private void sendHexString() {
        String str = binding.etHex.getText().toString().replaceAll("\n", "").replace(" ", "");
        if (str.isEmpty()) {
            show("未输入内容！");
            return;
        }
        binding.tvResult.setText("");
        ySerialPort.clearDataListener();
        ySerialPort.addDataListener(dataListener);
        YLog.e(ySerialPort.getDevice() + " " + ySerialPort.getBaudRate() + " " + str);
        binding.etHex.setText(str);
        ySerialPort.send(YConvert.hexStringToByte(str));
        //保存数据，下次打开页面直接填写历史记录
        YSharedPreferencesUtils.write(getApplicationContext(), SEND_HEX, str);
    }

    private void sendString() {
        String str = binding.editText.getText().toString();
        if (str.isEmpty()) {
            show("未输入内容！");
            return;
        }
        binding.tvResult.setText("");
        ySerialPort.clearDataListener();
        ySerialPort.addDataListener(dataListener);
        ySerialPort.send(str.getBytes(Charset.forName("GB18030")), value -> {
            if (!value) YToast.show(getApplicationContext(), "串口异常");
        });
        //保存数据，下次打开页面直接填写历史记录
        YSharedPreferencesUtils.write(getApplicationContext(), SEND_STRING, str);
    }

    DataListener dataListener = (hexString, bytes) -> {
        binding.tvResult.setText(binding.tvResult.getText().equals("") ? hexString : binding.tvResult.getText() + "\n" + hexString);
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        ySerialPort.onDestroy();
    }
}
