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
    private const val KEY = "lines"
    private const val MAX = 30

    private var prefs: SharedPreferences? = null
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)

    fun init(c: Context) {
        if (prefs == null) {
            prefs = c.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }
    }

    fun add(source: String, result: String, detail: String = "") {
        val p = prefs ?: return
        val clean = detail.replace('\n', ' ').replace('|', '/').take(110)
        val line = "${System.currentTimeMillis()}|$source|$result|$clean"
        val old = p.getString(KEY, "").orEmpty().lines().filter { it.isNotBlank() }
        val merged = (old + line).takeLast(MAX)
        p.edit().putString(KEY, merged.joinToString("\n")).apply()
    }

    /** 最新的在前 */
    fun read(): List<String> =
        prefs?.getString(KEY, "").orEmpty()
            .lines().filter { it.isNotBlank() }.reversed()

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
        prefs?.edit()?.remove(KEY)?.apply()
    }
}
