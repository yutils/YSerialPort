package com.yujing.yserialport;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.serialport.SerialPort;
import android.serialport.SerialPortFinder;
import android.util.Log;

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
 *
 * @author yujing 2019年12月2日09:46:02
 */
@SuppressWarnings("unused")
public class YSerialPort {
    private static String TAG = "YSerialPort";
    private OutputStream mOutputStream;
    private InputStream mInputStream;
    private final Handler handler = new Handler();
    private Activity activity;
    private String device;//串口
    private String baudRate;//波特率
    private static final String DEVICE = "DEVICE";
    private static final String BAUD_RATE = "BAUD_RATE";
    private static final String SERIAL_PORT = "SERIAL_PORT";
    private static final String[] BAUD_RATE_LIST = new String[]{"50", "75", "110", "134", "150", "200", "300", "600", "1200", "1800", "2400", "4800", "9600", "19200", "38400", "57600", "115200", "230400", "460800", "500000", "576000", "921600", "1000000", "1152000", "1500000", "2000000", "2500000", "3000000", "3500000", "4000000"};
    private boolean autoPackage = true;//自动组包
    private int packageTime = -1;//组包时间差，毫秒
    private int readTimeout = -1;//读取超时时间
    private int readLength = -1;//读取长度
    private YReadInputStream readInputStream;
    //串口类
    private SerialPort mSerialPort;
    //串口查找列表类
    private static final SerialPortFinder mSerialPortFinder = new SerialPortFinder();

    //获取串口查找列表类
    public static SerialPortFinder getSerialPortFinder() {
        return mSerialPortFinder;
    }

    //获取波特率列表
    public static String[] getBaudRateList() {
        return BAUD_RATE_LIST;
    }

    //回调结果
    private final List<DataListener> dataListeners = new ArrayList<>();
    //错误回调
    private ErrorListener errorListener;
    //单例模式，全局只有一个串口通信使用
    private static YSerialPort instance;

    /**
     * 单例模式，调用此方法前必须先调用getInstance(String ip, int port)
     *
     * @param activity activity
     * @return YSerialPort
     */
    public static synchronized YSerialPort getInstance(Activity activity) {
        if (instance == null) {
            synchronized (YSerialPort.class) {
                if (instance == null) {
                    instance = new YSerialPort(activity);
                }
            }
        }
        return instance;
    }

    /**
     * 单例模式
     *
     * @param activity activity
     * @param device   串口
     * @param baudRate 波特率
     * @return YSerialPort
     */
    public static YSerialPort getInstance(Activity activity, String device, String baudRate) {
        if (instance == null) {
            synchronized (YSerialPort.class) {
                if (instance == null) {
                    instance = new YSerialPort(activity, device, baudRate);
                }
            }
        }
        instance.setActivity(activity);
        instance.setDevice(device, baudRate);
        return instance;
    }

    /**
     * 构造函数
     *
     * @param activity activity
     */
    public YSerialPort(Activity activity) {
        this.activity = activity;
    }

