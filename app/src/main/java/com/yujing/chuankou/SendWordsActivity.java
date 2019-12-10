
package com.yujing.chuankou;

import android.annotation.SuppressLint;
import android.util.Base64;
import android.util.Log;
import android.view.View;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.yujing.chuankou.databinding.SendingWordsBinding;
import com.yujing.chuankou.zm703.SerialM1;
import com.yujing.utils.YBytes;
import com.yujing.utils.YConvert;
import com.yujing.utils.YSharedPreferencesUtils;
import com.yujing.utils.YToast;
import com.yujing.yserialport.YSerialPort;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * @author yujing
 * 2018年10月30日13:02:56
 */
public class SendWordsActivity extends BaseActivity<SendingWordsBinding> {
    YSerialPort ySerialPort;
    final String 读取电子秤ID = "0252445801EF0D";
    final String 读取电子秤重量 = "0252445301EA0D";
    final String SEND_STRING = "SEND_STRING";
    final String SEND_HEX = "SEND_HEX";

    @Override
    protected Integer getContentLayoutId() {
        return R.layout.sending_words;
    }

    View.OnClickListener sendStringOnClickListener = view -> {
        binding.tvResult.setText("");
        String str = binding.editText.getText().toString();
        if (str.isEmpty()) {
            show("未输入内容！");
            return;
        }
        try {
            ySerialPort.setDataLength(10);
            ySerialPort.send(str.getBytes(Charset.forName("GB18030")));
            //保存数据
            YSharedPreferencesUtils.write(getApplicationContext(), SEND_STRING, str);
        } catch (Exception e) {
            YToast.show(getApplicationContext(), "串口异常");
        }
    };

    View.OnClickListener sendHexOnClickListener = view -> {
        binding.tvResult.setText("");
        String str = binding.etHex.getText().toString().replaceAll("\n", "");
        binding.etHex.setText(str);
        if (str.isEmpty()) {
            show("未输入内容！");
            return;
        }
        try {
            ySerialPort.setDataLength(10);
            ySerialPort.send(YConvert.hexStringToByte(str));
            //保存数据
            YSharedPreferencesUtils.write(getApplicationContext(), SEND_HEX, str);
        } catch (Exception e) {
            YToast.show(getApplicationContext(), "串口异常");
        }
    };

    YSerialPort.DataListener dataListener = (hexString, bytes, size) -> {
        binding.tvResult.setText(hexString);
    };

    @Override
    protected void initData() {
        //上次使用的数据
        binding.editText.setText(YSharedPreferencesUtils.get(this, SEND_STRING));
        binding.etHex.setText(YSharedPreferencesUtils.get(this, SEND_HEX));
        binding.editText.setSelection(binding.editText.getText().length());

        ySerialPort = new YSerialPort(this);
        ySerialPort.setDataLength(10);
        ySerialPort.clearDataListener();
        ySerialPort.addDataListener(dataListener);
        ySerialPort.start();
        binding.button.setOnClickListener(sendStringOnClickListener);
        binding.btHex.setOnClickListener(sendHexOnClickListener);
        binding.buttonTest1.setOnClickListener(v -> printDingji());
        binding.buttonTest2.setOnClickListener(v -> printJiaoShou());
        binding.btDzcId.setOnClickListener(v -> DzcId());
        binding.btDzcWeight.setOnClickListener(v -> DzcWeight());
        binding.btBankCard.setOnClickListener(v -> readBankCard());
        binding.btCardManage.setOnClickListener(v -> readCardManage());
        binding.tvTips.setText("注意：" +
                "\n当前串口：" + YSerialPort.readDevice(this) + "，当前波特率：" + YSerialPort.readBaudRate(this) +
                "\n打印机是：\t/dev/ttyS2\t波特率9600" +
                "\n电子秤是：\t/dev/ttyS2\t波特率9600" +
                "\nM1读卡是：\t/dev/ttyS4\t波特率115200");
    }


    /**
     * 电子秤ID
     */
    private void DzcId() {
        ySerialPort.clearDataListener();
        ySerialPort.addDataListener(dataListener);
        binding.etHex.setText(读取电子秤ID);
        sendHexOnClickListener.onClick(binding.btDzcId);
    }

    /**
     * 电子秤重量
     */
    private void DzcWeight() {
        ySerialPort.clearDataListener();
        ySerialPort.addDataListener(dataListener);
        binding.etHex.setText(读取电子秤重量);
        sendHexOnClickListener.onClick(binding.btDzcWeight);
    }

