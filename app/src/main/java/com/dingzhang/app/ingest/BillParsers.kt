package com.dingzhang.app.ingest

import android.util.Log
import com.dingzhang.app.core.model.Dates
import com.dingzhang.app.core.model.Money
import com.dingzhang.app.core.model.Source
import com.dingzhang.app.core.model.TxnKind
import com.dingzhang.app.core.model.Wallet

enum class BillKind { WECHAT, ALIPAY, UNKNOWN }

data class ParseBatch(
    val kind: BillKind,
    val rows: List<Captured>,
    val totalDataRows: Int,
    val skipped: Int,
    val notes: List<String> = emptyList()
)

/**
 * 微信 / 支付宝官方账单 CSV 解析。
 *
 * 两家导出的文件结构不同，但表头都含「交易时间」和「金额」，所以统一走：
 * 定位表头 -> 按列名取索引 -> 逐行转换。
 * 关键字段名做多候选匹配，App 改版导致的列名微调不容易直接崩。
 */
object BillParsers {

    private const val TAG = "BillParser"

    /**
     * 说明行和汇总行（「共 42 笔记录」「收入：12 笔」）不需要单独识别：
     * 它们的第一列不是合法时间，解析时间时就会失败被跳过。靠时间做闸门更准，
     * 也不会像关键词过滤那样误杀「共享单车」这种含「共」字的真实交易。
     */

    /** 明确失败或无效的交易状态 */
    private val badStates = listOf("关闭", "失败", "撤销", "已失效", "等待付款", "已取消")

    /** 可接受的交易状态（微信的「朋友已收钱」这种也算成功） */
    private val goodStateHints = listOf("成功", "已存入", "已转入", "已收钱", "朋友已收钱", "已发送", "已到账")

    fun detect(text: String): BillKind {
        val head = text.take(4000)
        return when {
            head.contains("收/付款方式") || head.contains("商品说明") || head.contains("支付宝交易记录") ->
                BillKind.ALIPAY
            head.contains("交易类型") && head.contains("支付方式") -> BillKind.WECHAT
            head.contains("微信支付") || head.contains("微信昵称") -> BillKind.WECHAT
            head.contains("支付宝") -> BillKind.ALIPAY
            else -> BillKind.UNKNOWN
        }
    }

    fun parse(text: String, forcedKind: BillKind = BillKind.UNKNOWN): ParseBatch {
        val rows = BillCsv.parseRows(text)
        val headerIdx = BillCsv.findHeaderRow(rows)
        if (headerIdx < 0) {
            return ParseBatch(forcedKind, emptyList(), 0, 0, listOf("没找到表头（需要有「交易时间」和「金额」两列）"))
        }

        val header = rows[headerIdx].map { it.trim().trimStart('\uFEFF') }
        val kind = if (forcedKind != BillKind.UNKNOWN) forcedKind else detect(header.joinToString(","))

        // ---- 列映射 ----
        val iTime = BillCsv.col(header, "交易时间", "交易日期", "时间")
        val iPayee = BillCsv.col(header, "交易对方", "对方")
        val iGoods = BillCsv.col(header, "商品说明", "商品", "备注")
        val iDir = BillCsv.col(header, "收/支", "收支")
        val iAmount = BillCsv.col(header, "金额(元)", "金额")
        val iAccount = BillCsv.col(header, "收/付款方式", "支付方式", "付款方式")
        val iState = BillCsv.col(header, "当前状态", "交易状态", "状态")
        val iTradeNo = BillCsv.col(header, "交易单号", "交易订单号", "支付宝交易号")
        val iCat = BillCsv.col(header, "交易分类")

        if (iTime < 0 || iAmount < 0) {
            return ParseBatch(kind, emptyList(), 0, 0, listOf("表头缺少必需列（交易时间/金额）"))
        }

        val out = ArrayList<Captured>(rows.size)
        var total = 0
        var skipped = 0
        val notes = ArrayList<String>(4)

        for (r in (headerIdx + 1) until rows.size) {
            val row = rows[r]
            if (row.size <= 1 || row.joinToString("").isBlank()) continue

            val joined = row.joinToString(",")

            // 时间解析同时担任「是不是数据行」的闸门
            val timeRaw = BillCsv.value(row, iTime)
            val occurredAt = Dates.parseBillTime(timeRaw)
            if (occurredAt == null) { skipped++; continue }

            val amountRaw = BillCsv.value(row, iAmount).replace("-", "").trim()
            val amount = Money.parseToCents(amountRaw)
            if (amount == null || amount <= 0) { skipped++; continue }

            total++

            val dirRaw = BillCsv.value(row, iDir)
            val stateRaw = BillCsv.value(row, iState)
            val catRaw = BillCsv.value(row, iCat)

            if (badStates.any { stateRaw.contains(it) }) { skipped++; continue }

            val kindHint = when {
                dirRaw.contains("不计收支") || dirRaw.contains("中性") -> TxnKind.TRANSFER
                // 退款在账单里方向是「收入」，但它不是赚到的钱，要冲抵原来那笔支出
                !dirRaw.contains("支出") &&
                    listOf("退款", "已退回", "退货", "退回").any { joined.contains(it) } -> TxnKind.REFUND
                dirRaw.contains("收入") || dirRaw.contains("入账") -> TxnKind.INCOME
                dirRaw.contains("支出") || dirRaw.contains("出账") -> TxnKind.EXPENSE
                else -> null
            }

            // 方向不明且状态也不像成功 -> 丢弃，避免把未完成的单记进去
            if (kindHint == null && goodStateHints.none { stateRaw.contains(it) }) {
                skipped++
                continue
            }

            val payee = BillCsv.value(row, iPayee)
            val goods = BillCsv.value(row, iGoods)
            val account = BillCsv.value(row, iAccount)
            val tradeNo = BillCsv.value(row, iTradeNo).filter { it.isDigit() }.takeIf { it.length >= 8 }

            // 商品说明里经常是「商户名-商品」，作为备注，比只记商户名更有信息量
            val note = goods.takeIf { it.length in 1..30 } ?: ""
            val wallet = if (kind == BillKind.ALIPAY) Wallet.ALIPAY else Wallet.WECHAT

            out.add(
                Captured(
                    amountCents = amount,
                    wallet = wallet,
                    payee = IngestPipeline.cleanPayee(payee.ifBlank { note.ifBlank { catRaw } }),
                    source = Source.BILL_IMPORT,
                    occurredAt = occurredAt,
                    note = note,
                    raw = joined.take(300),
                    account = account,
                    kindHint = kindHint,
                    billNo = tradeNo
                )
            )
        }

        Log.i(TAG, "解析 ${kind} 账单：有效 ${out.size} / 数据 ${total} 行，跳过 ${skipped}")
        return ParseBatch(kind, out, total, skipped, notes)
    }
}
