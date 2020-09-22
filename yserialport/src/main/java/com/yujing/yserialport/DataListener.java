package com.yujing.yserialport;

/**
 * 结果回调
 */
public interface DataListener {
    void value(String hexString, byte[] bytes);
}