    /**
     * 读银行卡
     */
    private void readBankCard() {
        try {
            ySerialPort.clearDataListener();
            ySerialPort.setDataLength(7);
            ySerialPort.addDataListener(m1BankCardDataListener);
            byte[] cmd = SerialM1.getComplete(SerialM1.getCommandSearch());
            Log.d("发送串口命令", YConvert.bytesToHexString(cmd));
            binding.tvResult.setText("开始寻卡\n发送串口命令:" + YConvert.bytesToHexString(cmd));
            ySerialPort.send(cmd);
        } catch (IOException e) {
            Log.e("异常", "串口异常", e);
        }
    }

    /**
     * 读工作人员卡
     */
    private void readCardManage() {
        try {
            ySerialPort.clearDataListener();
            ySerialPort.setDataLength(7);
            ySerialPort.addDataListener(m1CardManageDataListener);
            byte[] cmd = SerialM1.getComplete(SerialM1.getCommandSearch());
            Log.d("发送串口命令", YConvert.bytesToHexString(cmd));
            binding.tvResult.setText("开始寻卡\n发送串口命令:" + "\n发送串口命令:" + YConvert.bytesToHexString(cmd));
            ySerialPort.send(cmd);
        } catch (IOException e) {
            Log.e("异常", "串口异常", e);
        }
    }

    /**
     * 打印交售数据
     */
    private void printJiaoShou() {
        String result = "{\"ResultType\":0,\"Msg\":\"确认刷卡打印小票信息成功\",\"Data\":{\"Command\":0,\"DataLength\":0,\"Receipts\":[\"HFcC0+DBv82o1qos0czFqcH0tOYKMDgzNC02MTIwNTU1Cr7Zsai157uwo7owODM0LTYxMjAwMDAKu7bTrdHMxanF89PRvOC2vQq5q7+quavV/aOs0fS54srVubqjoQotLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0Kz8LC1s/ewb86MC4wMEtnCs/CwtbI1cbaOi0tLS0KLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tCrWxyNXT4MG/OjgwLjIwS2cKs/a/2rG4u/XT4MG/OjAuMDBLZwq5+sTavMa7rtPgwb86ODAuMjBLZwq6z82s0+DBvzo4MC4yMEtnCrrPzazX3MG/OjQxMTYuMDBLZwotLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0KyrG85DoyMDE4LTExLTI3IDEwOjUwOjU5CtW+teM6u/DJvdHMteMK0NXD+zrA7rniwrwKus/NrDo2MjE0NTcyMTgwMDAxNjU5ODAzChxXA7rPzazT4MG/zajWqrWlCgoKCgo=\",\"HFcC1tUgILbLOjEKtqi8ttSxOs31s6/OxAqw9cLr1LE6za/B1sj0CsH3y666xTozICgxLzEpCr3wICC27joxOTUuNTEg1KoKw6sgINbYOjM5LjkwIMenv8sKxqQgINbYOjAuMDAgx6e/ywq+uyAg1tg6MzkuOTAgx6e/ywotLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0KQjFLICAgIDE5Ljk1ICAgIDExMS43MgpCMksgICAgMTkuOTUgICAgODMuNzkKLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tCry2sfAgIL671tgoS2cpICC98LbuKNSqKQotLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0KyrG85DoyMDE4LTExLTI3IDEwOjUwOjU5CtW+teM6u/DJvdHMteMK0NXD+zrA7rniwrwKus/NrDo2MjE0NTcyMTgwMDAxNjU5ODAzChxXAyDRzNK2ytW5urn9sPW1pQoKCgoK\"]},\"ErrorCode\":-1,\"ErrorMsg\":\"确认刷卡打印小票信息成功\"}";
        try {
            JSONObject jsonObject = new JSONObject(result);
            List<String> prints = (new Gson()).fromJson(jsonObject.getJSONObject("Data").getJSONArray("Receipts").toString(), new TypeToken<List<String>>() {
            }.getType());
            ySerialPort.clearDataListener();
            ySerialPort.addDataListener(dataListener);
            ySerialPort.setDataLength(10);
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
        } catch (Exception e) {
            YToast.show(getApplicationContext(), "打印机异常");
            Log.e("打印机异常", "打印机异常", e);
        }
    }

    /**
     * 打印定级单
     */
    private void printDingji() {
        @SuppressLint("SimpleDateFormat") SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String name = "烟农：余静";
        String cardId = "卡号：***888888";
        String code = "666";
        String personnel = "定级：余静";
        String time = simpleDateFormat.format(new Date());
        //自检
        try {
            ySerialPort.clearDataListener();
            ySerialPort.addDataListener(dataListener);
            ySerialPort.setDataLength(10);
            ySerialPort.send(YConvert.hexStringToByte(DY.检查));
        } catch (Exception e) {
            YToast.show(getApplicationContext(), "打印机异常");
            Log.e("打印机异常", "打印机异常", e);
        }
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
        try {
            ySerialPort.send(bytes.getBytes());
        } catch (Exception e) {
            YToast.show(getApplicationContext(), "打印机异常");
            Log.e("打印机异常", "打印机异常", e);
        }
    }


