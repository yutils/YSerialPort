package com.yujing.yserialport;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.yujing.serialport.SerialPort;
import com.yujing.serialport.SerialPortFinder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 串口工具类
 * 如果是异步，创建此类后使用完毕在释放时，必须调用onDestroy方法
 * 读取未知长度，请增大读取长度，并且增加组包时间差，组包时间差要小于读取超时时间。
 * 数据默认返回到UI线程
 *
 * @author yujing  最后调整：2022年2月15日10:59:11
 */

/*
获取串口
//String[] device = YSerialPort.getDevices();//获取串口列表
//String[] baudRate = YSerialPort.getBaudRates();//获取波特率列表

//YSerialPort.saveDevice(getApplication(), "/dev/ttyS4");//设置默认串口,可以不设置
//String device=YSerialPort.readDevice(getApplication());//获取上面设置的串口

//YSerialPort.saveBaudRate(getApplication(), "9600");//设置默认波特率,可以不设置
//String baudRate=YSerialPort.readBaudRate(getApplication());//获取上面设置的波特率
*/

/*
使用方法：（同步）
//拿流用法，自己通过流收发数据
SerialPort serialPort = SerialPort.newBuilder(new File("/dev/ttyS4"), 9600).build();
serialPort.getInputStream();//获取输入流
serialPort.getOutputStream();//获取输出流
serialPort.tryClose();//关闭

//同步收发 （不用每次都创建serialPort对象）
SerialPort serialPort = SerialPort.newBuilder(new File("/dev/ttyS4"), 9600).build();
byte[] bytes=YSerialPort.sendSyncOnce(serialPort,bys,1000);
byte[] bytes=YSerialPort.sendSyncTime(serialPort,bys,20,1000);
byte[] bytes=YSerialPort.sendSyncLength(serialPort,bys,20,1000);
serialPort.tryClose();//关闭

//发送并等待返回，死等
byte[] bytes = YSerialPort.sendSyncOnce("/dev/ttyS4", "9600", bytes);
//发送并等待返回，直到超时，如果超时则向上抛异常
byte[] bytes = YSerialPort.sendSyncOnce("/dev/ttyS4", "9600",bytes,500);
//一直不停组包，（maxGroupTime每次组包时间）当在maxGroupTime时间内没有数据，就返回并关闭连接
byte[] bytes = YSerialPort.sendSyncTime("/dev/ttyS4", "9600",bytes,500);
//一直不停组包，（maxGroupTime每次组包时间）当在maxGroupTime时间内没有数据，就返回并关闭连接（如果一直有数据，最多接收时间为maxTime）
byte[] bytes = YSerialPort.sendSyncTime("/dev/ttyS4", "9600",bytes,500,3000);
//一直不停组包，当数据长度达到minLength或超时，返回并关闭连接
byte[] bytes = YSerialPort.sendSyncLength("/dev/ttyS4", "9600", bytes,500,3000);
*/

