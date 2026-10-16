package com.yujing.chuankou.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用文件工具：导出、分享
 * 导出目录：/storage/emulated/0/Android/data/<包名>/files/export/
 */
object AppFiles {

    // 只在导出文件名中使用，模式为纯数字，用 Locale.ROOT 保证与系统语言无关。
    // 调用方在 IO 线程，故加锁访问（SimpleDateFormat 非线程安全）。
    private val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT)

    fun exportDir(context: Context): File {
        val dir = context.getExternalFilesDir("export") ?: File(context.filesDir, "export")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** 保存文本到导出目录 */
    fun saveText(context: Context, prefix: String, content: String): File? = runCatching {
        val f = File(exportDir(context), "${prefix}_${stampNow()}.txt")
        f.writeText(content, Charsets.UTF_8)
        f
    }.getOrNull()

    /** 保存字节到导出目录 */
    fun saveBytes(context: Context, prefix: String, ext: String, bytes: ByteArray): File? = runCatching {
        val f = File(exportDir(context), "${prefix}_${stampNow()}.$ext")
        f.writeBytes(bytes)
        f
    }.getOrNull()

    /** 生成分享 Intent */
    fun shareIntent(context: Context, file: File, mime: String = "text/plain"): Intent? = runCatching {
        val uri: Uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }.getOrNull()

    fun share(context: Context, file: File, mime: String = "text/plain") {
        shareIntent(context, file, mime)?.let {
            runCatching { context.startActivity(Intent.createChooser(it, "分享文件")) }
        }
    }

    private fun stampNow(): String = synchronized(stamp) { stamp.format(Date()) }
}
