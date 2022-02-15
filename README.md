# YSerialPort

源Android-SerialPort-API，重新封装代码，实现读取串口数据，实现重新组包一次性读取完整数据。可连续读取任意长度数据。

重写串口so库名称和调用函数名称，不与其他串口工具形成so冲突或者类名冲突。

已经多次长时间测试：串口打印机，PLC通信，串口电子秤，串口条码读卡器，串口二维码读卡器，串口LED屏，串口NFC读卡器，读M1区一次性读取64个扇区，读CPU区一次读取16KB数据。

理论上兼容 安卓4.0~安卓12.0

[![platform](https://img.shields.io/badge/platform-Android-lightgrey.svg)](https://developer.android.google.cn/studio/index.html)
![Gradle](https://img.shields.io/badge/Gradle-7.1-brightgreen.svg)
[![last commit](https://img.shields.io/github/last-commit/yutils/YSerialPort.svg)](https://github.com/yutils/YSerialPort/commits/master)
![repo size](https://img.shields.io/github/repo-size/yutils/YSerialPort.svg)
![android studio](https://img.shields.io/badge/android%20studio-2020.3.1-green.svg)
[![maven](https://img.shields.io/badge/maven-address-green.svg)](https://search.maven.org/artifact/com.kotlinx/yserialport)

**[releases里面有APK文件。点击前往](https://github.com/yutils/YSerialPort/releases)**

**[releases里面有AAR包。点击前往](https://github.com/yutils/YSerialPort/releases)**

**如果拉取整个项目，请用最新AS（AS2021.1.1以上）打开**

# `不建议直接拉取项目编译，请仔细看完 ` #

## 已经从jitpack.io仓库移动至maven中央仓库

## 引用

### [子module添加依赖，当前最新版：————> 2.2.5　　　　![最新版](https://img.shields.io/badge/%E6%9C%80%E6%96%B0%E7%89%88-2.2.5-green.svg)](https://search.maven.org/artifact/com.kotlinx/yserialport)

```
dependencies {
    //更新地址  https://github.com/yutils/YSerialPort 建议过几天访问看下有没有新版本
    implementation 'com.kotlinx:yserialport:2.2.5'
}
```

注：如果引用失败，或者工程未引入mavenCentral，请引入：mavenCentral()
```
allprojects {
    repositories {
        mavenCentral()
        //阿里云等...
    }
}
```

# 使用方法

可以参考SendActivity.java

## 基础使用方法

```java
YSerialPort ySerialPort=new YSerialPort(this,"/dev/ttyS4","9600");
//设置数据监听
ySerialPort.addDataListener(new DataListener(){
@Override
public void value(String hexString,byte[]bytes){
    //结果回调:haxString , bytes
    }
});
ySerialPort.start();
```

## 扩展使用方法

**java**  

**同步** 

```java
//拿流用法，自己通过流收发数据
SerialPort serialPort=SerialPort.newBuilder(new File("/dev/ttyS4"),9600).build();
serialPort.getInputStream();//获取输入流
serialPort.getOutputStream();//获取输出流
serialPort.tryClose();//关闭
        
//同步收发 （不用每次都创建serialPort对象）
SerialPort serialPort=SerialPort.newBuilder(new File("/dev/ttyS4"),9600).build();
byte[]bytes=YSerialPort.sendSyncOnce(serialPort,bys,1000);
byte[]bytes=YSerialPort.sendSyncTime(serialPort,bys,20,1000);
byte[]bytes=YSerialPort.sendSyncLength(serialPort,bys,20,1000);
serialPort.tryClose();//关闭
        
//发送并等待返回，死等
byte[]bytes=YSerialPort.sendSyncOnce("/dev/ttyS4","9600",bytes);
//发送并等待返回，直到超时，如果超时则向上抛异常
byte[]bytes=YSerialPort.sendSyncOnce("/dev/ttyS4","9600",bytes,500);
//一直不停组包，（maxGroupTime每次组包时间）当在maxGroupTime时间内没有数据，就返回并关闭连接
byte[]bytes=YSerialPort.sendSyncTime("/dev/ttyS4","9600",bytes,500);
//一直不停组包，（maxGroupTime每次组包时间）当在maxGroupTime时间内没有数据，就返回并关闭连接（如果一直有数据，最多接收时间为maxTime）
byte[]bytes=YSerialPort.sendSyncTime("/dev/ttyS4","9600",bytes,500,3000);
//一直不停组包，当数据长度达到minLength或超时，返回并关闭连接
byte[]bytes=YSerialPort.sendSyncLength("/dev/ttyS4","9600",bytes,500,3000);

```

**异步：（推荐）**

```java

//String[] device = YSerialPort.getDevices();//获取串口列表
//String[] baudRate = YSerialPort.getBaudRates();//获取波特率列表

//YSerialPort.saveDevice(getApplication(), "/dev/ttyS4");//设置默认串口,可以不设置
//String device=YSerialPort.readDevice(getApplication());//获取上面设置的串口

//YSerialPort.saveBaudRate(getApplication(), "9600");//设置默认波特率,可以不设置
//String baudRate=YSerialPort.readBaudRate(getApplication());//获取上面设置的波特率

//异步收发
YSerialPort ySerialPort=new YSerialPort(this,"/dev/ttyS4","9600");
//设置数据监听
ySerialPort.addDataListener(new DataListener(){
@Override
public void value(String hexString,byte[]bytes){
    //结果回调:haxString , bytes
    }
});
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
protected void onDestroy(){
    super.onDestroy();
    ySerialPort.onDestroy();
}

//如果要自己解析inputStream，请在start()之前实现此方法
ySerialPort.setInputStreamReadListener(inputStream->{
    int count=0;
    while(count==0)
        count=inputStream.available();
    byte[]bytes=new byte[count];
    //readCount，已经成功读取的字节的个数，这儿需读取count个数据，不够则循环读取，如果采用inputStream.read(bytes);可能读不完
    int readCount=0;
    while(readCount<count)
        readCount+=inputStream.read(bytes,readCount,count-readCount);
    return bytes;
});
```


**kotlin（推荐）**

```kotlin
//创建对象
val ySerialPort = YSerialPort(this, "/dev/ttyS4", "9600")
//设置数据监听
ySerialPort.addDataListener { hexString, bytes ->
    //结果回调:haxString , bytes
}
//设置自动组包，每次组包时长为40毫秒，如果40毫秒读取不到数据则返回结果
ySerialPort.setToAuto() //ySerialPort.setToAuto(40)
//或者,设置手动组包，读取长度100，超时时间为50毫秒。如果读取到数据大于等于100立即返回，否则直到读取到超时为止
//ySerialPort.setToManual(100,50)
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

## 根据协议头组包完整示例

```kotlin
//原理就是准备一个字符串，每次都数据都拼接到后面，然后判断协议头是否正确？（对了就取对应长度数据然后剪掉用过的数据长度）:（如果不对就剪掉协议头数据，重新开始）
class Test {
    var ySerialPort: YSerialPort? = null

    //log
    var showLog = false

    //最后一次组包剩余数据
    private var oldSurplus = ""

    fun test() {
        ySerialPort = YSerialPort(context, "/dev/ttyS2", "9600")
        ySerialPort?.addDataListener { hexString, bytes ->
            dataFilter(hexString)
        }
        ySerialPort?.start()
    }

    //拆包组包，举例：数据包：02 2B 30 30 30 30 30 30 30 31 42 03  //其中：协议头 02  2B正2D负   6位重量  1位小数点位数  2位校验(2B到30) 03结束位
    @Synchronized
    fun dataFilter(hexStr: String) {
        //先组装上一次剩余数据
        oldSurplus += hexStr.replace(" ", "")
        if (oldSurplus.isEmpty()) return

        //定义一个包长度
        var dataLength = 24

        //判断长度
        if (oldSurplus.length < dataLength) {
            if (showLog) YLog.d("数据长度不够，等一手！$oldSurplus")
            return
        }

        //验证协议头，非02开头就抛弃
        if (oldSurplus.substring(0, 2) != "02") {
            if (showLog) YLog.d("数据异常，抛弃数据头！$oldSurplus")
            //剪掉数据头，如果剩余数据大于一个完整包，递归一次
            oldSurplus = oldSurplus.substring(2)
            if (oldSurplus.length >= dataLength)
                dataFilter("")
            return
        }
        //如果有数据长度位，获取长度位+包头+包尾=包长，dataLength重新赋值新的包长
        //dataLength=数据长度+包头+包尾

        //验证协议尾，同上

        //验证校验码
        //......

        //获取数据处理 【这儿就是一个完整的数据包！】 【这儿就是一个完整的数据包！】 【这儿就是一个完整的数据包！】 
        handle(YConvert.hexStringToByte(oldSurplus.substring(0, dataLength)))

        //剪去用过的数据
        oldSurplus = oldSurplus.substring(dataLength)

        //剩余数据可能还能有完整包。所以递归一次
        if (oldSurplus.length >= dataLength)
            dataFilter("")
    }
    
    //处理数据。dataByteArray的长度应该是8
    private fun handle(dataByteArray: ByteArray) {
        //处理分析，转换成对象obj
        //.....

        //通知前端
        //YBusUtil.post("某某设备发送的数据", obj)
    }
    
    //退出页面时候注销
    fun onDestroy() {
        ySerialPort?.onDestroy()
    }
}
```

串口文件位置：/proc/tty/drivers

感谢：[Android-SerialPort-API](https://github.com/licheedev/Android-SerialPort-API)

不懂的问我QQ：3373217 （别问我为啥手机没串口，别问我模拟器怎么调试串口，别问我USB转的串口为什么不能识别）

如果是USB转串口，请参考这个工程：

[CH340/CH341的USB转串口](https://github.com/yutils/CH34xUART)


