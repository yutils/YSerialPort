package com.yujing.chuankou.utils;

import android.app.Activity;
import android.app.AlertDialog;

import com.yujing.chuankou.databinding.ViewSetBinding;
import com.yujing.contract.YListener;
import com.yujing.yserialport.YSerialPort;

public class Setting {
    /**
     * 设置
     * @param activity activity
     * @param includeSet include
     * @param yListener 是否设置完成
     */
    public static void setting(Activity activity, ViewSetBinding includeSet, YListener yListener) {
        if (YSerialPort.readDevice(activity) != null) {
            includeSet.tvCk.setText(YSerialPort.readDevice(activity));
        }
        if (YSerialPort.readBaudRate(activity) != null) {
            includeSet.tvBtl.setText(YSerialPort.readBaudRate(activity));
        }
        includeSet.llCk.setOnClickListener(v -> {
            int index = 0;//单选框默认值：从0开始
            new AlertDialog.Builder(activity)
                    .setTitle("选择串口")//设置对话框标题
                    .setIcon(android.R.drawable.ic_menu_info_details)//设置对话框图标
                    .setSingleChoiceItems(YSerialPort.getSerialPortFinder().getAllDevices(), index, (dialog, which) -> {
                        String device = YSerialPort.getSerialPortFinder().getAllDevicesPath()[which];
                        includeSet.tvCk.setText(device);
                        YSerialPort.saveDevice(activity, device);
                        dialog.dismiss();
                        //回调
                        if (yListener != null) yListener.value();
                    })
                    .show();
        });
        includeSet.llBtl.setOnClickListener(v -> {
            int index = 0;//单选框默认值：从0开始
            new AlertDialog.Builder(activity)
                    .setTitle("选择波特率")//设置对话框标题
                    .setIcon(android.R.drawable.ic_menu_info_details)//设置对话框图标
                    .setSingleChoiceItems(YSerialPort.getBaudRates(), index, (dialog, which) -> {
                        String baudRate = YSerialPort.getBaudRates()[which];
                        includeSet.tvBtl.setText(baudRate);
                        YSerialPort.saveBaudRate(activity, baudRate);
                        //回调
                        if (yListener != null) yListener.value();
                        dialog.dismiss();
                    }).show();
        });
    }
}
