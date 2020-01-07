
package com.yujing.chuankou;

import com.yujing.chuankou.databinding.SendingWordsBinding;
import com.yujing.utils.YConvert;
import com.yujing.utils.YSharedPreferencesUtils;
import com.yujing.utils.YToast;
import com.yujing.yserialport.YSerialPort;

import java.nio.charset.Charset;

/**
 * @author yujing
 * 2019年12月12日09:54:04
 */
public class SendWordsActivity extends BaseActivity<SendingWordsBinding> {
    YSerialPort ySerialPort;
    final String SEND_STRING = "SEND_STRING";
    final String SEND_HEX = "SEND_HEX";

    @Override
    protected Integer getContentLayoutId() {
        return R.layout.sending_words;
    }

    private void sendString() {
        binding.tvResult.setText("");
        ySerialPort.clearDataListener();
        ySerialPort.addDataListener(dataListener);
        String str = binding.editText.getText().toString();
        if (str.isEmpty()) {
            show("未输入内容！");
            return;
        }
        ySerialPort.send(str.getBytes(Charset.forName("GB18030")), value -> {
            if (!value) YToast.show(getApplicationContext(), "串口异常");
        });
        //保存数据
        YSharedPreferencesUtils.write(getApplicationContext(), SEND_STRING, str);
    }

    private void sendHexString() {
        binding.tvResult.setText("");
        ySerialPort.clearDataListener();
        ySerialPort.addDataListener(dataListener);
        String str = binding.etHex.getText().toString().replaceAll("\n", "");
        binding.etHex.setText(str);
        if (str.isEmpty()) {
            show("未输入内容！");
            return;
        }
            ySerialPort.send(YConvert.hexStringToByte(str));
            //保存数据
            YSharedPreferencesUtils.write(getApplicationContext(), SEND_HEX, str);
    }

    YSerialPort.DataListener dataListener = (hexString, bytes, size) -> {
        binding.tvResult.setText(hexString);
    };

    @Override
    protected void initData() {
        //上次使用的数据
        binding.editText.setText(YSharedPreferencesUtils.get(this, SEND_STRING));
        binding.etHex.setText(YSharedPreferencesUtils.get(this, SEND_HEX));
        binding.editText.setSelection(binding.editText.getText().length());

        ySerialPort = new YSerialPort(this);
        ySerialPort.clearDataListener();
        ySerialPort.addDataListener(dataListener);
        ySerialPort.start();
        binding.button.setOnClickListener(v -> sendString());
        binding.btHex.setOnClickListener(v -> sendHexString());
        binding.tvTips.setText("注意：当前串口：" + YSerialPort.readDevice(this) + "，当前波特率：" + YSerialPort.readBaudRate(this));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ySerialPort.onDestroy();
    }
}
