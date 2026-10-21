# YSerialPort

[简体中文](README.md) | **English**

Based on Android-SerialPort-API, the code has been re-wrapped to read serial port data and reassemble packets, so that a complete frame is returned in a single read. Continuous reads of arbitrary length are supported.

The native `.so` library name and the JNI function names have been rewritten, so it will not cause `.so` conflicts or class-name conflicts with other serial port tools.

Extensively tested over long sessions with: serial printers, PLC communication, serial electronic scales, serial barcode readers, serial QR code readers, serial LED displays, serial NFC readers (reading the M1 area fetches 64 sectors at once; reading the CPU area fetches 16 KB at once).

Theoretically compatible with **Android 4.4 ~ Android 14.0+**

- demo APK 2.2.7 – 2.2.9 → Android 4.4 (API 19); 2.3.0+ → Android 6.0+ (API 23)
- Using this library only requires **API 19+**

![platform](https://img.shields.io/badge/platform-Android-lightgrey.svg)

![Gradle](https://img.shields.io/badge/Gradle-9.8.1-brightgreen.svg)

![last commit](https://img.shields.io/github/last-commit/yutils/YSerialPort.svg)

![repo size](https://img.shields.io/github/repo-size/yutils/YSerialPort.svg)

![android studio](https://img.shields.io/badge/android%20studio-2026.2.1-green.svg)

![maven](https://img.shields.io/badge/maven-address-green.svg)

**[APK files are available in releases. Click to open](https://github.com/yutils/YSerialPort/releases)**

**[The AAR package is available in releases. Click to open](https://github.com/yutils/YSerialPort/releases)**

**If you clone the whole project, open it with the latest Android Studio (AS 2026.2.1 or newer).**

## Gradle uses Java 17

### How to set it: Project Structure → SDK Location → *JDK location was moved to Gradle Settings.* → Gradle JDK → JDK17

## Dependency

### [Add the dependency. Latest version: ——> 2.3.1　　[latest version](https://img.shields.io/badge/latest-2.3.1-green.svg)](https://search.maven.org/artifact/com.kotlinx/yserialport)

```
dependencies {
    // Update page: https://github.com/yutils/YSerialPort
    implementation 'com.kotlinx:yserialport:2.3.1'
}
```

Note: if the dependency cannot be resolved, try the following:

```
allprojects {
    repositories {
        google()
        //mavenCentral()

        // If it still fails to resolve, try maven.org directly
        maven { url 'https://repo1.maven.org/maven2' }
    }
}
```

---

# v2.3.0 · Demo App Full UI Rebuild

The `app` module has been completely rewritten in Kotlin + ViewBinding (Material 3 design language), **landscape for industrial control first, portrait auto-adapts**.

### Screen Structure

| Screen | Description |
| --- | --- |
| Terminal | TX/RX combined: receive area (ASCII/HEX, charset, timestamps, auto line break, 20 000-character cap, long-press to copy) + send area (ASCII/HEX/file, escape sequences, terminator, periodic send) |
| History | Browse / filter / search / copy / send back to input / export TX-RX records to file |
| Quick Commands | Quick commands: add, edit, delete; HEX/ASCII types; one-tap send |
| Auto Reply | Auto-reply rules (contains / equals / prefix / suffix / regex, HEX supports the `??` wildcard), delay, reply-only-once |
| Tools | Checksum calculator (31 CRC variants + SUM/XOR/LRC/BCC/Fletcher/Adler, with adjustable byte order, input/output reflection, XOR, inversion, bit width) + ASCII table (0–255, copyable and sendable) |
| Settings | Full serial parameters (port / baud rate / data bits / stop bits / parity / flow control / frame gap), receive config, send config, appearance (theme / background image / font size / keep screen on) |
| More | Serial ↔ TCP passthrough (server / client), theme switch, built-in or custom background image, export records, restore defaults, open-source link (copy / open in browser), exit app |

### Screenshots

![img](docs/screenshots/01.png)

![img](docs/screenshots/05-command.png)

![img](docs/screenshots/08-ascii-table.png)

![img](docs/screenshots/09-settings.png)

![img](docs/screenshots/10-more.png)

### Adaptive Layout

- Landscape: vertical navigation rail on the left + multi-column content on the right (terminal screen splits receive / send side by side)
- Portrait: top status bar + single-column content + bottom horizontal navigation (terminal screen splits receive / send top and bottom)
- The split container switches orientation automatically based on its own aspect ratio; rotation does not recreate the Activity

### Others

- Dark industrial style is the default theme; light theme and "follow system" are supported
- Built-in AI-generated circuit-board wallpaper; custom background images from the gallery are supported with adjustable scrim
- Wallpaper dimming, terminal font size, history entry limit, etc. are all configurable, and configuration is persisted

The design document is at [`docs/REFACTOR_PLAN.md`](docs/REFACTOR_PLAN.md); test screenshots are in [`docs/screenshots`](docs/screenshots).

### Build & Code Quality

- Dependency versions are pinned to the local Gradle cache, so `./gradlew --offline` works for a fully offline build
- Release builds enable `minifyEnabled` + `shrinkResources`; the release APK is about 2.8 MB
- Cleaned up with Android Lint (125 unused resources, 0 compile warnings); intentionally kept checks are documented in [`app/lint.xml`](app/lint.xml)
- Debug scripts: [`docs/tools/t.sh`](docs/tools/t.sh) (ADB click / screenshot / widget locating) and [`docs/crc_check.py`](docs/crc_check.py) (cross-validates CRC presets against reveng reference values)

---

## Library Enhancements (v2.3.0, backward compatible)

`yserialport` adds an enhanced serial port entry point. **Existing API behavior is completely unchanged:**

```java
SerialPort serialPort = SerialPort.newBuilder(new File("/dev/ttyS1"), 115200)
        .dataBits(8)          // 5 ~ 8
        .parity(0)            // 0:NONE 1:ODD 2:EVEN 3:MARK 4:SPACE
        .stopBits(1)          // 1:1 bit 2:2 bits 3:1.5 bits (requires 5 data bits)
        .flowControl(0)       // 0:none 1:hardware RTS/CTS 2:software XON/XOFF  ← new
        .build();
serialPort.flushBuffers();    // new: flush the TX/RX buffers
```

> The new JNI entry `openEx` is only used when extended parameters are involved (flow control / MARK / SPACE / 1.5 stop bits). Otherwise the original `open` is still used, guaranteeing compatibility.



---

# Usage

See the `app` module for a real integration: the serial send/receive wrapper is in [`core/serial/SerialManager.kt`](app/src/main/java/com/yujing/chuankou/core/serial/SerialManager.kt), and the UI is in [`ui/terminal/TerminalFragment.kt`](app/src/main/java/com/yujing/chuankou/ui/terminal/TerminalFragment.kt).

## Basic Usage

```java
YSerialPort ySerialPort = new YSerialPort(this, "/dev/ttyS4", "9600");
// Set the data listener
ySerialPort.addDataListener(new DataListener() {
    @Override
    public void value(String hexString, byte[] bytes) {
        // Result callback: hexString, bytes
    }
});
ySerialPort.start();
```

## Advanced Usage

**Java**

**Synchronous**

```java
// Stream-based usage: send and receive through the streams yourself
SerialPort serialPort = SerialPort.newBuilder(new File("/dev/ttyS4"), 9600)
        .parity(0)   // parity; 0:none (NONE, default); 1:odd (ODD); 2:even (EVEN)
        .dataBits(8) // data bits, default 8; valid values 5~8
        .stopBits(1) // stop bits, default 1; 1:1 stop bit; 2:2 stop bits
        .build();
serialPort.getInputStream();  // get the input stream
serialPort.getOutputStream(); // get the output stream
serialPort.tryClose();        // close
```

```java
// Synchronous send/receive (no need to create a new SerialPort object every time)
SerialPort serialPort = SerialPort.newBuilder(new File("/dev/ttyS4"), 9600).build();
byte[] bytes = YSerialPort.sendSyncOnce(serialPort, sendBytes, 1000);
byte[] bytes = YSerialPort.sendSyncTime(serialPort, sendBytes, 20, 1000);
byte[] bytes = YSerialPort.sendSyncLength(serialPort, sendBytes, 20, 1000);
serialPort.tryClose(); // close

// Variants that do not hold a SerialPort object (open/close the port internally)
// Send and wait for a response, blocking indefinitely
byte[] bytes = YSerialPort.sendSyncOnce("/dev/ttyS4", "9600", bytes);
// Send and wait for a response until timeout; throws an exception on timeout
byte[] bytes = YSerialPort.sendSyncOnce("/dev/ttyS4", "9600", bytes, 500);
// Keep reassembling packets (maxGroupTime = time per reassembly); returns and closes the connection
// once no data arrives within maxGroupTime
byte[] bytes = YSerialPort.sendSyncTime("/dev/ttyS4", "9600", bytes, 500);
// Keep reassembling packets (maxGroupTime = time per reassembly); returns and closes the connection
// once no data arrives within maxGroupTime (if data keeps arriving, the maximum receive time is maxTime)
byte[] bytes = YSerialPort.sendSyncTime("/dev/ttyS4", "9600", bytes, 500, 3000);
// Keep reassembling packets; returns and closes the connection once the data reaches minLength or times out
byte[] bytes = YSerialPort.sendSyncLength("/dev/ttyS4", "9600", bytes, 500, 3000);
```

**Asynchronous**

```java
//String[] device = YSerialPort.getDevices();      // get the serial port list
//String[] baudRate = YSerialPort.getBaudRates();  // get the baud rate list

//YSerialPort.saveDevice(getApplication(), "/dev/ttyS4"); // set the default port (optional)
//String device = YSerialPort.readDevice(getApplication()); // read the port set above

//YSerialPort.saveBaudRate(getApplication(), "9600");      // set the default baud rate (optional)
//String baudRate = YSerialPort.readBaudRate(getApplication()); // read the baud rate set above

// Asynchronous send/receive
YSerialPort ySerialPort = new YSerialPort(this, "/dev/ttyS4", "9600");
// Set the data listener
ySerialPort.addDataListener(new DataListener() {
    @Override
    public void value(String hexString, byte[] bytes) {
        // Result callback: hexString, bytes
    }
});
// Deliver callbacks on the main thread (main thread by default)
ySerialPort.setThreadMode(ThreadMode.MAIN);
// Automatic reassembly: 40 ms per packet; if no data is read within 40 ms, the result is returned
ySerialPort.setToAuto(); // ySerialPort.setToAuto(40);
// Or manual reassembly: read length 100, timeout 50 ms. If 100 or more bytes are read it returns
// immediately, otherwise it reads until the timeout
//ySerialPort.setToManual(100, 50);
// Start
ySerialPort.start();
// Send text
ySerialPort.send("你好".getBytes(Charset.forName("GB18030")));

// Unregister when leaving the screen
@Override
protected void onDestroy() {
    super.onDestroy();
    ySerialPort.onDestroy();
}

// If you want to parse the inputStream yourself, implement this before calling start()
ySerialPort.setInputStreamReadListener(inputStream -> {
    int count = 0;
    while (count == 0)
        count = inputStream.available();
    byte[] bytes = new byte[count];
    // readCount = number of bytes already read. We need to read `count` bytes here; loop until done.
    // Using inputStream.read(bytes) alone may not read them all.
    int readCount = 0;
    while (readCount < count)
        readCount += inputStream.read(bytes, readCount, count - readCount);
    return bytes;
});
```


**Kotlin**

```kotlin
// Create the object
val ySerialPort = YSerialPort(this, "/dev/ttyS4", "9600")
// Set the data listener
ySerialPort.addDataListener { hexString, bytes ->
    // Result callback: hexString, bytes
}
// Automatic reassembly: 40 ms per packet; if no data is read within 40 ms, the result is returned
ySerialPort.setToAuto() // ySerialPort.setToAuto(40)
// Or manual reassembly: read length 100, timeout 50 ms. If 100 or more bytes are read it returns
// immediately, otherwise it reads until the timeout
//ySerialPort.setToManual(100, 50)
// Start
ySerialPort.start()

// Send text
ySerialPort.send("你好".toByteArray(Charset.forName("GB18030")))

// Unregister when leaving the screen
override fun onDestroy() {
    super.onDestroy()
    ySerialPort.onDestroy()
}
```

## ProGuard / R8

```
-keepclasseswithmembernames class * {
    native <methods>;
}
#-keep class com.yujing.** { *; }
-keep class com.yujing.serialport.* { *; }
-keep class com.yujing.yserialport.* { *; }
```

## Full Example: Reassembling Packets by Protocol Header

```kotlin
// The idea: keep a string buffer, append every chunk of data to it, then check whether the protocol
// header is valid. If yes: take the corresponding length of data and cut off the consumed bytes.
// If not: cut off the header bytes and start over.
class Test {
    var ySerialPort: YSerialPort? = null

    // log
    var showLog = false

    // Data left over from the last reassembly
    private var oldSurplus = ""

    fun test() {
        ySerialPort = YSerialPort(context, "/dev/ttyS2", "9600")
        ySerialPort?.addDataListener { hexString, bytes ->
            dataFilter(hexString)
        }
        ySerialPort?.start()
    }

    // Splitting / reassembling packets. Example packet:
    // 02 2B 30 30 30 30 30 30 30 31 42 03
    // where: header 02 | 2B positive / 2D negative | 6-digit weight | 1 digit decimal places
    //        | 2-digit checksum (from 2B to 30) | 03 end byte
    @Synchronized
    fun dataFilter(hexStr: String) {
        // First append the leftover data from last time
        oldSurplus += hexStr.replace(" ", "")
        if (oldSurplus.isEmpty()) return

        // Define a packet length
        var dataLength = 24

        // Check the length
        if (oldSurplus.length < dataLength) {
            if (showLog) YLog.d("Not enough data yet, wait a moment! $oldSurplus")
            return
        }

        // Validate the protocol header; discard anything that does not start with 02
        if (oldSurplus.substring(0, 2) != "02") {
            if (showLog) YLog.d("Bad data, discard the header! $oldSurplus")
            // Cut off the header; if what remains is at least one full packet, recurse once
            oldSurplus = oldSurplus.substring(2)
            if (oldSurplus.length >= dataLength)
                dataFilter("")
            return
        }
        // If there is a length field, get it; packet length = length field + header + footer,
        // then reassign dataLength with the new packet length
        // dataLength = data length + header + footer

        // Validate the protocol footer, same as above

        // Validate the checksum
        // ......

        // Take the data — 【this is a complete packet!】 【this is a complete packet!】 【this is a complete packet!】
        handle(YConvert.hexStringToByte(oldSurplus.substring(0, dataLength)))

        // Cut off the consumed data
        oldSurplus = oldSurplus.substring(dataLength)

        // What remains may still contain a complete packet, so recurse once
        if (oldSurplus.length >= dataLength)
            dataFilter("")
    }

    // Handle the data. dataByteArray.size should be 8
    private fun handle(dataByteArray: ByteArray) {
        // Parse and convert into an object obj
        // .....

        // Notify the front end
        //YBusUtil.post("data sent by some device", obj)
    }

    // Unregister when leaving the screen
    fun onDestroy() {
        ySerialPort?.onDestroy()
    }
}
```

Serial port device files are listed at: `/proc/tty/drivers`

Thanks to: [Android-SerialPort-API](https://github.com/licheedev/Android-SerialPort-API)

Questions are welcome at QQ: 3373217

For USB-to-serial, please refer to this project:

[CH340/CH341 USB to Serial](https://github.com/yutils/CH34xUART)

---

[简体中文](README.md) | **English**
