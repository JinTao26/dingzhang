package com.dingzhang.app.ingest

import com.dingzhang.app.core.model.Money
import com.dingzhang.app.core.model.Source
import com.dingzhang.app.core.model.TxnKind
import com.dingzhang.app.core.model.Wallet
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 银行动账提醒 → 一笔账。
 *
 * 三个来源共用这一套规则：银行短信、手机银行 App 的推送、银行公众号的微信提醒。
 * 短信现在不少银行开始收费了，推送和公众号更省钱 —— 但它们说的是同一件事，
 * 没必要写三份解析器，措辞差异靠同一张动词表兜。
 *
 * 这条通道存在的唯一理由：工资到账、线下刷卡、银行间转账根本不经过微信支付宝，
 * 另外几条通道一条都看不见。
 *
 * 规则保守：认不出方向就丢掉，宁可漏一笔也不要把工资记成支出。
 * 余额、完整卡号一律不入库，只留尾号后四位。
 */
object BankAlertParser {

    private val bankSign = Regex("""【(.+?)】""")
    private val amountRe = Regex("""([0-9][0-9,]{0,12}(?:\.[0-9]{1,2})?)\s*元""")
    private val tailRe = Regex("""(?:尾号|卡号末四位|卡号后四位|账户末四位)[：:\s]*(\d{4})""")
    private val merchantRe = Regex("""(?:商户|商户名称|交易场所|收款方)[：:]\s*([^，,。；;、\s]+)""")
    private val balanceCut = Regex("""(?:余额|可用余额|账户余额)""")
    private val longDigits = Regex("""\d{9,}""")
    private val dateTimeRe = Regex("""(\d{1,2})月(\d{1,2})日\s*(\d{1,2})[:：](\d{2})""")
    private val dateOnlyRe = Regex("""(\d{1,2})月(\d{1,2})日""")

    /**
     * 这些提醒不是「钱真的动了」：账单、额度、验证码、活动。
     * 不排掉的话，「您9月账单应还2000元」会被记成一笔 2000 的消费。
     */
    private val notTxn = listOf(
        "应还", "待还", "还款日", "账单日", "账单已出", "额度", "验证码", "请于", "即将",
        "剩余可用", "可用额度不足", "累计积分", "活动", "优惠券", "推送通知已关闭", "退订"
    )

    private val incomeWords = listOf("工资", "入账", "收入", "存入", "转入", "到账", "汇入", "返现", "奖励")
    private val expenseWords = listOf("消费", "支出", "扣款", "转出", "取现", "付款", "还款", "代扣", "缴费", "转给")
    private val refundWords = listOf("退款", "退回", "冲正", "撤销")
    private val bankHints = listOf("银行", "储蓄卡", "储蓄账户", "信用卡", "借记卡", "账户")

    /**
     * @param source 短信传 [Source.SMS]，App 推送 / 公众号提醒传 [Source.NOTIFICATION]。
     */
    fun parse(body: String, at: Long, source: Source = Source.SMS): Captured? {
        val flat = body.replace('\n', ' ').replace('\r', ' ').trim()
        if (flat.isBlank()) return null
        if (!bankHints.any { flat.contains(it) }) return null
        if (notTxn.any { flat.contains(it) }) return null

        // 「余额 12345.67 元」在后面，不切掉会把余额当成交易金额
        val cutAt = balanceCut.find(flat)?.range?.first ?: flat.length
        val head = flat.substring(0, cutAt)

        val amountText = amountRe.find(head)?.groupValues?.get(1) ?: return null
        val cents = Money.parseToCents(amountText) ?: return null
        if (cents <= 0) return null

        val kind = when {
            // 退款要排在「到账 / 入账」前面，「退款到账」否则会被算成收入
            refundWords.any { head.contains(it) } -> TxnKind.REFUND
            head.contains("工资") || head.contains("薪") -> TxnKind.INCOME
            incomeWords.any { head.contains(it) } -> TxnKind.INCOME
            expenseWords.any { head.contains(it) } -> TxnKind.EXPENSE
            else -> return null
        }

        val bank = bankSign.find(flat)?.groupValues?.get(1)?.trim().orEmpty()
        val tail = tailRe.find(flat)?.groupValues?.get(1).orEmpty()
        val merchant = merchantRe.find(flat)?.groupValues?.get(1)?.trim().orEmpty()

        val account = buildString {
            if (bank.isNotEmpty()) append(bank)
            if (tail.isNotEmpty()) {
                if (isNotEmpty()) append(' ')
                append("尾号").append(tail)
            }
        }

        val payee = when {
            merchant.isNotEmpty() -> merchant
            head.contains("工资") || head.contains("薪") -> "工资"
            bank.isNotEmpty() && kind == TxnKind.INCOME -> "$bank 入账"
            bank.isNotEmpty() && kind == TxnKind.REFUND -> "$bank 退款"
            bank.isNotEmpty() -> "$bank 消费"
            kind == TxnKind.INCOME -> "银行入账"
            kind == TxnKind.REFUND -> "银行退款"
            else -> "银行卡消费"
        }

        return Captured(
            amountCents = cents,
            wallet = Wallet.BANK,
            payee = payee,
            source = source,
            occurredAt = timeOf(flat, at),
            note = bank.ifEmpty { "银行提醒" },
            raw = sanitize(flat),
            account = account,
            kindHint = kind
        )
    }

    /**
     * 提醒里写的交易时间比「收到的时间」准得多 —— 补发的、延迟的提醒尤其明显。
     * 只给月和日，年份按「不晚于现在」倒推（12月的提醒在次年1月补发也能对上）。
     */
    private fun timeOf(flat: String, fallback: Long): Long {
        val withTime = dateTimeRe.find(flat)
        val withDate = dateOnlyRe.find(flat)
        val month: Int
        val day: Int
        val hour: Int
        val minute: Int
        if (withTime != null) {
            month = withTime.groupValues[1].toIntOrNull() ?: return fallback
            day = withTime.groupValues[2].toIntOrNull() ?: return fallback
            hour = withTime.groupValues[3].toIntOrNull() ?: return fallback
            minute = withTime.groupValues[4].toIntOrNull() ?: return fallback
        } else if (withDate != null) {
            month = withDate.groupValues[1].toIntOrNull() ?: return fallback
            day = withDate.groupValues[2].toIntOrNull() ?: return fallback
            hour = 12
            minute = 0
        } else {
            return fallback
        }

        return try {
            val now = LocalDateTime.now()
            var dt = LocalDateTime.of(now.year, month, day, hour, minute)
            if (dt.isAfter(now.plusDays(1))) dt = LocalDateTime.of(now.year - 1, month, day, hour, minute)
            dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            fallback
        }
    }

    /** 完整卡号、账号一律打码，原文只留前 80 字。 */
    private fun sanitize(text: String): String =
        longDigits.replace(text, "****").take(80)
}
