package com.yujing.chuankou;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.yujing.ycrash.YCrash;


public class App extends Application {

    @SuppressLint("StaticFieldLeak")
    private static Context context;

    @Override
    public void onCreate() {
        setContext(getApplicationContext());
        YCrash.getInstance().init(this);
        YCrash.getInstance().setAppName(getString(R.string.app_name));
        YCrash.getInstance().setCrashInfoListener(appInfo -> Log.e("崩溃拦截",appInfo.崩溃信息));
        super.onCreate();
    }

    public static Context getContext() {
        return context;
    }

    private static void setContext(Context context) {
        App.context = context;
    }

}
