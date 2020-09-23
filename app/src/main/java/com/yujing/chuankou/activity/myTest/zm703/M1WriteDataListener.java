package com.yujing.chuankou.activity.myTest.zm703;

import com.yujing.contract.YListener1;
import com.yujing.contract.YSuccessFailListener;
import com.yujing.utils.YConvert;
import com.yujing.yserialport.DataListener;
import com.yujing.yserialport.YSerialPort;

/**
 * ZM703读卡器  mi写入
 *
 * @author yujing 2020年8月13日19:48:21
 */
public class M1WriteDataListener implements DataListener {
    private int blockStart;//开始扇区
    private int blockEnd;//结束扇区
    private String password;//密码
    private SerialM1.KEYType keyType = SerialM1.KEYType.KEY_A;
    private String data;//data
    private YSuccessFailListener<String, String> successFailListener;//错误
    private YListener1<String> logListener;//日志
    private YSerialPort ySerialPort;//串口

    public void setSuccessFailListener(YSuccessFailListener<String, String> successFailListener) {
        this.successFailListener = successFailListener;
    }

    public void setLogListener(YListener1<String> logListener) {
        this.logListener = logListener;
    }

    public M1WriteDataListener(YSerialPort ySerialPort, int blockStart, int blockEnd, String password, String data) {
        this.ySerialPort = ySerialPort;
        this.blockStart = blockStart;
        this.blockEnd = blockEnd;
        this.password = password;
        this.data = data;
    }

    public M1WriteDataListener(YSerialPort ySerialPort, int blockStart, int blockEnd, String password, SerialM1.KEYType keyType, String data) {
        this.ySerialPort = ySerialPort;
        this.blockStart = blockStart;
        this.blockEnd = blockEnd;
        this.password = password;
        this.keyType = keyType;
        this.data = data;
    }

    /**
     * 每个块长度16，连续写入会自动跳过密码块，如：读取0123456789扇块，会跳过3和7
     *
     * @return 长度
     */
    public int getDataLength() {
        int length = 0;
        if (blockStart == blockEnd)
            return length + 16;
        for (int i = blockStart; i <= blockEnd; i++)
            if (i % 4 != 3) length += 16;
        return length;
    }

    /**
     * 寻卡
     */
    public void search() {
        byte[] cmd = SerialM1.getComplete(SerialM1.getCommandSearch());
        if (logListener != null) logListener.value("发送寻卡命令：" + YConvert.bytesToHexString(cmd));
        ySerialPort.clearDataListener();
        ySerialPort.addDataListener(this);
        ySerialPort.send(cmd);
    }

    /**
     * 读M1扇区指令
     */
    public void writeM1() {
        try {
            //连续读取结果会自动跳过密码块，一次最多读4个扇区，也就是0-15扇区，应该返回12组数据
            byte[] cmd = SerialM1.getComplete(SerialM1.setCommandMultipleBlock(blockStart, blockEnd, keyType, YConvert.hexStringToByte(password), YConvert.hexStringToByte(data)));
            if (logListener != null) logListener.value("发送串口命令:" + YConvert.bytesToHexString(cmd));
            ySerialPort.send(cmd);
        } catch (Exception e) {
        }
    }

    @Override
    public void value(String hexString, byte[] bytes) {
        if (logListener != null) logListener.value("收到数据：" + hexString);
        ZM703 zm703 = new ZM703(hexString, bytes);
        if (logListener != null && zm703.getDataHexString() != null && !zm703.getDataHexString().isEmpty())
            logListener.value("剥壳数据：" + zm703.getDataHexString());
        if (!zm703.isStatus()) {
            if (logListener != null) {
                logListener.value("状态:失败");
                successFailListener.fail("失败");
            }
            return;
        }
        if (zm703.getDataSize() == 7) {//寻卡结果长度为7
            writeM1();
        } else if (zm703.getDataSize() == 0) {
            if (zm703.isStatus()) {
                if (logListener != null) {
                    logListener.value("状态:成功");
                    successFailListener.success("成功");
                }
            }
        }
    }
}
