package com.yujing.chuankou.activity.myTest;
import android.content.Intent;
import android.net.Uri;
import android.serialport.SerialPort;

import com.yujing.chuankou.R;
import com.yujing.chuankou.base.BaseActivity;
import com.yujing.chuankou.databinding.ActivitySendFileBinding;
import com.yujing.chuankou.utils.Setting;
import com.yujing.chuankou.utils.xmodem.Xmodem;
import com.yujing.utils.YConvert;
import com.yujing.utils.YShow;
import com.yujing.utils.YUri;
import com.yujing.yserialport.YSerialPort;

import java.io.File;

public class SendFileActivity extends BaseActivity<ActivitySendFileBinding> {
    File sendFile = null;//要发送的文件
    YSerialPort ySerialPort;
    @Override
    protected Integer getContentLayoutId() {
        return R.layout.activity_send_file;
    }

    @Override
    protected void initData() {
        //选择文件
        binding.buttonBrowse.setOnClickListener(v -> onClick());
        //发送文件
        binding.btSend.setOnClickListener(v -> sendFile());
        //发送文件Xmodem
        binding.btSendXmodem.setOnClickListener(v -> sendFileXmoden());

        ySerialPort = new YSerialPort(this);
        ySerialPort.addDataListener((hexString, bytes2, size) -> runOnUiThread(() -> binding.tvResult.setText(hexString)));
        ySerialPort.start();
        //设置
        Setting.setting(this, binding.includeSet, () -> {
            if (YSerialPort.readDevice(this) != null && YSerialPort.readBaudRate(this) != null)
                ySerialPort.reStart(YSerialPort.readDevice(this), YSerialPort.readBaudRate(this));
            binding.tvResult.setText("");
        });
        binding.ButtonQuit.setOnClickListener(v -> finish());
    }

    //直接发送文件到串口
    private void sendFile() {
        if (sendFile == null) {
            show("请先选择文件");
            return;
        }
        byte[] bytes = YConvert.fileToByte(sendFile);
        YShow.show(this, "发送中...", "进度：" + 0 + "/" + bytes.length);
        //初始化
        ySerialPort.send(bytes,
                aBoolean -> show("发送：" + (aBoolean ? "成功" : "失败")),
                integer -> {
                    YShow.setMessageOther("进度：" + integer + "/" + bytes.length);
                    if (integer == bytes.length) {
                        YShow.finish();
                        show("发送完成");
                        ySerialPort.onDestroy();
                    }
                });
        show("正在发送请稍后...");
    }

    //Xmoden发送文件到串口
    private void sendFileXmoden() {
        if (sendFile == null) {
            show("请先选择文件");
            return;
        }
        if (YSerialPort.readBaudRate(this) == null || YSerialPort.readDevice(this) == null) {
            show("请先选择串口和波特率");
            return;
        }
        try {
            SerialPort serialPort = new YSerialPort(this).buildSerialPort();
            Xmodem xmodem = new Xmodem(serialPort.getInputStream(), serialPort.getOutputStream());
            xmodem.send(sendFile.getPath(), aBoolean -> {
                serialPort.close();
                runOnUiThread(() -> show("发送" + (aBoolean ? "完成" : "失败")));
            });
            show("正在发送请稍后，可能需要很长时间...");
        } catch (Exception e) {
            show("异常");
        }
    }

    public void onClick() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");//设置类型，我这里是任意类型，任意后缀的可以这样写。
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, 1);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                Uri uri = data.getData();
                if (uri != null) {
                    String path = YUri.getPath(this, uri);
                    if (path != null) {
                        File file = new File(path);
                        if (file.exists()) {
                            sendFile = file;
                            String upLoadFilePath = file.toString();
                            String upLoadFileName = file.getName();
                            binding.FilePath.setText(upLoadFilePath);
                        }
                    }
                }
            }
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        ySerialPort.onDestroy();
    }
}
