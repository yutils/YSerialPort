package com.yujing.chuankou.activity.myTest;

import com.yujing.chuankou.R;
import com.yujing.chuankou.base.BaseActivity;
import com.yujing.chuankou.databinding.ActivitySendBinding;
import com.yujing.chuankou.utils.Setting;
import com.yujing.utils.YConvert;
import com.yujing.utils.YLog;
import com.yujing.utils.YSharedPreferencesUtils;
import com.yujing.yserialport.YReadInputStream;
import com.yujing.yserialport.YSerialPort;

/**
 * 同步发送
 *
 * @author yujing 2021年7月27日10:57:10
 */
public class SyncActivity extends BaseActivity<ActivitySendBinding> {

    final String SEND_STRING = "SEND_STRING";
    final String SEND_HEX = "SEND_HEX";
    String device = null;
    String baudRate = null;

    public SyncActivity() {
        super(R.layout.activity_send);
    }

    @Override
    protected void init() {
        YReadInputStream.setShowLog(true);
        //非阻塞读取线程，轮询不休息，将增加cpu消耗
        YReadInputStream.setSleep(false);
        //上次使用的数据
        binding.editText.setText(YSharedPreferencesUtils.get(this, SEND_STRING));
        binding.etHex.setText(YSharedPreferencesUtils.get(this, SEND_HEX));

        binding.editText.setSelection(binding.editText.getText().toString().length());
        binding.button.setOnClickListener(v -> sendString());
        binding.btHex.setOnClickListener(v -> sendHexString());

        //串口波特率
        device = YSerialPort.readDevice(this);
        baudRate = YSerialPort.readBaudRate(this);
        //设置
        Setting.setting(this, binding.includeSet, () -> {
            if (YSerialPort.readDevice(this) != null && YSerialPort.readBaudRate(this) != null) {
                device = YSerialPort.readDevice(this);
                baudRate = YSerialPort.readBaudRate(this);
            }
            binding.tvResult.setText("");
        });
        //退出
        binding.ButtonQuit.setOnClickListener(v -> finish());
    }

    private void sendHexString() {
        if (device == null || baudRate == null) {
            show("请先配置串口！");
            return;
        }
        String str = binding.etHex.getText().toString().replaceAll("\n", "").replace(" ", "");
        if (str.isEmpty()) {
            show("未输入内容！");
            return;
        }
        binding.tvResult.setText("");
        YLog.i("发送串口：" + device + "\t\t波特率：" + baudRate + "\t\t内容：" + str);

        //发送
        new Thread(() -> {
            try {
                //至少读取500毫秒
                byte[] re = YSerialPort.sendSyncContinuity(device, baudRate, YConvert.hexStringToByte(str), 500);
                //回显
                runOnUiThread(() -> binding.tvResult.setText(YConvert.bytesToHexString(re)));
            } catch (Exception e) {
                //回显
                runOnUiThread(() -> binding.tvResult.setText("发送失败，原因：" + e.getMessage()));
            }
        }).start();

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
        YLog.i("发送串口：" + device + "\t\t波特率：" + baudRate + "\t\t内容：" + str);

        //发送
        new Thread(() -> {
            try {
                //至少读取500毫秒
                byte[] re = YSerialPort.sendSyncContinuity(device, baudRate, str.getBytes(),500,10);
                //回显
                runOnUiThread(() -> binding.tvResult.setText(YConvert.bytesToHexString(re)));
            } catch (Exception e) {
                //回显
                runOnUiThread(() -> binding.tvResult.setText("发送失败，原因：" + e.getMessage()));
            }
        }).start();
        //保存数据，下次打开页面直接填写历史记录
        YSharedPreferencesUtils.write(getApplicationContext(), SEND_STRING, str);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
