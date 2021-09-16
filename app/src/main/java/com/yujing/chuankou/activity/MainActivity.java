package com.yujing.chuankou.activity;

import android.app.AlertDialog;

import com.yujing.chuankou.R;
import com.yujing.chuankou.activity.myTest.MyMainActivity;
import com.yujing.chuankou.activity.wifi.SerialPortToWiFiActivity;
import com.yujing.chuankou.base.BaseActivity;
import com.yujing.chuankou.databinding.ActivityMainBinding;
import com.yujing.utils.YPermissions;
import com.yujing.yserialport.YReadInputStream;

/**
 * 首页
 */
public class MainActivity extends BaseActivity<ActivityMainBinding> {

    public MainActivity() {
        super(R.layout.activity_main);
    }

    @Override
    protected void init() {
        YPermissions.Companion.requestAll(this);
        binding.ButtonQuit.setOnClickListener(v -> finish());
        binding.btnAuthor.setOnClickListener(v -> startActivity(MyMainActivity.class));
        binding.btnToWifi.setOnClickListener(v -> startActivity(SerialPortToWiFiActivity.class));
        binding.ButtonSendWords.setOnClickListener(v -> startActivity(SendActivity.class));
        binding.ButtonAbout.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("关于——余静的串口测试工具");
            builder.setMessage(R.string.about_msg);
            builder.show();
        });
    }
}
