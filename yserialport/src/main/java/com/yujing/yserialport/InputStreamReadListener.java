package com.yujing.yserialport;

import java.io.IOException;
import java.io.InputStream;

/**
 * inputSteam读取解析监听，用于自定义组包
 * @author yujing 2021年1月12日13:38:27
 */
public interface InputStreamReadListener {
    byte[] inputStreamToBytes(InputStream inputStream) throws IOException;
}