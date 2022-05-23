package com.yujing.chuankou.activity.myTest;

import android.content.Intent;
import android.net.Uri;

import com.yujing.chuankou.R;
import com.yujing.chuankou.base.BaseActivity;
import com.yujing.chuankou.config.Config;
import com.yujing.chuankou.databinding.ActivitySendFileBinding;
import com.yujing.chuankou.utils.Setting;
import com.yujing.utils.YConvert;
import com.yujing.utils.YPermissions;
import com.yujing.utils.YShow;
import com.yujing.utils.YToast;
import com.yujing.utils.YUri;
import com.yujing.yserialport.YSerialPort;

import java.io.File;

public class SendFileActivity extends BaseActivity<ActivitySendFileBinding> {
    File sendFile = null;//要发送的文件
    YSerialPort ySerialPort;

    public SendFileActivity() {
        super(R.layout.activity_send_file);
    }

    @Override
    protected void init() {
        YPermissions.Companion.requestAll(this);
        //选择文件
        binding.buttonBrowse.setOnClickListener(v -> onClick());
        //发送文件
        binding.btSend.setOnClickListener(v -> sendFile());

        ySerialPort = new YSerialPort(this, Config.getDevice(), Config.getBaudRate());
        ySerialPort.addDataListener((hexString, bytes2) -> runOnUiThread(() ->
                binding.tvResult.setText(binding.tvResult.getText().equals("") ? hexString : binding.tvResult.getText() + "\n" + hexString))
        );
        ySerialPort.start();
        //设置
        Setting.setting(this, binding.includeSet, () -> {
            if (Config.getDevice() != null && Config.getBaudRate() != null)
                ySerialPort.reStart(Config.getDevice(), Config.getBaudRate());
            binding.tvResult.setText("");
        });
        binding.ButtonQuit.setOnClickListener(v -> finish());
    }

    //直接发送文件到串口
    private void sendFile() {
        if (sendFile == null) {
            YToast.show("请先选择文件");
            return;
        }
        byte[] bytes = YConvert.fileToByte(sendFile);
        YShow.show(this, "发送中...", "进度：" + 0 + "/" + bytes.length);
        //初始化
        ySerialPort.send(bytes,
                aBoolean -> YToast.show("发送：" + (aBoolean ? "成功" : "失败")),
                integer -> {
                    YShow.setMessageOther("进度：" + integer + "/" + bytes.length);
                    if (integer == bytes.length) {
                        YShow.finish();
                        YToast.show("发送完成");
                        ySerialPort.onDestroy();
                    }
                });
        YToast.show("正在发送请稍后...");
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
    public void onDestroy() {
        super.onDestroy();
        ySerialPort.onDestroy();
    }
}
