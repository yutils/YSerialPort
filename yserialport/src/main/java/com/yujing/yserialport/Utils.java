package com.yujing.yserialport;

import android.util.Log;

import java.io.InputStream;

/**
 * 工具类
 *
 * @author yujing 2019年12月11日18:21:58
 */
public class Utils {
    /**
     * 指定时间内读取指定长度的InputStream
     *
     * @param mInputStream     输入流
     * @param readTimeOut      超时时间
     * @param dataLength       读取长度
     * @param groupPackageTime 每次组包时间间隔
     * @return YBytes
     */
    public static YBytes readInputStream(final InputStream mInputStream, final int readTimeOut, final int groupPackageTime, final int dataLength) {
        final int loopWaitTime = 1;//循环等待时间1毫秒
        final String TAG = "组包";
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
                                Thread.sleep(loopWaitTime);
                            } catch (Exception e) {
                                Thread.currentThread().interrupt();
                            }
                            continue;
                        }
                        Thread.sleep(groupPackageTime);//每次组包间隔，毫秒
                        //再读取一次
                        byte[] newBytes = new byte[dataLength < 64 ? 64 : dataLength];
                        int newSize = mInputStream.read(newBytes, 0, available);
                        if (newSize > 0) {
                            bytes.addByte(newBytes, newSize);
                            Log.i(TAG, "第" + i + "次组包后长度：" + bytes.getBytes().length + " ，目标长度：" + dataLength + " ，已耗时：" + (System.currentTimeMillis() - startTime) + "ms,超时时间：" + readTimeOut + "ms");
                        }
                    }
                    timeOut = false;
                } catch (InterruptedException e) {
                    interrupt();
                } catch (Exception e) {
                    Log.e(TAG, "读取线程异常", e);
                    interrupt();
                } finally {
                    Log.i(TAG, "读取线程关闭");
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
                    Log.i(TAG, "已超时：" + readTimeOut + "ms");
                    synchronized (bytes) {
                        bytes.notify();
                    }
                }
                Log.i(TAG, "超时线程关闭");
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
            Log.e(TAG, "同步锁被中断");
        }
        Log.i(TAG, (mReadThread.isTimeOut() ? "读取超时！" : "读取完毕"));
        //释放这两个线程
        if (!mReadThread.isInterrupted())
            mReadThread.interrupt();
        if (!stopReadThread.isInterrupted())
            stopReadThread.interrupt();
        return bytes;
    }
}
