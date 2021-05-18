package com.yujing.yserialport;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.serialport.SerialPort;
import android.serialport.SerialPortFinder;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 串口工具类，调用的此类的activity必须在onDestroy调用onDestroy方法
 * 默认50ms读取超时，读取长数据请设置读取长度和超时时间。
 * 读取未知长度，请增大读取长度，并且增加组包时间差，组包时间差要小于读取超时时间。
 * 构造函数传入的是activity就返回到UI线程 传的是Context就返回到线程
 *
 * @author yujing 2020年9月2日17:20:57
 */
/*
使用方法：
YSerialPort ySerialPort = new YSerialPort(this);
//设置串口,设置波特率,如果设置了默认可以不用设置
ySerialPort.setDevice("/dev/ttyS4", "9600");
//设置数据监听
ySerialPort.addDataListener(new DataListener() {
    @Override
    public void value(String hexString, byte[] bytes) {
        //结果回调:haxString
        //结果回调:bytes
        //结果回调:size
    }
});

//设置自动组包，每次组包时长为40毫秒，如果40毫秒读取不到数据则返回结果
ySerialPort.setAutoPackage(true);
//ySerialPort.setMaxGroupPackageTime(40);

//或者,设置非自动组包，读取长度1000，超时时间为500毫秒。如果读取到1000立即返回，否则直到读取到超时为止
//ySerialPort.setAutoPackage(false);
//ySerialPort.setLengthAndTimeout(1000,500);

//启动
ySerialPort.start();

//发送文字
ySerialPort.send("你好".getBytes(Charset.forName("GB18030")));

//退出页面时候注销
@Override
protected void onDestroy() {
    super.onDestroy();
    ySerialPort.onDestroy();
}

//自定义组包
ySerialPort.setInputStreamReadListener(inputStream -> {
    int count = 0;
    while (count == 0)
        count = inputStream.available();
    byte[] bytes = new byte[count];
    //readCount，已经成功读取的字节的个数，这儿需读取count个数据，不够则循环读取，如果采用inputStream.read(bytes);可能读不完
    int readCount = 0;
    while (readCount < count)
        readCount += inputStream.read(bytes, readCount, count - readCount);
    return bytes;
});
 */
@SuppressWarnings("unused")
public class YSerialPort {
    private static String TAG = "YSerialPort";
    private OutputStream outputStream;
    private InputStream inputStream;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Context context;
    private String device;//串口
    private String baudRate;//波特率
    private static final String DEVICE = "DEVICE";
    private static final String BAUD_RATE = "BAUD_RATE";
    private static final String SERIAL_PORT = "SERIAL_PORT";
    private static final String[] BAUD_RATE_LIST = new String[]{"50", "75", "110", "134", "150", "200", "300", "600", "1200", "1800", "2400", "4800", "9600", "19200", "38400", "57600", "115200", "230400", "460800", "500000", "576000", "921600", "1000000", "1152000", "1500000", "2000000", "2500000", "3000000", "3500000", "4000000"};
    private boolean autoPackage = true;//自动组包
    private int maxGroupPackageTime = -1;//最大组包时间
    private int readTimeout = -1;//读取超时时间
    private int readLength = -1;//读取长度
    private boolean noDataNotReturn = true;//无数据不返回
    private YReadInputStream readInputStream;
    private InputStreamReadListener inputStreamReadListener;//自定义读取InputStream
    //串口类
    private SerialPort serialPort;
    //串口查找列表类
    private static final SerialPortFinder mSerialPortFinder = new SerialPortFinder();

    //获取串口查找列表类
    public static SerialPortFinder getSerialPortFinder() {
        return mSerialPortFinder;
    }

    public static String[] getDevices() {
        return getSerialPortFinder().getAllDevicesPath();
    }

    //获取波特率列表
    public static String[] getBaudRates() {
        return BAUD_RATE_LIST;
    }

    //回调结果
    private final List<DataListener> dataListeners = new ArrayList<>();
    //错误回调
    private ErrorListener errorListener;
    //单例模式，全局只有一个串口通信使用
    private static YSerialPort instance;
    //读取线程，当inputStreamReadListener 不为null时，启用
    private ReadThread readThread;

