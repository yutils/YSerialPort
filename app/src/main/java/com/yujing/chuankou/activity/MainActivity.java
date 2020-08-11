package com.yujing.chuankou.activity;

import android.app.AlertDialog;

import com.yujing.chuankou.R;
import com.yujing.chuankou.activity.myTest.MyMainActivity;
import com.yujing.chuankou.base.BaseActivity;
import com.yujing.chuankou.databinding.ActivityMainBinding;
import com.yujing.utils.YPermissions;
import com.yujing.yserialport.YReadInputStream;

/**
 * 首页
 */
public class MainActivity extends BaseActivity<ActivityMainBinding> {
    @Override
    protected Integer getContentLayoutId() {
        return R.layout.activity_main;
    }

    @Override
    protected void initData() {
        binding.ButtonQuit.setOnClickListener(v -> finish());
        binding.btnAuthor.setOnClickListener(v -> startActivity(MyMainActivity.class));
        binding.ButtonSendWords.setOnClickListener(v -> startActivity(SendActivity.class));
        binding.ButtonAbout.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("关于——余静的串口测试工具");
            builder.setMessage(R.string.about_msg);
            builder.show();
        });
        YPermissions.requestAll(this);
        YReadInputStream.setShowLog(true);
        //binding.btnAuthor.setVisibility(YUtils.isDebug(this) ? View.VISIBLE : View.GONE);
    }
}
