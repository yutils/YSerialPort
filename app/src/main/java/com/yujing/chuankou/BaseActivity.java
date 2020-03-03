package com.yujing.chuankou;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ViewDataBinding;

import com.yujing.utils.YPermissions;
import com.yujing.utils.YShow;
import com.yujing.utils.YToast;
import com.yujing.utils.YTts;

/**
 * 基础activity
 *
 * @author yujing 2019年5月21日16:30:16
 */
@SuppressWarnings("unused")
public abstract class BaseActivity<B extends ViewDataBinding> extends AppCompatActivity {
    protected B binding;
    protected YTts yTts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Integer contentViewId = getContentLayoutId();
        if (contentViewId != null) {
            binding = DataBindingUtil.setContentView(this, contentViewId);
        }
        yTts = new YTts(this);
        YPermissions.requestAll(this);
        initData();
    }

    /**
     * 绑定layout
     */
    protected abstract Integer getContentLayoutId();

    /**
     * 初始化数据
     */
    protected abstract void initData();

    /**
     * 跳转
     */
    protected void startActivity(Class<?> classActivity) {
        Intent intent = new Intent();
        intent.setClass(this, classActivity);
        startActivity(intent);
    }

    /**
     * 跳转
     */
    protected void startActivity(Class<?> classActivity, int resultCode) {
        startActivityForResult(classActivity, resultCode);
    }

    /**
     * 跳转
     */
    protected void startActivityForResult(Class<?> classActivity, int resultCode) {
        Intent intent = new Intent();
        intent.setClass(this, classActivity);
        startActivityForResult(intent, resultCode);
    }

    /**
     * 显示toast
     */
    protected void show(String str) {
        if (str == null)
            return;
        YToast.showLong(getApplicationContext(), str);
    }


    /**
     * 窗口焦点改变监听
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

    }

    @Override
    protected void onResume() {
        super.onResume();

    }

    @Override
    protected void onStop() {
        super.onStop();

    }

    @Override
    protected void onPause() {
        super.onPause();

    }

    @Override
    protected void onDestroy() {
        System.gc();// 系统自动回收
        yTts.onDestroy();
        super.onDestroy();
    }

    /**
     * 延时关闭YShow
     */
    protected void delayedShowFinish() {
        delayedShowFinish(1000 * 5);
    }

    static int showFinishTime = 0;

    /**
     * 延时关闭YShow
     */
    protected void delayedShowFinish(int time) {
        showFinishTime = time;
        new Thread(() -> {
            while (showFinishTime > 0) {
                try {
                    Thread.sleep(50);
                    showFinishTime -= 50;
                } catch (InterruptedException e) {
                }
            }
            runOnUiThread(() -> {
                if (isDestroyed())
                    return;
                YShow.finish();
            });
        }).start();
    }

    @Override
    public void finish() {
        super.finish();
        YShow.finish();
    }
}
