package com.yujing.chuankou.config

import com.yujing.utils.YSaveFiles

object Config {
    //串口，此值可以在 sdcard/Android/data/包名/files/device.txt 中修改
    @JvmStatic
    var device: String?
        get() = YSaveFiles.get("device", null)
        set(value) = YSaveFiles.set("device", value)

    //波特率
    @JvmStatic
    var baudRate: String?
        get() = YSaveFiles.get("baudRate", null)
        set(value) = YSaveFiles.set("baudRate", value)
}