package com.dingzhang.app.classify

import com.dingzhang.app.core.model.LearnedRule
import com.dingzhang.app.core.model.TxnKind

/**
 * 自动分类引擎。优先级：
 *   1. 用户教过的说法（LearnedRule）—— 最高，长 pattern 优先
 *   2. 内置商户词典 —— 长词优先
 *   3. 兜底 other，并把置信度标低，让 UI 提示「待确认」
 *
 * 不做金额/时间的启发式猜测：小额不一定是餐饮，深夜不一定是夜宵。
 * 猜错的成本比不猜高——用户会失去对自动记账的信任。
 */
object CategoryEngine {

    data class Result(
        val categoryId: String,
        val kind: TxnKind,
        val matched: String?,
        val fromRule: Boolean,
        val confidence: Float
    )

    fun classify(
        payee: String,
        note: String,
        raw: String,
        rules: List<LearnedRule>
    ): Result {
        val text = sequenceOf(payee, note, raw)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()

        if (text.isBlank()) {
            return Result("other", TxnKind.EXPENSE, null, false, 0f)
        }

        // 1. 用户教过的
        val sorted = rules.sortedByDescending { it.pattern.length }
        for (r in sorted) {
            if (r.pattern.isBlank()) continue
            if (text.contains(r.pattern, ignoreCase = true)) {
                val kind = r.kind ?: if (r.categoryId == "income") TxnKind.INCOME else TxnKind.EXPENSE
                return Result(r.categoryId, kind, r.pattern, true, 1.0f)
            }
        }

        // 2. 词典
        val hit = MerchantDict.lookup(text)
        if (hit != null) {
            val kind = when (hit.kind) {
                "income" -> TxnKind.INCOME
                "transfer" -> TxnKind.TRANSFER
                else -> TxnKind.EXPENSE
            }
            val conf = when {
                hit.keyword.length >= 4 -> 0.92f
                hit.keyword.length == 3 -> 0.84f
                else -> 0.7f
            }
            return Result(hit.categoryId, kind, hit.keyword, false, conf)
        }

        // 3. 没命中
        return Result("other", TxnKind.EXPENSE, null, false, 0.24f)
    }
}

/**
 * 从一笔已经落库的交易反推「该记住什么说法」。
 * 用户手动改分类时调用，把最具体、最可能有复用价值的片段作为规则 key。
 */
object RuleLearning {

    private val tooGeneric = setOf(
        "支付", "付款", "收款", "转账", "消费", "微信支付", "支付宝", "商户", "个人",
        "收款方", "订单", "交易", "成功", "已完成", "已支付", "退款", "余额", "零钱", "花呗"
    )

    /**
     * 返回候选规则 key，从具体到宽泛。
     * payee 通常就是商户名，是最值的学的东西；note 次之。
     */
    fun candidates(payee: String, note: String): List<String> {
        val out = ArrayList<String>(3)
        val p = payee.trim()
        if (p.length in 2..16 && tooGeneric.none { p == it }) out.add(p)

        val n = note.trim()
        if (n.length in 2..12 && tooGeneric.none { n == it } && n != p) out.add(n)

        // 商户名里带后缀的，取主体：「小杨生煎(北仑店)」-> 「小杨生煎」
        val stripped = p.replace(Regex("[（(].*?[)）]"), "").trim()
        if (stripped.length in 2..12 && stripped != p && tooGeneric.none { stripped == it }) {
            out.add(0, stripped)
        }
        return out.distinct()
    }

    fun best(payee: String, note: String): String? = candidates(payee, note).firstOrNull()
}
