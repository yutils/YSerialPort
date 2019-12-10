
package com.yujing.chuankou.zm703;

import android.annotation.SuppressLint;
import android.util.Log;

import com.yujing.chuankou.BaseActivity;
import com.yujing.chuankou.R;
import com.yujing.chuankou.databinding.ActivityZm703CpuBinding;
import com.yujing.utils.YConvert;
import com.yujing.yserialport.YSerialPort;

import java.io.IOException;

/**
 * zm703读卡器 读取cpu区
 *
 * @author yujing 2019年12月3日16:18:35
 */
@SuppressLint("SetTextI18n")
public class ZM703CardCPUActivity extends BaseActivity<ActivityZm703CpuBinding> {
    YSerialPort ySerialPort;

    @Override
    protected Integer getContentLayoutId() {
        return R.layout.activity_zm703_cpu;
    }


    @Override
    protected void initData() {
        ySerialPort = new YSerialPort(this);
        ySerialPort.setDataLength(10);
        ySerialPort.clearDataListener();
        ySerialPort.start();

        binding.btCardCpu.setOnClickListener(v -> readCpu());

        binding.btDyk.setOnClickListener(v -> yTts.speak("测试，你好，烟包，称重，磅码，排号，烟农,仓储设备，管理人员，同重超次数。"));

        binding.tvTips.setText("注意：" +
                "当前串口：" + YSerialPort.readDevice(this) + "，当前波特率：" + YSerialPort.readBaudRate(this) +
                "。\t\tZM703读卡器：\t/dev/ttyS4\t波特率115200");
    }


    /**
     * 读CPU
     */
    private void readCpu() {
        try {
            ySerialPort.clearDataListener();
            ySerialPort.setDataLength(7);
            ySerialPort.addDataListener(new CpuDataListener());
            step = 0;
            byte[] cmd = SerialCpu.getComplete(SerialCpu.getCommandSearch());
            Log.d("发送串口命令", YConvert.bytesToHexString(cmd));
            binding.tvResult.setText("开始寻卡\n发送串口命令:" + YConvert.bytesToHexString(cmd));
            ySerialPort.send(cmd);
        } catch (IOException e) {
            Log.e("异常", "串口异常", e);
        }
    }

    int step = 0;

    /**
     * cpu读卡监听
     */
    class CpuDataListener implements YSerialPort.DataListener {
        @Override
        public void onDataReceived(String hexString, byte[] bytes, int size) {
            binding.tvResult.setText(binding.tvResult.getText() + "\n收到数据：" + hexString);
            Log.d("收到数据", hexString);
            ZM703 zm703 = new ZM703(hexString, bytes, size);
            Log.d("收到数据", zm703.toString());
            if (!zm703.isStatus()) {
                binding.tvResult.setText(binding.tvResult.getText() + "\n状态:失败");
                return;
            }
            binding.tvResult.setText(binding.tvResult.getText() + "\nvalue:\n" + zm703.getDataHexString());
            step++;
            if (step == 1) {
                step1();
            } else if (step == 2) {
                step2();
            } else if (step == 3) {
                step3();
            } else if (step == 4) {
                step4();
            } else if (step == 5) {
                step5();
            } else if (step == 6) {
                //分包
                int packetsLength = YConvert.bytesTwo2Int(new byte[]{zm703.getDataBytes()[1], zm703.getDataBytes()[0]});
                byte[][] packets = new byte[packetsLength][11];

                for (int i = 0; i < packetsLength; i++) {
                    System.arraycopy(zm703.getDataBytes(), i * 11 + 2, packets[i], 0, 11);
                }
                for (int i = 0; i < packetsLength; i++) {
                    byte[] item = packets[i];
                    binding.tvResult.setText(binding.tvResult.getText() + "\n烟包数：" + i + "：" + YConvert.bytesToHexString(item));
                }
            }
        }
    }


    protected void step1() {
        try {
            byte[] cmd = SerialCpu.getComplete(SerialCpu.getCpuInto());
            Log.d("发送串口命令", YConvert.bytesToHexString(cmd));
            binding.tvResult.setText(binding.tvResult.getText() + "\nCPU转入\n发送串口命令:" + YConvert.bytesToHexString(cmd));
            ySerialPort.send(cmd);
        } catch (IOException e) {
            Log.e("异常", "串口异常", e);
        }
    }

    protected void step2() {
        try {
            byte[] cmd = SerialCpu.getComplete(SerialCpu.getCos(SerialCpu.cosSelectDf()));
            Log.d("发送串口命令", YConvert.bytesToHexString(cmd));
            binding.tvResult.setText(binding.tvResult.getText() + "\n选择DF\n发送串口命令:" + YConvert.bytesToHexString(cmd));
            ySerialPort.send(cmd);
        } catch (IOException e) {
            Log.e("异常", "串口异常", e);
        }
    }

    protected void step3() {
        try {
            byte[] cmd = SerialCpu.getComplete(SerialCpu.getAuthentication());
            Log.d("发送串口命令", YConvert.bytesToHexString(cmd));
            binding.tvResult.setText(binding.tvResult.getText() + "\n复合认证\n发送串口命令:" + YConvert.bytesToHexString(cmd));
            ySerialPort.send(cmd);
        } catch (IOException e) {
            Log.e("异常", "串口异常", e);
        }
    }

    protected void step4() {
        try {
            byte[] cmd = SerialCpu.getComplete(SerialCpu.getCos(SerialCpu.cosSelectFile()));
            Log.d("发送串口命令", YConvert.bytesToHexString(cmd));
            binding.tvResult.setText(binding.tvResult.getText() + "\n选择文件\n发送串口命令:" + YConvert.bytesToHexString(cmd));
            ySerialPort.send(cmd);
        } catch (IOException e) {
            Log.e("异常", "串口异常", e);
        }
    }

    protected void step5() {
//        try {
//            byte[] cmd = SerialCpu.getComplete(SerialCpu.getCos(SerialCpu.cosReadFile("0000", "ef")));
//            Log.d("发送串口命令", YConvert.bytesToHexString(cmd));
//            ySerialPort.setDataLength(248, 50);
//            binding.tvResult.setText(binding.tvResult.getText() + "\n读文件\n发送串口命令:" + YConvert.bytesToHexString(cmd));
//            ySerialPort.send(cmd);
//        } catch (IOException e) {
//            Log.e("异常", "串口异常", e);
//        }

        try {
            byte[] cmd = SerialCpu.getComplete(SerialCpu.readFile16k("0000", "4000"));
            Log.d("发送串口命令", YConvert.bytesToHexString(cmd));
            ySerialPort.setDataLength(16384 + 7, 2000);
            binding.tvResult.setText(binding.tvResult.getText() + "\n读文件\n发送串口命令:" + YConvert.bytesToHexString(cmd));
            ySerialPort.send(cmd);
        } catch (IOException e) {
            Log.e("异常", "串口异常", e);
        }
    }

    //退出注销
    @Override
    protected void onDestroy() {
        super.onDestroy();
        ySerialPort.onDestroy();
    }
}
