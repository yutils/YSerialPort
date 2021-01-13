
package com.yujing.chuankou.activity.myTest.zm703;

import android.annotation.SuppressLint;
import android.widget.ArrayAdapter;

import com.yujing.chuankou.R;
import com.yujing.chuankou.base.BaseActivity;
import com.yujing.chuankou.databinding.ActivityZm703M1Binding;
import com.yujing.chuankou.utils.Setting;
import com.yujing.contract.YSuccessFailListener;
import com.yujing.utils.YLog;
import com.yujing.yserialport.YSerialPort;

import java.util.Arrays;

/**
 * zm703读卡器 读取m1区
 *
 * @author yujing 2020年8月13日19:47:40
 */
@SuppressLint("SetTextI18n")
public class ZM703CardM1Activity extends BaseActivity<ActivityZm703M1Binding> {
    YSerialPort ySerialPort;

    public ZM703CardM1Activity() {
        super(R.layout.activity_zm703_m1);
    }

    @Override
    protected void init() {
        ySerialPort = new YSerialPort(this);
        ySerialPort.clearDataListener();
        ySerialPort.start();
        binding.btCardM1Read.setOnClickListener(v -> readM1Read());
        binding.btCardM1Write.setOnClickListener(v -> readM1Write());
        binding.btSetDyk.setOnClickListener(v -> set("0", "63", "665544332211"));
        binding.btSetV3Gzry.setOnClickListener(v -> set("1", "5", "ffffffffffff"));
        binding.btSetV3Yhk.setOnClickListener(v -> set("4", "4", "000000000000"));
        binding.btClear.setOnClickListener(v -> binding.tvResult.setText(""));
        binding.tvTips.setText(String.format("注意：\n\t\tZM703读卡器：\t/dev/ttyS4\t波特率115200", ySerialPort.getDevice(), ySerialPort.getBaudRate()));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, Arrays.asList("KeyA", "KeyB"));
        binding.sp.setAdapter(adapter);

        //设置
        Setting.setting(this, binding.includeSet, () -> {
            if (YSerialPort.readDevice(this) != null && YSerialPort.readBaudRate(this) != null)
                ySerialPort.reStart(YSerialPort.readDevice(this), YSerialPort.readBaudRate(this));
            binding.tvResult.setText("");
        });
        //退出
        binding.ButtonQuit.setOnClickListener(v -> finish());
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
        if (!check()) return;
        String blockStartString = binding.etBlockStart.getText().toString();
        String blockEndString = binding.etBlockEnd.getText().toString();
        String blockPasswordString = binding.etBlockPassword.getText().toString();
        int blockStart = Integer.parseInt(blockStartString);
        int blockEnd = Integer.parseInt(blockEndString);
        binding.tvResult.setText("开始");
        SerialM1.KEYType keyType = binding.sp.getSelectedItemPosition() == 0 ? SerialM1.KEYType.KEY_A : SerialM1.KEYType.KEY_B;
        M1ReadDataListener listener = new M1ReadDataListener(ySerialPort, blockStart, blockEnd, blockPasswordString, keyType);
        listener.setDataListener(s -> {

        });
        listener.setDataNoFListener(s -> {
            binding.etBlockData.setText(s);
        });
        listener.setLogListener(s -> {
            YLog.d(s);
            binding.tvResult.setText(binding.tvResult.getText().toString() + "\n" + s);
        });
        listener.setFailListener(s -> {

        });
        listener.search();
    }

    /**
     * 写入
     */
    private void readM1Write() {
        if (!check()) return;
        String blockStartString = binding.etBlockStart.getText().toString();
        String blockEndString = binding.etBlockEnd.getText().toString();
        String blockPasswordString = binding.etBlockPassword.getText().toString();
        String blockData = binding.etBlockData.getText().toString();
        int blockStart = Integer.parseInt(blockStartString);
        int blockEnd = Integer.parseInt(blockEndString);
        binding.tvResult.setText("开始");
        SerialM1.KEYType keyType = binding.sp.getSelectedItemPosition() == 0 ? SerialM1.KEYType.KEY_A : SerialM1.KEYType.KEY_B;
        M1WriteDataListener listener = new M1WriteDataListener(ySerialPort, blockStart, blockEnd, blockPasswordString, keyType, blockData);
        int writeLength = listener.getDataLength() * 2;
        if (blockData.length() != writeLength) {
            show("写入数据长度不正确，当前长度：" + blockData.length() + "需要长度：" + writeLength);
            return;
        }

        listener.setLogListener(s -> {
            YLog.d(s);
            binding.tvResult.setText(binding.tvResult.getText().toString() + "\n" + s);
        });
        listener.setSuccessFailListener(new YSuccessFailListener<String, String>() {
            @Override
            public void success(String s) {
                binding.tvResult.setText(binding.tvResult.getText().toString() + "\n" + s);
            }

            @Override
            public void fail(String s) {
                binding.tvResult.setText(binding.tvResult.getText().toString() + "\n" + s);
            }
        });
        listener.search();
    }

    //检查完整性
    private boolean check() {
        String blockStartString = binding.etBlockStart.getText().toString();
        String blockEndString = binding.etBlockEnd.getText().toString();
        String blockPasswordString = binding.etBlockPassword.getText().toString();
        if (blockStartString.isEmpty()) {
            show("开始块不能为空");
            return false;
        }
        if (blockEndString.isEmpty()) {
            show("结束块不能为空");
            return false;
        }
        if (blockPasswordString.isEmpty()) {
            show("密码不能为空");
            return false;
        }
        if (blockPasswordString.length() != 12) {
            show("密码长度不正确");
            return false;
        }
        int blockStart = Integer.parseInt(blockStartString);
        int blockEnd = Integer.parseInt(blockEndString);
        if (blockStart > blockEnd) {
            show("开始扇区不能大于结束扇区");
            return false;
        }
        return true;
    }

    @Override
    public void onDestroy() {
        ySerialPort.onDestroy();
        super.onDestroy();
    }
}