/*
使用方法：（异步）

//异步收发
YSerialPort ySerialPort = new YSerialPort(this,"/dev/ttyS4", "9600");
//设置数据监听
ySerialPort.addDataListener(new DataListener() {
    @Override
    public void value(String hexString, byte[] bytes) {
        //结果回调:haxString , bytes
    }
});
//设置回调线程为主线程，默认主线程
ySerialPort.setThreadMode(ThreadMode.MAIN);
//设置自动组包，每次组包时长为40毫秒，如果40毫秒读取不到数据则返回结果
ySerialPort.setToAuto(); //ySerialPort.setToAuto(40);
//或者,设置手动组包，读取长度100，超时时间为50毫秒。如果读取到数据大于等于100立即返回，否则直到读取到超时为止
//ySerialPort.setToManual(100,50);
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
    private static final String DEVICE = "DEVICE";
    private static final String BAUD_RATE = "BAUD_RATE";
    private static final String SERIAL_PORT = "SERIAL_PORT";
    private static final String[] BAUD_RATE_LIST = new String[]{"50", "75", "110", "134", "150", "200", "300", "600", "1200", "1800", "2400", "4800", "9600", "19200", "38400", "57600", "115200", "230400", "460800", "500000", "576000", "921600", "1000000", "1152000", "1500000", "2000000", "2500000", "3000000", "3500000", "4000000"};
    private OutputStream outputStream;
    private InputStream inputStream;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Context context;
    private String device;//串口
    private String baudRate;//波特率
    private boolean noDataNotReturn = true;//无数据不返回
    private YReadInputStream readInputStream = new YReadInputStream();//读取InputStream
    private boolean setAutoComplete = false;//设置组包状态完成
    private ThreadMode threadMode = ThreadMode.MAIN; //返回数据在哪个线程

    //自定义读取InputStream
    private InputStreamReadListener inputStreamReadListener;

    //串口类
    private SerialPort serialPort;

    //串口查找列表类
    private static final SerialPortFinder mSerialPortFinder = new SerialPortFinder();

    //获取串口查找列表类
    public static SerialPortFinder getSerialPortFinder() {
        return mSerialPortFinder;
    }

    //获取设备列表
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

    //读取线程，当inputStreamReadListener 不为null时，启用
    private ReadThread readThread;

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
     * 设置为自动组包
     */
    public void setToAuto() {
        //向上取整 所以 +0.499999999f
        setToAuto(Math.round(10f + (2f / (Integer.parseInt(baudRate) / 115200f)) + 0.499999999f));
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
        readInputStream.setToAuto(maxGroupPackageTime);
        setAutoComplete = true;
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
        readInputStream.setToManual(readLength, maxTime);
        setAutoComplete = true;
    }

    /**
     * 开始读取串口
     */
    public void start() {
        start(null);
    }

    /**
     * 开始读取串口
     *
     * @param sp 外部传入SerialPort
     */
    public void start(SerialPort sp) {
        readInputStream.stop();
        if (serialPort != null) {
            serialPort.close();
            serialPort = null;
        }
        try {
            if (device == null || baudRate == null) throw new NullPointerException("串口或者波特率不能为空");
            serialPort = sp != null ? sp : SerialPort.newBuilder(new File(device), Integer.parseInt(baudRate)).build();
            outputStream = serialPort.getOutputStream();
            inputStream = serialPort.getInputStream();
            if (inputStreamReadListener != null) {
                readThread = new ReadThread();
                readThread.setName("YSerialPort-读取线程");
                readThread.setReadListener(bytes ->
                        post(() -> {
                            for (DataListener item : dataListeners) item.value(bytesToHexString(bytes), bytes);
                        })
                );
                readThread.start();
            } else {
                assert readInputStream != null;
                readInputStream.setInputStream(inputStream);
                readInputStream.setReadListener(bytes ->
                        post(() -> {
                            for (DataListener item : dataListeners) item.value(bytesToHexString(bytes), bytes);
                        })
                );
                //如果没有设置组包方式
                if (!setAutoComplete) setToAuto();
                //设置无数据不返回
                readInputStream.setNoDataNotReturn(noDataNotReturn);
                //开始读取
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

    //自定义读线程
    protected class ReadThread extends Thread {
        private YListener<byte[]> readListener;

        public void setReadListener(YListener<byte[]> readListener) {
            this.readListener = readListener;
        }

        @Override
        public void run() {
            while (!isInterrupted()) {
                try {
                    if (readListener != null) readListener.value(inputStreamReadListener.inputStreamToBytes(inputStream));
                } catch (Exception e) {
                    e.printStackTrace();
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
            if (listener != null) {
                post(() -> listener.value(result));
            }
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
            if (bytes == null) {
                Log.e(TAG, "发送的数据不能为null,sendSynchronization(null,progressListener)");
                return false;
            }
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
                    post(() -> progressListener.value(finalCount));
                }
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "发送失败", e);
            return false;
        }
    }

    //判断当前线程是否是主线程
    private boolean isMainThread() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            return handler.getLooper().isCurrentThread();
        else
            return Looper.getMainLooper().getThread() == Thread.currentThread();
    }

    //通知外部接收到数据
    private void post(Runnable runnable) {
        switch (threadMode) {
            case CURRENT:
                runnable.run();
                break;
            case NEW:
                new Thread(runnable).start();
                break;
            case MAIN:
                if (isMainThread())
                    runnable.run();
                else
                    handler.post(runnable);
                break;
            case IO:
                if (isMainThread())
                    new Thread(runnable).start();
                else
                    runnable.run();
                break;
        }
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

    /**
     * 获取返回线程类型
     *
     * @return 返回线程类型
     */
    public ThreadMode getThreadMode() {
        return threadMode;
    }

    /**
     * 设置返回线程类型
     *
     * @param threadMode 返回线程类型
     */
    public void setThreadMode(ThreadMode threadMode) {
        this.threadMode = threadMode;
    }

    /**
     * 错误处理
     *
     * @param error 错误消息
     */
    private void DisplayError(String error) {
        if (errorListener != null) {
            post(() -> errorListener.error(error));
        } else {
            if (isMainThread()) {
                Toast.makeText(context, "错误:" + error, Toast.LENGTH_LONG).show();
            } else {
                handler.post(() -> Toast.makeText(context, "错误:" + error, Toast.LENGTH_LONG).show());
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


    //****************************************************************静态方法****************************************************************

    //单例模式，全局只有一个串口通信使用
    private static YSerialPort instance;

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


    //保存串口
    @Deprecated
    public static void saveDevice(Context context, String device) {
        SharedPreferences sp = context.getSharedPreferences(SERIAL_PORT, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(DEVICE, device);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            editor.apply();
        } else {
            editor.commit();
        }
    }

    //读取上面方法保存的串口
    @Deprecated
    public static String readDevice(Context context) {
        SharedPreferences sp = context.getSharedPreferences(SERIAL_PORT, Context.MODE_PRIVATE);
        return sp.getString(DEVICE, null);// null为默认值
    }

    //保存波特率
    @Deprecated
    public static void saveBaudRate(Context context, String device) {
        SharedPreferences sp = context.getSharedPreferences(SERIAL_PORT, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(BAUD_RATE, device);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            editor.apply();
        } else {
            editor.commit();
        }
    }

    //读取上面方法保存的波特率
    @Deprecated
    public static String readBaudRate(Context context) {
        SharedPreferences sp = context.getSharedPreferences(SERIAL_PORT, Context.MODE_PRIVATE);
        return sp.getString(BAUD_RATE, null);// null为默认值
    }

    /**
     * 同步发送数据，建立连接,发送完毕后，等待接收数据（读取到数据立即返回），接收完毕后关闭连接
     *
     * @param device   串口名称
     * @param baudRate 波特率
     * @param bytes    发送的数据
     * @return 读取的数据
     */
    @Deprecated
    public static byte[] sendSyncOnce(String device, String baudRate, byte[] bytes) throws Exception {
        return sendSyncOnce(device, baudRate, bytes, -1);
    }

    /**
     * 同步发送数据，建立连接,发送完毕后，等待接收数据（读取到数据立即返回），最多等待timeOut时间，接收完毕后关闭连接
     *
     * @param device   串口名称
     * @param baudRate 波特率
     * @param bytes    发送的数据
     * @param timeOut  读取超时时间，最多等待timeOut时间，读取到数据立即返回
     * @return 读取的数据
     */
    public static byte[] sendSyncOnce(String device, String baudRate, byte[] bytes, int timeOut) throws Exception {
        //串口类
        SerialPort serialPort = null;
        try {
            serialPort = SerialPort.newBuilder(new File(device), Integer.parseInt(baudRate)).build();
            return sendSyncOnce(serialPort, bytes, timeOut);
        } finally {
            if (serialPort != null) serialPort.tryClose();
        }
    }

    /**
     * 同步发送数据，建立连接,发送完毕后，等待接收数据（读取到数据立即返回），接收完毕后关闭连接
     * 用法：
     * SerialPort serialPort = SerialPort.newBuilder(new File("/dev/ttyS4"), 9600).build();
     * byte[] bytes=YSerialPort.sendSyncOnce(serialPort,bys);
     *
     * @param serialPort 串口
     * @param bytes      发送的数据
     * @return 读取的数据
     */
    @Deprecated
    public static byte[] sendSyncOnce(SerialPort serialPort, byte[] bytes) throws Exception {
        return sendSyncOnce(serialPort, bytes, -1);
    }

    /**
     * 同步发送数据，建立连接,发送完毕后，等待接收数据（读取到数据立即返回），最多等待timeOut时间，接收完毕后关闭连接
     * 用法：
     * SerialPort serialPort = SerialPort.newBuilder(new File("/dev/ttyS4"), 9600).build();
     * byte[] bytes=YSerialPort.sendSyncOnce(serialPort,bys,1000);
     *
     * @param serialPort 串口
     * @param bytes      发送的数据
     * @param timeOut    读取超时时间，最多等待timeOut时间，读取到数据立即返回
     * @return 读取的数据
     */
    public static byte[] sendSyncOnce(SerialPort serialPort, byte[] bytes, int timeOut) throws Exception {
        serialPort.getInputStream().skip(serialPort.getInputStream().available());
        //发送
        final int sendLength = 1024;//每次写入长度
        //数据拆分
        List<byte[]> list = YBytes.split(bytes, sendLength);
        for (byte[] item : list) {
            serialPort.getOutputStream().write(item);
        }
        serialPort.getOutputStream().flush();
        return (timeOut <= 0) ? YReadInputStream.readOnce(serialPort.getInputStream()) : YReadInputStream.readOnce(serialPort.getInputStream(), timeOut);
    }

    /**
     * 同步发送数据，建立连接 ,发送完毕后，等待接收数据（maxGroupTime每次组包时间）当在maxGroupTime时间内没有数据，就返回并关闭连接
     *
     * @param device       串口名称
     * @param baudRate     波特率
     * @param bytes        发送的数据
     * @param maxGroupTime 每次组包时间
     * @return 读取的数据
     */
    @Deprecated
    public static byte[] sendSyncTime(String device, String baudRate, byte[] bytes, int maxGroupTime) throws Exception {
        return sendSyncTime(device, baudRate, bytes, maxGroupTime, Integer.MAX_VALUE);
    }

    /**
     * 同步发送数据，建立连接 ,发送完毕后，等待接收数据（maxGroupTime每次组包时间）当在maxGroupTime时间内没有数据，就返回并关闭连接（如果一直有数据，最多接收时间为maxTime）
     *
     * @param device       串口名称
     * @param baudRate     波特率
     * @param bytes        发送的数据
     * @param maxGroupTime 每次组包时间
     * @param maxTime      最长读取时间
     * @return 读取的数据
     */
    public static byte[] sendSyncTime(String device, String baudRate, byte[] bytes, int maxGroupTime, int maxTime) throws Exception {
        //串口类
        SerialPort serialPort = null;
        try {
            serialPort = SerialPort.newBuilder(new File(device), Integer.parseInt(baudRate)).build();
            return sendSyncTime(serialPort, bytes, maxGroupTime, maxTime);
        } finally {
            if (serialPort != null) serialPort.tryClose();
        }
    }

    /**
     * 同步发送数据，建立连接 ,发送完毕后，等待接收数据（maxGroupTime每次组包时间）当在maxGroupTime时间内没有数据，就返回并关闭连接
     * 用法：
     * SerialPort serialPort = SerialPort.newBuilder(new File("/dev/ttyS4"), 9600).build();
     * byte[] bytes=YSerialPort.sendSyncTime(serialPort,bys,20);
     *
     * @param serialPort   串口
     * @param bytes        发送的数据
     * @param maxGroupTime 每次组包时间
     * @return 读取的数据
     */
    public static byte[] sendSyncTime(SerialPort serialPort, byte[] bytes, int maxGroupTime) throws Exception {
        return sendSyncTime(serialPort, bytes, maxGroupTime, Integer.MAX_VALUE);
    }

    /**
     * 同步发送数据，建立连接 ,发送完毕后，等待接收数据（maxGroupTime每次组包时间）当在maxGroupTime时间内没有数据，就返回并关闭连接（如果一直有数据，最多接收时间为maxTime）
     * 用法：
     * SerialPort serialPort = SerialPort.newBuilder(new File("/dev/ttyS4"), 9600).build();
     * byte[] bytes=YSerialPort.sendSyncTime(serialPort,bys,20,1000);
     *
     * @param serialPort   串口
     * @param bytes        发送的数据
     * @param maxGroupTime 每次组包时间
     * @param maxTime      最长读取时间
     * @return 读取的数据
     */
    public static byte[] sendSyncTime(SerialPort serialPort, byte[] bytes, int maxGroupTime, int maxTime) throws Exception {
        serialPort.getInputStream().skip(serialPort.getInputStream().available());
        //发送
        final int sendLength = 1024;//每次写入长度
        //数据拆分
        List<byte[]> list = YBytes.split(bytes, sendLength);
        for (byte[] item : list) {
            serialPort.getOutputStream().write(item);
        }
        serialPort.getOutputStream().flush();
        //读取
        YBytes yBytes = YReadInputStream.readTime(serialPort.getInputStream(), maxGroupTime, maxTime);
        return yBytes.getBytes();
    }

    /**
     * 同步发送数据，建立连接,发送完毕后，等待接收数据，一直不停接收，当数据长度达到minLength或超时，返回并关闭连接
     *
     * @param device    串口名称
     * @param baudRate  波特率
     * @param bytes     发送的数据
     * @param maxTime   最多读取这么长时间
     * @param minLength 至少读取长度，及时没有读取到timeOut时间，但是长度够了，直接返回
     * @return 读取的数据
     */
    public static byte[] sendSyncLength(String device, String baudRate, byte[] bytes, int minLength, int maxTime) throws Exception {
        //串口类
        SerialPort serialPort = null;
        try {
            serialPort = SerialPort.newBuilder(new File(device), Integer.parseInt(baudRate)).build();
            return sendSyncLength(serialPort, bytes, minLength, maxTime);
        } finally {
            if (serialPort != null) serialPort.tryClose();
        }
    }

    /**
     * 同步发送数据，建立连接,发送完毕后，等待接收数据，一直不停接收，当数据长度达到minLength或超时，返回并关闭连接
     * 用法：
     * SerialPort serialPort = SerialPort.newBuilder(new File("/dev/ttyS4"), 9600).build();
     * byte[] bytes=YSerialPort.sendSyncLength(serialPort,bys,20,1000);
     *
     * @param serialPort 串口
     * @param bytes      发送的数据
     * @param maxTime    最多读取这么长时间
     * @param minLength  至少读取长度，及时没有读取到timeOut时间，但是长度够了，直接返回
     * @return 读取的数据
     */
    public static byte[] sendSyncLength(SerialPort serialPort, byte[] bytes, int minLength, int maxTime) throws Exception {
        serialPort.getInputStream().skip(serialPort.getInputStream().available());
        //发送
        final int sendLength = 1024;//每次写入长度
        //数据拆分
        List<byte[]> list = YBytes.split(bytes, sendLength);
        for (byte[] item : list) {
            serialPort.getOutputStream().write(item);
        }
        serialPort.getOutputStream().flush();
        //读取
        YBytes yBytes = YReadInputStream.readLength(serialPort.getInputStream(), minLength, maxTime);
        return yBytes.getBytes();
    }
}
