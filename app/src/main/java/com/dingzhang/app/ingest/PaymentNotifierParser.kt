package com.dingzhang.app.ingest

import com.dingzhang.app.core.model.Money
import com.dingzhang.app.core.model.Source
import com.dingzhang.app.core.model.TxnKind
import com.dingzhang.app.core.model.Wallet
import com.dingzhang.app.data.Settings

/**
 * 通道 A：通知监听解析。
 *
 * 现实边界（很重要）：
 * - 微信/支付宝并不是每笔支付都会推一条带金额的通知。微信很多机型只弹「微信支付凭证」四个字，
 *   没有金额。支付宝的付款成功通知通常带金额，覆盖率更高。
 * - 所以这个通道只能算「实时兜底」，不能当主通道。主通道是无障碍 + 官方账单导入。
 * - 一旦解析不出金额，就直接放弃，不做任何猜测。
 */
object PaymentNotifierParser {

    const val PKG_WECHAT = "com.tencent.mm"
    const val PKG_ALIPAY = "com.eg.android.AlipayGphone"

    private val AMOUNT_WITH_SYMBOL = Regex("[¥￥]\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)")
    private val AMOUNT_WITH_YUAN = Regex("([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*元")

    private val incomeCues = listOf(
        "收款到账", "收款成功", "已收款", "到账", "入账", "退款到账", "已退回", "收入到账",
        // 收红包 / 收转账：别人转进来的钱要算收入，不能当成没发生
        "红包到账", "收到红包", "收到转账", "已收钱", "存入零钱", "入账金额", "转入成功"
    )
    private val expenseCues = listOf(
        "支付成功", "付款成功", "已支付", "已付款", "扣款", "消费", "付款", "支付",
        // 发红包 / 转给朋友：这是实打实花出去的钱
        "发红包", "发出红包", "已转账给", "转账给"
    )
    /** 只有这些是「自己的钱换个口袋」：提现、还款、零钱转余额，不该进收支统计。 */
    private val transferCues = listOf(
        "还款成功", "已还款", "提现成功", "余额转入", "零钱转入", "信用卡还款", "自动还款"
    )
    /** 明确写给别人的，算支出 */
    private val outgoingCues = listOf("转账给", "转给", "已转出", "转出", "已转账给")
    private val refundCues = listOf("退款", "已退回", "退回")

    /** 这些通知肯定不是一笔消费，先挡掉，省得后面误判。 */
    private val noise = listOf(
        "验证码", "登录", "优惠券", "红包雨", "活动", "升级", "版本更新", "为您推荐",
        "账单已出", "还款提醒", "待还款", "即将到期", "签到", "积分"
    )

    private val payeePatterns = listOf(
        Regex("向(.{2,24}?)(?:付款|支付|转账)"),
        Regex("付款给(.{2,24}?)(?:\\s|，|,|$|。|的)"),
        Regex("在(.{2,24}?)(?:消费|付款|支付)"),
        Regex("(?:收款方|商户名称|交易对方|对方)[:：\\s]\\s*(.{2,24}?)(?:\\s|，|,|$|。|\\n)"),
        Regex("微信支付[\\-—]\\s*(.{2,24}?)(?:\\s|，|,|$|。|\\n)"),
        Regex("付款[\\-—]\\s*(.{2,24}?)(?:\\s|，|,|$|。|\\n)")
    )

    /**
     * @return 解析成功返回 Captured，失败返回 null。失败不抛异常、不猜。
     */
    fun parse(
        pkg: String,
        title: CharSequence?,
        text: CharSequence?,
        bigText: CharSequence?,
        postedAt: Long,
        settings: Settings
    ): Captured? {
        val wallet = when (pkg) {
            PKG_WECHAT -> Wallet.WECHAT
            PKG_ALIPAY -> Wallet.ALIPAY
            else -> return null
        }

        val corpus = listOfNotNull(title?.toString(), text?.toString(), bigText?.toString())
            .joinToString("\n")
            .trim()
        if (corpus.isEmpty() || corpus.length > 400) return null
        if (noise.any { corpus.contains(it) }) return null

        val isIncome = incomeCues.any { corpus.contains(it) }
        val isExpense = expenseCues.any { corpus.contains(it) }
        val isTransfer = transferCues.any { corpus.contains(it) }
        val isOutgoing = outgoingCues.any { corpus.contains(it) }
        if (!isIncome && !isExpense && !isTransfer && !isOutgoing) return null

        val amount = extractAmount(corpus) ?: return null

        // 微信的支付卡片第一行直接写店名（「加贝购物超市」），没有「向 XX 付款」的句式，
        // 所以正则匹配不到时再逐行猜一遍，猜不到才退回泛称
        val lines = listOfNotNull(title?.toString(), text?.toString(), bigText?.toString())
            .flatMap { it.split("\n") }
        val payee = extractPayee(corpus)
            ?: IngestPipeline.guessPayeeFromLines(lines)
            ?: when {
                // 红包和转账本来就没有商户，别写成「微信支付」这种没有信息量的泛称
                corpus.contains("红包") -> "红包"
                corpus.contains("转账") || corpus.contains("转入") -> "转账"
                wallet == Wallet.WECHAT -> "微信支付"
                wallet == Wallet.ALIPAY -> "支付宝"
                else -> ""
            }

        val kind = when {
            // 「退款到账」也会命中 incomeCues，得先在退款上判一次
            refundCues.any { corpus.contains(it) } -> TxnKind.REFUND
            // 提现、还款、零钱转余额：钱只是换了位置，不进收支统计
            isTransfer -> TxnKind.TRANSFER
            isIncome && !isExpense -> TxnKind.INCOME
            isExpense || isOutgoing -> TxnKind.EXPENSE
            // 只写「转账成功」看不出方向时按支出处理：收钱那一侧一般会带「已收款 / 收款到账」
            else -> TxnKind.EXPENSE
        }

        return Captured(
            amountCents = amount,
            wallet = wallet,
            payee = payee,
            source = Source.NOTIFICATION,
            occurredAt = postedAt,
            note = "",
            raw = corpus,
            kindHint = kind
        )
    }

    /**
     * 只取「跟着 ¥ 号」或「跟着元字」的金额。
     * 通知里经常同时出现「余额 1234.56」「优惠 2 元」，这些不能当成消费金额。
     */
    private fun extractAmount(corpus: String): Long? {
        // 先排除余额 / 优惠附近的数字
        val sanitized = corpus
            .replace(Regex("(余额|可用|优惠|立减|已省)[:：]?\\s*[¥￥]?\\s*[0-9][0-9,]*(?:\\.[0-9]{1,2})?\\s*元?"), " ")

        AMOUNT_WITH_SYMBOL.find(sanitized)?.let { m ->
            Money.parseToCents(m.groupValues[1])?.let { if (it > 0) return it }
        }
        AMOUNT_WITH_YUAN.find(sanitized)?.let { m ->
            Money.parseToCents(m.groupValues[1])?.let { if (it > 0) return it }
        }
        return null
    }

    private fun extractPayee(corpus: String): String? {
        for (p in payeePatterns) {
            val m = p.find(corpus) ?: continue
            val raw = m.groupValues.getOrNull(1)?.trim().orEmpty()
            if (raw.length >= 2 && raw.none { it.isDigit() }) {
                return IngestPipeline.cleanPayee(raw)
            }
        }
        return null
    }
}
