package com.dingzhang.app.update

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 版本更新：直接读 GitHub Releases。
 *
 * 开源侧载 App 没有应用商店，更新只能自己查。所以走 GitHub 公开 API：
 * `GET /repos/{owner}/{repo}/releases/latest`，拿到 tag、更新说明和 APK 直链。
 * 不引第三方更新库——这点需求不值得多一个依赖，也不该让更新这种敏感行为经过第三方。
 *
 * 不用「静默自安装」：下载 APK 后交给系统安装器，用户自己确认。
 * 应用内只负责「告诉你有没有新版本」和「把下载页给你」。
 */
object UpdateChecker {

    private const val TAG = "Update"
    const val OWNER = "JinTao26"
    const val REPO = "dingzhang"
    const val REPO_PAGE = "https://github.com/$OWNER/$REPO"
    const val RELEASE_PAGE = "https://github.com/$OWNER/$REPO/releases/latest"

    data class Result(
        val currentVersion: String,
        val latestVersion: String? = null,
        val notes: String = "",
        val pageUrl: String = RELEASE_PAGE,
        val apkUrl: String? = null,
        val publishedAt: String = "",
        val hasUpdate: Boolean = false,
        val message: String = ""
    )

    suspend fun check(current: String): Result = withContext(Dispatchers.IO) {
        val api = URL("https://api.github.com/repos/$OWNER/$REPO/releases/latest")
        var conn: HttpURLConnection? = null
        try {
            conn = (api.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                // GitHub API 强制要求 UA，不带会 403
                setRequestProperty("User-Agent", "dingzhang-android")
                setRequestProperty("Accept", "application/vnd.github+json")
            }

            when (val code = conn.responseCode) {
                404 -> return@withContext Result(current, message = "仓库还没有发布过版本")
                in 200..299 -> Unit
                else -> return@withContext Result(current, message = "检查失败（HTTP $code）")
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name").trim()
            val notes = json.optString("body").trim().take(1200)
            val pageUrl = json.optString("html_url").ifBlank { RELEASE_PAGE }
            val publishedAt = json.optString("published_at")

            var apkUrl: String? = null
            json.optJSONArray("assets")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val a = arr.optJSONObject(i) ?: continue
                    if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apkUrl = a.optString("browser_download_url")
                        break
                    }
                }
            }

            val has = isNewer(tag, current)
            Log.i(TAG, "当前 $current / 最新 $tag / 有更新=$has")
            Result(
                currentVersion = current,
                latestVersion = tag.ifBlank { null },
                notes = notes,
                pageUrl = pageUrl,
                apkUrl = apkUrl,
                publishedAt = publishedAt,
                hasUpdate = has,
                message = if (has) "发现新版本 $tag" else "已经是最新版本"
            )
        } catch (e: Exception) {
            Log.w(TAG, "检查更新失败", e)
            Result(current, message = "连不上 GitHub，检查下网络再试")
        } finally {
            conn?.disconnect()
        }
    }

    /** 逐段比较，`v0.4.1` 比 `0.4.0` 新；解析不出来的当作不更新，宁可漏更也不要误提示。 */
    fun isNewer(latest: String, current: String): Boolean {
        val a = parse(latest)
        val b = parse(current)
        if (a.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parse(v: String): List<Int> = v.trim()
        .removePrefix("v").removePrefix("V")
        .split('.')
        .mapNotNull { part -> part.takeWhile { it.isDigit() }.toIntOrNull() }
}
