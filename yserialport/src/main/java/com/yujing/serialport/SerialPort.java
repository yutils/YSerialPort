/*
 * Copyright 2009 Cedric Priscal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yujing.serialport;

import android.util.Log;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 串口工具
 *
 * @author yujing 2021年11月9日15:58:16
 * 不合其他串口工具发生so冲突，类名冲突
 */
/*用法
//创建
SerialPort serialPort = SerialPort.newBuilder(new File("/dev/ttyS4"), 9600).build();
//获取输入流
serialPort.getInputStream();
//获取输出流
serialPort.getOutputStream();
//关闭
serialPort.tryClose();
 */
public final class SerialPort {
    private static final String TAG = "SerialPort";
    private static String suPath = "/system/bin/su";
    private File device; //串口设备文件
    private int baudRate; //波特率
    private int dataBits; //数据位；默认8,可选值为5~8
    private int parity; //奇偶校验；0:无校验位(NONE，默认)；1:奇校验位(ODD);2:偶校验位(EVEN)
    private int stopBits; //停止位；默认1；1:1位停止位；2:2位停止位
    private int flags;//默认0
    private int flowControl;//流控；0:无(默认) 1:硬件RTS/CTS 2:软件XON/XOFF
    private FileDescriptor mFd;
    private FileInputStream mFileInputStream;
    private FileOutputStream mFileOutputStream;

    //JNI 打开串口
    private native FileDescriptor open(String absolutePath, int baudRate, int dataBits, int parity, int stopBits, int flags);

    /**
     * JNI 打开串口（增强版）
     * parity 额外支持 3:MARK 4:SPACE；stopBits 额外支持 3:1.5位；并支持流控
     */
    private native FileDescriptor openEx(String absolutePath, int baudRate, int dataBits, int parity, int stopBits, int flags, int flowControl);

    //JNI 关闭串口
    public native void close();

    /**
     * 清空串口收发缓冲，返回 0 表示成功
     */
    public native int flushBuffers();

