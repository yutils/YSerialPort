package com.yujing.chuankou.activity.myTest;

import com.yujing.chuankou.R;
import com.yujing.chuankou.base.BaseActivity;
import com.yujing.chuankou.databinding.ActivityMyMainBinding;

public class MyMainActivity extends BaseActivity<ActivityMyMainBinding> {
    public MyMainActivity() {
        super(R.layout.activity_my_main);
    }
    @Override
    protected void init() {
        binding.ButtonQuit.setOnClickListener(v -> finish());
        binding.ButtonSendFile.setOnClickListener(v -> startActivity(SendFileActivity.class));
    }
}
