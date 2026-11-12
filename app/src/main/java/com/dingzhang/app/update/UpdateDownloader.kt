package com.dingzhang.app.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log

/**
 * 应用内下载新版本。
 *
 * 以前点「去下载」只是拿浏览器打开 GitHub 直链：手机上什么都看不见，
 * 下载进度在浏览器里、好与不好全靠运气，GitHub 直链在国内又常常断。
 * 交给系统的下载管理器后，通知栏有一条带进度的通知，下完点一下就装。
 */
object UpdateDownloader {

    private const val TAG = "Download"

    /** @return 下载任务 id，失败返回 null（调用方退回浏览器） */
    fun start(ctx: Context, version: String, apkUrl: String): Long? {
        return runCatching {
            val name = "dingzhang-" + version.trim().ifBlank { "latest" } + ".apk"
            val req = DownloadManager.Request(Uri.parse(apkUrl)).apply {
                setTitle("叮账 " + version.trim())
                // 通知栏那条写着这句话。用户点「去下载」之后如果没反应，
                // 至少要能在这里看到它确实在跑
                setDescription("正在下载，下完点这条通知安装")
                setMimeType("application/vnd.android.package-archive")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            ctx.getSystemService(DownloadManager::class.java).enqueue(req)
        }.onFailure {
            Log.w(TAG, "下载入队失败：" + it.message)
        }.getOrNull()
    }
}
