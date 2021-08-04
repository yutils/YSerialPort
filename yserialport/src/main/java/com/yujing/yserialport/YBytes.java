package com.yujing.yserialport;

import java.util.ArrayList;
import java.util.List;

/**
 * byte拼接类
 *
 * @author YuJing 2019年12月5日09:39:55
 */
@SuppressWarnings("unused")
public class YBytes {
    private byte[] bytes;

    /**
     * 构造函数，创建一个长度为0的byte数组
     */
    public YBytes() {
        bytes = new byte[0];
    }

    /**
     * 构造函数，创建一个长度为i的byte数组
     *
     * @param i byte[]长度
     */
    public YBytes(int i) {
        bytes = new byte[i];
    }

    /**
     * 构造函数创建一个初始的byte数组
     *
     * @param b 初始数组
     */
    public YBytes(byte[] b) {
        this.bytes = new byte[b.length];
        System.arraycopy(b, 0, this.bytes, 0, b.length);
    }

    /**
     * 在byte数组末尾添加一个byte
     *
     * @param b b
     * @return YBytes
     */
    public YBytes addByte(byte b) {
        byte[] temp = new byte[bytes.length + 1];
        System.arraycopy(bytes, 0, temp, 0, bytes.length);
        temp[temp.length - 1] = b;
        bytes = temp;
        return this;
    }

    /**
     * 在byte数组末尾添加一个byte[]
     *
     * @param bs bs
     * @return YBytes
     */
    public YBytes addByte(byte[] bs) {
        byte[] temp = new byte[bytes.length + bs.length];
        System.arraycopy(bytes, 0, temp, 0, bytes.length);
        System.arraycopy(bs, 0, temp, bytes.length, bs.length);
        bytes = temp;
        return this;
    }

    /**
     * 在byte数组末尾添加一个byte[],给定添加的长度
     *
     * @param bs     添加的数组
     * @param length 添加的长度
     * @return Bytes
     */
    public YBytes addByte(byte[] bs, int length) {
        byte[] temp = new byte[bytes.length + length];
        System.arraycopy(bytes, 0, temp, 0, bytes.length);
        System.arraycopy(bs, 0, temp, bytes.length, length);
        bytes = temp;
        return this;
    }

    /**
     * 在byte数组末尾添加一个byte[],给定添加的长度
     *
     * @param bs     添加的数组
     * @param start  开始位置
     * @param length 添加的长度
     * @return Bytes
     */
    public YBytes addByte(byte[] bs, int start, int length) {
        byte[] temp = new byte[bytes.length + length];
        System.arraycopy(bytes, 0, temp, 0, bytes.length);
        System.arraycopy(bs, start, temp, bytes.length, length);
        bytes = temp;
        return this;
    }

    /**
     * 在byte数组末尾添加一组Byte
     *
     * @param bs bs
     * @return YBytes
     */
    public YBytes addByte(List<Byte> bs) {
        byte[] temp = new byte[bytes.length + bs.size()];
        System.arraycopy(bytes, 0, temp, 0, bytes.length);
        for (int i = 0; i < bs.size(); i++) {
            temp[bytes.length + i] = bs.get(i);
        }
        bytes = temp;
        return this;
    }

    /**
     * 在byte数组末尾添加一个List
     *
     * @param bs     添加的集合
     * @param length 添加的长度
     * @return Bytes
     */
    public YBytes addByte(List<Byte> bs, int length) {
        byte[] temp = new byte[bytes.length + length];
        System.arraycopy(bytes, 0, temp, 0, bytes.length);
        for (int i = 0; i < length; i++) {
            temp[bytes.length + i] = bs.get(i);
        }
        bytes = temp;
        return this;
    }

    /**
     * 在byte数组末尾添加一个List
     *
     * @param bs     添加的集合
     * @param start  开始位置
     * @param length 添加的长度
     * @return Bytes
     */
    public YBytes addByte(List<Byte> bs, int start, int length) {
        byte[] temp = new byte[bytes.length + length];
        System.arraycopy(bytes, 0, temp, 0, bytes.length);
        for (int i = 0; i < length; i++) {
            temp[bytes.length + i] = bs.get(i + start);
        }
        bytes = temp;
        return this;
    }

    /**
     * 修改byte数组中一位的值为byte
     *
     * @param b     数据
     * @param index 位置
     * @return YBytes
     */
    public YBytes changeByte(byte b, int index) {
        if (index >= 0 && index < bytes.length) {
            bytes[index] = b;
        }
        return this;
    }

    /**
     * 修改byte数组中第index位起值为b
     *
     * @param b     数据
     * @param index 位置
     * @return YBytes
     */
    public YBytes changeByte(byte[] b, int index) {
        return changeByte(b, index, index + b.length);
    }

    /**
     * 修改byte数组中第start位起值为b，连续修改length位
     *
     * @param b      数据
     * @param start  起始位置
     * @param length 结束位置
     * @return YBytes
     */
    public YBytes changeByte(byte[] b, int start, int length) {
        if (start >= 0 && length > 0) {
            for (int i = 0; i < length; i++) {
                if (start + i < bytes.length) {
                    bytes[start + i] = b[i];
                }
            }
        }
        return this;
    }

    /**
     * 修改byte数组中第index位起值为b
     *
     * @param b     数据
     * @param index 位置
     * @return YBytes
     */
    public YBytes changeByte(List<Byte> b, int index) {
        return changeByte(b, index,  b.size());
    }

    /**
     * 修改byte数组中第start位起值为b，连续修改length位
     *
     * @param b      数据
     * @param start  起始位置
     * @param length 结束位置
     * @return YBytes
     */
    public YBytes changeByte(List<Byte> b, int start, int length) {
        if (start >= 0 && length > 0) {
            for (int i = 0; i < length; i++) {
                if (start + i < bytes.length) {
                    bytes[start + i] = b.get(i);
                }
            }
        }
        return this;
    }

    /**
     * 替换bytes数组
     *
     * @param bytes bytes
     */
    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }

    /**
     * 拆分byte数组为多个byte数组
     *
     * @param bytes  需要拆分的数组
     * @param length 每组最大长度
     * @return 最终拆分的数据
     */
    public static List<byte[]> split(byte[] bytes, int length) {
        List<byte[]> list = new ArrayList<>();
        int count = 0;//统计已经发送长度
        while (true) {
            //剩余长度
            int sy = bytes.length - count;
            //如果剩余长度小于等于0，说明发送完成
            if (sy <= 0) break;
            //如果剩余长度大于每次写入长度，就写入对应长度，如果不大于就写入剩余长度
            byte[] current = new byte[Math.min(sy, length)];
            //数组copy
            System.arraycopy(bytes, count, current, 0, current.length);
            //写入
            list.add(current);
            //统计已经发送长度
            count += current.length;
        }
        return list;
    }

    /**
     * 拆分byte数组为多个byte数组
     *
     * @param length 每组最大长度
     * @return 最终拆分的数据
     */
    public List<byte[]> split(int length) {
        return split(bytes, length);
    }

    /**
     * 获取bytes数组
     *
     * @return byte[]
     */
    public byte[] getBytes() {
        return bytes;
    }
}