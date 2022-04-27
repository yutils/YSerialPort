package com.yujing.yserialport

/**
 * 线程类型
 * CURRENT：直接执行
 * NEW：新建线程执行
 * MAIN：是主线程直接执行，不是就先回到主线程再执行
 * IO：是主线程就创建线程，不是就直接执行
 */
enum class ThreadMode {
    CURRENT,
    NEW,
    MAIN,
    IO
}