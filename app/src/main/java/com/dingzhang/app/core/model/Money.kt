package com.dingzhang.app.core.model

import android.util.Log
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 金额一律以「分」为单位的 Long 存储和计算，避免 0.1 + 0.2 这类浮点误差。
 * 只在渲染和用户输入时转成字符串。
 */
object Money {

    fun parseToCents(raw: String?): Long? {
        if (raw == null) return null
        var s = raw.trim()
            .replace("¥", "").replace("￥", "")
            .replace(",", "").replace("元", "")
            .replace(" ", "")
            .replace("RMB", "", ignoreCase = true)
        if (s.isEmpty()) return null
        // 处理「12.345.678」这类脏数据
        if (s.count { it == '.' } > 1) return null
        val bd = try {
            BigDecimal(s)
        } catch (e: NumberFormatException) {
            return null
        }
        if (bd.signum() < 0) return null
        return bd.setScale(2, RoundingMode.HALF_UP)
            .multiply(BigDecimal(100))
            .toLong()
    }

    /** 25 -> "25.00"，1234.5 -> "1,234.50" */
    fun yuan(cents: Long): String {
        val v = BigDecimal.valueOf(cents).divide(BigDecimal(100))
        val f = java.text.DecimalFormat("#,##0.00")
        return f.format(v)
    }

    /** 带正负号，用于列表右侧 */
    fun signed(cents: Long, kind: TxnKind): String = when (kind) {
        TxnKind.EXPENSE -> "-" + yuan(cents)
        TxnKind.INCOME -> "+" + yuan(cents)
        TxnKind.TRANSFER -> yuan(cents)
        // 退款用负号：它减的是支出，不是加的收入
        TxnKind.REFUND -> "-" + yuan(cents)
    }
}

object Dates {

    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.CHINA)
    private val fullFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
    private val monthFmt = SimpleDateFormat("yyyy-MM", Locale.CHINA)

    val weekNames = arrayOf("日", "一", "二", "三", "四", "五", "六")

    fun dayOf(ts: Long): String = dayFmt.format(Date(ts))
    fun monthOf(ts: Long): String = monthFmt.format(Date(ts))
    fun timeOf(ts: Long): String = timeFmt.format(Date(ts))
    fun fullOf(ts: Long): String = fullFmt.format(Date(ts))

    fun weekdayOf(ts: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ts }
        return "周" + weekNames[c.get(Calendar.DAY_OF_WEEK) - 1]
    }

    fun startOfDay(ts: Long): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = ts
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return c.timeInMillis
    }

    fun startOfMonth(ts: Long): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = ts
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return c.timeInMillis
    }

    fun daysInMonth(ts: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = ts }
        return c.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    fun dayIndex(ts: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = ts }
        return c.get(Calendar.DAY_OF_MONTH)
    }

    /** 解析账单里的各种时间写法：2024-11-05 12:33:10 / 2024/11/5 12:33 / 2024-11-05 */
    fun parseBillTime(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val normalized = raw.trim().replace("/", "-").replace(" ", " ").replace("\u00a0", " ")
        val patterns = listOf(
            "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd",
            "yyyy-M-d HH:mm:ss", "yyyy-M-d HH:mm", "yyyy-M-d"
        )
        for (p in patterns) {
            try {
                val sdf = SimpleDateFormat(p, Locale.CHINA).apply { isLenient = true }
                val d = sdf.parse(normalized)
                if (d != null) return d.time
            } catch (e: Exception) {
                // 换下一个格式
            }
        }
        Log.w("Dates", "无法解析时间: $raw")
        return null
    }

    /** 给「今天/昨天/前天」这种人话用 */
    fun relativeLabel(ts: Long): String {
        val t = System.currentTimeMillis()
        val diff = (startOfDay(t) - startOfDay(ts)) / 86_400_000L
        return when (diff) {
            0L -> "今天"
            1L -> "昨天"
            2L -> "前天"
            in 3L..6L -> "${diff}天前"
            else -> dayOf(ts).substring(5).replace("-", ".")
        }
    }
}
