package com.yujing.chuankou;

/**
 * 打印机命令，全部采用hex
 *
 * @author yujing 2019年1月4日10:12:57
 * 如：顺时针旋转90度放大8倍调整打印位置打印内容为c3v半切：1C49011D21771D4C60000A430A330A560A0A0A1D5601
 */
public class DY {
    public static final String 换行 = "0c";
    public static final String 检查 = "100401";
    public static final String 打印并走纸一行 = "0a";
    public static final String 打印并回车 = "0d";
    public static final String 标准ASCII字体A = "1b2100";
    public static final String 压缩ASCII字体B = "1b2101";
    public static final String 选择加粗 = "1b2108";
    public static final String 选择倍高 = "1b2110";
    public static final String 选择倍宽 = "1b2120";
    public static final String 选择下划线 = "1b2180";
    public static final String 下划线模式取消 = "1b2D00";
    public static final String 下划线模式1点宽 = "1b2d01";
    public static final String 下划线模式2点宽 = "1b2d02";
    public static final String 设置默认行高 = "1B32";
    public static final String 初始化打印机 = "1b40";

    //设置横向跳格位置1b44 n1...nk  00
    public static final String 横向跳格 = "09";
    public static final String 左对齐 = "1b6100";
    public static final String 中间对齐 = "1b6101";
    public static final String 右对齐 = "1b6102";

    public static final String 顺时针旋转0度 = "1C4900";
    public static final String 顺时针旋转90度 = "1C4901";
    public static final String 旋转180度 = "1B7B01";

    public static final String 放大0倍 = "1D2100";
    public static final String 放大1倍 = "1D2111";
    public static final String 放大2倍 = "1D2122";
    public static final String 放大3倍 = "1D2133";//第一个3横向，第二个3纵向
    public static final String 放大4倍 = "1D2144";
    public static final String 放大5倍 = "1D2155";
    public static final String 放大6倍 = "1D2166";
    public static final String 放大7倍 = "1D2177";
    public static final String 放大8倍 = "1D2188";
    public static final String 左边距 = "1D4C";
    public static final String 设置左边距 = 左边距 + "6000";//6000个单位，左边距设置为 [( 60 + 00 × 256) × 横向移动单位)] 英寸。

    public static final String 半切 = "1D5601";


}
