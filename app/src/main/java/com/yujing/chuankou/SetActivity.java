package com.yujing.chuankou;

import android.app.AlertDialog;
import android.content.DialogInterface;

import com.yujing.chuankou.databinding.ActivitySetBinding;
import com.yujing.utils.YDelayAndroid;
import com.yujing.yserialport.YSerialPort;

/**
 * 设置
 */
public class SetActivity extends BaseActivity<ActivitySetBinding> {
    @Override
    protected Integer getContentLayoutId() {
        return R.layout.activity_set;
    }

    @Override
    protected void initData() {
        if (YSerialPort.readDevice(this) != null) {
            binding.tvCk.setText(YSerialPort.readDevice(this));
        }
        if (YSerialPort.readBaudRate(this) != null) {
            binding.tvBtl.setText(YSerialPort.readBaudRate(this));
        }

        binding.llCk.setOnClickListener(v -> {
            int index = 0;//单选框默认值：从0开始
            new AlertDialog.Builder(SetActivity.this)
                    .setTitle("选择串口")//设置对话框标题
                    .setIcon(android.R.drawable.ic_menu_info_details)//设置对话框图标
                    .setSingleChoiceItems(YSerialPort.getSerialPortFinder().getAllDevices(), index, (dialog, which) -> {
                        String device = YSerialPort.getSerialPortFinder().getAllDevicesPath()[which];
                        binding.tvCk.setText(device);
                        YSerialPort.saveDevice(getApplication(), device);
                        dialog.dismiss();
                        //判断串口是否可用
                        if (YSerialPort.readBaudRate(SetActivity.this) != null) {
                            final YSerialPort ySerialPort = new YSerialPort(SetActivity.this);
                            ySerialPort.setDataLength(10);
                            ySerialPort.start();
                            YDelayAndroid.run(100, () -> ySerialPort.onDestroy());
                        }
                    })
                    .show();
        });
        binding.llBtl.setOnClickListener(v -> {
            int index = 0;//单选框默认值：从0开始
            new AlertDialog.Builder(SetActivity.this)
                    .setTitle("选择波特率")//设置对话框标题
                    .setIcon(android.R.drawable.ic_menu_info_details)//设置对话框图标
                    .setSingleChoiceItems(YSerialPort.getBaudRateList(), index, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            binding.tvBtl.setText(YSerialPort.getBaudRateList()[which]);
                            YSerialPort.saveBaudRate(getApplication(), YSerialPort.getBaudRateList()[which]);
                            dialog.dismiss();
                        }
                    })
                    .show();
        });
    }
}
