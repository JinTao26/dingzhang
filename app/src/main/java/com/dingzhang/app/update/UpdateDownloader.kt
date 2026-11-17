package com.dingzhang.app.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 应用内下载新版本。
 *
 * 以前点「去下载」只是拿浏览器打开 GitHub 直链：手机上什么都看不见，
 * 下载进度在浏览器里、好与不好全靠运气。
 *
 * 交给系统的下载管理器后，通知栏有一条带进度的通知，下完点一下就能装。
 *
 * 线路只走 GitHub 原始地址，不套镜像、也不在下载前测速。
 * 测速这一步本身就要先连上、再读 64KB 才能算出速度，运气不好光测速就干等十几秒 ——
 * 用户点完按钮半天没动静，比慢更糟。直接入队交给系统下载器，进度条立刻就在通知栏里。
 */
object UpdateDownloader {

    private const val TAG = "Download"

    /**
     * 在后台入队，结果回主线程。
     * 入队本身很快，但别在点击的回调里直接跑。
     */
    fun startAsync(ctx: Context, version: String, apkUrl: String, onDone: (Boolean) -> Unit) {
        Thread {
            val id = enqueue(ctx, version, apkUrl)
            Log.i(TAG, "下载入队 " + (id ?: "失败"))
            Handler(Looper.getMainLooper()).post { onDone(id != null) }
        }.apply { name = "dz-download"; isDaemon = true }.start()
    }

    /** @return 下载任务 id，失败返回 null（调用方退回浏览器） */
    private fun enqueue(ctx: Context, version: String, apkUrl: String): Long? {
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
