package com.yujing.yserialport;

import android.os.SystemClock;
import android.util.Log;

import java.io.InputStream;
import java.util.concurrent.TimeoutException;

/**
 * 读取InputStream
 *
 * @author yujing  2021年5月18日14:03:25
 */
/*
用法：
同步：
//只读一次，读取到就返回，读取不到就一直等
YReadInputStream.readOnce(inputStream);
//只读一次，读取到就返回。读取不到，一直等直到超时，如果超时则向上抛异常
YReadInputStream.readOnce(inputStream, timeOut);
//读取inputStream数据到YBytes,一直不停组包，至少读取时间：leastTime。
YReadInputStream.readTime(inputStream, leastTime);
//读取inputStream数据到YBytes,一直不停组包，至少读取时间：leastTime。但是期间读取长度达到minReadLength，立即返回。
YReadInputStream.readLength(inputStream, leastTime, minReadLength);

异步：
private YReadInputStream readInputStream;
readInputStream = new YReadInputStream(inputStream, bytes ->
    //读取到的数据：bytes
);
//至少读取长度，至少读取时间
readInputStream.setLengthAndTime(readLength, readTime);
//设置自动组包
readInputStream.setAutoPackage(true);
//设置最大组包时间
readInputStream.setMaxGroupPackageTime(100);
//设置无数据不返回
readInputStream.setNoDataNotReturn(noDataNotReturn);
//开始读取
readInputStream.start();
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
    private int readLength = -1;//至少读取长度
    private int readTime = -1;//至少读取时间，大于0时候生效
    private boolean noDataNotReturn = true;//无数据不返回

    public YReadInputStream(InputStream inputStream, YListener<byte[]> readListener) {
        this.inputStream = inputStream;
        this.readListener = readListener;
    }

    //开始读取
    public void start() {
        readThread = new ReadThread();
        readThread.setName("YSerialPort-YReadInputStream-读取线程");
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
                        byte[] bytes = (!autoPackage && readTime > 0 && readLength > 0) ?
                                readLength(inputStream, readTime, readLength).getBytes() :
                                readTime(inputStream, maxGroupPackageTime).getBytes();
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

    public void setLengthAndTime(int readLength, int readTime) {
        this.readLength = readLength;
        this.readTime = readTime;
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
     * 只读一次，读取到就返回，读取不到就一直等
     *
     * @param inputStream inputStream
     * @return byte[]
     * @throws Exception Exception
     */
    public static byte[] readOnce(InputStream inputStream) throws Exception {
        int count = 0;
        while (count == 0) count = inputStream.available();//获取真正长度
        byte[] bytes = new byte[count];
        // 一定要读取count个数据，如果inputStream.read(bytes);可能读不完
        int readCount = 0; // 已经成功读取的字节的个数
        while (readCount < count)
            readCount += inputStream.read(bytes, readCount, count - readCount);
        return bytes;
    }

    /**
     * 只读一次，读取到就返回。读取不到，一直等直到超时，如果超时则向上抛异常
     *
     * @param inputStream inputStream
     * @param timeOut     超时毫秒
     * @return byte[]
     * @throws Exception Exception
     */
    public static byte[] readOnce(InputStream inputStream, long timeOut) throws Exception {
        long startTime = System.currentTimeMillis();
        int count = 0;
        while (count == 0 && System.currentTimeMillis() - startTime < timeOut)
            count = inputStream.available();//获取真正长度
        if (System.currentTimeMillis() - startTime >= timeOut) {
            throw new TimeoutException("读取超时");
        }
        byte[] bytes = new byte[count];
        // 一定要读取count个数据，如果inputStream.read(bytes);可能读不完
        int readCount = 0; // 已经成功读取的字节的个数
        while (readCount < count)
            readCount += inputStream.read(bytes, readCount, count - readCount);
        return bytes;
    }

    public static YBytes readTime(InputStream inputStream, int maxGroupTime) throws Exception {
        return readTime(inputStream, maxGroupTime, Integer.MAX_VALUE);
    }

    /**
     * 读取inputStream数据到YBytes,一直不停组包，至少读取时间：leastTime。
     *
     * @param inputStream inputStream
     * @param maxGroupTime 最大组包时间，如果这个时间内有数据，就一直组包。如果这个时间都没数据，就返回。
     * @param maxTime     最多读取这么长时间
     * @return YBytes
     * @throws Exception Exception
     */
    public static YBytes readTime(InputStream inputStream, int maxGroupTime, int maxTime) throws Exception {
        final YBytes bytes = new YBytes();
        long startTime = System.currentTimeMillis();//开始时间
        long groupTime;//运行时间
        int i = 0;//第几次组包
        int count = inputStream.available();//可读取多少字节内容
        do {
            byte[] newBytes = new byte[1024];
            int newSize = inputStream.read(newBytes, 0, count);
            if (newSize > 0) {
                bytes.addByte(newBytes, newSize);
                log("第" + (++i) + "次组包后长度：" + bytes.getBytes().length + "，\t已耗时：" + (System.currentTimeMillis() - startTime));
            }
            if (sleep) SystemClock.sleep(1);
            count = inputStream.available();
            groupTime = System.currentTimeMillis();
            //如果读取长度为0，那么休息1毫秒继续读取，如果在timeOut时间内都没有数据，那么就退出循环
            while (count == 0 && System.currentTimeMillis() - groupTime <= maxGroupTime) {
                if (sleep) SystemClock.sleep(1);
                count = inputStream.available();
            }
        } while (System.currentTimeMillis() - groupTime <= maxGroupTime && System.currentTimeMillis() - startTime <= maxTime);
        return bytes;
    }

    /**
     * 读取inputStream数据到YBytes,一直不停组包，至少读取时间：leastTime。但是期间读取长度达到minReadLength，立即返回。
     *
     * @param inputStream inputStream
     * @param maxTime   最多读取这么长时间
     * @param minLength   至少读取长度，即使没有读取到timeOut时间，只要读取长度大于等于minReadLength，直接返回
     * @return YBytes
     * @throws Exception Exception
     */
    public static YBytes readLength(final InputStream inputStream, final int minLength, final int maxTime) throws Exception {
        final YBytes bytes = new YBytes();
        long startTime = System.currentTimeMillis();
        int i = 0;
        while (bytes.getBytes().length < minLength && System.currentTimeMillis() - startTime < maxTime) {
            //如果可读取消息为0，就不继续。防止InputStream.read阻塞
            if (inputStream.available() == 0) {
                if (sleep) SystemClock.sleep(1);
                continue;
            }
            byte[] newBytes = new byte[Math.max(minLength, 1024)];
            int newSize = inputStream.read(newBytes, 0, inputStream.available());
            if (newSize > 0) {
                bytes.addByte(newBytes, newSize);
                log("第" + (++i) + "次组包后长度：" + bytes.getBytes().length + "，\t目标长度：" + minLength + "，\t已耗时：" + (System.currentTimeMillis() - startTime) + "ms，\t超时时间：" + maxTime + "ms");
            }
        }
        if (System.currentTimeMillis() - startTime >= maxTime)
            log("超时返回，超时时间：" + maxTime + "ms");
        return bytes;
    }
}
