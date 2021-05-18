# YSerialPort

源Android-SerialPort-API，重新封装代码，实现读取串口数据，实现重新组包一次性读取完整数据。可连续读取任意长度数据。

已经多次长时间测试：串口打印机命令，串口电子秤，串口条码读卡器，串口二维码读卡器，串口NFC读卡器，读M1区一次性读取64个扇区，读CPU区一次读取16KB数据。

[![platform](https://img.shields.io/badge/platform-Android-lightgrey.svg)](https://developer.android.google.cn/studio/index.html)
![Gradle](https://img.shields.io/badge/Gradle-6.1.1-brightgreen.svg)
[![last commit](https://img.shields.io/github/last-commit/yutils/YSerialPort.svg)](https://github.com/yutils/YSerialPort/commits/master)
![repo size](https://img.shields.io/github/repo-size/yutils/YSerialPort.svg)
[![jitpack](https://jitpack.io/v/yutils/YSerialPort.svg)](https://jitpack.io/#yutils/YSerialPort)

**[releases里面有APK文件。点击前往](https://github.com/yutils/YSerialPort/releases)**

**[releases里面有AAR包。点击前往](https://github.com/yutils/YSerialPort/releases)**


**如果拉取整个项目，请用AS4.0以上打开**

# `不建议直接拉取项目编译，请仔细看完 ` #

## 引用

1. 在根build.gradle中添加

```
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```

2. 子项目build.gradle添加依赖   版本号：[![](https://jitpack.io/v/yutils/YSerialPort.svg)](https://jitpack.io/#yutils/YSerialPort)

```
dependencies {
    //更新地址  https://github.com/yutils/YSerialPort 建议过几天访问看下有没有新版本
    implementation 'com.github.yutils:YSerialPort:2.1.7'
}
```

3.在AndroidManifest.xml文件中加入

```
<application
    ...
    tools:replace="android:label" >
```

# 使用方法

可以参考SendActivity.java 

**java**

```java
//String[] device = YSerialPort.getDevices();//获取串口列表
//String[] baudRate = YSerialPort.getBaudRates();//获取波特率列表
//YSerialPort.saveDevice(getApplication(), "/dev/ttyS4");//设置默认串口,可以不设置
//YSerialPort.saveBaudRate(getApplication(), "9600");//设置默认波特率,可以不设置

//创建对象
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

```

**如果要自己解析inputStream，请在start()之前实现此方法**  

```java
//如果要自己解析inputStream，请在start()之前实现此方法
ySerialPort.setInputStreamReadListener(InputStreamReadListener inputStreamReadListener)

//举例：自定义组包
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
```

**kotlin**  
```kotlin
//val device = YSerialPort.getDevices()//获取串口列表
//val baudRate = YSerialPort.getBaudRates() //获取波特率列表
//YSerialPort.saveDevice(application, "/dev/ttyS4") //设置默认串口,可以不设置
//YSerialPort.saveBaudRate(application, "9600") //设置默认波特率,可以不设置

//创建对象
val ySerialPort = YSerialPort(this)
//设置串口,设置波特率,如果设置了默认可以不用设置
ySerialPort.setDevice("/dev/ttyS4", "9600")
//设置数据监听
ySerialPort.addDataListener { hexString, bytes ->
    //结果回调:haxString
    //结果回调:bytes
    //结果回调:size
}
//设置自动组包，每次组包时长为40毫秒，如果40毫秒读取不到数据则返回结果
ySerialPort.isAutoPackage = true
//ySerialPort.maxGroupPackageTime = 40

//或者,设置非自动组包，读取长度1000，超时时间为500毫秒。如果读取到1000立即返回，否则直到读取到超时为止
//ySerialPort.isAutoPackage = false
//ySerialPort.setLengthAndTimeout(1000, 500)

//启动
ySerialPort.start()
//发送文字
ySerialPort.send("你好".toByteArray(Charset.forName("GB18030")))


//退出页面时候注销
override fun onDestroy() {
    super.onDestroy()
    ySerialPort.onDestroy()
}
```

串口文件位置：/proc/tty/drivers

[Android-SerialPort-API](https://github.com/licheedev/Android-SerialPort-API)

不懂的问我QQ：3373217 （别问我为啥手机没串口，别问我模拟器怎么调试串口，别问我USB转的串口为什么有的转换器能识别而有的转换器不能识别）
