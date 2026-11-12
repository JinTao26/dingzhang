package com.dingzhang.app.ingest

import android.app.Notification
import android.os.Bundle

/**
 * 把一条通知里「所有可能带字」的地方都读出来。
 *
 * 以前只看 EXTRA_TITLE / EXTRA_TEXT / EXTRA_BIG_TEXT 三个字段。
 * 这是通知通道命中率低的一大原因：微信支付凭证常常走 InboxStyle，
 * 金额不在 EXTRA_TEXT 里，而在 EXTRA_TEXT_LINES 这个数组里 ——
 * 三个字段读完是空的，于是被判成「付款通知没带金额」。
 *
 * 另外几个字段平时也容易漏：
 * - EXTRA_SUB_TEXT / EXTRA_SUMMARY_TEXT / EXTRA_INFO_TEXT：各家 ROM 和银行 App 爱往这里塞
 * - EXTRA_MESSAGES：聊天式通知把内容放进消息列表，正文是空的
 * - tickerText：旧接口，但仍有老 App 只写它
 */
object NotificationText {

    /**
     * @param title  标题
     * @param body   正文，多个字段去重后拼成一段
     * @param fields 实际读到了哪些字段（TEXT+BIG+LINES…）。给采集记录看 ——
     *               用户截图就能分出「通知根本没来」还是「来了但没读到」。
     */
    data class Snapshot(val title: String, val body: String, val fields: String)

    fun read(n: Notification?): Snapshot {
        val extras: Bundle = n?.extras ?: return Snapshot("", "", "")
        val title = normalized(extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
            .ifBlank { normalized(extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()) }

        val parts = ArrayList<String>(8)
        val hits = ArrayList<String>(8)

        fun push(name: String, raw: CharSequence?) {
            val t = normalized(raw?.toString())
            if (t.isEmpty()) return
            hits.add(name)
            // 同一句话会在 TEXT / BIG_TEXT / LINES 里各出现一遍。
            // 留更长的那个（信息量更大），短的那份直接丢掉。
            val dup = parts.indexOfFirst { it.contains(t) || t.contains(it) }
            if (dup >= 0) {
                if (t.length > parts[dup].length) parts[dup] = t
            } else {
                parts.add(t)
            }
        }

        push("TEXT", extras.getCharSequence(Notification.EXTRA_TEXT))
        push("BIG", extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { push("LINES", it) }
        push("SUB", extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
        push("SUMMARY", extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT))
        push("INFO", extras.getCharSequence(Notification.EXTRA_INFO_TEXT))

        // 聊天式通知：正文是空的，话在消息列表里
        runCatching {
            @Suppress("DEPRECATION")
            val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            messages?.forEach { m ->
                if (m is Bundle) push("MSG", m.getCharSequence("text"))
            }
        }

        val ticker = n?.tickerText?.toString()
        if (!ticker.isNullOrBlank()) push("TICKER", ticker)

        return Snapshot(
            title = title,
            body = parts.joinToString(" "),
            fields = hits.distinct().joinToString("+")
        )
    }

    /**
     * 归一化。三件小事，但每件都能让正则白跑：
     * - 「微信[3条]」「(2条)」是分组计数，不是内容
     * - 全角数字（￥１６.００）会让 [0-9] 认不出来
     * - ￥ 和 ¥ 是两个码位，正则里只写一个就会漏
     */
    fun normalized(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var s = raw.trim()
            .replace(Regex("[\\[［【]\\s*\\d+\\s*条\\s*[]］】]"), "")
            .replace(Regex("[（(]\\s*\\d+\\s*条\\s*[）)]"), "")
        val sb = StringBuilder(s.length)
        for (ch in s) {
            sb.append(if (ch in '\uFF10'..'\uFF19') ('0' + (ch - '\uFF10')) else ch)
        }
        s = sb.toString().replace('￥', '¥')
        return s.trim().replace(Regex("\\s+"), " ")
    }
}
