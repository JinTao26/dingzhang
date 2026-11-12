package com.dingzhang.app.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用内下载新版本。
 *
 * 以前点「去下载」只是拿浏览器打开 GitHub 直链：手机上什么都看不见，
 * 下载进度在浏览器里、好与不好全靠运气。
 *
 * 交给系统的下载管理器后，通知栏有一条带进度的通知，下完点一下就能装。
 *
 * 但是线路得选：GitHub 原始地址和 ghproxy 镜像，哪个快完全看当时的状态 ——
 * 实测同一天里，直链 1 秒下完，镜像只有 16 KB/s，等了 45 秒才下了一半。
 * 写死任何一个都会让另一部分人等到想摔手机。所以下之前各拉一小段测一下，
 * 谁快用谁。
 */
object UpdateDownloader {

    private const val TAG = "Download"
    /** 测速读这么多就停，不为此把整个包下一遍 */
    private const val PROBE_BYTES = 64 * 1024

    /**
     * 在后台测速选线路再入队，结果回主线程。
     * 不阻塞点击：界面立刻有反馈，选线路那点时间藏在 Toast 里。
     */
    fun startAsync(ctx: Context, version: String, apkUrl: String, onDone: (Boolean) -> Unit) {
        Thread {
            val picked = pickFastest(candidates(apkUrl))
            Log.i(TAG, "选用线路：" + picked)
            val id = enqueue(ctx, version, picked)
            Handler(Looper.getMainLooper()).post { onDone(id != null) }
        }.apply { name = "dz-download"; isDaemon = true }.start()
    }

    /**
     * 直链和镜像各来一份。传进来的是镜像就反推回直链，两个都要试 ——
     * 只有一条能通的时候，另一条就是留着的备胎。
     */
    private fun candidates(url: String): List<String> {
        val raw = if (url.startsWith(UpdateChecker.MIRROR_PREFIX)) {
            url.removePrefix(UpdateChecker.MIRROR_PREFIX)
        } else url
        val mirror = UpdateChecker.MIRROR_PREFIX + raw
        return listOf(raw, mirror).distinct()
    }

    private fun pickFastest(urls: List<String>): String {
        var best = urls.first()
        var bestSpeed = -1.0
        for (u in urls) {
            val speed = runCatching { speedOf(u) }.getOrDefault(-1.0)
            Log.i(TAG, "测速 $u = " + speed + " KB/ms")
            if (speed > bestSpeed) {
                bestSpeed = speed
                best = u
            }
        }
        return best
    }

    /**
     * 拉前 64KB 计时。返回 KB/毫秒，越大越快；连不上返回 -1。
     * 带 Range 只取一段 —— GitHub 支持，镜像不一定，所以也接受服务器直接甩全量，
     * 读够就断开。
     */
    private fun speedOf(url: String): Double {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "dingzhang-android")
                setRequestProperty("Range", "bytes=0-" + (PROBE_BYTES - 1))
            }
            val start = System.currentTimeMillis()
            var got = 0
            conn.inputStream.use { input ->
                val buf = ByteArray(16 * 1024)
                while (got < PROBE_BYTES) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    got += n
                }
            }
            val ms = (System.currentTimeMillis() - start).coerceAtLeast(1)
            got.toDouble() / ms
        } finally {
            conn?.disconnect()
        }
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
