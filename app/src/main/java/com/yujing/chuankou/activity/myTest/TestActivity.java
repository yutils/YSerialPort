
package com.yujing.chuankou.activity.myTest;

import android.annotation.SuppressLint;
import android.util.Base64;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.yujing.chuankou.R;
import com.yujing.chuankou.activity.myTest.zm703.SerialM1;
import com.yujing.chuankou.activity.myTest.zm703.ZM703;
import com.yujing.chuankou.base.BaseActivity;
import com.yujing.chuankou.databinding.ActivityTestBinding;
import com.yujing.chuankou.utils.DY;
import com.yujing.chuankou.utils.Setting;
import com.yujing.utils.YBytes;
import com.yujing.utils.YConvert;
import com.yujing.utils.YLog;
import com.yujing.utils.YSharedPreferencesUtils;
import com.yujing.utils.YToast;
import com.yujing.yserialport.YSerialPort;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 各种硬件测试都写在这个类
 *
 * @author yujing
 * 2019年12月12日09:57:41
 */
public class TestActivity extends BaseActivity<ActivityTestBinding> {
    YSerialPort ySerialPort;
    final String 读取电子秤ID = "0252445801EF0D";
    final String 读取电子秤重量 = "0252445301EA0D";
    final String SEND_STRING = "SEND_STRING";
    final String SEND_HEX = "SEND_HEX";

    @Override
    protected Integer getContentLayoutId() {
        return R.layout.activity_test;
    }

    @Override
    protected void initData() {
        //上次使用的数据
        binding.editText.setText(YSharedPreferencesUtils.get(this, SEND_STRING));
        binding.etHex.setText(YSharedPreferencesUtils.get(this, SEND_HEX));
        binding.editText.setSelection(binding.editText.getText().length());
        binding.button.setOnClickListener(v -> sendString());
        binding.btHex.setOnClickListener(v -> sendHexString());
        binding.buttonTest1.setOnClickListener(v -> printDJ());
        binding.buttonTest2.setOnClickListener(v -> printJS());
        binding.buttonTest3.setOnClickListener(v -> printSetDefault());
        binding.btDzcId.setOnClickListener(v -> DzcId());
        binding.btDzcWeight.setOnClickListener(v -> DzcWeight());
        binding.btBankCard.setOnClickListener(v -> readBankCard());
        binding.btCardManage.setOnClickListener(v -> readCardManage());
        binding.btQr.setOnClickListener(v -> printQr());
        binding.tvTips.setText(String.format("注意：\n打印机是：\t/dev/ttyS2\t波特率9600\n电子秤是：\t/dev/ttyS2\t波特率9600\nM1读卡是：\t/dev/ttyS4\t波特率115200", YSerialPort.readDevice(this), YSerialPort.readBaudRate(this)));

        ySerialPort = new YSerialPort(this);
        ySerialPort.clearDataListener();
        ySerialPort.addDataListener(dataListener);
        ySerialPort.start();
        //设置
        Setting.setting(this, binding.includeSet, () -> {
            if (YSerialPort.readDevice(this) != null && YSerialPort.readBaudRate(this) != null)
                ySerialPort.reStart(YSerialPort.readDevice(this), YSerialPort.readBaudRate(this));
            binding.tvResult.setText("");
        });
        //退出
        binding.ButtonQuit.setOnClickListener(v -> finish());
    }

    private void sendString() {
        binding.tvResult.setText("");
        ySerialPort.clearDataListener();
        ySerialPort.addDataListener(dataListener);
        String str = binding.editText.getText().toString();
        if (str.isEmpty()) {
            show("未输入内容！");
            return;
        }
        ySerialPort.send(str.getBytes(Charset.forName("GB18030")), value -> {
            if (!value) YToast.show(getApplicationContext(), "串口异常");
        });
        //保存数据
        YSharedPreferencesUtils.write(getApplicationContext(), SEND_STRING, str);
    }

    private void sendHexString() {
        binding.tvResult.setText("");
        ySerialPort.clearDataListener();
        ySerialPort.addDataListener(dataListener);
        String str = binding.etHex.getText().toString().replaceAll("\n", "");
        binding.etHex.setText(str);
        if (str.isEmpty()) {
            show("未输入内容！");
            return;
        }
        ySerialPort.send(YConvert.hexStringToByte(str));
        //保存数据
        YSharedPreferencesUtils.write(getApplicationContext(), SEND_HEX, str);
    }

