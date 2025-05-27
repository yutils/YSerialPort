# 设置构建本地库所需的最低 CMake 版本
# 此版本及以上才支持 target_link_options 等指令
cmake_minimum_required(VERSION 3.13)

# 添加一个共享库（.so 文件），名称为 YSerialPort
# 参数为源代码路径，可添加多个 add_library() 来编译多个库
add_library(
        YSerialPort              # 要生成的库名称，对应 libYSerialPort.so
        SHARED                   # 指定为共享库（即动态链接库）
        src/main/cpp/YSerialPort.c)  # 要编译的 C 源文件路径

# 查找 NDK 提供的 log 库，并保存到变量 log-lib 中
find_library(
        log-lib                  # 保存路径的变量名
        log)                     # 要查找的库名称（NDK 自带的 Android 日志库）

# 将 log 库链接到目标库 YSerialPort 中
target_link_libraries(
        YSerialPort              # 目标库，即前面定义的 YSerialPort
        ${log-lib})              # 要链接的系统库变量名

# 添加链接器参数，确保 .so 文件按 16KB 对齐
target_link_options(
        YSerialPort
        PRIVATE
        "-Wl,-z,max-page-size=16384"
)

#打包完成的aar验证16k对齐：
#C:\Users\yujing\AppData\Local\Android\Sdk\build-tools\36.0.0\zipalign.exe -c -v 16384 yserialport\build\outputs\aar\yserialport-release.aar
#wsl
#root@YU-GS:/# readelf -l /mnt/d/Projects/AndroidStudioProjects/lib/YSerialPort/yserialport/build/outputs/aar/jni/arm64-v8a/libYSerialPort.so