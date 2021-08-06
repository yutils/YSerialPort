package com.yujing.chuankou;

import org.junit.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.DecimalFormat;

/**
 * 计算屏幕宽度，实现分辨率兼容自动缩放
 *
 * @author yujing  2020年12月4日11:27:43
 */

/*
像素密度=DPI/160
屏幕宽度DP=实际宽度/像素密度

java
int smallestWidth = getResources().getConfiguration().smallestScreenWidthDp; //屏幕最小宽度
DisplayMetrics dm = getResources().getDisplayMetrics();
show("分辨率：" + dm.widthPixels + "*" + dm.heightPixels + "\nDPI:" + dm.densityDpi + "\n最小宽度" + smallestWidth);

kotlin
val sWidth = resources.configuration.smallestScreenWidthDp //屏幕最小宽度
val dpi = resources.configuration.densityDpi //DPI
var width =  resources.displayMetrics.widthPixels //APP能使用宽度
var height =  resources.displayMetrics.heightPixels //APP能使用高度

//物理宽高
val outSize = Point()
display?.getRealSize(outSize)
val width = outSize.x //屏幕物理宽度
val height = outSize.y //屏幕物理高度
show("DPI：" + sWidth + " " + dpi + " " + width + " " + height)
 */
public class Values_sw {
    private static final DecimalFormat df = new DecimalFormat("#0.00");
    private static final double defaultWidth = 720d;//默认宽度，开发采用的屏幕宽度
    private static final double defaultDpi = 240d;//默认dpi，开发采用的设备dpi,用代码获取
    private static final int MAX_SP = 200;

    @Test
    public void run() {
        System.out.println("设置大小如：\n\tandroid:textSize=\"@dimen/sp20\" \n\tandroid:layout_width=\"@dimen/dp40\" \n请在build.gradle中添加，如下4行代码”\n" +
                "    //资源合并\n" +
                "    sourceSets {\n" +
                "        main { res.srcDirs = ['src/main/res', 'src/main/res_screen'] }\n" +
                "    }");
        //设置默认文件夹
        System.out.print("当前DPI=" + defaultWidth + "\t当前像素密度=" + 1d + "  \t屏幕宽度DP=" + df.format(defaultWidth));
        setDimen(1d, "values");
        //最小dpi=160,最大为580，步长为20
        for (double dpi = 160; dpi <= 540; dpi += 20) {
            double density = dpi / 160d;//密度
            double sWidth = (defaultDpi / 160d) * defaultWidth / density;
            System.out.print("当前DPI=" + dpi + "\t当前像素密度=" + density + "  \t屏幕宽度DP=" + df.format(sWidth));
            setDimen(sWidth / defaultWidth, "values-sw" + ((int) sWidth) + "dp");
        }
//        setDimen(320d / 720d, "values-sw320dp");
//        setDimen(360d / 720d, "values-sw360dp");
//        setDimen(480d / 720d, "values-sw480dp");
//        setDimen(640d / 720d, "values-sw640dp");
    }

    static void setDimen(double scale, String name) {
        StringBuilder sb = new StringBuilder();
        PrintWriter out;
        try {
            sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" + "<resources>\n");
            //dp
            for (int i = 0; i <= defaultWidth; i++)
                sb.append("<dimen name=\"dp").append(i).append("\">").append(df.format(i * scale)).append("dp</dimen>\n");
            //sp
            for (int i = 0; i <= MAX_SP; i++)
                sb.append("<dimen name=\"sp").append(i).append("\">").append(df.format(i * scale)).append("sp</dimen>\n");
            sb.append("</resources>");
            //这里是文件名，1 注意修改 sw 后面的值，和转换值一一对应  2 文件夹和文件要先创建好否则要代码创建
            String filePath = "src/main/res_screen/" + name + "/dimens.xml";
            File file = new File(filePath);
            file.getParentFile().mkdirs();
            out = new PrintWriter(new BufferedWriter(new FileWriter(file)));
            out.println(sb.toString());
            out.close();
            System.out.println("\t创建完成：" + name + "\t比例为：" + df.format(scale) + "\t路径为：" + filePath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
