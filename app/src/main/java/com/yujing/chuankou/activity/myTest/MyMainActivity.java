package com.yujing.chuankou.activity.myTest;

import com.yujing.chuankou.R;
import com.yujing.chuankou.activity.myTest.zm703.ZM703CardCPUActivity;
import com.yujing.chuankou.activity.myTest.zm703.ZM703CardM1Activity;
import com.yujing.chuankou.base.BaseActivity;
import com.yujing.chuankou.databinding.ActivityMyMainBinding;

public class MyMainActivity extends BaseActivity<ActivityMyMainBinding> {
    @Override
    protected Integer getContentLayoutId() {
        return R.layout.activity_my_main;
    }
    @Override
    protected void initData() {
        binding.ButtonQuit.setOnClickListener(v -> finish());
        binding.btTest.setOnClickListener(v -> startActivity(TestActivity.class));
        binding.btZM703M1.setOnClickListener(v -> startActivity(ZM703CardM1Activity.class));
        binding.btZM703Cpu.setOnClickListener(v -> startActivity(ZM703CardCPUActivity.class));
        binding.ButtonSendFile.setOnClickListener(v -> startActivity(SendFileActivity.class));
    }
}
