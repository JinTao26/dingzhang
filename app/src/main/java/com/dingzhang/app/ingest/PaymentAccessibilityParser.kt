package com.dingzhang.app.ingest

import android.util.Log
import com.dingzhang.app.core.model.Dates
import com.dingzhang.app.core.model.Money
import com.dingzhang.app.core.model.Source
import com.dingzhang.app.core.model.TxnKind
import com.dingzhang.app.core.model.Wallet

/**
 * 通道 B：无障碍页面解析。
 *
 * 这是国内自动记账真正靠得住的实时通道（钱迹、小账本都是这条路），
 * 因为它读的是「支付结果页 / 账单详情页」上已经渲染出来的文字，
 * 只要页面信息完整，金额、商户、支付方式、单号都能拿到。
 *
 * 代价：
 * - 页面改版会失效，所以这里把「页面规则」抽成可扩展的 PageRule，出问题先加规则。
 * - 淘宝主动屏蔽无障碍，读不到，这是既定事实，不在这里硬刚。
 */
object PaymentAccessibilityParser {

    /** 一个页面结构的识别规则。新增适配时只加一条，不动解析逻辑。 */
    data class PageRule(
        val wallet: Wallet,
        val name: String,
        /** 页面上必须同时出现这些词，才认为「这是在支付/账单页」 */
        val mustContain: List<String>,
        /** 出现在这些词里说明这不是交易页（聊天页、首页、营销页） */
        val mustNotContain: List<String> = emptyList()
    )

    val rules = listOf(
        PageRule(
            Wallet.ALIPAY, "支付宝-付款成功",
            mustContain = listOf("付款成功", "支付成功", "成功付款"),
            mustNotContain = listOf("收款码", "扫一扫", "首页", "我的")
        ),
        PageRule(
            Wallet.ALIPAY, "支付宝-账单详情",
            mustContain = listOf("交易金额", "支付时间", "商户全称", "交易单号")
        ),
        PageRule(
            Wallet.WECHAT, "微信-支付结果",
            mustContain = listOf("支付成功", "已支付", "付款成功"),
            mustNotContain = listOf("聊天", "朋友圈", "正在输入")
        ),
        PageRule(
            Wallet.WECHAT, "微信-账单详情",
            mustContain = listOf("交易单号", "商户全称", "支付时间")
        )
    )

    private val amountWithSymbol = Regex("[¥￥]\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)")
    private val amountPlain = Regex("([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*元")

    private val payeeLabels = listOf("收款方", "商户全称", "商户名称", "交易对方", "对方", "商家名称")
    private val accountLabels = listOf("付款方式", "支付方式", "收付款方式", "扣款方式")
    private val timeLabels = listOf("支付时间", "交易时间", "创建时间", "付款时间")
    private val tradeNoLabels = listOf("交易单号", "订单号", "商户单号", "交易号")

    private val incomeCues = listOf(
        "收款到账", "已收款", "到账金额", "收入", "红包到账", "收到红包", "收到转账", "已收钱"
    )
    private val transferCues = listOf("还款", "提现", "转账")