    static {
        try {
            System.loadLibrary("YSerialPort");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "加载JNI库失败，请检查环境配置", e);
            //throw new RuntimeException("无法加载串口库", e);
        }
    }

    public static void setSuPath(String suPath) {
        if (suPath == null) return;
        SerialPort.suPath = suPath;
    }

    public static String getSuPath() {
        return suPath;
    }

    /**
     * 串口
     *
     * @param device   串口设备文件
     * @param baudRate 波特率
     * @param dataBits 数据位；默认8,可选值为5~8
     * @param parity   奇偶校验；0:无校验位(NONE，默认)；1:奇校验位(ODD);2:偶校验位(EVEN)
     * @param stopBits 停止位；默认1；1:1位停止位；2:2位停止位
     * @param flags    默认0
     * @throws SecurityException
     * @throws IOException
     */
    public SerialPort(File device, int baudRate, int dataBits, int parity, int stopBits, int flags) throws SecurityException, IOException {
        this(device, baudRate, dataBits, parity, stopBits, flags, 0);
    }

    /**
     * 串口
     *
     * @param device      串口设备文件
     * @param baudRate    波特率
     * @param dataBits    数据位；默认8,可选值为5~8
     * @param parity      奇偶校验；0:无校验位(NONE，默认)；1:奇校验位(ODD);2:偶校验位(EVEN);3:MARK;4:SPACE
     * @param stopBits    停止位；默认1；1:1位停止位；2:2位停止位;3:1.5位停止位(需5位数据位)
     * @param flags       默认0
     * @param flowControl 流控；0:无(默认) 1:硬件RTS/CTS 2:软件XON/XOFF
     * @throws SecurityException
     * @throws IOException
     */
    public SerialPort(File device, int baudRate, int dataBits, int parity, int stopBits, int flags, int flowControl)
            throws SecurityException, IOException {
        this.device = device;
        this.baudRate = baudRate;
        this.dataBits = dataBits;
        this.parity = parity;
        this.stopBits = stopBits;
        this.flags = flags;
        this.flowControl = flowControl;

        /* 检查访问权限 */
        if (!device.canRead() || !device.canWrite()) {
            Process su = null;
            try {
                su = Runtime.getRuntime().exec(suPath);
                // 消费stdout、stderr，防止缓冲区塞满阻塞进程
                final Process process = su;
                new Thread(() -> {
                    try {
                        byte[] buf = new byte[1024];
                        while (process.getInputStream().read(buf) != -1) ;
                    } catch (IOException ignored) {
                    }
                }).start();
                new Thread(() -> {
                    try {
                        byte[] buf = new byte[1024];
                        while (process.getErrorStream().read(buf) != -1) ;
                    } catch (IOException ignored) {
                    }
                }).start();

                String cmd = "chmod 666 " + device.getAbsolutePath() + "\n" + "exit\n";
                su.getOutputStream().write(cmd.getBytes());
                su.getOutputStream().flush(); // 强制刷入命令
                su.getOutputStream().close();

                int exitCode = su.waitFor();
                // 重新new File，刷新Java权限缓存
                File newDevice = new File(device.getAbsolutePath());
                if (exitCode != 0 || !newDevice.canRead() || !newDevice.canWrite()) {
                    throw new SecurityException("chmod失败");
                }
                // 更新引用
                device = newDevice;
            } catch (Exception e) {
                e.printStackTrace();
                if (su != null) {
                    su.destroy();
                }
                throw new SecurityException("没有权限，chmod失败", e);
            }
        }

        // 仅在用到扩展参数时才走 openEx，保证原有行为完全不变
        boolean needExt = flowControl != 0 || parity > 2 || stopBits == 3;
        mFd = needExt
                ? openEx(device.getAbsolutePath(), baudRate, dataBits, parity, stopBits, flags, flowControl)
                : open(device.getAbsolutePath(), baudRate, dataBits, parity, stopBits, flags);
        if (mFd == null) {
            Log.e(TAG, "打开串口失败");
            throw new IOException();
        }
        mFileInputStream = new FileInputStream(mFd);
        mFileOutputStream = new FileOutputStream(mFd);
    }

    /**
     * 串口，默认的8n1
     *
     * @param device   串口设备文件
     * @param baudRate 波特率
     * @throws SecurityException
     * @throws IOException
     */
    public SerialPort(File device, int baudRate) throws SecurityException, IOException {
        this(device, baudRate, 8, 0, 1, 0);
    }

    /**
     * 串口
     *
     * @param device   串口设备文件
     * @param baudRate 波特率
     * @param dataBits 数据位；默认8,可选值为5~8
     * @param parity   奇偶校验；0:无校验位(NONE，默认)；1:奇校验位(ODD);2:偶校验位(EVEN)
     * @param stopBits 停止位；默认1；1:1位停止位；2:2位停止位
     * @throws SecurityException
     * @throws IOException
     */
    public SerialPort(File device, int baudRate, int dataBits, int parity, int stopBits)
            throws SecurityException, IOException {
        this(device, baudRate, dataBits, parity, stopBits, 0);
    }

    public InputStream getInputStream() {
        return mFileInputStream;
    }

    public OutputStream getOutputStream() {
        return mFileOutputStream;
    }

    /**
     * 串口设备文件
     */
    public File getDevice() {
        return device;
    }

    /**
     * 波特率
     */
    public int getBaudRate() {
        return baudRate;
    }

    /**
     * 数据位；默认8,可选值为5~8
     */
    public int getDataBits() {
        return dataBits;
    }

    /**
     * 奇偶校验；0:无校验位(NONE，默认)；1:奇校验位(ODD);2:偶校验位(EVEN)
     */
    public int getParity() {
        return parity;
    }

    /**
     * 停止位；默认1；1:1位停止位；2:2位停止位
     */
    public int getStopBits() {
        return stopBits;
    }

    public int getFlags() {
        return flags;
    }

    /**
     * 流控；0:无 1:硬件RTS/CTS 2:软件XON/XOFF
     */
    public int getFlowControl() {
        return flowControl;
    }

    /**
     * 关闭流和串口，已经try-catch
     */
    public void tryClose() {
        try {
            close();
        } catch (Exception ignored) {
        }

        try {
            if (mFileInputStream != null) {
                mFileInputStream.close();
                mFileInputStream = null;
            }
        } catch (IOException ignored) {
        }

        try {
            if (mFileOutputStream != null) {
                mFileOutputStream.close();
                mFileOutputStream = null;
            }
        } catch (IOException ignored) {
        }
    }


    public static Builder newBuilder(File device, int baudRate) {
        return new Builder(device, baudRate);
    }

    public static Builder newBuilder(String devicePath, int baudRate) {
        return new Builder(devicePath, baudRate);
    }

    public final static class Builder {
        private File device; //串口设备文件
        private int baudRate; //波特率
        private int dataBits = 8;  //数据位；默认8,可选值为5~8
        private int parity = 0; //奇偶校验；0:无校验位(NONE，默认)；1:奇校验位(ODD);2:偶校验位(EVEN)
        private int stopBits = 1;  //停止位；默认1；1:1位停止位；2:2位停止位
        private int flags = 0; //默认0
        private int flowControl = 0; //流控；0:无(默认) 1:硬件RTS/CTS 2:软件XON/XOFF

        private Builder(File device, int baudRate) {
            this.device = device;
            this.baudRate = baudRate;
        }

        private Builder(String devicePath, int baudRate) {
            this(new File(devicePath), baudRate);
        }

        /**
         * 数据位
         *
         * @param dataBits 默认8,可选值为5~8
         * @return
         */
        public Builder dataBits(int dataBits) {
            this.dataBits = dataBits;
            return this;
        }

        /**
         * 校验位
         *
         * @param parity 0:无校验位(NONE，默认)；1:奇校验位(ODD);2:偶校验位(EVEN)
         * @return
         */
        public Builder parity(int parity) {
            this.parity = parity;
            return this;
        }

        /**
         * 停止位
         *
         * @param stopBits 默认1；1:1位停止位；2:2位停止位
         * @return
         */
        public Builder stopBits(int stopBits) {
            this.stopBits = stopBits;
            return this;
        }

        /**
         * 标志
         *
         * @param flags 默认0
         * @return
         */
        public Builder flags(int flags) {
            this.flags = flags;
            return this;
        }

        /**
         * 流控
         *
         * @param flowControl 0:无(默认) 1:硬件RTS/CTS 2:软件XON/XOFF
         * @return
         */
        public Builder flowControl(int flowControl) {
            this.flowControl = flowControl;
            return this;
        }

        /**
         * 打开并返回串口
         *
         * @return
         * @throws SecurityException
         * @throws IOException
         */
        public SerialPort build() throws SecurityException, IOException {
            return new SerialPort(device, baudRate, dataBits, parity, stopBits, flags, flowControl);
        }
    }
}
