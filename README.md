# YSerialPort

Google官方的Android串口通信Demo，重新封装代码，实现读取串口数据，实现重新组包一次性读取完整数据。可连续读取任意长度数据。

已经多次长时间测试：串口打印机命令，串口电子秤，串口NFC读卡器，读M1区一次性读取64个扇区，读CPU区一次读取16KB数据。

[![](https://jitpack.io/v/yu1441/YSerialPort.svg)](https://jitpack.io/#yu1441/YSerialPort)

**Gradle 引用**
1. 在根build.gradle中添加
```
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```

2. 子module添加依赖

```
dependencies {
       implementation 'com.github.yu1441:YSerialPort:2.0.0'
}
```
## 注意

1.因为Android-SerialPort-API的SDK最低版本22，低于22的用户请使用Android-SerialPort-API1.0.1，把YSerialPort里面的复制出来，稍加改动就可以使用。

```
    minSdkVersion 22
```

2.在AndroidManifest.xml文件中加入

```
    tools:replace="android:label"
```

# 使用方法

```
int index = 0;//第0个串口
String device = YSerialPort.getSerialPortFinder().getAllDevicesPath()[index];//获取串口列表
String baudRate = YSerialPort.getBaudRateList()[index];//获取波特率列表
YSerialPort.saveDevice(getApplication(), device);//设置默认串口,可以不设置
YSerialPort.saveBaudRate(getApplication(), baudRate);//设置默认波特率,可以不设置

//创建对象
YSerialPort ySerialPort = new YSerialPort(this);
//设置串口,设置波特率
ySerialPort.setDevice(device,baudRate);
//设置数据监听
ySerialPort.addDataListener(new YSerialPort.DataListener() {
    @Override
    public void onDataReceived(String hexString, byte[] bytes, int size) {
        //结果回调:haxString
        //结果回调:bytes
        //结果回调:size
    }
});
//设置本次读取长度
ySerialPort.setDataLength(64);
//设置本次读取超时时间
ySerialPort.setReadTimeOut(100);
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

```

官方源码 https://github.com/licheedev/Android-SerialPort-API

不懂的问我QQ：3373217
