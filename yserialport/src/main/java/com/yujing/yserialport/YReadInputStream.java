package com.yujing.yserialport;

import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

/**
 * 读取InputStream
 *
 * @author yujing 2020年9月22日17:27:55
 */

public class YReadInputStream {
    private static final String TAG = "YRead";
    private static boolean showLog = false;
    private InputStream inputStream;
    private YListener<byte[]> readListener;
    private ReadThread readThread;
    private boolean autoPackage = true;//自动组包
    private int maxGroupPackageTime = 1;//组包时间差，毫秒
    private int readLength = -1;//读取长度
    private int readTimeout = -1;//读取超时时间
    private boolean noDataNotReturn = true;//无数据不返回

    public YReadInputStream(InputStream inputStream, YListener<byte[]> readListener) {
        this.inputStream = inputStream;
        this.readListener = readListener;
    }

    //开始读取
    public void start() {
        readThread = new ReadThread();
        readThread.start();
    }

    //停止
    public void stop() {
        if (readThread != null) {
            readThread.interrupt();
        }
    }

    private class ReadThread extends Thread {
        @Override
        public void run() {
            log("开启一个读取线程");
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    //如果可读取消息为0，就不继续。防止InputStream.read阻塞
                    if (inputStream.available() == 0) {
                        SystemClock.sleep(1);//休息1毫秒
                        continue;
                    }
                    if (readListener != null) {
                        byte[] bytes = (!autoPackage && readTimeout > 0 && readLength > 0) ?
                                read(inputStream, readTimeout, readLength).getBytes() :
                                read(inputStream, maxGroupPackageTime).getBytes();
                        //无数据不返回
                        if (!noDataNotReturn || bytes.length != 0) readListener.value(bytes);
                    }
                } catch (Throwable e) {
                    log("读取线程异常", e);
                }
            }
            log("关闭一个读取线程");
        }
    }

    private static void log(String string) {
        if (showLog) Log.i(TAG, string);
    }

    private static void log(String string, Throwable e) {
        if (showLog) Log.e(TAG, string, e);
    }

    public static boolean isShowLog() {
        return showLog;
    }

    public static void setShowLog(boolean showLog) {
        YReadInputStream.showLog = showLog;
    }


    public void setLengthAndTimeout(int readLength, int readTimeout) {
        this.readLength = readLength;
        this.readTimeout = readTimeout;
    }

    public int getMaxGroupPackageTime() {
        return maxGroupPackageTime;
    }

    public void setMaxGroupPackageTime(int maxGroupPackageTime) {
        this.maxGroupPackageTime = maxGroupPackageTime;
    }

    public boolean isAutoPackage() {
        return autoPackage;
    }

    public void setAutoPackage(boolean autoPackage) {
        this.autoPackage = autoPackage;
    }

    public boolean isNoDataNotReturn() {
        return noDataNotReturn;
    }

    public void setNoDataNotReturn(boolean noDataNotReturn) {
        this.noDataNotReturn = noDataNotReturn;
    }
    //★★★★★★★★★★★★★★★★★★★★★★★★★★★★★读流操作★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★

    /**
     * 读取inputStream数据到 YBytes 每次组包时间不大于 groupPackageTime，如果groupPackageTime内有数据，继续组包，否则理解返回
     *
     * @param inputStream         inputStream
     * @param maxGroupPackageTime 每次组包间隔不大于groupPackageTime
     * @return YBytes
     * @throws IOException IO异常
     */
    public static YBytes read(InputStream inputStream, int maxGroupPackageTime) throws IOException {
        final YBytes bytes = new YBytes();
        final long startTime = System.currentTimeMillis();
        int i = 0;//第几次组包
        int available = inputStream.available();// 可读取多少字节内容
        int packageTime = 0;//每次组包时间间隔
        do {
            byte[] newBytes = new byte[1024];
            int newSize = inputStream.read(newBytes, 0, available);
            if (newSize > 0) {
                bytes.addByte(newBytes, newSize);
                log("第" + (++i) + "次组包后长度：" + bytes.getBytes().length + "，\t组包间隔：" + (packageTime) + "，\t最大间隔：" + (maxGroupPackageTime) + "ms，\t已耗时：" + (System.currentTimeMillis() - startTime));
            }
            SystemClock.sleep(1);//休息1毫秒
            available = inputStream.available();// 可读取多少字节内容
            packageTime = 1;//组包时间间隔1ms
            //如果读取长度为0，那么休息1毫秒继续读取，如果在groupPackageTime时间内都没有数据，那么就退出循环
            if (available == 0) {
                for (int j = 0; j <= maxGroupPackageTime; j++) {
                    SystemClock.sleep(1);//休息1毫秒
                    packageTime++;//组包时间间隔+1ms
                    available = inputStream.available();// 可读取多少字节内容
                    if (available != 0) break;//如果读取到数据立即关闭循环
                }
            }
            //如果组包countLength0次后，大于设置的时间就退出读取
        } while (packageTime <= maxGroupPackageTime);
        return bytes;
    }

    /**
     * 读取inputStream数据到 YBytes
     * 至少读取minReadLength位，如果读取长度大于等于minReadLength，直接返回
     * 如果超时，直接返回
     *
     * @param inputStream   inputStream
     * @param timeOut       超时时间，大于这个时间直接返回
     * @param minReadLength 至少读取，如果读取长度大于等于minReadLength，直接返回
     * @return YBytes
     * @throws IOException IO异常
     */
    public static YBytes read(final InputStream inputStream, final int timeOut, final int minReadLength) throws IOException {
        final YBytes bytes = new YBytes();
        long startTime = System.currentTimeMillis();
        int i = 0;
        while (bytes.getBytes().length < minReadLength && System.currentTimeMillis() - startTime < timeOut) {
            //如果可读取消息为0，就不继续。防止InputStream.read阻塞
            if (inputStream.available() == 0) {
                SystemClock.sleep(1);//休息1毫秒
                continue;
            }
            byte[] newBytes = new byte[Math.max(minReadLength, 1024)];
            int newSize = inputStream.read(newBytes, 0, inputStream.available());
            if (newSize > 0) {
                bytes.addByte(newBytes, newSize);
                log("第" + (++i) + "次组包后长度：" + bytes.getBytes().length + "，\t目标长度：" + minReadLength + "，\t已耗时：" + (System.currentTimeMillis() - startTime) + "ms，\t超时时间：" + timeOut + "ms");
            }
        }
        if (System.currentTimeMillis() - startTime >= timeOut) log("超时返回，超时时间：" + timeOut + "ms");
        return bytes;
    }
}
