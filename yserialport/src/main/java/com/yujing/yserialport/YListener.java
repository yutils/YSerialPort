package com.yujing.yserialport;

/**
 * 通用监听
 *
 * @param <T> 返回类型
 * @author yujing 2019年12月5日09:57:21
 */
public interface YListener<T> {
    /**
     * 返回值
     *
     * @param value 值
     */
    void value(T value);
}
