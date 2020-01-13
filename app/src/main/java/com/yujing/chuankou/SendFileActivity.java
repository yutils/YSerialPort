package com.yujing.chuankou;


import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import com.yujing.chuankou.databinding.ActivitySendFileBinding;
import com.yujing.chuankou.xmodem.Xmodem;
import com.yujing.utils.YConvert;
import com.yujing.utils.YShow;
import com.yujing.yserialport.YSerialPort;

import java.io.File;

public class SendFileActivity extends BaseActivity<ActivitySendFileBinding> {
    YSerialPort ySerialPort;
    File sendFile = null;//要发送的文件

    @Override
    protected Integer getContentLayoutId() {
        return R.layout.activity_send_file;
    }

    @Override
    protected void initData() {
        //选择文件
        binding.buttonBrowse.setOnClickListener(v -> onClick());
        //发送文件
        binding.btSend.setOnClickListener(v -> sendFile());
        //发送文件Xmodem
        binding.btSendXmodem.setOnClickListener(v -> sendFileXmoden());
        //初始化
        ySerialPort = new YSerialPort(this);
        ySerialPort.addDataListener((hexString, bytes, size) -> runOnUiThread(() -> binding.tvResult.setText(hexString)));
        ySerialPort.start();
        binding.tvTips.setText(String.format("注意：当前串口：%s，当前波特率：%s。", ySerialPort.getDevice(), ySerialPort.getBaudRate()));
    }

    //直接发送文件到串口
    private void sendFile() {
        if (sendFile == null) {
            show("请先选择文件");
            return;
        }
        byte[] bytes = YConvert.fileToByte(sendFile);
        YShow.show(this, "发送中...", "进度：" + 0 + "/" + bytes.length);
        ySerialPort.send(bytes,
                aBoolean -> show("发送：" + (aBoolean ? "成功" : "失败")),
                integer -> {
                    YShow.setMessageOther("进度：" + integer + "/" + bytes.length);
                    if (integer == bytes.length) {
                        YShow.finish();
                        show("发送完成");
                    }
                });
        show("正在发送请稍后...");
    }

    //Xmoden发送文件到串口
    private void sendFileXmoden() {
        if (sendFile == null) {
            show("请先选择文件");
            return;
        }
        if (YSerialPort.readBaudRate(this) == null || YSerialPort.readDevice(this) == null) {
            show("请先选择串口和波特率");
            return;
        }
        Xmodem xmodem = new Xmodem(ySerialPort.getInputStream(), ySerialPort.getOutputStream());
        xmodem.send(sendFile.getPath());
        show("正在发送请稍后，可能需要很长时间...");
    }

    public void onClick() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");//设置类型，我这里是任意类型，任意后缀的可以这样写。
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, 1);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                Uri uri = data.getData();
                if (uri != null) {
                    String path = getPath(this, uri);
                    if (path != null) {
                        File file = new File(path);
                        if (file.exists()) {
                            sendFile = file;
                            String upLoadFilePath = file.toString();
                            String upLoadFileName = file.getName();
                            binding.FilePath.setText(upLoadFilePath);
                        }
                    }
                }
            }
        }
    }

    public String getPath(final Context context, final Uri uri) {
        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];
                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }
            } else if ("com.android.providers.downloads.documents".equals(uri.getAuthority())) {
                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
                return getDataColumn(context, contentUri, null, null);
            } else if ("com.android.providers.media.documents".equals(uri.getAuthority())) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];
                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }
                final String selection = "_id=?";
                final String[] selectionArgs = new String[]{split[1]};
                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        } else if ("content".equalsIgnoreCase(uri.getScheme())) {
            return getDataColumn(context, uri, null, null);
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        return null;
    }

    public String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        final String column = "_data";
        final String[] projection = {column};
        try (Cursor cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        }
        return null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ySerialPort != null)
            ySerialPort.onDestroy();
    }
}
