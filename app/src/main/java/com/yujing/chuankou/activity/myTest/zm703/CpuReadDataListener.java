package com.yujing.chuankou.activity.myTest.zm703;

import com.yujing.contract.YListener1;
import com.yujing.utils.YConvert;
import com.yujing.utils.YConvertNumberBytes;
import com.yujing.yserialport.DataListener;
import com.yujing.yserialport.YSerialPort;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * CPU卡读卡解析
 *
 * @author yujing 2020年8月13日19:48:35
 */
public class CpuReadDataListener implements DataListener {
    final String TAG = "CpuRead";
    int step = 0;
    int packetsLength;
    private YSerialPort ySerialPort;//串口
    private YListener1<List<String>> dataListener;
    private YListener1<String> failListener;//错误
    private YListener1<String> logListener;//日志

    public CpuReadDataListener(YSerialPort ySerialPort) {
        this.ySerialPort = ySerialPort;
    }

    public void setDataListener(YListener1<List<String>> dataListener) {
        this.dataListener = dataListener;
    }

    public void setFailListener(YListener1<String> failListener) {
        this.failListener = failListener;
    }

    public void setLogListener(YListener1<String> logListener) {
        this.logListener = logListener;
    }

    /**
     * 寻卡
     */
    public void search() {
        step = 0;//步骤设置成0
        byte[] cmd = SerialCpu.getComplete(SerialCpu.getCommandSearch());
        if (logListener != null)
            logListener.value("◆开始寻卡，发送串口命令:" + YConvert.bytesToHexString(cmd));
        ySerialPort.clearDataListener();
        ySerialPort.addDataListener(this);
        ySerialPort.send(cmd);
    }

    @Override
    public void value(String hexString, byte[] bytes) {
        if (logListener != null) logListener.value("收到数据：" + hexString);
        ZM703 zm703 = new ZM703(hexString, bytes, bytes.length);
        if (logListener != null && zm703.getDataHexString() != null && !zm703.getDataHexString().isEmpty())
            logListener.value("剥壳数据:" + zm703.getDataHexString());
        if (!zm703.isStatus()) {
            if (logListener != null) logListener.value("状态:失败");
            if (failListener != null) failListener.value("状态:失败");
            return;
        }
        step++;
        //判断是否是自动寻卡成功，寻卡成功数据区长度为7，总长度14，启动自动寻卡数据区长度为0，总长度为7。所以，当数据区长度为7，或者，长度为0但是总长度为14+7
        if (step == 1 && !(zm703.getDataSize() == 7 || (zm703.getDataSize() == 0 && zm703.getSize() == 21))) {
            step = 0;
        }
        if (step == 1) {
            step1();
        } else if (step == 2) {
            if (zm703.getDataBytes()[5] == 0x4D && zm703.getDataBytes()[6] == 0x54) {
                step2_new();
            } else {
                step2_old();
            }
        } else if (step == 3) {
            if ("9000".equals(zm703.getDataHexString().substring(zm703.getDataHexString().length() - 4)))
                step3();
            else {
                if (logListener != null) logListener.value("选择DF失败");
                if (failListener != null) failListener.value("选择DF失败");
                step = 0;
            }
        } else if (step == 4) {
            if ("9000".equals(zm703.getDataHexString())) step4();
            else {
                if (logListener != null) logListener.value("选择DF失败");
                if (failListener != null) failListener.value("复合认证失败");
                step = 0;
            }
        } else if (step == 5) {
            if ("9000".equals(zm703.getDataHexString())) step5();
            else {
                if (logListener != null) logListener.value("选择DF失败");
                if (failListener != null) failListener.value("选择文件失败");
                step = 0;
            }
        } else if (step == 6) {
            //分包
            packetsLength = YConvertNumberBytes.bytes2ToInt(zm703.getDataBytes());
            step6(packetsLength);
        } else if (step == 7) {
            if (zm703.getDataBytes().length < 11) {
                if (logListener != null) logListener.value("长度不够");
                if (failListener != null) failListener.value("长度不够");
                return;
            }
            byte[][] packets = new byte[packetsLength][11];
            for (int i = 0; i < packetsLength; i++) {
                System.arraycopy(zm703.getDataBytes(), i * 11, packets[i], 0, 11);
            }
            List<String> strings = new ArrayList<>();
            for (int i = 0; i < packetsLength; i++) {
                byte[] item = packets[i];
                if (logListener != null)
                    logListener.value("烟包ID：" + i + "：" + YConvert.bytesToHexString(item) + "------解析----->" + new String(item, StandardCharsets.US_ASCII));
                strings.add(new String(item, StandardCharsets.US_ASCII));
            }
            if (dataListener != null)
                dataListener.value(strings);
        }
    }

    void step1() {
        byte[] cmd = SerialCpu.getComplete(SerialCpu.getCpuInto());
        if (logListener != null)
            logListener.value("◆CPU转入，发送串口命令:" + YConvert.bytesToHexString(cmd));
        ySerialPort.send(cmd);
    }

    protected void step2_old() {
        byte[] cmd = SerialCpu.getComplete(SerialCpu.getCos(SerialCpu.cosSelectDfDefault()));
        if (logListener != null)
            logListener.value("◆选择DF，发送串口命令:" + YConvert.bytesToHexString(cmd));
        ySerialPort.send(cmd);
    }

    protected void step2_new() {
        byte[] cmd = SerialCpu.getComplete(SerialCpu.getCos(SerialCpu.cosSelectDfMax()));
        if (logListener != null)
            logListener.value("◆选择DF，发送串口命令:" + YConvert.bytesToHexString(cmd));
        ySerialPort.send(cmd);
    }

    void step3() {
        byte[] cmd = SerialCpu.getComplete(SerialCpu.getAuthentication());
        if (logListener != null)
            logListener.value("◆复合认证，发送串口命令:" + YConvert.bytesToHexString(cmd));
        ySerialPort.send(cmd);
    }

    void step4() {
        byte[] cmd = SerialCpu.getComplete(SerialCpu.getCos(SerialCpu.cosSelectFile()));
        if (logListener != null)
            logListener.value("◆选择文件，发送串口命令:" + YConvert.bytesToHexString(cmd));
        ySerialPort.send(cmd);
    }

    //读取长度
    void step5() {
        byte[] cmd = SerialCpu.getComplete(SerialCpu.readFile16k("0000", "0002"));
        if (logListener != null)
            logListener.value("◆读文件，发送串口命令:" + YConvert.bytesToHexString(cmd));
        ySerialPort.send(cmd);
    }

    void step6(int packetsLength) {
        int startIndex = 11 + 2;//开始位置2位长度位+11个基本属性位
        int length = packetsLength * 11;//长度位=烟包*11
        byte[] cmd = SerialCpu.getComplete(SerialCpu.readFile16k(YConvert.bytesToHexString(YConvertNumberBytes.intTo2Bytes(startIndex)), YConvert.bytesToHexString(YConvertNumberBytes.intTo2Bytes(length))));
        if (logListener != null)
            logListener.value("◆读文件，发送串口命令:" + YConvert.bytesToHexString(cmd));
        ySerialPort.send(cmd);
    }

}