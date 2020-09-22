package com.yujing.chuankou.activity.myTest.zm703;

import com.yujing.contract.YListener1;
import com.yujing.utils.YConvert;
import com.yujing.yserialport.DataListener;
import com.yujing.yserialport.YSerialPort;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * m1读卡监听
 *
 * @author yujing 2020年8月13日19:48:35
 */
public class M1ReadDataListener implements DataListener {
    final String TAG = "M1Read";
    private int blockStart;//开始扇区
    private int blockEnd;//结束扇区
    private String password;//密码
    private SerialM1.KEYType keyType;
    private YSerialPort ySerialPort;//串口
    private YListener1<String> dataListener;//数据，有FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF
    private YListener1<String> dataNoFListener;//数据，无FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF
    private YListener1<List<String>> dataListListener;//数据分块，有FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF
    private YListener1<String> failListener;//错误
    private YListener1<String> logListener;//日志

    public void setDataListener(YListener1<String> dataListener) {
        this.dataListener = dataListener;
    }

    public void setLogListener(YListener1<String> logListener) {
        this.logListener = logListener;
    }

    public void setDataListListener(YListener1<List<String>> dataListListener) {
        this.dataListListener = dataListListener;
    }

    public void setFailListener(YListener1<String> failListener) {
        this.failListener = failListener;
    }

    public M1ReadDataListener(YSerialPort ySerialPort, int blockStart, int blockEnd, String passwordHexString) {
        this(ySerialPort, blockStart, blockEnd, passwordHexString, SerialM1.KEYType.KEY_A);
    }

    public void setDataNoFListener(YListener1<String> dataNoFListener) {
        this.dataNoFListener = dataNoFListener;
    }

    public M1ReadDataListener(YSerialPort ySerialPort, int blockStart, int blockEnd, String passwordHexString, SerialM1.KEYType keyType) {
        this.ySerialPort = ySerialPort;
        this.blockStart = blockStart;
        this.blockEnd = blockEnd;
        this.password = passwordHexString;
        this.keyType = keyType;
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

    @Override
    public void value(String hexString, byte[] bytes) {
        if (logListener != null) logListener.value("收到数据:" + hexString);
        ZM703 zm703 = new ZM703(hexString, bytes, bytes.length);
        if (logListener != null && zm703.getDataHexString() != null && !zm703.getDataHexString().isEmpty())
            logListener.value("剥壳数据：" + zm703.getDataHexString());
        if (!zm703.isStatus()) {
            if (logListener != null) logListener.value("状态:失败");
            if (failListener != null) failListener.value("状态:失败");
            return;
        }
        //判断是否是自动寻卡成功，寻卡成功数据区长度为7，总长度14，启动自动寻卡数据区长度为0，总长度为7。所以，当数据区长度为7，或者，长度为0但是总长度为14+7
        if (zm703.getDataSize() == 7) {
            if (logListener != null) logListener.value("寻卡成功");
            readM1();
        } else if (zm703.getDataSize() == 0 && zm703.getSize() == 21) {
            if (logListener != null) logListener.value("启动寻卡且寻卡成功");
            readM1();
        } else if (zm703.getDataSize() % 16 == 0) {//数据正好是16的倍数
            byte[][] data = SerialM1.getData(hexString);//连续读取结果会自动跳过密码块
            if (data == null || data.length == 0) return;
            //如果扇区
            if ((blockEnd - blockStart) == 63 && data.length != 48) {
                if (logListener != null) logListener.value("长度不够");
                if (failListener != null) failListener.value("长度不够");
                return;
            }
            m1DataHandle(data);
        }
    }

    /**
     * 读M1扇区指令
     */
    public void readM1() {
        //连续读取结果会自动跳过密码块，一次最多读4个扇区，也就是0-15扇区，应该返回12组数据
        byte[] cmd = SerialM1.getComplete(SerialM1.getCommandMultipleBlock(blockStart, blockEnd, keyType, YConvert.hexStringToByte(password)));
        if (logListener != null) logListener.value("发送串口命令:\t\t" + YConvert.bytesToHexString(cmd));
        ySerialPort.send(cmd);
    }

    /**
     * 读取到m1扇区的结果
     *
     * @param data
     */
    public void m1DataHandle(byte[][] data) {
        //LOG,显示
        StringBuilder sb1 = new StringBuilder("◆Byte[]");
        for (int i = 0; i < data.length; i++)
            sb1.append("\n").append(i).append("\t").append(":").append(Arrays.toString(data[i]));
        if (logListener != null)
            logListener.value(sb1.toString());

        //LOG,每个块内容
        StringBuilder sb2 = new StringBuilder("◆hexString：");
        for (int i = 0; i < data.length; i++) {
            byte[] item = data[i];
            sb2.append("\n" + i + "\t" + YConvert.bytesToHexString(item));
        }
        if (logListener != null)
            logListener.value(sb2.toString());

        //LOG,翻译每个块内容
        StringBuilder sb3 = new StringBuilder("\n◆基础翻译：");
        for (int i = 0; i < data.length; i++) {
            byte[] item = data[i];
            sb3.append("\n").append(i).append("\t").append("原始数据\t\t\t\t：").append(YConvert.bytesToHexString(item));
            try {
                sb3.append("\n").append(i).append("\t").append("翻译(US_ASCII)\t：").append(new String(item, StandardCharsets.US_ASCII));
                sb3.append("\n").append(i).append("\t").append("翻译(GB18030)\t：").append(new String(item, "GB18030"));
                sb3.append("\n").append(i).append("\t").append("翻译(GBK)\t\t\t\t：").append(new String(item, "GBK"));
                sb3.append("\n").append(i).append("\t").append("翻译(GB2312)\t\t：").append(new String(item, "GB2312"));
                sb3.append("\n").append(i).append("\t").append("翻译(UTF_8)\t\t\t：").append(new String(item, StandardCharsets.UTF_8));
                sb3.append("\n").append(i).append("\t").append("翻译(BCD)\t\t\t：").append(YConvert.bcd2String(item));
            } catch (Exception e) {
                sb3.append("\n").append(i).append("\t").append("翻译错误\t\t\t\t：").append(i).append(e.getMessage());
            }
        }
        if (logListener != null)
            logListener.value(sb3.toString());

        //赋值写数据
        StringBuilder writeBuilder = new StringBuilder();
        for (byte[] item : data) writeBuilder.append(YConvert.bytesToHexString(item));
        if (dataNoFListener != null) dataNoFListener.value(writeBuilder.toString());


        //添加虚拟密码块
        List<String> strings = new ArrayList<>();
        for (int i = 0; i < data.length; i++) {
            byte[] item = data[i];
            strings.add(YConvert.bytesToHexString(item));
            if (i % 3 == 2) strings.add("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF");
        }
        if (dataListListener != null)
            dataListListener.value(strings);
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < strings.size(); i++) builder.append(strings.get(i));
        if (dataListener != null)
            dataListener.value(builder.toString());
        if (logListener != null)
            logListener.value("◆最终返回：\n" + builder.toString());
    }

    /**
     * 长度加7，每个块长度16，连续读取结果会自动跳过密码块，如：读取0123456789扇块，会跳过3和7
     *
     * @return 长度
     */
    public int getDataLength() {
        int length = 7;
        if (blockStart == blockEnd)
            return length + 16;
        for (int i = blockStart; i <= blockEnd; i++)
            if (i % 4 != 3) length += 16;
        return length;
    }
}
