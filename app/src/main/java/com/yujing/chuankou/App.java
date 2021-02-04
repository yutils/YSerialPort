package com.yujing.chuankou;

import android.app.Application;
import android.util.Log;

import com.hn.utils.HnUtils;
import com.yujing.utils.YLog;
import com.yujing.utils.YPath;
import com.yujing.ycrash.YCrash;


public class App extends Application {

    public static App INSTANCE;

    public static App get() {
        if (INSTANCE == null) {
            synchronized (App.class) {
                if (INSTANCE == null) {
                    INSTANCE = new App();
                }
            }
        }
        return INSTANCE;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        INSTANCE = this;
        //异常捕获
        YCrash.getInstance().init(this);
        YCrash.getInstance().setAppName(getString(R.string.app_name));
        YCrash.getInstance().setCrashInfoListener(appInfo -> Log.e("崩溃拦截", appInfo.崩溃信息));
        //作者的测试
        HnUtils.Companion.init(this);
        HnUtils.Companion.initDevice();
        //打开日志保存
        YLog.saveOpen(YPath.getFilePath(this, "log"));
        //保存最近30天日志
        YLog.delDaysAgo(30);
    }
}
