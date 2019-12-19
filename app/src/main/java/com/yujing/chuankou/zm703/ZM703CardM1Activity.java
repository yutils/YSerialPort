
package com.yujing.chuankou.zm703;

import android.annotation.SuppressLint;
import android.util.Log;
import android.widget.ArrayAdapter;

import com.yujing.chuankou.BaseActivity;
import com.yujing.chuankou.R;
import com.yujing.chuankou.databinding.ActivityZm703M1Binding;
import com.yujing.utils.YConvert;
import com.yujing.yserialport.YSerialPort;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * zm703读卡器 读取m1区
 *
 * @author yujing 2019年12月3日16:18:35
 */
@SuppressLint("SetTextI18n")
public class ZM703CardM1Activity extends BaseActivity<ActivityZm703M1Binding> {
    YSerialPort ySerialPort;

    @Override
    protected Integer getContentLayoutId() {
        return R.layout.activity_zm703_m1;
    }

    @Override
    protected void initData() {
        ySerialPort = new YSerialPort(this);
        ySerialPort.setDataLength(10);
        ySerialPort.clearDataListener();
        ySerialPort.start();

        binding.btCardM1Read.setOnClickListener(v -> readM1Read());
        binding.btCardM1Write.setOnClickListener(v -> readM1Write());
        binding.btSetDyk.setOnClickListener(v -> set("0", "32", "665544332211"));
        binding.btSetV3Gzry.setOnClickListener(v -> set("1", "5", "ffffffffffff"));
        binding.btSetV3Yhk.setOnClickListener(v -> set("4", "4", "000000000000"));
        binding.btClear.setOnClickListener(v -> binding.tvResult.setText(""));
        binding.tvTips.setText(String.format("注意：当前串口：%s，当前波特率：%s。\t\tZM703读卡器：\t/dev/ttyS4\t波特率115200", ySerialPort.getDevice(),ySerialPort.getBaudRate()));

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, Arrays.asList("KeyA", "KeyB"));
        binding.sp.setAdapter(adapter);
    }

    private void set(String blockStart, String blockEnd, String password) {
        binding.etBlockStart.setText(blockStart);
        binding.etBlockEnd.setText(blockEnd);
        binding.etBlockPassword.setText(password);
    }

    /**
     * 读M1
     */
    private void readM1Read() {
        try {
            String blockStartString = binding.etBlockStart.getText().toString();
            String blockEndString = binding.etBlockEnd.getText().toString();
            String blockPasswordString = binding.etBlockPassword.getText().toString();
            if (blockStartString.isEmpty()) {
                show("开始块不能为空");
                return;
            }
            if (blockEndString.isEmpty()) {
                show("结束块不能为空");
                return;
            }
            if (blockPasswordString.isEmpty()) {
                show("密码不能为空");
                return;
            }
            if (blockPasswordString.length() != 12) {
                show("密码长度不正确");
                return;
            }
            int blockStart = Integer.parseInt(blockStartString);
            int blockEnd = Integer.parseInt(blockEndString);
            if (blockStart > blockEnd) {
                show("开始扇区不能大于结束扇区");
                return;
            }

            SerialM1.KEYType keyType = binding.sp.getSelectedItemPosition() == 0 ? SerialM1.KEYType.KEY_A : SerialM1.KEYType.KEY_B;
            M1ReadDataListener listener = new M1ReadDataListener(blockStart, blockEnd, blockPasswordString, keyType);
            byte[] cmd = SerialM1.getComplete(SerialM1.getCommandSearch());
            Log.d("发送串口命令", YConvert.bytesToHexString(cmd));
            binding.tvResult.setText("开始寻卡\n发送串口命令:" + YConvert.bytesToHexString(cmd));

            ySerialPort.clearDataListener();
            ySerialPort.setDataLength(7);
            ySerialPort.addDataListener(listener);
            ySerialPort.send(cmd);
        } catch (IOException e) {
            Log.e("异常", "串口异常", e);
        }
    }

    /**
     * 写入
     */
    private void readM1Write() {
        try {
            String blockStartString = binding.etBlockStart.getText().toString();
            String blockEndString = binding.etBlockEnd.getText().toString();
            String blockPasswordString = binding.etBlockPassword.getText().toString();
            String blockData = binding.etBlockData.getText().toString();
            if (blockStartString.isEmpty()) {
                show("开始块不能为空");
                return;
            }
            if (blockEndString.isEmpty()) {
                show("结束块不能为空");
                return;
            }
            if (blockPasswordString.isEmpty()) {
                show("密码不能为空");
                return;
            }
            if (blockPasswordString.length() != 12) {
                show("密码长度不正确");
                return;
            }
            int blockStart = Integer.parseInt(blockStartString);
            int blockEnd = Integer.parseInt(blockEndString);
            if (blockStart > blockEnd) {
                show("开始扇区不能大于结束扇区");
                return;
            }
            SerialM1.KEYType keyType = binding.sp.getSelectedItemPosition() == 0 ? SerialM1.KEYType.KEY_A : SerialM1.KEYType.KEY_B;
            M1WriteDataListener m1WriteDataListener = new M1WriteDataListener(blockStart, blockEnd, blockPasswordString, keyType, blockData);
            int writeLength = m1WriteDataListener.getDataLength() * 2;
            if (blockData.length() != writeLength) {
                show("写入数据长度不正确，当前长度：" + blockData.length() + "需要长度：" + writeLength);
                return;
            }

            byte[] cmd = SerialM1.getComplete(SerialM1.getCommandSearch());
            Log.d("发送串口命令", YConvert.bytesToHexString(cmd));
            binding.tvResult.setText("开始寻卡\n发送串口命令:" + YConvert.bytesToHexString(cmd));

            ySerialPort.clearDataListener();
            ySerialPort.setDataLength(7);
            ySerialPort.addDataListener(m1WriteDataListener);
            ySerialPort.send(cmd);
        } catch (IOException e) {
            Log.e("异常", "串口异常", e);
        }
    }

    @Override
    protected void onDestroy() {
        ySerialPort.onDestroy();
        super.onDestroy();
    }

    //*********************************************************类完毕**********************************************************************

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
            try {
                //连续读取结果会自动跳过密码块，一次最多读4个扇区，也就是0-15扇区，应该返回12组数据
                ySerialPort.setDataLength(getDataLength(), 100);
                ySerialPort.setGroupPackageTime(5);
                byte[] cmd = SerialM1.getComplete(SerialM1.getCommandMultipleBlock(blockStart, blockEnd, keyType, YConvert.hexStringToByte(password)));
                Log.d("发送串口命令", YConvert.bytesToHexString(cmd));
                binding.tvResult.setText(binding.tvResult.getText() + "\n发送串口命令:" + YConvert.bytesToHexString(cmd));
                ySerialPort.send(cmd);
            } catch (Exception e) {
            }
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
            for (int i = 0; i < data.length; i++)
                stringBuilder.append("\n").append(i).append("\t").append(":").append(Arrays.toString(data[i]));
            binding.tvResult.setText(binding.tvResult.getText().toString() + stringBuilder.toString());
            //赋值写数据
            StringBuilder writeBuilder = new StringBuilder();
            for (byte[] item : data) writeBuilder.append(YConvert.bytesToHexString(item));
            binding.etBlockData.setText(writeBuilder.toString());

            //翻译每个块内容
            StringBuilder string = new StringBuilder("\n基础翻译：");
            for (int i = 0; i < data.length; i++) {
                byte[] item = data[i];
                Log.i(i + "\t" + "读卡数据", YConvert.bytesToHexString(item));
                string.append("\n\n").append(i).append("\t").append("原始数据\t\t\t\t：").append(YConvert.bytesToHexString(item));
                try {
                    string.append("\n").append(i).append("\t").append("翻译(US_ASCII)\t：").append(new String(item, StandardCharsets.US_ASCII));
                    string.append("\n").append(i).append("\t").append("翻译(GB18030)\t：").append(new String(item, "GB18030"));
                    string.append("\n").append(i).append("\t").append("翻译(GBK)\t\t\t\t：").append(new String(item, "GBK"));
                    string.append("\n").append(i).append("\t").append("翻译(GB2312)\t\t：").append(new String(item, "GB2312"));
                    string.append("\n").append(i).append("\t").append("翻译(UTF_8)\t\t\t：").append(new String(item, StandardCharsets.UTF_8));
                    string.append("\n").append(i).append("\t").append("翻译(BCD)\t\t\t：").append(YConvert.bcd2String(item));
                } catch (Exception e) {
                    string.append("\n").append(i).append("\t").append("翻译错误\t\t\t\t：").append(i).append(e.getMessage());
                }
            }
            binding.tvResult.setText(binding.tvResult.getText().toString() + string.toString());
        }
    }

    /**
     * m1写卡监听
     */
    class M1WriteDataListener implements YSerialPort.DataListener {
        private int blockStart;//开始扇区
        private int blockEnd;//结束扇区
        private String password;//密码
        private SerialM1.KEYType keyType = SerialM1.KEYType.KEY_A;
        private String data;//data

        public M1WriteDataListener(int blockStart, int blockEnd, String password, String data) {
            this.blockStart = blockStart;
            this.blockEnd = blockEnd;
            this.password = password;
            this.data = data;
        }

        public M1WriteDataListener(int blockStart, int blockEnd, String password, SerialM1.KEYType keyType, String data) {
            this.blockStart = blockStart;
            this.blockEnd = blockEnd;
            this.password = password;
            this.keyType = keyType;
            this.data = data;
        }

        /**
         * 每个块长度16，连续写入会自动跳过密码块，如：读取0123456789扇块，会跳过3和7
         *
         * @return 长度
         */
        public int getDataLength() {
            int length = 0;
            if (blockStart == blockEnd)
                return length + 16;
            for (int i = blockStart; i <= blockEnd; i++)
                if (i % 4 != 3) length += 16;
            return length;
        }

        /**
         * 读M1扇区指令
         */
        public void writeM1() {
            try {
                //连续读取结果会自动跳过密码块，一次最多读4个扇区，也就是0-15扇区，应该返回12组数据
                ySerialPort.setDataLength(7);
                byte[] cmd = SerialM1.getComplete(SerialM1.setCommandMultipleBlock(blockStart, blockEnd, keyType, YConvert.hexStringToByte(password), YConvert.hexStringToByte(data)));
                Log.d("发送串口命令", YConvert.bytesToHexString(cmd));
                binding.tvResult.setText(binding.tvResult.getText() + "\n发送串口命令:" + YConvert.bytesToHexString(cmd));
                ySerialPort.send(cmd);
            } catch (Exception e) {
            }
        }

        @Override
        public void onDataReceived(String hexString, byte[] bytes, int size) {
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
                writeM1();
            } else if (zm703.getDataSize() == 0) {
                if (zm703.isStatus()) {
                    binding.tvResult.setText(binding.tvResult.getText() + "\n状态:成功");
                }
            }
        }
    }
}

