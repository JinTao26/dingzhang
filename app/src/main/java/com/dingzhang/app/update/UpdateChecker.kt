package com.dingzhang.app.update

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 版本更新检查。
 *
 * 侧载 App 没有应用商店，更新只能自己查。所以走 GitHub 公开 API：
 * `GET /repos/{owner}/{repo}/releases/latest`，一次请求拿到 tag、更新说明和 APK 直链。
 *
 * 只打这一个源。中间试过经 jsDelivr 分发 version.json，多一次请求、多一层缓存，
 * 国内反而更慢，已经撤掉。连不上就如实说连不上，并给出 ghproxy 镜像页。
 *
 * 不做静默自安装：下载完交给系统安装器，用户自己点确认。
 */
object UpdateChecker {

    private const val TAG = "Update"
    const val OWNER = "JinTao26"
    const val REPO = "dingzhang"
    const val REPO_PAGE = "https://github.com/$OWNER/$REPO"
    const val RELEASE_PAGE = "https://github.com/$OWNER/$REPO/releases/latest"

    /** 国内可达的下载镜像前缀 */
    const val MIRROR_PREFIX = "https://ghproxy.net/"

    private const val API_URL = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    data class Result(
        val currentVersion: String,
        val latestVersion: String? = null,
        val notes: String = "",
        val pageUrl: String = RELEASE_PAGE,
        val apkUrl: String? = null,
        val publishedAt: String = "",
        val hasUpdate: Boolean = false,
        /** 这次检查本身成功没有。false 时 message 说明原因。 */
        val ok: Boolean = true,
        val message: String = ""
    )

    suspend fun check(current: String): Result = withContext(Dispatchers.IO) {
        val release = httpText(API_URL)?.let {
            runCatching { parseRelease(current, it) }.getOrNull()
        }
        if (release != null) return@withContext release

        Log.w(TAG, "更新源没连上")
        Result(
            current, ok = false,
            message = "连不上 GitHub。有网的话可以打开镜像页看看有没有新版：" +
                "${MIRROR_PREFIX}${RELEASE_PAGE}"
        )
    }

    // ------------------------------------------------------------------ 解析

    private fun parseRelease(current: String, body: String): Result? {
        val j = JSONObject(body)
        val tag = j.optString("tag_name").trim()
        if (tag.isBlank()) return null

        var apkUrl: String? = null
        j.optJSONArray("assets")?.let { arr ->
            for (i in 0 until arr.length()) {
                val a = arr.optJSONObject(i) ?: continue
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apkUrl = a.optString("browser_download_url").takeIf { it.startsWith("http") }
                    break
                }
            }
        }

        val has = isNewer(tag, current)
        Log.i(TAG, "Releases API：当前 $current / 最新 $tag / 有更新=$has")
        return Result(
            currentVersion = current,
            latestVersion = tag,
            notes = j.optString("body").trim().take(1200),
            pageUrl = j.optString("html_url").ifBlank { RELEASE_PAGE },
            apkUrl = apkUrl?.let { mirrorOf(it) },
            publishedAt = j.optString("published_at"),
            hasUpdate = has,
            message = if (has) "发现新版本 $tag" else "已经是最新版本"
        )
    }

    /** GitHub 直链换成镜像；已经是镜像原样返回。 */
    private fun mirrorOf(url: String): String =
        if (url.contains("ghproxy.net/")) url else MIRROR_PREFIX + url

    private fun httpText(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                instanceFollowRedirects = true
                // GitHub API 强制要求 UA，不带会 403
                setRequestProperty("User-Agent", "dingzhang-android")
                setRequestProperty("Accept", "application/json, text/plain, */*")
            }
            if (conn.responseCode !in 200..299) {
                Log.w(TAG, "${conn.responseCode} <- $url")
                return null
            }
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "拉取失败 $url: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    // ------------------------------------------------------------------ 版本比较

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
