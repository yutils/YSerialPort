package com.yujing.chuankou.activity.myTest.zm703;

import android.util.Log;

import com.yujing.utils.YConvert;
import com.yujing.utils.YConvertNumberBytes;

import java.util.Arrays;

/**
 * ZM703读卡器返回实体
 */
public class ZM703 {
    private final static String TAG = "ZM703";
    private final static String HEAD = "55AAFF";
    //完整hexString
    private String hexString;
    //完整bytes
    private byte[] bytes;
    //状态，状态位第五位为FF为正确
    private boolean status;
    //状态，状态byte
    private byte statusByte;
    //状态，状态hexString
    private String statushexString;

    //数据区的HexString
    private String dataHexString;
    //数据区的Bytes
    private byte[] dataBytes;
    //数据区的长度
    private int dataSize;

    public ZM703(String hexString, byte[] bytes) {
        this.hexString = hexString;
        this.bytes = bytes;
        init();
    }

    private void init() {
        //长度不够7直接错误
        if (bytes.length < 7) {
            status = false;
            Log.i(TAG, "长度不正确");
            return;
        }
        if (!HEAD.equals(hexString.substring(0, 6))) {
            status = false;
            Log.i(TAG, "头部不正确");
            return;
        }
        statusByte = bytes[5];
        statushexString = YConvert.bytesToHexString(new byte[]{statusByte});
        //长度状态位不是ff直接错误
        if (bytes[5] != (byte) 255) {
            status = false;
            Log.i(TAG, "状态失败");
            return;
        }
        //获取hex字符串真实长度
        byte[] lengthByte = new byte[]{0, 0, bytes[3], bytes[4]};
        int length = YConvertNumberBytes.bytesToInt(lengthByte);
        length += 5;//加上前面5个固定位置
        if (hexString.length() < length * 2) {
            Log.i(TAG, "长度不够，抛弃。需要长度：" + (length) + "实际长度：" + hexString.length() / 2);
            status = false;
            return;
        }

        status = true;
        dataSize = length - 7;//减去固定位5，校验位1，状态位1

        dataHexString = hexString.substring(12, hexString.length() - 2);
        dataBytes = new byte[dataSize];
        System.arraycopy(bytes, 6, dataBytes, 0, dataSize);

    }

    public String getHexString() {
        return hexString;
    }

    public void setHexString(String hexString) {
        this.hexString = hexString;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }

    public String getDataHexString() {
        return dataHexString;
    }

    public void setDataHexString(String dataHexString) {
        this.dataHexString = dataHexString;
    }

    public byte[] getDataBytes() {
        return dataBytes;
    }

    public void setDataBytes(byte[] dataBytes) {
        this.dataBytes = dataBytes;
    }

    public boolean isStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public int getDataSize() {
        return dataSize;
    }

    public void setDataSize(int dataSize) {
        this.dataSize = dataSize;
    }

    public byte getStatusByte() {
        return statusByte;
    }

    public void setStatusByte(byte statusByte) {
        this.statusByte = statusByte;
    }

    public String getStatushexString() {
        return statushexString;
    }

    public void setStatushexString(String statushexString) {
        this.statushexString = statushexString;
    }

    @Override
    public String toString() {
        return "ZM703{" +
                "hexString='" + hexString + '\'' +
                ", bytes=" + Arrays.toString(bytes) +
                ", status=" + status +
                ", statusByte=" + statusByte +
                ", statushexString='" + statushexString + '\'' +
                ", dataHexString='" + dataHexString + '\'' +
                ", dataBytes=" + Arrays.toString(dataBytes) +
                ", dataSize=" + dataSize +
                '}';
    }
}
