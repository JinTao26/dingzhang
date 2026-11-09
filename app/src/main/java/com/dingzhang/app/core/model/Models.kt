package com.dingzhang.app.core.model

/** 交易方向。TRANSFER 是转账/内部划转，不计入收支统计。 */
enum class TxnKind(val db: String) {
    EXPENSE("expense"), INCOME("income"), TRANSFER("transfer");

    companion object {
        fun from(v: String?): TxnKind = values().firstOrNull { it.db == v } ?: EXPENSE
    }
}

/** 这笔账从哪儿来的。决定了可信度和去重优先级。 */
enum class Source(val db: String) {
    ACCESSIBILITY("a11y"),   // 无障碍读支付结果页 / 账单详情页
    NOTIFICATION("notify"),  // 通知监听
    BILL_IMPORT("bill"),     // 官方账单 CSV/Excel 导入（最权威）
    SMS("sms"),              // 银行短信：工资到账、刷卡消费这类不经过微信支付宝的钱
    MANUAL("manual");        // 手记

    companion object {
        fun from(v: String?): Source = values().firstOrNull { it.db == v } ?: MANUAL
    }

    val label: String
        get() = when (this) {
            ACCESSIBILITY -> "自动识别"
            NOTIFICATION -> "支付通知"
            BILL_IMPORT -> "账单导入"
            SMS -> "银行短信"
            MANUAL -> "手动"
        }
}

/** 记录状态：AUTO 待确认，CONFIRMED 用户确认过，MATCHED 与官方账单对上号。 */
enum class TxnStatus(val db: String) {
    AUTO("auto"), CONFIRMED("confirmed"), MATCHED("matched"), IGNORED("ignored");

    companion object {
        fun from(v: String?): TxnStatus = values().firstOrNull { it.db == v } ?: AUTO
    }
}

/** 资金渠道。微信/支付宝账单和通知按这个归并去重。 */
enum class Wallet(val db: String) {
    WECHAT("wechat"), ALIPAY("alipay"), BANK("bank"), CASH("cash"), UNKNOWN("unknown");

    companion object {
        fun from(v: String?): Wallet = values().firstOrNull { it.db == v } ?: UNKNOWN
    }

    val label: String
        get() = when (this) {
            WECHAT -> "微信"
            ALIPAY -> "支付宝"
            BANK -> "银行卡"
            CASH -> "现金"
            UNKNOWN -> "未指定"
        }
}

data class Txn(
    val id: Long = 0,
    val amountCents: Long,
    val kind: TxnKind,
    val categoryId: String,
    val wallet: Wallet,
    val payee: String,             // 商户 / 对方
    val note: String,
    val occurredAt: Long,          // epoch millis
    val source: Source,
    val status: TxnStatus,
    val rawText: String = "",
    val externalId: String? = null, // 微信/支付宝交易单号
    val categoryLocked: Boolean = false,
    val account: String = "",       // 零钱 / 花呗 / 招行储蓄卡…
    val fingerprint: String = ""
)

/** 用户教给它的分类规则，优先级高于内置词典。 */
data class LearnedRule(
    val id: Long = 0,
    val pattern: String,
    val categoryId: String,
    val kind: TxnKind? = null,
    val hitCount: Int = 1,
    val updatedAt: Long = 0
)

data class CategoryTotal(val categoryId: String, val cents: Long, val count: Int)