    /**
     * 单例模式，调用此方法前必须先调用getInstance(String ip, int port)
     *
     * @param context context
     * @return YSerialPort
     */
    public static synchronized YSerialPort getInstance(Context context) {
        if (instance == null) {
            synchronized (YSerialPort.class) {
                if (instance == null) {
                    instance = new YSerialPort(context);
                }
            }
        }
        return instance;
    }

    /**
     * 单例模式
     *
     * @param context  context
     * @param device   串口
     * @param baudRate 波特率
     * @return YSerialPort
     */
    public static YSerialPort getInstance(Context context, String device, String baudRate) {
        if (instance == null) {
            synchronized (YSerialPort.class) {
                if (instance == null) {
                    instance = new YSerialPort(context, device, baudRate);
                }
            }
        }
        instance.setContext(context);
        instance.setDevice(device, baudRate);
        return instance;
    }

    /**
     * 构造函数
     *
     * @param context context
     */
    public YSerialPort(Context context) {
        this.context = context;
    }

    /**
     * 构造函数
     *
     * @param context  context
     * @param device   串口
     * @param baudRate 波特率
     */
    public YSerialPort(Context context, String device, String baudRate) {
        this.context = context;
        this.device = device;
        this.baudRate = baudRate;
    }

    /**
     * 开始读取串口
     */
    public void start() {
        if (readInputStream != null) {
            readInputStream.stop();
        }
        if (serialPort != null) {
            serialPort.close();
            serialPort = null;
        }
        try {
            serialPort = buildSerialPort();
            outputStream = serialPort.getOutputStream();
            inputStream = serialPort.getInputStream();
            if (inputStreamReadListener != null) {
                readThread = new ReadThread();
                readThread.start();
            } else {
                readInputStream = new YReadInputStream(inputStream, bytes -> {
                    handler.post(() -> {
                        for (DataListener item : dataListeners) {
                            item.value(bytesToHexString(bytes), bytes);
                        }
                    });
                });
                readInputStream.setLengthAndTimeout(readLength, readTimeout);
                readInputStream.setAutoPackage(autoPackage);
                if (maxGroupPackageTime == -1) setMaxGroupPackageTimeDefault();//设置默认组包时间
                readInputStream.setMaxGroupPackageTime(maxGroupPackageTime);
                readInputStream.setNoDataNotReturn(noDataNotReturn);
                readInputStream.start();
            }
        } catch (SecurityException e) {
            DisplayError("您对串行端口没有读/写权限。");
        } catch (IOException e) {
            DisplayError("由于未知原因，无法打开串行端口。");
        } catch (InvalidParameterException e) {
            DisplayError("请先配置你的串口。");
        }
    }

