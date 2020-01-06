package com.yujing.chuankou.zm703;

import com.yujing.utils.YBytes;
import com.yujing.utils.YConvert;
import com.yujing.utils.YConvertBytes;
import com.yujing.utils.YString;
import com.yujing.yserialport.YSerialPort;

import java.util.List;

/**
 * 串口读取CPU数据
 *
 * @author yujing 2019年12月6日13:06:57
 * 1.寻卡		55AAFF 0003 0233 32
 * 2.CPU转入		55AAFF 0003 0401 06
 * ★发COS命令举例：	55AAFF 0008 04F1 0084000008 71，cos命令为：0084000008（获取8个字节的随机数）
 * 3.选择文件COS	00 A4 00 00 02 3F 02（大卡02 ，小卡01）
 * 4.复合认证		55AAFF 0015 0501 08 00 FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF 19,密码为16个F
 * 或者，	1.取随机数	COS	00 84 00 00 08	，获取9000前面的数据
 * 2.3DES加密	密码：FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF
 * 3.外部认证COS	00 82 00 00 08+加密后的数据
 * 5.选择文件COS	00 A4 00 00 02 00 16		，0016是文件
 * 6.读文件COS	00 B0 0000 0E		，0000是开始位置，0E是读取长度，最长为EF，起始位最大为7FFF
 * 7.写入COS		00 D6 0000 02 FFFF		,0000是开始位置，02是读取长度，数据取为FFFF
 */
public class SerialCpu {
//    public static final String M1_DEVICE = "/dev/ttyS4";
//    public static final String M1_RATE = "115200";

    /**
     * 头部
     *
     * @return
     */
    public static String getHead() {
        return "55AAFF";
    }

    /**
     * 长度位
     *
     * @return
     */
    public static String getLength() {
        return "0000";
    }

    /**
     * 命令,寻卡。55AAFF 0003 0233 32
     * 自动寻卡 022126，
     * 硬件复位自动寻卡 022152，
     *
     * @return
     */
    public static String getCommandSearch() {
        return "022152";
    }

    /**
     * 命令,CPU转入。55AAFF 0003 0401 06
     *
     * @return
     */
    public static String getCpuInto() {
        return "0401";
    }

    /**
     * 命令,CPU转出，CPU停卡。55AAFF 0003 04F0 F7
     * 返回数据55AAFF 0002 FF FD
     *
     * @return
     */
    public static String getCpuExit() {
        return "04F0";
    }

    /**
     * 选择DF文件00 A4 00 00 02 3F 02（大卡02 ，小卡01）
     *
     * @return
     */
    public static String cosSelectDf() {
        return cosSelectDf("02");
    }

    /**
     * 选择DF文件00 A4 00 00 02 3F 02（大卡02 ，小卡01）
     *
     * @param df
     * @return
     */
    public static String cosSelectDf(String df) {
        return "00A40000023F" + df;
    }

    /**
     * 命令,复合认证。55AAFF 0015 0501 08 00 FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF 19
     *
     * @param password
     * @return
     */
    public static String getAuthentication(String password) {
        return "0501" + "0800" + password;
    }

    /**
     * 命令,cos指令。发COS命令举例：	55AAFF 0008 04F1 0084000008 71，cos命令为：0084000008（获取8个字节的随机数）
     *
     * @return
     */
    public static String getCos(String cosHexString) {
        return "04F1" + cosHexString;
    }

    /**
     * 复合认证
     *
     * @return
     */
    public static String getAuthentication() {
        return getAuthentication("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF");
    }


    /**
     * cos命令，选文件0016是文件，00A40000020016
     */
    public static String cosSelectFile() {
        return cosSelectFile("0016");
    }

    /**
     * cos命令，选文件0016是文件，00A40000020016
     *
     * @param file 文件如 0016
     * @return
     */
    public static String cosSelectFile(String file) {
        return "00A4000002" + file;
    }


    /**
     * cos命令，读文件COS，00B0 0000 0E，0000是开始位置，0E是读取长度，最长为EF，起始位最大为7FFF
     *
     * @param start  开始位置，最大为7FFF
     * @param length 长度，最长为EF
     * @return
     */
    public static String cosReadFile(String start, String length) {
        return "00B0" + start + length;
    }

    /**
     * cos命令，写文件COS，00 D6 0000 02 FFFF ,0000是开始位置，02是写入长度，数据取为FFFF，最长为EF，起始位最大为7FFF
     *
     * @return
     */
    public static String cosWriteFile(String hexData) {
        String length = "0" + hexData.length() / 2;
        length = length.substring(length.length() - 2);
        return "00D60000" + length + hexData;
    }

    /**
     * 1-2 字节：命令头    55AA
     * 3 字节：模块地址   FF
     * 4-5 字节：数据长度    0009
     * 6-7 字节：命令字    0481
     * 8-9 字节：读数据长度    如 4K 字节（1000） 10-11 字节：读二进制文件命令码  00B0
     * 12-13 字节：文件起始地址：如   0000
     * 14 字节：校验字    2C
     *
     * @param start
     * @param length
     * @return
     */
    public static String readFile16k(String start, String length) {
        return "0481" + length + "00B0" + start;
    }

    /**
     * 像串口发送数据
     *
     * @param m1    串口工具
     * @param bytes 数据
     */
    public static void send(YSerialPort m1, byte[] bytes) {
        m1.send(bytes);
    }

    /**
     * 将hexString里面的块数据取出来
     *
     * @param hexString 完整真确的包含块数据的hexString
     * @return 块数据
     */
    public static byte[][] getData(String hexString) {
        String ss = hexString.substring(12, hexString.length() - 2);
        List<StringBuilder> stringBuilders = YString.group(ss, 32);
        byte[][] data = new byte[stringBuilders.size()][16];
        for (int j = 0; j < stringBuilders.size(); j++) {
            data[j] = YConvert.hexStringToByte(stringBuilders.get(j).toString());
        }
        return data;
    }

    /**
     * 获取完成信息
     *
     * @param command 命令
     * @return 完整指令
     */
    public static byte[] getComplete(String command) {
        byte[] bytes = YConvert.hexStringToByte(getHead() + getLength() + command);
        byte b = calcCheck(bytes);
        YBytes yBytes = new YBytes(bytes);
        yBytes.addByte(b);
        return yBytes.getBytes();
    }

    /**
     * 计算长度和校验位，异或
     */
    public static byte calcCheck(byte[] bytes) {
        byte[] length = YConvertBytes.intToBytes(bytes.length - 4);
        bytes[3] = length[2];
        bytes[4] = length[3];
        byte b = bytes[0];
        for (int i = 1; i < bytes.length; i++)
            b = (byte) (b ^ bytes[i]);
        return b;
    }
}