    /**
     * m1读银行卡监听
     */
    YSerialPort.DataListener m1BankCardDataListener = (hexString, bytes, size) -> {
        hexString = SerialM1.getHexString(hexString, bytes, size);
        if (hexString == null) return;
        binding.tvResult.setText("");
        binding.tvResult.setText("寻卡结果:\n" + hexString);
        byte[][] data = null;
        if (hexString.length() == 28) {
            try {
                byte[] cmd = SerialM1.getComplete(SerialM1.getCommandMultipleBlock(4, 4, YConvert.hexStringToByte("000000000000")));
                Log.d("发送串口命令", YConvert.bytesToHexString(cmd));
                binding.tvResult.setText("开始寻卡");
                binding.tvResult.setText(binding.tvResult.getText() + "\n发送串口命令:" + YConvert.bytesToHexString(cmd));

                ySerialPort.setDataLength(7 + 16);
                ySerialPort.send(cmd);
            } catch (Exception e) {
            }
        } else if ((hexString.length() - 14) % 32 == 0) {
            data = SerialM1.getData(hexString);
        } else if (bytes[5] != (byte) 255) {
            StringBuilder error = new StringBuilder("\n错误消息:" + hexString);
            if (hexString.contains("55AAFF000340")) {
                error.append("   错误分析：认证失败");
            } else if (hexString.contains("55AAFF000341")) {
                error.append("   错误分析：读卡失败");
            } else if (hexString.contains("55AAFF000342")) {
                error.append("   错误分析：写卡失败");
            }
            binding.tvResult.setText(binding.tvResult.getText() + error.toString());
            return;
        }
        if (data == null || data.length == 0) return;
        //显示
        binding.tvResult.setText(binding.tvResult.getText() + "\n读取数据结果:");
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < data.length; i++) {
            byte[] item = data[i];
            stringBuilder.append("\n").append(i).append("\t").append(":").append(Arrays.toString(item));
        }
        binding.tvResult.setText(binding.tvResult.getText().toString() + stringBuilder.toString());

        if (data.length == 1) {
            byte[] bs = data[0];
            String hex = YConvert.bytesToHexString(bs);
            if (hex.contains("F")) {
                String idCard = hex.substring(0, hex.indexOf("F"));
                Log.i("SendActivity", "卡号一一一〉" + idCard);
                YToast.show(SendWordsActivity.this, idCard);
                binding.tvResult.setText(binding.tvResult.getText() + "\n银行卡号:\n" + idCard);
            }
        }
    };

    /**
     * m1读卡监听
     */
    YSerialPort.DataListener m1CardManageDataListener = (hexString, bytes, size) -> {
        hexString = SerialM1.getHexString(hexString, bytes, size);
        if (hexString == null) return;
        binding.tvResult.setText("");
        binding.tvResult.setText("寻卡结果:\n" + hexString);
        byte[][] data = null;
        if (hexString.length() == 28) {
            try {
                byte[] cmd = SerialM1.getComplete(SerialM1.getCommandMultipleBlock(1, 2, YConvert.hexStringToByte("FFFFFFFFFFFF")));
                Log.d("发送串口命令", YConvert.bytesToHexString(cmd));
                binding.tvResult.setText("开始寻卡");
                binding.tvResult.setText(binding.tvResult.getText() + "\n发送串口命令:" + YConvert.bytesToHexString(cmd));

                ySerialPort.setDataLength(7 + 32);
                ySerialPort.send(cmd);
            } catch (Exception e) {
            }
        } else if ((hexString.length() - 14) % 32 == 0) {
            data = SerialM1.getData(hexString);
        } else if (bytes[5] != (byte) 255) {
            StringBuilder error = new StringBuilder("\n错误消息:" + hexString);
            if (hexString.contains("55AAFF000340")) {
                error.append("   错误分析：认证失败");
            } else if (hexString.contains("55AAFF000341")) {
                error.append("   错误分析：读卡失败");
            } else if (hexString.contains("55AAFF000342")) {
                error.append("   错误分析：写卡失败");
            }
            binding.tvResult.setText(binding.tvResult.getText() + error.toString());
            return;
        }
        if (data == null || data.length == 0) return;
        //显示
        binding.tvResult.setText(binding.tvResult.getText() + "\n读取数据结果:");
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < data.length; i++)
            stringBuilder.append("\n").append(i).append("\t").append(":").append(Arrays.toString(data[i]));
        binding.tvResult.setText(binding.tvResult.getText().toString() + stringBuilder.toString());
        if (data.length <= 1)
            return;

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
    };

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
