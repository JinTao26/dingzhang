package com.dingzhang.app.data

import com.dingzhang.app.core.model.Txn
import com.dingzhang.app.core.model.TxnKind

/** 一笔被识别出来的周期性扣款（会员订阅、房租、话费这类）。 */
data class RecurringItem(
    val payee: String,
    val amountCents: Long,
    val categoryId: String,
    val lastAt: Long,
    val cycleDays: Int,
    val count: Int
) {
    val cycleLabel: String
        get() = when (cycleDays) {
            7 -> "每周"
            30 -> "每月"
            90 -> "每季度"
            365 -> "每年"
            else -> "每 ${cycleDays} 天"
        }
}

/**
 * 周期性扣款识别。
 *
 * 思路很朴素，也刻意保持朴素：同商户 + 同金额，出现两次以上，且相邻间隔的中位数
 * 稳定落在某个周期附近，才算订阅。不用「猜测」，避免把「每周都去的超市」误报成订阅——
 * 那种情况金额不会每次都一样。
 *
 * 这条能力是从成熟记账 App（Ivy Wallet 的 Recurring、Veralog 的 recurring transactions）学来的：
 * 用户真正想知道的不是「这个月花了多少」，而是「我有哪些钱是在自动往外流」。
 */
object RecurringDetector {

    private const val MIN_AMOUNT_CENTS = 500L      // 低于 5 元的多半是零星消费
    private const val MAX_GAP_DAYS = 400L

    fun detect(txns: List<Txn>, now: Long = System.currentTimeMillis()): List<RecurringItem> {
        val expenses = txns.filter {
            it.kind == TxnKind.EXPENSE && it.amountCents >= MIN_AMOUNT_CENTS && it.payee.isNotBlank()
        }
        if (expenses.size < 2) return emptyList()

        val groups = expenses.groupBy { "${it.payee}|${it.amountCents}" }
        val out = ArrayList<RecurringItem>()

        for ((_, items) in groups) {
            if (items.size < 2) continue

            val sorted = items.sortedBy { it.occurredAt }
            val gaps = sorted.zipWithNext { a, b -> (b.occurredAt - a.occurredAt) / 86_400_000L }
                .filter { it in 1..MAX_GAP_DAYS }
            if (gaps.isEmpty()) continue

            val median = gaps.sorted()[gaps.size / 2]
            val cycle = when (median) {
                in 5L..9L -> 7
                in 25L..36L -> 30
                in 80L..100L -> 90
                in 350L..380L -> 365
                else -> 0
            }
            if (cycle == 0) continue

            // 间隔不能太飘：允许周期本身 ±40% 的波动，超了就说明不是稳定订阅
            val tolerate = (cycle * 0.4).toLong() + 2
            val stable = gaps.all { it >= cycle - tolerate && it <= cycle + tolerate }
            if (!stable) continue

            val last = sorted.last()
            // 已经超过一个周期 + 宽限期没再扣，多半是退订了，不再提醒
            if (now - last.occurredAt > (cycle + 15).toLong() * 86_400_000L) continue

            out.add(
                RecurringItem(
                    payee = last.payee,
                    amountCents = last.amountCents,
                    categoryId = last.categoryId,
                    lastAt = last.occurredAt,
                    cycleDays = cycle,
                    count = sorted.size
                )
            )
        }

        return out.sortedByDescending { it.amountCents }
    }

    /** 每月固定支出的合计，用于首页提示「每月有 x 元在自动扣」 */
    fun monthlyTotal(items: List<RecurringItem>): Long {
        var sum = 0L
        items.forEach { item ->
            sum += when (item.cycleDays) {
                7 -> item.amountCents * 4
                30 -> item.amountCents
                90 -> item.amountCents / 3
                365 -> item.amountCents / 12
                else -> item.amountCents
            }
        }
        return sum
    }
}
