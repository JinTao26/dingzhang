package com.dingzhang.app.ingest

import com.dingzhang.app.core.model.Money
import com.dingzhang.app.core.model.Source
import com.dingzhang.app.core.model.TxnKind
import com.dingzhang.app.core.model.Wallet

/**
 * 银行短信 → 一笔账。
 *
 * 这条通道存在的唯一理由：工资到账、线下刷卡、银行间转账根本不经过微信支付宝，
 * 前三条通道一条都看不见。短信是唯一能实时拿到这些钱的入口。
 *
 * 规则很保守 —— 认不出方向就丢掉，宁可漏一笔也不要把工资记成支出。
 * 余额、完整卡号一律不入库，只留尾号后四位。
 */
object BankSmsParser {

    private val bankSign = Regex("""【(.+?)】""")
    private val amountRe = Regex("""([0-9][0-9,]{0,12}(?:\.[0-9]{1,2})?)\s*元""")
    private val tailRe = Regex("""(?:尾号|卡号末四位|卡号后四位|账户末四位)[：:\s]*(\d{4})""")
    private val merchantRe = Regex("""(?:商户|商户名称|交易场所|收款方)[：:]\s*([^，,。；;、\s]+)""")
    private val balanceCut = Regex("""(?:余额|可用余额|账户余额)""")
    private val longDigits = Regex("""\d{9,}""")

    private val incomeWords = listOf("工资", "入账", "收入", "存入", "转入", "到账", "汇入", "退款", "返现", "奖励")
    private val expenseWords = listOf("消费", "支出", "扣款", "转出", "取现", "付款", "还款", "代扣", "缴费", "转给")
    private val bankHints = listOf("银行", "储蓄卡", "储蓄账户", "信用卡", "借记卡", "账户")

    fun parse(body: String, at: Long): Captured? {
        val flat = body.replace('\n', ' ').replace('\r', ' ').trim()
        if (flat.isBlank()) return null
        if (!bankHints.any { flat.contains(it) }) return null

        // 「余额 12345.67 元」在后面，不切掉会把余额当成交易金额
        val cutAt = balanceCut.find(flat)?.range?.first ?: flat.length
        val head = flat.substring(0, cutAt)

        val amountText = amountRe.find(head)?.groupValues?.get(1) ?: return null
        val cents = Money.parseToCents(amountText) ?: return null
        if (cents <= 0) return null

        val kind = when {
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
            bank.isNotEmpty() -> "$bank 消费"
            kind == TxnKind.INCOME -> "银行入账"
            else -> "银行卡消费"
        }

        return Captured(
            amountCents = cents,
            wallet = Wallet.BANK,
            payee = payee,
            source = Source.SMS,
            occurredAt = at,
            note = bank.ifEmpty { "银行短信" },
            raw = sanitize(flat),
            account = account,
            kindHint = kind
        )
    }

    /** 完整卡号、账号一律打码，原始短信只留前 80 字。 */
    private fun sanitize(text: String): String =
        longDigits.replace(text, "****").take(80)
}
