
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

    @Override
    protected Integer getContentLayoutId() {
        return R.layout.activity_send;
    }

    @Override
    protected void initData() {
        //上次使用的数据
        binding.editText.setText(YSharedPreferencesUtils.get(this, SEND_STRING));
        binding.etHex.setText(YSharedPreferencesUtils.get(this, SEND_HEX));
        binding.editText.setSelection(binding.editText.getText().length());
        binding.button.setOnClickListener(v -> sendString());
        binding.btHex.setOnClickListener(v -> sendHexString());

        ySerialPort = new YSerialPort(this);
        ySerialPort.addDataListener(dataListener);
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
        binding.tvResult.setText(hexString);
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ySerialPort.onDestroy();
    }
}
