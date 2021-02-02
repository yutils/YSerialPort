
package com.yujing.chuankou.activity.myTest;

import android.annotation.SuppressLint;

import com.hn.utils.zm703.CpuReadDataListener;
import com.yujing.chuankou.R;
import com.yujing.chuankou.base.BaseActivity;
import com.yujing.chuankou.databinding.ActivityZm703CpuBinding;
import com.yujing.chuankou.utils.Setting;
import com.yujing.utils.YLog;
import com.yujing.yserialport.YSerialPort;

/**
 * zm703读卡器 读取cpu区
 *
 * @author yujing 2020年8月13日19:47:40
 */
@SuppressLint("SetTextI18n")
public class ZM703CardCPUActivity extends BaseActivity<ActivityZm703CpuBinding> {
    YSerialPort ySerialPort;
    CpuReadDataListener cpuDataListener;

    public ZM703CardCPUActivity() {
        super(R.layout.activity_zm703_cpu);
    }
    @Override
    protected void init() {
        ySerialPort = new YSerialPort(this);
        ySerialPort.clearDataListener();
        ySerialPort.start();
        cpuDataListener = new CpuReadDataListener(ySerialPort);
        binding.btCardCpu.setOnClickListener(v -> readCpu());
        binding.btDyk.setOnClickListener(v -> show("未开发"));
        binding.tvTips.setText(String.format("注意：\n\t\tZM703读卡器：\t/dev/ttyS4\t波特率115200", ySerialPort.getDevice(), ySerialPort.getBaudRate()));
        //设置
        Setting.setting(this, binding.includeSet, () -> {
            if (YSerialPort.readDevice(this) != null && YSerialPort.readBaudRate(this) != null)
                ySerialPort.reStart(YSerialPort.readDevice(this), YSerialPort.readBaudRate(this));
            binding.tvResult.setText("");
        });
        //退出
        binding.ButtonQuit.setOnClickListener(v -> finish());
    }

    /**
     * 读CPU
     */
    private void readCpu() {
        binding.tvResult.setText("开始");
        ySerialPort.clearDataListener();
        ySerialPort.addDataListener(cpuDataListener);
        cpuDataListener.setLogListener(s -> {
            YLog.d(s);
            binding.tvResult.setText(binding.tvResult.getText().toString() + "\n" + s);
        });
        cpuDataListener.setFailListener(s -> {

        });
        cpuDataListener.search();
    }

    //退出注销
    @Override
    public void onDestroy() {
        ySerialPort.onDestroy();
        super.onDestroy();
    }
}
