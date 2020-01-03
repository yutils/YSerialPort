package com.yujing.chuankou;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.yujing.chuankou.databinding.MainBinding;
import com.yujing.chuankou.zm703.ZM703CardCPUActivity;
import com.yujing.chuankou.zm703.ZM703CardM1Activity;
import com.yujing.yserialport.YReadInputStream;

import java.util.ArrayList;

public class MainMenu extends BaseActivity<MainBinding> {
    @Override
    protected Integer getContentLayoutId() {
        return R.layout.main;
    }

    @Override
    protected void initData() {
        binding.ButtonQuit.setOnClickListener(v -> MainMenu.this.finish());
        binding.btTest.setOnClickListener(v -> startActivity(TestActivity.class));
        binding.ButtonSetup.setOnClickListener(v -> startActivity(SetActivity.class));
        binding.btZM703M1.setOnClickListener(v -> startActivity(ZM703CardM1Activity.class));
        binding.btZM703Cpu.setOnClickListener(v -> startActivity(ZM703CardCPUActivity.class));
        binding.ButtonSendWords.setOnClickListener(v -> startActivity(SendWordsActivity.class));
        binding.ButtonAbout.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainMenu.this);
            builder.setTitle("关于——余静的串口测试工具");
            builder.setMessage(R.string.about_msg);
            builder.show();
        });
        initPermission();
        YReadInputStream.setShowLog(true);
    }

    /**
     * 获取权限
     */
    private void initPermission() {
        String[] permissions = {
                //串口权限
                Manifest.permission.GET_ACCOUNTS,
                Manifest.permission.READ_CONTACTS,
                //异常提交权限
                Manifest.permission.INTERNET,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
        };
        ArrayList<String> toApplyList = new ArrayList<String>();
        for (String perm : permissions) {
            if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(this, perm)) {
                toApplyList.add(perm); // 进入到这里代表没有权限.
            }
        }
        String[] tmpList = new String[toApplyList.size()];
        if (!toApplyList.isEmpty()) {
            ActivityCompat.requestPermissions(this, toApplyList.toArray(tmpList), 123);
        }
    }
}
