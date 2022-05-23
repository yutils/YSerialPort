package com.yujing.chuankou.activity.myTest;

import com.yujing.chuankou.R;
import com.yujing.chuankou.base.KBaseActivity;
import com.yujing.chuankou.config.Config;
import com.yujing.chuankou.databinding.ActivitySendBinding;
import com.yujing.chuankou.utils.Setting;
import com.yujing.utils.YConvert;
import com.yujing.utils.YLog;
import com.yujing.utils.YShared;
import com.yujing.utils.YToast;
import com.yujing.yserialport.YSerialPort;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 同步发送
 *
 * @author yujing 2021年7月27日10:57:10
 */
public class SyncActivity extends KBaseActivity<ActivitySendBinding> {

    final String SEND_STRING = "SEND_STRING";
    final String SEND_HEX = "SEND_HEX";
    String device = null;
    String baudRate = null;
    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("[HH:mm:ss.SSS]", Locale.getDefault());

    public SyncActivity() {
        super(R.layout.activity_send);
    }

    @Override
    protected void init() {
        //YReadInputStream.setShowLog(true);
        //非阻塞读取线程，轮询不休息，将增加cpu消耗
        //YReadInputStream.setSleep(false);
        binding.tvTitle.setText("同步发送数据");
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
        //串口波特率
        device = Config.getDevice();
        baudRate = Config.getBaudRate();
        //设置
        Setting.setting(this, binding.includeSet, () -> {
            if (Config.getDevice() != null && Config.getBaudRate() != null) {
                device = Config.getDevice();
                baudRate = Config.getBaudRate();
            }
            binding.tvResult.setText("");
            binding.tvSend.setText("");
        });
    }

    private void sendHexString() {
        if (device == null || baudRate == null) {
            YToast.show("请先配置串口！");
            return;
        }
        String str = binding.etHex.getText().toString().replaceAll("\n", "").replace(" ", "");
        if (str.isEmpty()) {
            YToast.show("未输入内容！");
            return;
        }
        //去空格后
        binding.etHex.setText(str);

        //保存数据，下次打开页面直接填写历史记录
        YShared.write(getApplicationContext(), SEND_HEX, str);

        //发送
        YLog.i("发送串口：" + device + "\t\t波特率：" + baudRate + "\t\t内容：" + str);
        new Thread(() -> {
            try {
                //最多等待500毫秒
                byte[] re = YSerialPort.sendSyncTime(device, baudRate, YConvert.hexStringToByte(str), 500);
                //至少读取100毫秒,读满10字节返回
//                byte[] re = YSerialPort.sendSyncContinuity(device, baudRate, YConvert.hexStringToByte(str), 100,10);
                //回显
                showData(re);
            } catch (Exception e) {
                //回显
                showDataFail("发送失败，原因：" + e.getMessage());
            }
        }).start();


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
        YLog.i("发送串口：" + device + "\t\t波特率：" + baudRate + "\t\t内容：" + str);
        new Thread(() -> {
            try {
                //最多等待500毫秒
                byte[] re = YSerialPort.sendSyncTime(device, baudRate, YConvert.hexStringToByte(str), 500);
                //至少读取100毫秒,读满10字节返回
                //byte[] re = YSerialPort.sendSyncContinuity(device, baudRate, YConvert.hexStringToByte(str), 100,10);
                //回显
                showData(re);
            } catch (Exception e) {
                //回显
                showDataFail("失败：" + e.getMessage());
            }
        }).start();

        //显示
        if (binding.tvSend.getText().toString().length() > 10000)
            binding.tvSend.setText(binding.tvSend.getText().toString().substring(0, 2000));
        binding.tvSend.setText(
                "STR " + simpleDateFormat.format(new Date()) + "：" + str + "\n" + binding.tvSend.getText().toString());
    }

    private void showData(byte[] bytes) {
        runOnUiThread(() -> {
            String hexString = YConvert.bytesToHexString(bytes);
            //显示
            if (binding.tvResult.getText().toString().length() > 10000)
                binding.tvResult.setText(binding.tvResult.getText().toString().substring(0, 2000));
            binding.tvResult.setText(
                    "HEX " + simpleDateFormat.format(new Date()) + "：" + hexString + "\n" + binding.tvResult.getText().toString());
        });
    }

    private void showDataFail(String fail) {
        runOnUiThread(() -> {
            //显示
            if (binding.tvResult.getText().toString().length() > 10000)
                binding.tvResult.setText(binding.tvResult.getText().toString().substring(0, 2000));
            binding.tvResult.setText(
                    "ERROR " + simpleDateFormat.format(new Date()) + "：" + fail + "\n" + binding.tvResult.getText().toString());
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
