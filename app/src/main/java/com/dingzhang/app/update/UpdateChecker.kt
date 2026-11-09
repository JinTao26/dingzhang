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
 * 侧载 App 没有应用商店，更新只能自己查。国内直连 `api.github.com` 经常超时，
 * 所以改成两个源依次尝试：
 *   1. 仓库根目录的 `version.json`，经 jsDelivr 分发（`cdn.jsdelivr.net` 国内多数网络可达）
 *   2. GitHub 官方 Releases API
 * 两个都不通，就如实告诉用户「连不上更新源」并给出镜像地址，
 * 而不是像以前那样只在日志里写一行、界面上什么都没有。
 *
 * 下载链接统一换成 ghproxy 镜像前缀：`https://ghproxy.net/https://github.com/...`，实测能下 APK。
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

    private const val MANIFEST_URL = "https://cdn.jsdelivr.net/gh/$OWNER/$REPO@main/version.json"
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

    /** 连不上时冷却一阵，避免用户每切一次前台就打一轮请求 */
    private var lastFailAt = 0L
    private const val FAIL_COOLDOWN_MS = 15 * 60 * 1000L

    suspend fun check(current: String): Result = withContext(Dispatchers.IO) {
        if (System.currentTimeMillis() - lastFailAt < FAIL_COOLDOWN_MS) {
            return@withContext Result(
                current, ok = false,
                message = "上次没连上更新源，过一会儿再试"
            )
        }

        val manifest = httpText(MANIFEST_URL)?.let {
            runCatching { parseManifest(current, it) }.getOrNull()
        }
        if (manifest != null) return@withContext manifest

        val release = httpText(API_URL)?.let {
            runCatching { parseRelease(current, it) }.getOrNull()
        }
        if (release != null) return@withContext release

        lastFailAt = System.currentTimeMillis()
        Log.w(TAG, "两个更新源都没连上")
        Result(
            current, ok = false,
            message = "连不上更新源（jsDelivr 和 GitHub 都没通）。" +
                "可以打开镜像页手动下载：${MIRROR_PREFIX}${RELEASE_PAGE}"
        )
    }

    // ------------------------------------------------------------------ 解析

    private fun parseManifest(current: String, body: String): Result? {
        val j = JSONObject(body)
        val ver = j.optString("version").trim()
        if (ver.isBlank()) return null
        val has = isNewer(ver, current)
        Log.i(TAG, "version.json：当前 $current / 最新 $ver / 有更新=$has")
        return Result(
            currentVersion = current,
            latestVersion = ver,
            notes = j.optString("notes").trim().take(1200),
            pageUrl = j.optString("page").trim().ifBlank { RELEASE_PAGE },
            apkUrl = j.optString("apk").trim().takeIf { it.startsWith("http") }?.let { mirrorOf(it) },
            publishedAt = j.optString("published_at"),
            hasUpdate = has,
            message = if (has) "发现新版本 $ver" else "已经是最新版本"
        )
    }

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