    /**
     * 构造函数
     *
     * @param activity activity
     * @param device   串口
     * @param baudRate 波特率
     */
    public YSerialPort(Activity activity, String device, String baudRate) {
        this.activity = activity;
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
        if (mSerialPort != null) {
            mSerialPort.close();
            mSerialPort = null;
        }
        try {
            mOutputStream = getSerialPort().getOutputStream();
            mInputStream = getSerialPort().getInputStream();
            readInputStream = new YReadInputStream(mInputStream, new YListener<byte[]>() {
                @Override
                public void value(final byte[] bytes) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            for (DataListener item : dataListeners) {
                                item.onDataReceived(bytesToHexString(bytes), bytes, bytes.length);
                            }
                        }
                    });
                }
            });
            readInputStream.setLengthAndTimeout(readLength, readTimeout);
            if (packageTime == -1) setPackageTimeDefault();//设置默认组包时间
            readInputStream.setPackageTime(packageTime);
            readInputStream.start();
        } catch (SecurityException e) {
            DisplayError("您对串行端口没有读/写权限。");
        } catch (IOException e) {
            DisplayError("由于未知原因，无法打开串行端口。");
        } catch (InvalidParameterException e) {
            DisplayError("请先配置你的串口。");
        }
    }

    /**
     * 获取SerialPort类
     *
     * @return SerialPort
     * @throws SecurityException         串行端口权限
     * @throws IOException               IO异常
     * @throws InvalidParameterException 未配置串口
     */
    private SerialPort getSerialPort() throws SecurityException, IOException, InvalidParameterException {
        if (mSerialPort == null) {
            if (device == null || baudRate == null) {
                if (readDevice(activity) == null || readBaudRate(activity) == null || (readDevice(activity).length() == 0) || (readBaudRate(activity).length() == 0)) {
                    throw new InvalidParameterException();
                }
                device = readDevice(activity);
                baudRate = readBaudRate(activity);
            }
            /* 打开串口 */
            mSerialPort = SerialPort.newBuilder(new File(device), Integer.parseInt(baudRate)).build();
        }
        return mSerialPort;
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
        try {
            if (mSerialPort != null) mOutputStream = mSerialPort.getOutputStream();
            mOutputStream.write(bytes);
            if (listener != null) listener.value(true);
        } catch (Exception e) {
            Log.e(TAG, "发送失败", e);
            if (listener != null) listener.value(false);
        }
    }

    //保存串口
    public static void saveDevice(Context context, String device) {
        SharedPreferences sp = context.getSharedPreferences(SERIAL_PORT, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(DEVICE, device);
        editor.apply();
    }

    //读取串口
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

    //读取波特率
    public static String readBaudRate(Context context) {
        SharedPreferences sp = context.getSharedPreferences(SERIAL_PORT, Context.MODE_PRIVATE);
        return sp.getString(BAUD_RATE, null);// null为默认值
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
     * 获取activity
     *
     * @return Activity
     */
    public Activity getActivity() {
        return activity;
    }

    /**
     * 设置activity
     *
     * @param activity activity
     */
    public void setActivity(Activity activity) {
        this.activity = activity;
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
    public int getPackageTime() {
        return packageTime;
    }

    /**
     * 设置组包最小时间差  方法互斥 setReadTimeOutAndLength
     *
     * @param packageTime 组包最小时间差,毫秒
     */
    public void setPackageTime(int packageTime) {
        this.packageTime = packageTime;
        if (readInputStream != null) {
            readInputStream.setPackageTime(packageTime);
            setAutoPackage(true);
        }
    }

    private void setPackageTimeDefault() {
        if (baudRate != null) {
            int intBaudRate = Integer.parseInt(baudRate);
            packageTime = Math.round((4f / (intBaudRate / 115200f)) + 0.4999f);//向上取整
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
            AlertDialog.Builder b = new AlertDialog.Builder(activity);
            b.setTitle("错误");
            b.setMessage(error);
            b.setPositiveButton("确定", null);
            b.show();
        }
    }

    /**
     * 结果回调
     */
    public interface DataListener {
        void onDataReceived(String hexString, byte[] bytes, int size);
    }

    /**
     * 错误回调
     */
    public interface ErrorListener {
        void error(String error);
    }

    /**
     * onDestroy,调用的此类的activity必须在onDestroy调用此方法
     */
    public void onDestroy() {
        Log.i(TAG, "调用onDestroy");
        try {
            if (readInputStream != null) {
                readInputStream.stop();
            }
            if (mInputStream != null) {
                mInputStream.close();
                mOutputStream = null;
            }
            if (mOutputStream != null) {
                mOutputStream.close();
                mOutputStream = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "onDestroy异常", e);
        } finally {
            if (mSerialPort != null) {
                mSerialPort.close();
                mSerialPort = null;
            }
        }
        clearDataListener();
    }
}
