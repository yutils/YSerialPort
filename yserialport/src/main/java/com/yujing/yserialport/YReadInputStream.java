package com.yujing.yserialport;

import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

/**
 * 读取InputStream
 *
 * @author yujing  2021年5月18日14:03:25
 */
public class YReadInputStream {
    private static final String TAG = "YRead";
    private static boolean showLog = false;
    //轮询时候，是否休息1毫秒。inputStream.available()，如果不休息将会增加CPU功耗。
    private static boolean sleep = true;
    private InputStream inputStream;
    private YListener<byte[]> readListener;
    private ReadThread readThread;
    private boolean autoPackage = true;//自动组包
    private int maxGroupPackageTime = 1;//组包时间差，毫秒
    private int readLength = -1;//读取长度
    private int readTimeout = -1;//读取超时时间，大于0时候生效
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
            while (!this.isInterrupted()) {
                try {
                    //如果可读取消息为0，就不继续。防止InputStream.read阻塞
                    if (inputStream.available() == 0) {
                        if (sleep) SystemClock.sleep(1);//休息1毫秒
                        continue;
                    }
                    //如果读取到了数据，而且readListener不为空
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

    public static boolean isSleep() {
        return sleep;
    }

    public static void setSleep(boolean sleep) {
        YReadInputStream.sleep = sleep;
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
     * 读取inputStream数据到YBytes,一直不停组包，直到连续timeOut时间内都没数据，就返回
     *
     * @param inputStream inputStream
     * @param timeOut     每次组包间隔不大于timeOut
     * @return YBytes
     * @throws IOException IO异常
     */
    public static YBytes read(InputStream inputStream, int timeOut) throws IOException {
        final YBytes bytes = new YBytes();
        long startTime = System.currentTimeMillis();//开始时间
        long runTime;//运行时间
        int i = 0;//第几次组包
        int count = inputStream.available();// 可读取多少字节内容
        do {
            byte[] newBytes = new byte[1024];
            int newSize = inputStream.read(newBytes, 0, count);
            if (newSize > 0) {
                bytes.addByte(newBytes, newSize);
                log("第" + (++i) + "次组包后长度：" + bytes.getBytes().length + "，\t已耗时：" + (System.currentTimeMillis() - startTime));
            }
            if (sleep) SystemClock.sleep(1);
            count = inputStream.available();
            runTime = System.currentTimeMillis();
            //如果读取长度为0，那么休息1毫秒继续读取，如果在timeOut时间内都没有数据，那么就退出循环
            while (count == 0 && System.currentTimeMillis() - runTime <= timeOut) {
                if (sleep) SystemClock.sleep(1);
                count = inputStream.available();
            }
        } while (System.currentTimeMillis() - runTime <= timeOut);
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
                if (sleep) SystemClock.sleep(1);
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