    protected class ReadThread extends Thread {
        @Override
        public void run() {
            while (!isInterrupted()) {
                try {
                    byte[] resultBytes = inputStreamReadListener.inputStreamToBytes(inputStream);
                    handler.post(() -> {
                        for (DataListener item : dataListeners) {
                            item.value(bytesToHexString(resultBytes), resultBytes);
                        }
                    });
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * 重启
     */
    public void reStart() {
        stop();
        start();
    }

    /**
     * 重启
     *
     * @param device   串口
     * @param baudRate 波特率
     */
    public void reStart(String device, String baudRate) {
        setDevice(device, baudRate);
        stop();
        start();
    }

    /**
     * 构建SerialPort类
     *
     * @return SerialPort
     * @throws SecurityException         串行端口权限
     * @throws IOException               IO异常
     * @throws InvalidParameterException 未配置串口
     */
    public SerialPort buildSerialPort() throws SecurityException, IOException, InvalidParameterException {
        if (device == null || baudRate == null) {
            if (readDevice(context) == null || readBaudRate(context) == null || (readDevice(context).length() == 0) || (readBaudRate(context).length() == 0)) {
                throw new InvalidParameterException();
            }
            device = readDevice(context);
            baudRate = readBaudRate(context);
        }
        return SerialPort.newBuilder(new File(device), Integer.parseInt(baudRate)).build();
    }

    /**
     * 发送
     *
     * @param bytes 数据
     */
    public void send(byte[] bytes) {
        send(bytes, null);
    }

    /**
     * 发送
     *
     * @param bytes    数据
     * @param listener 状态，成功回调true，失败false
     */
    public void send(byte[] bytes, YListener<Boolean> listener) {
        send(bytes, listener, null);
    }

    /**
     * 发送
     *
     * @param bytes            数据
     * @param listener         状态，成功回调true，失败false
     * @param progressListener 进度监听，返回已经发送长度
     */
    public void send(final byte[] bytes, final YListener<Boolean> listener, final YListener<Integer> progressListener) {
        new Thread(() -> {
            boolean result = sendSynchronization(bytes, progressListener);
            if (listener != null)
                handler.post(() -> listener.value(result));
        }).start();
    }

    /**
     * 同步发送
     *
     * @param bytes 数据
     * @return 是否成功
     */
    public boolean sendSynchronization(final byte[] bytes) {
        return sendSynchronization(bytes, null);
    }

    /**
     * 同步发送
     *
     * @param bytes            数据
     * @param progressListener 进度
     * @return 是否成功
     */
    public boolean sendSynchronization(final byte[] bytes, final YListener<Integer> progressListener) {
        try {
            if (serialPort != null) outputStream = serialPort.getOutputStream();
            final int sendLength = 1024;//每次写入长度
            int count = 0;//统计已经发送长度
            //数据拆分
            List<byte[]> list = YBytes.split(bytes, sendLength);
            for (byte[] item : list) {
                //写入
                outputStream.write(item);
                count += item.length;
                //回调进度
                if (progressListener != null) {
                    final int finalCount = count;
                    handler.post(() -> progressListener.value(finalCount));
                }
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "发送失败", e);
            return false;
        }
    }


    //保存串口
    public static void saveDevice(Context context, String device) {
        SharedPreferences sp = context.getSharedPreferences(SERIAL_PORT, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(DEVICE, device);
        editor.apply();
    }

    //读取上面方法保存的串口
    public static String readDevice(Context context) {
        SharedPreferences sp = context.getSharedPreferences(SERIAL_PORT, Context.MODE_PRIVATE);
        return sp.getString(DEVICE, null);// null为默认值
    }

    //保存波特率
    public static void saveBaudRate(Context context, String device) {
        SharedPreferences sp = context.getSharedPreferences(SERIAL_PORT, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(BAUD_RATE, device);
        editor.apply();
    }

    //读取上面方法保存的波特率
    public static String readBaudRate(Context context) {
        SharedPreferences sp = context.getSharedPreferences(SERIAL_PORT, Context.MODE_PRIVATE);
        return sp.getString(BAUD_RATE, null);// null为默认值
    }

    public boolean isNoDataNotReturn() {
        return noDataNotReturn;
    }

    public void setNoDataNotReturn(boolean noDataNotReturn) {
        if (readInputStream != null)
            readInputStream.setNoDataNotReturn(noDataNotReturn);
        this.noDataNotReturn = noDataNotReturn;
    }

    /**
     * 添加回调函数
     *
     * @param dataListener 数据监听回调
     */
    public void addDataListener(DataListener dataListener) {
        if (!dataListeners.contains(dataListener))
            dataListeners.add(dataListener);
    }

    /**
     * 删除回调函数
     *
     * @param dataListener 数据监听回调
     */
    public void removeDataListener(DataListener dataListener) {
        dataListeners.remove(dataListener);
    }

    /**
     * 删除全部回调函数
     */
    public void clearDataListener() {
        dataListeners.clear();
    }

    /**
     * 设置串口和波特率
     *
     * @param device   串口
     * @param baudRate 波特率
     */
    public void setDevice(String device, String baudRate) {
        this.device = device;
        this.baudRate = baudRate;
    }

    /**
     * 获取当前串口
     *
     * @return 串口名
     */
    public String getDevice() {
        return device;
    }

    /**
     * 设置当前串口
     *
     * @param device 串口名
     */
    public void setDevice(String device) {
        this.device = device;
    }

    /**
     * 获取当前波特率
     *
     * @return 波特率
     */
    public String getBaudRate() {
        return baudRate;
    }

    /**
     * 设置当前波特率
     *
     * @param baudRate 波特率
     */
    public void setBaudRate(String baudRate) {
        this.baudRate = baudRate;
    }

    /**
     * 自定义读取InputStream
     *
     * @param inputStreamReadListener InputStream监听
     */
    public void setInputStreamReadListener(InputStreamReadListener inputStreamReadListener) {
        this.inputStreamReadListener = inputStreamReadListener;
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    /**
     * 设置错误回调
     *
     * @param errorListener errorListener
     */
    public void setErrorListener(ErrorListener errorListener) {
        this.errorListener = errorListener;
    }

    /**
     * 获取组包最小时间差
     *
     * @return 毫秒
     */
    public int getMaxGroupPackageTime() {
        return maxGroupPackageTime;
    }

    /**
     * 设置组包最小时间差  方法互斥 setReadTimeOutAndLength
     *
     * @param maxGroupPackageTime 组包最小时间差,毫秒
     */
    public void setMaxGroupPackageTime(int maxGroupPackageTime) {
        this.maxGroupPackageTime = maxGroupPackageTime;
        if (readInputStream != null) {
            readInputStream.setMaxGroupPackageTime(maxGroupPackageTime);
            setAutoPackage(true);
        }
    }

    /**
     * 获取SerialPort对象
     *
     * @return SerialPort
     */
    public SerialPort getSerialPort() {
        return serialPort;
    }

    /**
     * 获取输出流
     *
     * @return OutputStream
     */
    public OutputStream getOutputStream() {
        return serialPort.getOutputStream();
    }

    /**
     * 获取输入流
     *
     * @return InputStream
     */
    public InputStream getInputStream() {
        return serialPort.getInputStream();
    }

    public void setMaxGroupPackageTimeDefault() {
        if (baudRate != null) {
            int intBaudRate = Integer.parseInt(baudRate);
            maxGroupPackageTime = Math.round(10f + (2f / (intBaudRate / 115200f)) + 0.499999999f);//向上取整
            setAutoPackage(true);
        }
    }

    /**
     * 设置读取超时时间和读取最小长度 方法互斥 setGroupPackageTime
     *
     * @param readTimeout 读取超时时间
     * @param readLength  读取最小长度
     */
    public void setLengthAndTimeout(int readLength, int readTimeout) {
        this.readTimeout = readTimeout;
        this.readLength = readLength;
        if (readInputStream != null) {
            readInputStream.setLengthAndTimeout(readLength, readTimeout);
            setAutoPackage(false);
        }
    }

    public boolean isAutoPackage() {
        return autoPackage;
    }

    public void setAutoPackage(boolean autoPackage) {
        this.autoPackage = autoPackage;
        if (readInputStream != null) readInputStream.setAutoPackage(autoPackage);
    }

    /**
     * bytesToHexString
     *
     * @param bArray bytes
     * @return HexString
     */
    public static String bytesToHexString(byte[] bArray) {
        StringBuilder sb = new StringBuilder(bArray.length);
        String sTemp;
        for (byte aBArray : bArray) {
            sTemp = Integer.toHexString(0xFF & aBArray);
            if (sTemp.length() < 2)
                sb.append(0);
            sb.append(sTemp.toUpperCase(Locale.US));
        }
        return sb.toString();
    }

    /**
     * 错误处理
     *
     * @param error 错误消息
     */
    private void DisplayError(String error) {
        if (errorListener != null) {
            errorListener.error(error);
        } else {
            if (context instanceof Activity) {
                Activity activity = (Activity) context;
                activity.runOnUiThread(() -> {
                    AlertDialog.Builder b = new AlertDialog.Builder(activity);
                    b.setTitle("错误");
                    b.setMessage(error);
                    b.setPositiveButton("确定", null);
                    b.show();
                });
            } else {
                Toast.makeText(context, "错误:" + error, Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * 错误回调
     */
    public interface ErrorListener {
        void error(String error);
    }

    /**
     * 关闭串口释放资源
     */
    private void stop() {
        try {
            if (readInputStream != null) {
                readInputStream.stop();
            }
            if (inputStream != null) {
                inputStream.close();
                inputStream = null;
            }
            if (outputStream != null) {
                outputStream.close();
                outputStream = null;
            }
            if (readThread != null) {
                readThread.interrupt();
                readThread = null;
            }
        } catch (Throwable e) {
            Log.e(TAG, "stop异常", e);
        } finally {
            if (serialPort != null) {
                try {
                    serialPort.close();
                } catch (Throwable ignored) {
                }
                serialPort = null;
            }
        }
    }

    /**
     * onDestroy,调用的此类的activity必须在onDestroy调用此方法
     */
    public void onDestroy() {
        Log.i(TAG, "调用onDestroy");
        stop();
        clearDataListener();
    }
}
