package com.yujing.chuankou.zm703;

import com.yujing.utils.YBytes;
import com.yujing.utils.YConvert;
import com.yujing.utils.YConvertBytes;
import com.yujing.utils.YString;

import java.util.List;

/**
 * 串口读取M1数据
 *
 * @author yujing 2019年12月6日13:06:51
 * 头：55AAFF，长度0004,标识字（或数据域）+ 校验字,从命令头到数据域最后一字节的逐字节异或值
 * 自动寻卡：	55AAFF 0004 022126 01
 * 手动寻卡：	55AAFF 0003 0231 30 //直接返回寻的卡
 * 成功：		55AAFF 0002 FF FD
 * 失败：		55AAFF 0002 01 03
 * 寻到了卡：
 * 55AAFF 0009 FF 000428B72F600F 2D //952卡
 * 55AAFF 0009 FF 000428D611914F C3 //803卡
 * <p>
 * 密钥认证，1扇区
 * 55AAFF 000B 0301 60 01 000000000000 68
 * 正确返回 	55AAFF 0002 FF FD
 * 错误返回 	55AAFF 0002 01 03
 * 读04块
 * 发送命令 	55AAFF 0004 030204 01
 * 正确返回 	55AAFF 0012 FF 6214572180000715952FFFFFFFFFFFFF C5
 * 错误返回 	55AAFF 0002 01 03
 * <p>
 * 4. 写块
 * 发送数据：55AAFF 0014 0303 04 11223344556677881122334455667788 10
 * （操作04块，待写入的数据）
 * 返回数据：55AAFF 0002 FF FD
 * <p>
 * //-----------------------------复合模式-----------------------------
 * 2. 多扇区读/多扇区写
 * M1卡复合读块数据  发送命令：55AAFF000C04A0 0405 60 000000000000 C9
 * （0405起始/结束地址，60密钥A，密钥值）
 * 返回数据：55AAFF 0012 FF xx…xx ED   (所读到的数据)
 * <p>
 * M1卡多扇区写块数据  发送命令：55AAFF 02FC 04A1 013F 60 FFFFFFFFFFFF 00～0000 05
 * （013f起始/结束地址，60密钥A，密钥值，待写入数据）
 * 返回数据：55AAFF 0002 FF FD
 * 发送命令：55AAFF 0003 0310 10
 * 返回数据：55AAFF 0002 FF FD
 */
public class SerialM1 {
    public static final String M1_DEVICE = "/dev/ttyS4";
    public static final String M1_RATE = "115200";
    private static int readType = 0;//读取类型

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
     * 命令,寻卡 55AAFF 0004 022126 01
     *
     * @return
     */
    public static String getCommandSearch() {
        return "022126";
    }

    /**
     * key类型
     */
    public static enum KEYType {
        KEY_A,
        KEY_B
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
     * 多扇区读数据，必须连续块。
     * 如"04A0"+"003F 60 FFFFFFFFFFFF";
     *
     * @param startBlock 开始块号
     * @param endBlock   结束块号
     * @param password   密钥
     * @return 产生的命令
     */
    public static String getCommandMultipleBlock(int startBlock, int endBlock, byte[] password) {
        return getCommandMultipleBlock(startBlock, endBlock, KEYType.KEY_A, password);
    }

    /**
     * 多扇区读数据，必须连续块。
     * 如"04A0"+"003F 60 FFFFFFFFFFFF";
     *
     * @param startBlock 开始块号
     * @param endBlock   结束块号
     * @param keyType    密钥类型KEY_A,KEY_B
     * @param password   密钥
     * @return 产生的命令
     */
    public static String getCommandMultipleBlock(int startBlock, int endBlock, KEYType keyType, byte[] password) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("04A0");//读
        stringBuilder.append(YConvert.bytesToHexString(new byte[]{(byte) startBlock, (byte) endBlock}));
        if (keyType == KEYType.KEY_A) {
            stringBuilder.append("60");
        } else if (keyType == KEYType.KEY_B) {
            stringBuilder.append("61");
        }
        stringBuilder.append(YConvert.bytesToHexString(password));

        return stringBuilder.toString();
    }

    /**
     * 多扇区写数据
     *
     * @param startBlock 开始块号
     * @param endBlock   结束块号
     * @param password   密钥
     * @return 产生的命令
     */
    public static String setCommandMultipleBlock(int startBlock, int endBlock, byte[] password, byte[] data) {
        return setCommandMultipleBlock(startBlock, endBlock, KEYType.KEY_A, password, data);
    }

    /**
     * 多扇区写数据
     *
     * @param startBlock 开始块号
     * @param endBlock   结束块号
     * @param keyType    密钥类型KEY_A,KEY_B
     * @param password   密钥
     * @param data       数据
     * @return 产生的命令
     */
    public static String setCommandMultipleBlock(int startBlock, int endBlock, KEYType keyType, byte[] password, byte[] data) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("04A1");//写
        stringBuilder.append(YConvert.bytesToHexString(new byte[]{(byte) startBlock, (byte) endBlock}));
        if (keyType == KEYType.KEY_A) {
            stringBuilder.append("60");
        } else if (keyType == KEYType.KEY_B) {
            stringBuilder.append("61");
        }
        stringBuilder.append(YConvert.bytesToHexString(password));
        stringBuilder.append(YConvert.bytesToHexString(data));
        return stringBuilder.toString();
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
