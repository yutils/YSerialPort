package com.yujing.yserialport;

import android.util.Log;

import java.io.InputStream;

/**
 * 读取InputStream
 *
 * @author yujing 2020年1月3日10:10:51
 */
@SuppressWarnings("unused")
public class YReadInputStream {
    private static final String TAG = "YRead";
    private static boolean showLog = false;
    private InputStream inputStream;
    private YListener<byte[]> readListener;
    private ReadThread readThread;
    private int groupPackageTime = 1;//组包时间差，毫秒
    private int loopWaitTime = 1;//循环等待时间1毫秒

    public YReadInputStream() {
    }

    public YReadInputStream(InputStream inputStream, YListener<byte[]> readListener) {
        this.inputStream = inputStream;
        this.readListener = readListener;
    }

    public InputStream getInputStream() {
        return inputStream;
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
            try {
                log("开启一个读取线程");
                while (!Thread.currentThread().isInterrupted()) {
                    int available = inputStream.available();// 可读取多少字节内容
                    if (available == 0) {//如果可读取消息为0，那么久休息loopWaitTime毫秒。防止InputStream.read阻塞
                        try {
                            Thread.sleep(loopWaitTime);
                        } catch (Exception e) {
                            Thread.currentThread().interrupt();
                        }
                        continue;
                    }
                    YBytes yBytes = read(inputStream, groupPackageTime);
                    if (readListener != null) {
                        readListener.value(yBytes.getBytes());
                    }
                }
            } catch (Exception e) {
                log("读取线程崩溃", e);
            } finally {
                log("关闭一个读取线程");
            }
        }
    }

    public int getLoopWaitTime() {
        return loopWaitTime;
    }

    public void setLoopWaitTime(int loopWaitTime) {
        this.loopWaitTime = loopWaitTime;
    }

    private static void log(String string) {
        if (showLog) Log.i(TAG, string);
    }

    private static void log(String string, Exception e) {
        if (showLog) Log.e(TAG, string, e);
    }

    public static boolean isShowLog() {
        return showLog;
    }

    public static void setShowLog(boolean showLog) {
        YReadInputStream.showLog = showLog;
    }

    public int getGroupPackageTime() {
        return groupPackageTime;
    }

    public void setGroupPackageTime(int groupPackageTime) {
        this.groupPackageTime = groupPackageTime;
    }
    //★★★★★★★★★★★★★★★★★★★★★★★★★★★★★读流操作★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★

    /**
     * 读取InputStream
     *
     * @param mInputStream     输入流
     * @param groupPackageTime 每次组包时间间隔
     * @return YBytes
     */
    public static YBytes read(final InputStream mInputStream, final int groupPackageTime) {
        final YBytes bytes = new YBytes();
        final long startTime = System.currentTimeMillis();
        //方法内部类，读取线程
        class MReadThread extends Thread {
            @Override
            public void run() {
                try {
                    int i = 0;//第几次组包
                    int available = mInputStream.available();// 可读取多少字节内容
                    while (!Thread.currentThread().isInterrupted() && available > 0) {
                        i++;
                        //再读取一次
                        byte[] newBytes = new byte[1024];
                        int newSize = mInputStream.read(newBytes, 0, available);
                        if (newSize > 0) {
                            bytes.addByte(newBytes, newSize);
                            log("第" + i + "次组包后长度：" + bytes.getBytes().length + "  ，已耗时：" + (System.currentTimeMillis() - startTime));
                        }
                        Thread.sleep(groupPackageTime);//每次组包间隔，毫秒
                        available = mInputStream.available();// 可读取多少字节内容
                    }
                } catch (InterruptedException e) {
                    interrupt();
                } catch (Exception e) {
                    log("读取线程异常", e);
                    interrupt();
                } finally {
                    log("读取线程关闭");
                    synchronized (bytes) {
                        bytes.notify();
                    }
                }
            }
        }
        //开个线程来读取
        final MReadThread mReadThread = new MReadThread();
        mReadThread.start();
        try {
            //同步锁
            synchronized (bytes) {
                bytes.wait();
            }
        } catch (Exception e) {
            mReadThread.interrupt();
            //Thread.currentThread().interrupt();
            log("同步锁被中断");
        }
        log(("读取完毕"));
        return bytes;
    }

    /**
     * 指定时间内读取指定长度的InputStream
     *
     * @param mInputStream     输入流
     * @param readTimeOut      超时时间
     * @param dataLength       读取长度
     * @param groupPackageTime 每次组包时间间隔
     * @return YBytes
     */
    public static YBytes read(final InputStream mInputStream, final int readTimeOut, final int groupPackageTime, final int dataLength) {
        final YBytes bytes = new YBytes();
        final long startTime = System.currentTimeMillis();
        //方法内部类，读取线程
        class MReadThread extends Thread {
            private boolean timeOut = true;

            private boolean isTimeOut() {
                return timeOut;
            }

            @Override
            public void run() {
                try {
                    int i = 0;//第几次组包
                    while (!Thread.currentThread().isInterrupted() && bytes.getBytes().length < dataLength && i * groupPackageTime < readTimeOut) {
                        i++;
                        int available = mInputStream.available();// 可读取多少字节内容
                        if (available == 0) {//如果可读取消息为0，那么久休息loopWaitTime毫秒。防止InputStream.read阻塞
                            try {
                                Thread.sleep(groupPackageTime);
                            } catch (Exception e) {
                                Thread.currentThread().interrupt();
                            }
                            continue;
                        }
                        Thread.sleep(groupPackageTime);//每次组包间隔，毫秒
                        //再读取一次
                        byte[] newBytes = new byte[dataLength < 1024 ? 1024 : dataLength];
                        int newSize = mInputStream.read(newBytes, 0, available);
                        if (newSize > 0) {
                            bytes.addByte(newBytes, newSize);
                            log("第" + i + "次组包后长度：" + bytes.getBytes().length + " ，目标长度：" + dataLength + " ，已耗时：" + (System.currentTimeMillis() - startTime) + "ms,超时时间：" + readTimeOut + "ms");
                        }
                    }
                    timeOut = false;
                } catch (InterruptedException e) {
                    interrupt();
                } catch (Exception e) {
                    log("读取线程异常", e);
                    interrupt();
                } finally {
                    log("读取线程关闭");
                    synchronized (bytes) {
                        bytes.notify();
                    }
                }
            }
        }
        //方法内部类，终止线程
        class StopReadThread extends Thread {
            @Override
            public void run() {
                try {
                    Thread.sleep(readTimeOut);
                } catch (InterruptedException e) {
                    interrupt();
                }
                if (!isInterrupted()) {
                    log("已超时：" + readTimeOut + "ms");
                    synchronized (bytes) {
                        bytes.notify();
                    }
                }
                log("超时线程关闭");
            }
        }

        //开个线程来读取
        final MReadThread mReadThread = new MReadThread();
        mReadThread.start();
        //开个线程来终止
        final StopReadThread stopReadThread = new StopReadThread();
        stopReadThread.start();
        try {
            //同步锁
            synchronized (bytes) {
                bytes.wait();
            }
        } catch (Exception e) {
            mReadThread.interrupt();
            stopReadThread.interrupt();
            //Thread.currentThread().interrupt();
            log("同步锁被中断");
        }
        log((mReadThread.isTimeOut() ? "读取超时！" : "读取完毕"));
        //释放这两个线程
        if (!mReadThread.isInterrupted())
            mReadThread.interrupt();
        if (!stopReadThread.isInterrupted())
            stopReadThread.interrupt();
        return bytes;
    }
}
