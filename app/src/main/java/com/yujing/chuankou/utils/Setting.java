package com.yujing.chuankou.utils;

import android.app.Activity;
import android.app.AlertDialog;

import com.yujing.chuankou.config.Config;
import com.yujing.chuankou.databinding.ViewSetBinding;
import com.yujing.contract.YListener;
import com.yujing.yserialport.YSerialPort;

public class Setting {
    /**
     * 设置
     *
     * @param activity   activity
     * @param includeSet include
     * @param yListener  是否设置完成
     */
    public static void setting(Activity activity, ViewSetBinding includeSet, YListener yListener) {
        if (Config.getDevice() != null) {
            includeSet.tvCk.setText(Config.getDevice());
        }
        if (Config.getBaudRate() != null) {
            includeSet.tvBtl.setText(Config.getBaudRate());
        }
        includeSet.llCk.setOnClickListener(v -> {
            //单选框默认值：当前已保存的设备
            int index = 0;
            String currentDevice = YSerialPort.readDevice(activity);
            String[] devicesPath = YSerialPort.getSerialPortFinder().getAllDevicesPath();
            if (currentDevice != null && devicesPath != null) {
                for (int i = 0; i < devicesPath.length; i++) {
                    if (currentDevice.equals(devicesPath[i])) {
                        index = i;
                        break;
                    }
                }
            }
            new AlertDialog.Builder(activity)
                    .setTitle("选择串口")//设置对话框标题
                    .setIcon(android.R.drawable.ic_menu_info_details)//设置对话框图标
                    .setSingleChoiceItems(YSerialPort.getSerialPortFinder().getAllDevices(), index, (dialog, which) -> {
                        String device = YSerialPort.getSerialPortFinder().getAllDevicesPath()[which];
                        includeSet.tvCk.setText(device);
                        Config.setDevice(device);
                        dialog.dismiss();
                        //回调
                        if (yListener != null) yListener.value();
                    })
                    .show();
        });
        includeSet.llBtl.setOnClickListener(v -> {
            //单选框默认值：当前已保存的波特率
            int index = 0;
            String currentBaudRate = YSerialPort.readBaudRate(activity);
            String[] baudRates = YSerialPort.getBaudRates();
            if (currentBaudRate != null && baudRates != null) {
                for (int i = 0; i < baudRates.length; i++) {
                    if (currentBaudRate.equals(baudRates[i])) {
                        index = i;
                        break;
                    }
                }
            }
            new AlertDialog.Builder(activity)
                    .setTitle("选择波特率")//设置对话框标题
                    .setIcon(android.R.drawable.ic_menu_info_details)//设置对话框图标
                    .setSingleChoiceItems(YSerialPort.getBaudRates(), index, (dialog, which) -> {
                        String baudRate = YSerialPort.getBaudRates()[which];
                        includeSet.tvBtl.setText(baudRate);
                        Config.setBaudRate(baudRate);
                        //回调
                        if (yListener != null) yListener.value();
                        dialog.dismiss();
                    }).show();
        });
    }
}
