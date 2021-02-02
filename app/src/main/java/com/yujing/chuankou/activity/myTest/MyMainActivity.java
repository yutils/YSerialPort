package com.yujing.chuankou.activity.myTest;

import com.hn.utils.TestHnUtilsActivity;
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
        binding.btTest.setOnClickListener(v -> startActivity(TestActivity.class));
        binding.btZM703M1.setOnClickListener(v -> startActivity(ZM703CardM1Activity.class));
        binding.btZM703Cpu.setOnClickListener(v -> startActivity(ZM703CardCPUActivity.class));
        binding.ButtonSendFile.setOnClickListener(v -> startActivity(SendFileActivity.class));
        binding.btOther.setOnClickListener(v -> startActivity(TestHnUtilsActivity.class));
    }
}
