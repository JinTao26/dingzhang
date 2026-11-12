package com.dingzhang.app.ingest

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 采集现场记录。
 *
 * 自动记账出问题的时候，用户只能看到「没记上」，看不到卡在哪一步：
 * 是无障碍事件压根没来、是抓到了窗口但没认出支付页、是金额没读出来，
 * 还是被当成重复丢了。这几种情况在「我的 → 自动记账原理与限制」里能看到，
 * 截图发过来就能判断。
 *
 * 存在 SharedPreferences 里而不是内存：用户往往是付完钱过一会儿才打开 App，
 * 那时后台进程可能已经被系统回收了，内存日志会跟着丢。
 */
object IngestLog {

    private const val PREF_NAME = "dingzhang_ingest_log"

    /** 记上的账。这一段单独存，永远不会被「为什么没记上」的诊断挤掉。 */
    private const val KEY_OK = "ok_lines"

    /** 没记上的现场、通道存活状态。 */
    private const val KEY_DIAG = "diag_lines"

    private const val MAX_OK = 25
    private const val MAX_DIAG = 12

    /** 旧版本只有一个 key，第一次启动时搬过来，别把用户手上的现场弄丢。 */
    private const val KEY_OLD = "lines"

    private var prefs: SharedPreferences? = null
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)

    fun init(c: Context) {
        if (prefs == null) {
            val p = c.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs = p
            // 老数据搬到诊断段
            val legacy = p.getString(KEY_OLD, "").orEmpty()
            if (legacy.isNotBlank()) {
                val merged = (p.getString(KEY_DIAG, "").orEmpty().lines() + legacy.lines())
                    .filter { it.isNotBlank() }
                    .distinct()
                    .takeLast(MAX_DIAG)
                p.edit().putString(KEY_DIAG, merged.joinToString("\n")).remove(KEY_OLD).apply()
            }
        }
    }

    fun add(source: String, result: String, detail: String = "") {
        val p = prefs ?: return
        // 「记上了」和诊断分两段存。以前挤在一个 30 条的队列里，
        // 连着几次读不到窗口就能把刚记上的那笔顶掉 —— 用户打开一看全是废话。
        val isOk = result == "记上了"
        val key = if (isOk) KEY_OK else KEY_DIAG
        val max = if (isOk) MAX_OK else MAX_DIAG

        val clean = detail.replace('\n', ' ').replace('|', '/').take(110)
        val now = System.currentTimeMillis()
        val line = "$now|$source|$result|$clean"
        val old = p.getString(key, "").orEmpty().lines().filter { it.isNotBlank() }.toMutableList()

        // 同一种情况一分钟内连着出现，只留一条、更新时间戳
        val last = old.lastOrNull()
        if (last != null) {
            val parts = last.split("|", limit = 4)
            val lastResult = parts.getOrNull(2).orEmpty()
            val lastDetail = parts.getOrNull(3).orEmpty()
            val lastAt = parts.getOrNull(0)?.toLongOrNull() ?: 0L
            if (lastResult == result && lastDetail == clean && now - lastAt < 60_000L) {
                old[old.size - 1] = line
                p.edit().putString(key, old.takeLast(max).joinToString("\n")).apply()
                return
            }
        }

        p.edit().putString(key, (old + line).takeLast(max).joinToString("\n")).apply()
    }

    /** 记上的账，最新的在前 */
    fun readOk(): List<String> =
        prefs?.getString(KEY_OK, "").orEmpty().lines().filter { it.isNotBlank() }.reversed()

    /** 没记上的现场，最新的在前 */
    fun readDiag(): List<String> =
        prefs?.getString(KEY_DIAG, "").orEmpty().lines().filter { it.isNotBlank() }.reversed()

    fun pretty(line: String): String {
        val parts = line.split("|", limit = 4)
        if (parts.size < 3) return line
        val at = parts[0].toLongOrNull() ?: 0L
        val time = fmt.format(Date(at))
        val src = parts[1].trim()
        val result = parts[2].trim()
        val detail = parts.getOrElse(3) { "" }.trim()
        return "$time · $src $result" + if (detail.isNotEmpty()) "\n    $detail" else ""
    }

    fun clear() {
        prefs?.edit()?.remove(KEY_OK)?.remove(KEY_DIAG)?.apply()
    }
}
