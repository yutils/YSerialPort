package com.yujing.chuankou;

import android.app.AlertDialog;
import android.content.Intent;
import android.widget.Button;

import com.yujing.chuankou.databinding.MainBinding;
import com.yujing.chuankou.zm703.ZM703CardCPUActivity;
import com.yujing.chuankou.zm703.ZM703CardM1Activity;

public class MainMenu extends BaseActivity<MainBinding> {
    @Override
    protected Integer getContentLayoutId() {
        return R.layout.main;
    }

    @Override
    protected void initData() {
        final Button buttonSetup = findViewById(R.id.ButtonSetup);
        buttonSetup.setOnClickListener(v -> startActivity(new Intent(MainMenu.this, SetActivity.class)));
        binding.btZM703M1.setOnClickListener(v -> startActivity(ZM703CardM1Activity.class));
        binding.btZM703Cpu.setOnClickListener(v -> startActivity(ZM703CardCPUActivity.class));
        binding.ButtonSendWords.setOnClickListener(v -> startActivity(new Intent(MainMenu.this, SendWordsActivity.class)));
        binding.ButtonQuit.setOnClickListener(v -> MainMenu.this.finish());
        binding.ButtonAbout.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainMenu.this);
            builder.setTitle("关于——余静的串口测试工具");
            builder.setMessage(R.string.about_msg);
            builder.show();
        });
    }
}