    /**
     * @param texts 页面上抓到的所有可见文本，按遍历顺序
     * @return 解析不出、或判定为非支付页时返回 null
     */
    fun parse(pkg: String, texts: List<String>, now: Long): Captured? {
        if (texts.isEmpty() || texts.size > 200) return null

        val wallet = when (pkg) {
            PaymentNotifierParser.PKG_WECHAT -> Wallet.WECHAT
            PaymentNotifierParser.PKG_ALIPAY -> Wallet.ALIPAY
            else -> return null
        }

        val flat = texts.joinToString(" ").trim()
        if (flat.length < 6) return null

        // 必须命中某条页面规则
        val matchedRules = rules.filter { r ->
            r.wallet == wallet &&
                r.mustContain.count { flat.contains(it) } >= 2 &&
                r.mustNotContain.none { flat.contains(it) }
        }
        if (matchedRules.isEmpty()) return null

        // 金额
        val amount = extractAmount(texts) ?: return null
        if (amount <= 0 || amount > 10_000_000_00L) return null

        // 有「商户全称：」标签就用标签；微信的支付结果页没这个标签，
        // 第一行往往就是店名，所以再逐行猜一次
        val payee = findValue(texts, payeeLabels)
            ?.let { IngestPipeline.cleanPayee(it) }
            ?.takeIf { it.length >= 2 }
            ?: IngestPipeline.guessPayeeFromLines(texts)
            ?: defaultPayee(wallet)

        val account = findValue(texts, accountLabels)?.take(20).orEmpty()

        // 时间：优先用页面上写的支付时间，拿不到用当前时刻
        val occurredAt = findValue(texts, timeLabels)
            ?.let { Dates.parseBillTime(it) }
            ?: now

        val tradeNo = findValue(texts, tradeNoLabels)
            ?.filter { it.isDigit() }
            ?.takeIf { it.length >= 10 }

        val kind = when {
            transferCues.any { flat.contains(it) } && wallet == Wallet.WECHAT && flat.contains("转账") ->
                TxnKind.TRANSFER
            incomeCues.any { flat.contains(it) } && !flat.contains("支付成功") ->
                TxnKind.INCOME
            else -> TxnKind.EXPENSE
        }

        Log.d("A11y", "解析成功 ${matchedRules.first().name}: $payee $amount")

        return Captured(
            amountCents = amount,
            wallet = wallet,
            payee = payee,
            source = Source.ACCESSIBILITY,
            occurredAt = occurredAt,
            account = account,
            raw = flat.take(300),
            externalId = tradeNo,
            kindHint = kind
        )
    }

    private fun defaultPayee(wallet: Wallet): String = when (wallet) {
        Wallet.WECHAT -> "微信支付"
        Wallet.ALIPAY -> "支付宝"
        else -> "未知商户"
    }

    /**
     * 页面上的金额优先级：
     * 1. 带 ¥ 号且带负号的（支付结果页最常见的形态）
     * 2. 「金额」标签后面的那个值
     * 3. 任意带 ¥ 或「元」的
     */
    private fun extractAmount(texts: List<String>): Long? {
        // 「-25.00」这种负号在单独的节点上时，合并判断
        for (t in texts) {
            val m = amountWithSymbol.find(t)
            if (m != null) {
                Money.parseToCents(m.groupValues[1])?.let { if (it > 0) return it }
            }
        }
        findValue(texts, listOf("交易金额", "支付金额", "金额", "实付"))?.let {
            Money.parseToCents(it)?.let { c -> if (c > 0) return c }
        }
        for (t in texts) {
            val m = amountPlain.find(t)
            if (m != null) {
                Money.parseToCents(m.groupValues[1])?.let { if (it > 0) return it }
            }
        }
        return null
    }

    /**
     * 无障碍树里 label 和 value 通常是两个节点：
     * ["收款方", "小杨生煎", "付款方式", "零钱"]
     * 也兼容「收款方：小杨生煎」写在同一个节点里的情况。
     */
    private fun findValue(texts: List<String>, labels: List<String>): String? {
        for (i in texts.indices) {
            val t = texts[i].trim()
            val label = labels.firstOrNull { l ->
                t == l || t.startsWith("$l：") || t.startsWith("$l:") || t.startsWith("$l ")
            } ?: continue

            if (t.length > label.length + 1) {
                val v = t.substringAfter(label).trim().trimStart('：', ':', ' ')
                if (v.isNotEmpty()) return v
            }

            var j = i + 1
            while (j < texts.size && j <= i + 3) {
                val v = texts[j].trim()
                if (v.isNotEmpty() && v != label && !looksLikeLabel(v)) return v
                j++
            }
        }
        return null
    }

    private fun looksLikeLabel(v: String): Boolean =
        v.length <= 6 && listOf("收款方", "商户", "付款方式", "支付方式", "交易单号", "订单号",
            "支付时间", "交易时间", "商品", "金额", "备注", "状态", "账单").any { v == it || v.endsWith(it) }
}
