package com.dingzhang.app.update

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

    /**
     * 国内能通的清单源：仓库根目录的 version.json，每次发版同步更新。
     *
     * **这才是「从来没弹过更新」的真正原因**：api.github.com 在手机上十次有九次连不上，
     * 检查静默失败，用户那边看到的就是「一直没提醒」。换成 CDN 上的静态清单后，
     * 一次请求几十 KB，全国都能拿到。
     */
    private const val MANIFEST_URL = "https://cdn.jsdelivr.net/gh/$OWNER/$REPO@main/version.json"

    /**
     * 同一份清单的直连源，没有任何缓存。
     *
     * 只打 jsDelivr 一个源出过事：它是 CDN，`@main` 会被缓存十几个小时。
     * 发完新版用户立刻点「检查更新」，拿到的还是上一版那份，界面上写着「已是最新版本」——
     * 而那会儿新包其实已经挂出去了。现在两个源一起打，谁报的版本大就用谁，
     * 只要有一个拿到新内容就不会漏。国内直连常常连不上，所以它只是其中一个，不是唯一。
     */
    private const val RAW_URL = "https://raw.githubusercontent.com/$OWNER/$REPO/main/version.json"

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
        // 先打 CDN 清单；不通再退回 GitHub API（海外、挂了梯子的时候才用得上）
        manifest(current)?.let { return@withContext it }

        val release = httpText(API_URL)?.let {
            runCatching { parseRelease(current, it) }.getOrNull()
        }
        if (release != null) return@withContext release

        Log.w(TAG, "两个更新源都没连上")
        Result(
            current, ok = false,
            message = "连不上 GitHub。有网的话可以打开镜像页看看有没有新版：" +
                "${MIRROR_PREFIX}${RELEASE_PAGE}"
        )
    }

    // ------------------------------------------------------------------ 清单源

    private suspend fun manifest(current: String): Result? {
        val stamp = System.currentTimeMillis()
        val urls = listOf(
            "$RAW_URL?_=$stamp",                      // 实时，但国内可能连不上
            "$MANIFEST_URL?_=${stamp / 3_600_000L}"    // 稳，但可能缓存旧内容
        )

        // 并发打两个源，取版本号大的那份。串行的话 raw 超时 6 秒会把整个检查拖垮。
        val results = coroutineScope {
            urls.map { u -> async { parseManifest(current, httpText(u)) } }.awaitAll()
        }.filterNotNull()

        return results.maxByOrNull { rank(it.latestVersion) }
    }

    /** 把版本号拆成可比较的数，用来在两个源之间挑更新的那份。 */
    private fun rank(v: String?): Long {
        val parts = (v ?: "").trim().removePrefix("v").split(".")
        var n = 0L
        for (i in 0 until 3) {
            val p = parts.getOrNull(i)?.toLongOrNull() ?: 0L
            n = n * 1000 + p.coerceIn(0, 999)
        }
        return n
    }

    private fun parseManifest(current: String, text: String?): Result? {
        if (text == null) return null
        return try {
            val j = JSONObject(text)
            val v = j.optString("version").trim()
            if (v.isBlank()) return null
            val apk = j.optString("apk").trim()
            // 清单里的版本号和下载链接必须对得上。改漏一处就当这份清单不能用 ——
            // 宁可这次不提示更新，也不要让人点进去下到旧包。
            if (apk.isNotBlank() && !apk.contains(v)) {
                Log.w(TAG, "清单版本 $v 与下载链对不上，跳过")
                return null
            }
            val has = isNewer(v, current)
            Log.i(TAG, "清单源：当前 $current / 最新 $v / 有更新=$has")
            Result(
                currentVersion = current,
                latestVersion = v,
                notes = j.optString("notes").trim().take(1200),
                pageUrl = j.optString("page").trim().ifBlank { RELEASE_PAGE },
                apkUrl = apk.takeIf { it.startsWith("http") }?.let { mirrorOf(it) },
                publishedAt = j.optString("published_at").trim(),
                hasUpdate = has,
                message = if (has) "发现新版本 $v" else "已经是最新版本"
            )
        } catch (e: Exception) {
            Log.w(TAG, "清单解析失败：${e.message}")
            null
        }
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
