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
        val mustNotContain: List<String> = emptyList(),
        /**
         * 命中几个词才算这个页面。
         * 「支付成功」「付款成功」本身就是强信号，页面只写一个也要认；
         * 账单详情页全是弱标签（交易金额 / 支付时间 / 订单号…），要凑两个才敢认，
         * 否则随便一个含「支付时间」的页面就会被当成账单。
         */
        val minHits: Int = 2
    )

    val rules = listOf(
        PageRule(
            Wallet.ALIPAY, "支付宝-付款成功",
            mustContain = listOf("付款成功", "支付成功", "成功付款"),
            mustNotContain = listOf("收款码", "扫一扫", "首页", "我的"),
            minHits = 1
        ),
        PageRule(
            Wallet.ALIPAY, "支付宝-账单详情",
            // 新版支付宝账单详情把标签改成了「收款方全称 / 订单号」，
            // 金额也不再带 ¥，所以这里把常见的几种标签都放进来，命中任意两个即可
            mustContain = listOf(
                "交易金额", "支付时间", "商户全称", "收款方全称",
                "订单号", "交易单号", "收单机构", "支付方式", "当前状态", "账单分类"
            ),
            mustNotContain = listOf("收款码", "扫一扫")
        ),
        PageRule(
            Wallet.WECHAT, "微信-支付结果",
            mustContain = listOf("支付成功", "已支付", "付款成功"),
            // 只命中一个「支付成功」就认，但聊天页的痕迹要排掉：
            // 聊天界面里有人发「支付成功 20 元」这种文字，也会被当成支付结果页
            mustNotContain = listOf("聊天", "朋友圈", "正在输入", "发送", "按住", "相册", "表情", "语音"),
            minHits = 1
        ),
        PageRule(
            Wallet.WECHAT, "微信-账单详情",
            mustContain = listOf("交易单号", "商户全称", "支付时间", "当前状态", "商品")
        )
    )

    /**
     * 金额标签，从最明确的往最泛的排。
     * 「金额」放最后：有些页面把它当成模块标题用。
     */
    private val amountLabels = listOf(
        "实付金额", "实际支付", "实际付款", "实际扣款", "扣款金额", "实付",
        "付款金额", "支付金额", "订单金额", "交易金额", "金额"
    )

    private val amountWithSymbol = Regex("[¥￥]\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)")
    private val amountPlain = Regex("([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*元")
    /** 账单详情页的大字金额：「-20.00」，负号、没有货币符号 */
    private val amountSigned = Regex("^[−\\-－]\\s*[¥￥]?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)")
    /** 金额前面的正负号：账单详情页「-20.00」是支出，「+20.00」是进账 */
    private val amountPosSign = Regex("^[+＋]\\s*[¥￥]?\\s*[0-9]")
    /**
     * 页面明确写着退款才算退款。只写「退款」两个字不行 ——
     * 微信账单页底部有「可在支持的商户扫码退款」，那样会把每笔都判成退款。
     */
    private val refundCues = listOf("退款成功", "退款到账", "退款完成", "已全额退款", "已退款")
    /** 只有数字和小数点的一行；要求带小数，免得把订单号、卡号尾号当成金额 */
    private val amountBareDecimal = Regex("^[¥￥]?\\s*([0-9][0-9,]*\\.[0-9]{1,2})$")

    private val payeeLabels = listOf(
        "收款方全称", "商户全称", "收款方名称", "商户名称", "商家名称", "交易对方", "收款方", "对方"
    )
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
                r.mustContain.count { flat.contains(it) } >= r.minHits &&
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

        // 支付宝自己标好的分类，比如「餐饮美食」。抓到就带上，比自己猜准
        val platformCategory = findValue(texts, listOf("账单分类"))
            ?.replace(">", "")
            ?.trim()
            ?.takeIf { it.length in 2..12 }

        val tradeNo = findValue(texts, tradeNoLabels)
            ?.filter { it.isDigit() }
            ?.takeIf { it.length >= 10 }

        val kind = when {
            // 退款最优先：页面上写了退款成功，这笔就是退回来的钱
            refundCues.any { flat.contains(it) } -> TxnKind.REFUND
            // 金额带 + 号 = 钱进来了（收款、退款到零钱都在这条上）
            texts.any { amountPosSign.containsMatchIn(it.trim()) } ->
                if (wallet == Wallet.WECHAT && flat.contains("转账")) TxnKind.TRANSFER else TxnKind.INCOME
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
            kindHint = kind,
            platformCategory = platformCategory
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
        // 先认明确的「实付 / 实际扣款」标签，再退回扫描 ¥ 符号。
        // 顺序反过来的话，页面同时写着「原价 ¥35.00 / 优惠 ¥5.00 / 实付 ¥30.00」，
        // 第一个扫到的 ¥ 是 35，记的就是没打折的金额。
        // findValue 是精确前缀匹配，所以「金额」不会误吃到「优惠金额」这一行。
        // 按标签的优先级一个个找，而不是把标签一锅端给 findValue。
        // findValue 是按控件树顺序扫的，页面上「金额 35」排在「实付金额 30」前面就会取错。
        for (label in amountLabels) {
            val v = findValue(texts, listOf(label)) ?: continue
            Money.parseToCents(v)?.let { c -> if (c > 0) return c }
        }

        // 「-25.00」这种负号在单独的节点上时，合并判断
        for (t in texts) {
            val m = amountWithSymbol.find(t)
            if (m != null) {
                Money.parseToCents(m.groupValues[1])?.let { if (it > 0) return it }
            }
        }
        for (t in texts) {
            val m = amountSigned.find(t)
            if (m != null) {
                Money.parseToCents(m.groupValues[1])?.let { if (it > 0) return it }
            }
        }
        for (t in texts) {
            val m = amountPlain.find(t)
            if (m != null) {
                Money.parseToCents(m.groupValues[1])?.let { if (it > 0) return it }
            }
        }
        // 最后兜一层：页面上单独一行、只写了「20.00」的，基本就是这笔的金额
        for (t in texts) {
            val m = amountBareDecimal.find(t.trim())
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
