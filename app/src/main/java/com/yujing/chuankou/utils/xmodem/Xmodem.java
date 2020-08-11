package com.yujing.chuankou.utils.xmodem;


import com.yujing.yserialport.YListener;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * XModem协议
 *
 * @author yangle
 */
@SuppressWarnings("all")
public class Xmodem {
    // 开始
    private final byte SOH = 0x01;
    // 结束
    private final byte EOT = 0x04;
    // 应答
    private final byte ACK = 0x06;
    // 重传
    private final byte NAK = 0x15;
    // 无条件结束
    private final byte CAN = 0x18;

    // 以128字节块的形式传输数据
    private final int SECTOR_SIZE = 128;
    // 最大错误（无应答）包数
    private final int MAX_ERRORS = 10;

    // 输入流，用于读取串口数据
    private InputStream inputStream;
    // 输出流，用于发送串口数据
    private OutputStream outputStream;

    public Xmodem(InputStream inputStream, OutputStream outputStream) {
        this.inputStream = inputStream;
        this.outputStream = outputStream;
    }

    /**
     * 发送数据
     *
     * @param filePath 文件路径
     */
    public void send(String filePath) {
        send(filePath,null);
    }

    public void send(final String filePath, YListener<Boolean> listener) {
        new Thread() {
            public void run() {
                try {
                    // 错误包数
                    int errorCount;
                    // 包序号
                    byte blockNumber = 0x01;
                    // 校验和
                    int checkSum;
                    // 读取到缓冲区的字节数量
                    int nbytes;
                    // 初始化数据缓冲区
                    byte[] sector = new byte[SECTOR_SIZE];
                    // 读取文件初始化
                    DataInputStream inputStream = new DataInputStream(
                            new FileInputStream(filePath));
                    while ((nbytes = inputStream.read(sector)) > 0) {
                        // 如果最后一包数据小于128个字节，以0xff补齐
                        if (nbytes < SECTOR_SIZE) {
                            for (int i = nbytes; i < SECTOR_SIZE; i++) {
                                sector[i] = (byte) 0xff;
                            }
                        }
                        // 同一包数据最多发送10次
                        errorCount = 0;
                        while (errorCount < MAX_ERRORS) {
                            // 组包
                            // 控制字符 + 包序号 + 包序号的反码 + 数据区段 + 校验和
                            putData(SOH);
                            putData(blockNumber);
                            putData(~blockNumber);
                            checkSum = CRC16.calc(sector) & 0x00ffff;
                            putChar(sector, (short) checkSum);
                            outputStream.flush();
                            // 获取应答数据
                            byte data = getData();
                            // 如果收到应答数据则跳出循环，发送下一包数据
                            // 未收到应答，错误包数+1，继续重发
                            if (data == ACK) {
                                break;
                            } else {
                                ++errorCount;
                            }
                        }
                        // 包序号自增
                        blockNumber = (byte) ((++blockNumber) % 256);
                    }

                    // 所有数据发送完成后，发送结束标识
                    boolean isAck = false;
                    while (!isAck) {
                        putData(EOT);
                        isAck = getData() == ACK;
                    }
                    if (listener != null) {
                        listener.value(true);
                    }
                } catch (Exception e) {
                    if (listener != null) {
                        listener.value(false);
                    } else {
                        e.printStackTrace();
                    }
                }
            }

            ;
        }.start();
    }

    /**
     * 接收数据
     *
     * @param filePath 文件路径
     * @return 是否接收完成
     * @throws IOException 异常
     */
    public boolean receive(String filePath) throws Exception {
        // 错误包数
        int errorCount = 0;
        // 包序号
        byte blocknumber = 0x01;
        // 数据
        byte data;
        // 校验和
        int checkSum;
        // 初始化数据缓冲区
        byte[] sector = new byte[SECTOR_SIZE];
        // 写入文件初始化
        DataOutputStream outputStream = new DataOutputStream(
                new FileOutputStream(filePath));

        // 发送字符C，CRC方式校验
        putData((byte) 0x43);

        while (true) {
            if (errorCount > MAX_ERRORS) {
                outputStream.close();
                return false;
            }

            // 获取应答数据
            data = getData();
            if (data != EOT) {
                try {
                    // 判断接收到的是否是开始标识
                    if (data != SOH) {
                        errorCount++;
                        continue;
                    }

                    // 获取包序号
                    data = getData();
                    // 判断包序号是否正确
                    if (data != blocknumber) {
                        errorCount++;
                        continue;
                    }

                    // 获取包序号的反码
                    byte _blocknumber = (byte) ~getData();
                    // 判断包序号的反码是否正确
                    if (data != _blocknumber) {
                        errorCount++;
                        continue;
                    }

                    // 获取数据
                    for (int i = 0; i < SECTOR_SIZE; i++) {
                        sector[i] = getData();
                    }

                    // 获取校验和
                    checkSum = (getData() & 0xff) << 8;
                    checkSum |= (getData() & 0xff);
                    // 判断校验和是否正确
                    int crc = CRC16.calc(sector);
                    if (crc != checkSum) {
                        errorCount++;
                        continue;
                    }

                    // 发送应答
                    putData(ACK);
                    // 包序号自增
                    blocknumber++;
                    // 将数据写入本地
                    outputStream.write(sector);
                    // 错误包数归零
                    errorCount = 0;

                } catch (Exception e) {
                    e.printStackTrace();

                } finally {
                    // 如果出错发送重传标识
                    if (errorCount != 0) {
                        putData(NAK);
                    }
                }
            } else {
                break;
            }
        }

        // 关闭输出流
        outputStream.close();
        // 发送应答
        putData(ACK);

        return true;
    }

    /**
     * 获取数据
     *
     * @return 数据
     * @throws IOException 异常
     */
    private byte getData() throws IOException {
        return (byte) inputStream.read();
    }

    /**
     * 发送数据
     *
     * @param data 数据
     * @throws IOException 异常
     */
    private void putData(int data) throws IOException {
        outputStream.write((byte) data);
    }

    /**
     * 发送数据
     *
     * @param data     数据
     * @param checkSum 校验和
     * @throws IOException 异常
     */
    private void putChar(byte[] data, short checkSum) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(data.length + 2).order(
                ByteOrder.BIG_ENDIAN);
        bb.put(data);
        bb.putShort(checkSum);
        outputStream.write(bb.array());
    }
}