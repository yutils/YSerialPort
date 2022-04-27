package com.yujing.yserialport;

import android.os.SystemClock;
import android.util.Log;

import java.io.InputStream;
import java.util.concurrent.TimeoutException;

/**
 * 读取InputStream
 *
 * @author yujing  2021年11月12日15:20:08
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
//设置自动组包
readInputStream.setToAuto(10);
//设置手自动组包，读取长度100，超时时间为50毫秒。如果读取到数据大于等于100立即返回，否则直到读取到超时为止
//readInputStream.setToManual(100,50);
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
    private int maxTime = -1;//至少读取时间，大于0时候生效

    private boolean noDataNotReturn = true;//无数据不返回

    public YReadInputStream() {
    }

    public YReadInputStream(InputStream inputStream, YListener<byte[]> readListener) {
        this.inputStream = inputStream;
        this.readListener = readListener;
    }

    public void setInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    public void setReadListener(YListener<byte[]> readListener) {
        this.readListener = readListener;
    }

    //开始读取
    public void start() {
        readThread = new ReadThread();
        readThread.setName("YReadInputStream-读取线程");
        readThread.start();
    }

    //停止
    public void stop() {
        if (readThread != null) {
            readThread.interrupt();
        }
    }

    /**
     * 设置为自动组包
     * <p>
     * 举例：现有串口设备，随时可能给设备发数据，且长度不固定，（根据波特率不同，可计算出每字节时间差，列：5毫秒至少1byte），那么这样设置 .setToAuto(10);
     * 这儿设置成是不是5是因为考虑到波动或者阻塞等其他情况，可以设置大点。
     *
     * @param maxGroupPackageTime 组包时间差，毫秒，列：设置成10，意思是如果连续10毫秒没收到数据，就回调给应用层当前读取到的数据
     */
    public void setToAuto(int maxGroupPackageTime) {
        autoPackage = true;
        this.maxGroupPackageTime = maxGroupPackageTime;
    }

    /**
     * 设置为手动组包，指在规定时间内，每次至少组包到指定长度。
     * <p>
     * 举例：现有串口设备，每间隔2秒发送20字节到安卓（根据波特率不同，发送20个字节总时间不同，列：20字节大概10毫秒发完），那么这样设置 .setToManual(20,15);
     *
     * @param readLength 每次至少读取长度
     * @param maxTime    最长读取时间
     */
    public void setToManual(int readLength, int maxTime) {
        autoPackage = false;
        this.readLength = readLength;
        this.maxTime = maxTime;
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
                        byte[] bytes = (autoPackage) ? readTime(inputStream, maxGroupPackageTime).getBytes() : readLength(inputStream, readLength, maxTime).getBytes();
                        //无数据不返回
                        if (!noDataNotReturn || bytes.length != 0) readListener.value(bytes);
                    }
                } catch (Throwable e) {
                    Log.e(TAG, "读取线程异常", e);
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

    public boolean isAutoPackage() {
        return autoPackage;
    }

    public int getMaxGroupPackageTime() {
        return maxGroupPackageTime;
    }

    public int getReadLength() {
        return readLength;
    }

    public int getMaxTime() {
        return maxTime;
    }

    public boolean isNoDataNotReturn() {
        return noDataNotReturn;
    }

    public void setNoDataNotReturn(boolean noDataNotReturn) {
        this.noDataNotReturn = noDataNotReturn;
    }

    //★★★★★★★★★★★★★★★★★★★★★★★★★★★★★静态方法·读流操作★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★

    /**
     * 只读一次，读取到就返回，读取不到就一直等
     *
     * @param inputStream inputStream
     * @return byte[]
     * @throws Exception Exception
     */
    @Deprecated
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

    /**
     * 读取inputStream数据到YBytes,一直不停组包，每次组包时间maxGroupTime，如果maxGroupTime内没数据，就返回。
     *
     * @param inputStream  inputStream
     * @param maxGroupTime 最大组包时间，如果这个时间内有数据，就一直组包。如果这个时间都没数据，就返回。
     * @return YBytes
     * @throws Exception Exception
     */
    @Deprecated
    public static YBytes readTime(InputStream inputStream, int maxGroupTime) throws Exception {
        return readTime(inputStream, maxGroupTime, Integer.MAX_VALUE);
    }

    /**
     * 读取inputStream数据到YBytes,一直不停组包，每次组包时间maxGroupTime，如果一直有数据，不超过maxTime。
     *
     * @param inputStream  inputStream
     * @param maxGroupTime 最大组包时间，如果这个时间内有数据，就一直组包。如果这个时间都没数据，就返回。
     * @param maxTime      最多读取这么长时间
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
            //如果读取长度为0，那么休息1毫秒继续读取，如果在maxGroupTime时间内都没有数据，那么就退出循环,或者超过maxTime
            while (count == 0 && System.currentTimeMillis() - groupTime <= maxGroupTime && System.currentTimeMillis() - startTime <= maxTime) {
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
     * @param maxTime     最多读取这么长时间
     * @param minLength   至少读取长度，只要读取长度大于等于minLength，直接返回，最多读取maxTime时间
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
