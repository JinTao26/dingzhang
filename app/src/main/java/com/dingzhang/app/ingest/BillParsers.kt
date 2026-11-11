package com.dingzhang.app.ingest

import android.util.Log
import com.dingzhang.app.core.model.Dates
import com.dingzhang.app.core.model.Money
import com.dingzhang.app.core.model.Source
import com.dingzhang.app.core.model.TxnKind
import com.dingzhang.app.core.model.Wallet

enum class BillKind { WECHAT, ALIPAY, GENERIC, UNKNOWN }

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
            head.contains("微信支付") || head.contains("微信昵称") -> BillKind.WECHAT
            head.contains("交易类型") && head.contains("支付方式") -> BillKind.WECHAT
            head.contains("支付宝") -> BillKind.ALIPAY
            // 别家的表格（vivo 钱包、银行、平台自己导的）没有这些招牌字段，
            // 只要表头同时有时间列和金额列，就按通用规则读，不再一律判「认不出」
            looksLikeTable(head) -> BillKind.GENERIC
            else -> BillKind.UNKNOWN
        }
    }

    /** 表头看着像账单：有时间、有金额就算。具体是哪个平台交给列名去对。 */
    private fun looksLikeTable(head: String): Boolean {
        val flat = head.replace(" ", "")
        val time = listOf("交易时间", "交易日期", "记账时间", "发生时间", "入账时间", "日期")
            .any { flat.contains(it) }
        val amount = listOf("金额", "发生额", "交易额").any { flat.contains(it) }
        return time && amount
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
        val iTime = BillCsv.col(header, "交易时间", "交易日期", "记账时间", "发生时间", "入账时间", "时间", "日期")
        val iPayee = BillCsv.col(header, "交易对方", "对方", "商户名称", "商户", "收款方", "交易对象", "对方户名")
        val iGoods = BillCsv.col(header, "商品说明", "商品", "备注", "摘要", "交易说明", "用途")
        val iDir = BillCsv.col(header, "收/支", "收支", "借贷标志", "资金方向", "收支方向", "方向")
        val iAmount = BillCsv.col(header, "金额(元)", "交易金额", "发生金额", "收支金额", "金额", "发生额", "交易额")
        val iAccount = BillCsv.col(header, "收/付款方式", "支付方式", "付款方式", "付款账户", "交易卡号", "账户")
        val iState = BillCsv.col(header, "当前状态", "交易状态", "状态", "是否成功")
        val iTradeNo = BillCsv.col(header, "交易单号", "交易订单号", "支付宝交易号", "订单号", "流水号", "交易流水号")
        val iCat = BillCsv.col(header, "交易分类", "分类", "类别", "消费类型")

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

            val amountText = BillCsv.value(row, iAmount)
            val negative = amountText.trim().startsWith("-") ||
                (amountText.trim().startsWith("(") && amountText.trim().endsWith(")"))
            val amountRaw = amountText.replace("-", "").replace("(", "").replace(")", "").trim()
            val amount = Money.parseToCents(amountRaw)
            if (amount == null || amount <= 0) { skipped++; continue }

            total++

            val dirRaw = BillCsv.value(row, iDir)
            val stateRaw = BillCsv.value(row, iState)
            val catRaw = BillCsv.value(row, iCat)

            if (badStates.any { stateRaw.contains(it) }) { skipped++; continue }

            val dir = dirRaw.trim()
            val kindHint = when {
                dir.contains("不计收支") || dir.contains("中性") -> TxnKind.TRANSFER
                // 退款在账单里方向是「收入」，但它不是赚到的钱，要冲抵原来那笔支出
                !dir.contains("支出") &&
                    listOf("退款", "已退回", "退货", "退回").any { joined.contains(it) } -> TxnKind.REFUND
                dir.contains("收入") || dir.contains("入账") || dir.contains("贷") ||
                    dir == "收" || dir.contains("收款") -> TxnKind.INCOME
                dir.contains("支出") || dir.contains("出账") || dir.contains("借") ||
                    dir == "支" || dir.contains("付款") -> TxnKind.EXPENSE
                // 方向列空着、但金额写了负号 —— 这就是支出
                dir.isBlank() && negative -> TxnKind.EXPENSE
                else -> null
            }

            // 方向不明时：
            // 状态列写着成功 -> 认；
            // 状态列压根不存在（很多支付类 App 导出的账单没有这一列）—— 也认，按支出算。
            // 账单里绝大多数就是花钱，全丢掉才是更糟的错。
            var finalKind = kindHint
            if (finalKind == null) {
                if (stateRaw.isBlank() || goodStateHints.any { stateRaw.contains(it) }) {
                    finalKind = TxnKind.EXPENSE
                } else {
                    skipped++
                    continue
                }
            }

            val payee = BillCsv.value(row, iPayee)
            val goods = BillCsv.value(row, iGoods)
            val account = BillCsv.value(row, iAccount)
            val tradeNo = BillCsv.value(row, iTradeNo).filter { it.isDigit() }.takeIf { it.length >= 8 }

            // 商品说明里经常是「商户名-商品」，作为备注，比只记商户名更有信息量
            val note = goods.takeIf { it.length in 1..30 } ?: ""
            val wallet = when (kind) {
                BillKind.ALIPAY -> Wallet.ALIPAY
                BillKind.WECHAT -> Wallet.WECHAT
                // 通用表格（vivo 钱包、银行、平台导出）不知道走的哪个钱包，不要瞎记成微信
                else -> Wallet.UNKNOWN
            }

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
                    kindHint = finalKind,
                    billNo = tradeNo
                )
            )
        }

        Log.i(TAG, "解析 ${kind} 账单：有效 ${out.size} / 数据 ${total} 行，跳过 ${skipped}")
        return ParseBatch(kind, out, total, skipped, notes)
    }
}