    //回调监听
    YSerialPort.DataListener dataListener = (hexString, bytes, size) -> {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                binding.tvResult.setText(hexString);
            }
        });

    };

    /**
     * 打印二维码测试
     */
    private void printQr() {
        String result = "SIZE 50mm,146mm\n" +
                "BLINE 4mm,0\n" +
                "\n" +
                "OFFSET 0mm\n" +
                "SPEED 4\n" +
                "DENSITY 9\n" +
                "DIRECTION 0,0\n" +
                "REFERENCE 0,0\n" +
                "SET PRINTKEY OFF\n" +
                "SET RIBBON ON\n" +
                "CLS\n" +
                "TEXT 40,340,\"FONT001\",0,2,2,\"产地: 会理 海潮烟点\"\n" +
                "TEXT 100,380,\"FONT001\",0,2,2,\"1号收购线\"\n" +
                "TEXT 40,420,\"FONT001\",0,2,2,\"红花大金元 散烟\"\n" +
                "TEXT 40,460,\"FONT001\",0,4,4,\"等级：CX1K\"\n" +
                "TEXT 40,540,\"FONT001\",0,2,2,\"打包管理员：哈么石吉子\"\n" +
                "TEXT 40,580,\"FONT001\",0,2,2,\"定级员：张国伟\"\n" +
                "TEXT 40,620,\"FONT001\",0,2,2,\"烟农：五哈砂海子 等\"\n" +
                "TEXT 40,660,\"FONT001\",0,2,2,\"净重：39.95Kg\"\n" +
                "TEXT 40,700,\"FONT001\",0,2,2,\"成件时间：20180722 13:22\"\n" +
                "\n" +
                "QRCODE 40,740,H,7,A,0,M2,\"http://www.lstobacco.com/GTIN/A/9111510422018119070110000103001498200000000011\"\n" +
                "\n" +
                "TEXT 20,1036,\"FONT001\",0,2,2,\"9111510422018119070110000\"\n" +
                "TEXT 50,1076,\"FONT001\",0,2,2,\"103001498610000000011\"\n" +
                "TEXT 200,1116,\"FONT001\",0,2,2,\"打印次数：1\"\n" +
                "\n" +
                "PRINT 1\n\n";
        ySerialPort.clearDataListener();
        ySerialPort.addDataListener(dataListener);
        //打印
        try {
            ySerialPort.send(result.getBytes("GBK"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    /**
     * 电子秤ID
     */
    private void DzcId() {
        ySerialPort.clearDataListener();
        ySerialPort.addDataListener(dataListener);
        binding.etHex.setText(读取电子秤ID);
        sendHexString();
    }

    /**
     * 电子秤重量
     */
    private void DzcWeight() {
        ySerialPort.clearDataListener();
        ySerialPort.addDataListener(dataListener);
        binding.etHex.setText(读取电子秤重量);
        sendHexString();
    }

    /**
     * 读银行卡
     */
    private void readBankCard() {
        ySerialPort.clearDataListener();
        ySerialPort.addDataListener(new M1ReadDataListener(4, 4, "000000000000"));
        byte[] cmd = SerialM1.getComplete(SerialM1.getCommandSearch());
        binding.tvResult.setText("开始寻卡\n发送串口命令:" + YConvert.bytesToHexString(cmd));
        ySerialPort.send(cmd);
    }

    /**
     * 读工作人员卡
     */
    private void readCardManage() {
        ySerialPort.clearDataListener();
        ySerialPort.addDataListener(new M1ReadDataListener(1, 2, "FFFFFFFFFFFF"));
        byte[] cmd = SerialM1.getComplete(SerialM1.getCommandSearch());
        binding.tvResult.setText("开始寻卡\n发送串口命令:" + "\n发送串口命令:" + YConvert.bytesToHexString(cmd));
        ySerialPort.send(cmd);
    }

    /**
     * 打印交售数据
     */
    private void printJS() {
        String result = "{\"ResultType\":0,\"Msg\":\"确认刷卡打印小票信息成功\",\"Data\":{\"Command\":0,\"DataLength\":0,\"Receipts\":[\"HFcC0+DBv82o1qos0czFqcH0tOYKMDgzNC02MTIwNTU1Cr7Zsai157uwo7owODM0LTYxMjAwMDAKu7bTrdHMxanF89PRvOC2vQq5q7+quavV/aOs0fS54srVubqjoQotLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0Kz8LC1s/ewb86MC4wMEtnCs/CwtbI1cbaOi0tLS0KLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tCrWxyNXT4MG/OjgwLjIwS2cKs/a/2rG4u/XT4MG/OjAuMDBLZwq5+sTavMa7rtPgwb86ODAuMjBLZwq6z82s0+DBvzo4MC4yMEtnCrrPzazX3MG/OjQxMTYuMDBLZwotLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0KyrG85DoyMDE4LTExLTI3IDEwOjUwOjU5CtW+teM6u/DJvdHMteMK0NXD+zrA7rniwrwKus/NrDo2MjE0NTcyMTgwMDAxNjU5ODAzChxXA7rPzazT4MG/zajWqrWlCgoKCgo=\",\"HFcC1tUgILbLOjEKtqi8ttSxOs31s6/OxAqw9cLr1LE6za/B1sj0CsH3y666xTozICgxLzEpCr3wICC27joxOTUuNTEg1KoKw6sgINbYOjM5LjkwIMenv8sKxqQgINbYOjAuMDAgx6e/ywq+uyAg1tg6MzkuOTAgx6e/ywotLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0KQjFLICAgIDE5Ljk1ICAgIDExMS43MgpCMksgICAgMTkuOTUgICAgODMuNzkKLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tCry2sfAgIL671tgoS2cpICC98LbuKNSqKQotLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0KyrG85DoyMDE4LTExLTI3IDEwOjUwOjU5CtW+teM6u/DJvdHMteMK0NXD+zrA7rniwrwKus/NrDo2MjE0NTcyMTgwMDAxNjU5ODAzChxXAyDRzNK2ytW5urn9sPW1pQoKCgoK\"]},\"ErrorCode\":-1,\"ErrorMsg\":\"确认刷卡打印小票信息成功\"}";
        try {
            JSONObject jsonObject = new JSONObject(result);
            List<String> prints = (new Gson()).fromJson(jsonObject.getJSONObject("Data").getJSONArray("Receipts").toString(), new TypeToken<List<String>>() {
            }.getType());
            ySerialPort.clearDataListener();
            ySerialPort.addDataListener(dataListener);
            ySerialPort.send(YConvert.hexStringToByte(DY.检查));
            ySerialPort.send(YConvert.hexStringToByte(DY.初始化打印机));
            ySerialPort.send(YConvert.hexStringToByte(DY.旋转180度));
            //打印
            for (String item : prints)
                ySerialPort.send(Base64.decode(item, Base64.DEFAULT));
            ySerialPort.send(YConvert.hexStringToByte(DY.半切));
        } catch (JSONException e) {
            YToast.show(getApplicationContext(), "解析失败：" + result);
            Log.e("解析失败", result, e);
        }
    }

    //初始化打印机
    private void printSetDefault() {
        try {
            ySerialPort.clearDataListener();
            ySerialPort.addDataListener(dataListener);
            ySerialPort.send(YConvert.hexStringToByte(DY.检查));
            ySerialPort.send(YConvert.hexStringToByte(DY.顺时针旋转0度));
            ySerialPort.send(YConvert.hexStringToByte(DY.放大0倍));
            ySerialPort.send(YConvert.hexStringToByte(DY.初始化打印机));
            YToast.show(getApplicationContext(), "设置成功");
        } catch (Exception e) {
            YToast.show(getApplicationContext(), "设置失败");
            YLog.e("设置失败", e);
        }
    }

    /**
     * 打印定级单
     */
    private void printDJ() {
        @SuppressLint("SimpleDateFormat") SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String name = "烟农：余静";
        String cardId = "卡号：***888888";
        String code = "666";
        String personnel = "定级：余静";
        String time = simpleDateFormat.format(new Date());
        //自检

        ySerialPort.clearDataListener();
        ySerialPort.addDataListener(dataListener);
        ySerialPort.send(YConvert.hexStringToByte(DY.检查));

        YBytes bytes = new YBytes();
        bytes.addByte(YConvert.hexStringToByte(DY.初始化打印机))
                .addByte((name + "\n").getBytes(Charset.forName("GB18030")))
                .addByte((cardId + "\n\n\n\n\n\n").getBytes(Charset.forName("GB18030")))
                .addByte(YConvert.hexStringToByte(DY.顺时针旋转90度))
                .addByte(YConvert.hexStringToByte(DY.放大7倍))
                .addByte(YConvert.hexStringToByte(DY.设置左边距))
                .addByte(code.replaceAll("(.{1})", "$1\n").getBytes(Charset.forName("GB18030")))
                .addByte(YConvert.hexStringToByte(DY.初始化打印机))
                .addByte(("\n\n" + personnel + "\n").getBytes(Charset.forName("GB18030")))
                .addByte((time + "\n\n\n\n\n\n").getBytes(Charset.forName("GB18030")))
                .addByte(YConvert.hexStringToByte(DY.半切));

        ySerialPort.send(bytes.getBytes());
    }


    /**
     * m1读卡监听
     */
    class M1ReadDataListener implements YSerialPort.DataListener {
        private int blockStart;//开始扇区
        private int blockEnd;//结束扇区
        private String password;//密码
        private SerialM1.KEYType keyType;

        public M1ReadDataListener(int blockStart, int blockEnd, String passwordHexString) {
            this(blockStart, blockEnd, passwordHexString, SerialM1.KEYType.KEY_A);
        }

        public M1ReadDataListener(int blockStart, int blockEnd, String passwordHexString, SerialM1.KEYType keyType) {
            this.blockStart = blockStart;
            this.blockEnd = blockEnd;
            this.password = passwordHexString;
            this.keyType = keyType;
        }

        @Override
        public void onDataReceived(String hexString, byte[] bytes, int size) {
            runOnUiThread(() -> {
                binding.tvResult.setText(binding.tvResult.getText() + "\n收到数据：" + hexString);
                Log.d("收到数据", hexString);
                ZM703 zm703 = new ZM703(hexString, bytes, size);
                Log.d("收到数据", zm703.toString());
                if (!zm703.isStatus()) {
                    binding.tvResult.setText(binding.tvResult.getText() + "\n状态:失败");
                    return;
                }
                binding.tvResult.setText(binding.tvResult.getText() + "\nvalue:" + zm703.getDataHexString());
                if (zm703.getDataSize() == 7) {//寻卡结果长度为7
                    readM1();
                } else if (zm703.getDataSize() % 16 == 0) {//数据正好是16的倍数
                    byte[][] data = SerialM1.getData(hexString);//连续读取结果会自动跳过密码块
                    if (data == null || data.length == 0) return;
                    m1DataHandle(data);
                }
            });

        }

        /**
         * 长度加7，每个块长度16，连续读取结果会自动跳过密码块，如：读取0123456789扇块，会跳过3和7
         *
         * @return 长度
         */
        public int getDataLength() {
            int length = 7;
            if (blockStart == blockEnd)
                return length + 16;
            for (int i = blockStart; i <= blockEnd; i++)
                if (i % 4 != 3) length += 16;
            return length;
        }

        /**
         * 读M1扇区指令
         */
        public void readM1() {
            //连续读取结果会自动跳过密码块，一次最多读4个扇区，也就是0-15扇区，应该返回12组数据
            byte[] cmd = SerialM1.getComplete(SerialM1.getCommandMultipleBlock(blockStart, blockEnd, keyType, YConvert.hexStringToByte(password)));
            Log.d("发送串口命令", YConvert.bytesToHexString(cmd));
            binding.tvResult.setText(binding.tvResult.getText() + "\n发送串口命令:" + YConvert.bytesToHexString(cmd));
            ySerialPort.send(cmd);
        }

        /**
         * 读取到m1扇区的结果
         *
         * @param data
         */
        public void m1DataHandle(byte[][] data) {
            //显示
            binding.tvResult.setText(binding.tvResult.getText() + "\n读取数据结果:");
            StringBuilder stringBuilder = new StringBuilder();
            for (int i = 0; i < data.length; i++) {
                byte[] item = data[i];
                stringBuilder.append("\n").append(i).append("\t").append(":").append(Arrays.toString(item));
            }
            binding.tvResult.setText(binding.tvResult.getText().toString() + stringBuilder.toString());
            if (data.length == 1) {
                bankCardHandle(data);
            } else if (data.length == 2) {
                manageHandle(data);
            }
        }
    }

    /**
     * 获取到银行卡信息处理
     *
     * @param data data
     */
    public void bankCardHandle(byte[][] data) {
        byte[] bs = data[0];
        String hex = YConvert.bytesToHexString(bs);
        if (hex.contains("F")) {
            String idCard = hex.substring(0, hex.indexOf("F"));
            Log.i("SendActivity", "卡号一一一〉" + idCard);
            YToast.show(TestActivity.this, idCard);
            binding.tvResult.setText(binding.tvResult.getText() + "\n银行卡号:\n" + idCard);
        }
    }

    /**
     * 获取到工作人员信息处理
     *
     * @param data data
     */
    public void manageHandle(byte[][] data) {
        //检测空长度
        int count0 = 0;
        for (int i = 0; i < data.length; i++)
            for (int j = 0; j < data[i].length; j++)
                if (0 == data[i][j])
                    count0++;
        if (count0 == data.length * data[0].length)
            return;

        //显示
        binding.tvResult.setText(binding.tvResult.getText() + "\n翻译hexString:");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length; i++)
            sb.append("\n").append(i).append("\t").append(":").append(YConvert.bytesToHexString(data[i]));
        binding.tvResult.setText(binding.tvResult.getText().toString() + sb.toString());

        //解析名字和id
        try {
            binding.tvResult.setText(binding.tvResult.getText() + "\nid：" + new String(data[0], "US-ASCII"));
            binding.tvResult.setText(binding.tvResult.getText() + "\n名字：" + new String(copyToStop(data[1], (byte) 0), "GB18030"));
        } catch (UnsupportedEncodingException e) {
            binding.tvResult.setText(binding.tvResult.getText() + "\n转码错误：" + e.getMessage());
        }
    }

    public static byte[] copyToStop(byte[] bytes, byte stopKey) {
        int point = 0;
        for (; point < bytes.length; point++) if (bytes[point] == stopKey) break;
        byte[] name = new byte[point];
        System.arraycopy(bytes, 0, name, 0, point);
        return name;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ySerialPort.onDestroy();
    }
}
