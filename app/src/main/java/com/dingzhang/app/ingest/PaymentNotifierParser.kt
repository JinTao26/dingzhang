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

    /**
     * 购物 App 自己发的支付成功通知。
     *
     * 京东下单用京东支付 / 银行卡时，微信和支付宝都没有任何动静，
     * 只有京东自己会推一条「使用【xx卡】支付 16.90」。这类通知是能救回一整类订单的，
     * 但它同时也是营销推送的重灾区（「省钱金额已达 648.13 元」），
     * 所以规则必须比微信支付宝严：只认明确的成交句，绝不认裸「¥xx」。
     */
    fun isShoppingApp(pkg: String): Boolean =
        pkg.contains("jingdong") || pkg.contains("jd.jr") || pkg.contains("jdlite") ||
            // 抖音、淘宝、天猫、拼多多、美团、得物、小红书、云闪付都有自己的支付通知。
            // 以前这些包进来就被判「不是支付 App」直接扔掉，白丢一整类订单。
            pkg.contains("taobao") || pkg.contains("tmall") ||
            pkg.contains("ugc.aweme") || pkg.contains("ugc.live") ||
            pkg.contains("pinduoduo") || pkg.contains("xiaomi.market") ||
            pkg.contains("meituan") || pkg.contains("sankuai") ||
            pkg.contains("shizhuang") || pkg.contains("xingin") ||
            pkg.contains("unionpay")

    /** 包名换成人看得懂的名字，诊断里用 */
    fun appLabel(pkg: String): String = appName(pkg).ifBlank { pkg.takeLast(16) }

    private fun appName(pkg: String): String = when {
        pkg.contains("taobao") -> "淘宝"
        pkg.contains("tmall") -> "天猫"
        pkg.contains("pinduoduo") -> "拼多多"
        pkg.contains("ugc.aweme") || pkg.contains("ugc.live") -> "抖音"
        pkg.contains("meituan") || pkg.contains("sankuai") -> "美团"
        pkg.contains("shizhuang") -> "得物"
        pkg.contains("xingin") -> "小红书"
        pkg.contains("unionpay") -> "云闪付"
        pkg.contains("jingdong") || pkg.contains("jd.jr") || pkg.contains("jdlite") -> "京东"
        else -> ""
    }

    /**
     * 明确的「这单付掉了」。购物类 App 的通知里混着物流、评价、营销，
     * 没有这几句就不认 —— 宁可漏，不能把「省钱金额已达 648.13 元」记成支出。
     */
    private val strongPayCues = listOf(
        "支付成功", "付款成功", "交易成功", "支付完成", "付款完成", "已支付", "成功支付",
        "已付款", "扣款成功", "付款已成功"
    )

    /**
     * 任何 App 的通知都适用的判断：只要有一句明确的成交话术（或退款话术）加一个金额，
     * 就值得当一笔支付看。
     *
     * 充电桩、外卖、小众商户 App 的包名不可能一个个列全，它们的通知以前一律被丢掉。
     * 但「支付成功 ¥3.00」「退款 1.80 元」这种话术本身已经够明确了 ——
     * 靠话术判断，不靠包名白名单。
     */
    fun looksLikeGenericPay(s: String): Boolean {
        if (s.length > 400) return false
        if (noise.any { s.contains(it) }) return false
        val hasSignal = strongPayCues.any { s.contains(it) } ||
            refundCues.any { s.contains(it) } ||
            expenseCues.any { s.contains(it) }
        if (!hasSignal) return false
        return AMOUNT_WITH_SYMBOL.containsMatchIn(s) || AMOUNT_WITH_YUAN.containsMatchIn(s)
    }

    private val shoppingExpensePatterns = listOf(
        Regex("使用【(.+?)】支付([0-9]+(?:\\.[0-9]{1,2})?)"),
        Regex("(?:支付|付款)成功[，,：:]?[¥￥]?([0-9]+(?:\\.[0-9]{1,2})?)"),
        Regex("成功支付[¥￥]?([0-9]+(?:\\.[0-9]{1,2})?)"),
        Regex("(?:支付|付款)完成[，,：:]?[¥￥]?([0-9]+(?:\\.[0-9]{1,2})?)"),
        Regex("已支付[¥￥]([0-9]+(?:\\.[0-9]{1,2})?)"),
        Regex("实付[¥￥]([0-9]+(?:\\.[0-9]{1,2})?)")
        // 刻意没有「支付([0-9.]+)」和「[¥￥]([0-9.]+)」：
        // 后者会把「省钱金额已达648.13元」记成一笔支出
    )

    /** 出现任何一个就整条不算：这是营销文案、账单提醒、还没付的状态。 */
    private val shoppingNoise = listOf(
        "省钱", "优惠", "立减", "满减", "红包", "优惠券", "领券", "余额", "额度",
        "可用", "还款", "账单已出", "待还款", "待付款", "待支付", "去支付", "请支付",
        "免息", "分期", "白条额度", "提额", "签到", "积分", "推荐", "降价", "收藏"
    )

    private val AMOUNT_WITH_SYMBOL = Regex("[¥￥]\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)")
    private val AMOUNT_WITH_YUAN = Regex("([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*元")
    /** 少数银行 App / 境外卡通知写成 CNY32.00、RMB 32 */
    private val AMOUNT_WITH_CODE = Regex("(?:CNY|RMB)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)")
    /** 「金额 / 实付 / 合计」后面的数字，单位可能一个都没有 */
    private val BARE_AMOUNT = Regex(
        "(金额|实付|实付款|合计|交易额|支付金额)\\s*[:：]?\\s*[¥￥]?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"
    )

    private val incomeCues = listOf(
        "收款到账", "收款成功", "已收款", "到账", "入账", "退款到账", "已退回", "收入到账",
        // 收红包 / 收转账：别人转进来的钱要算收入，不能当成没发生
        "红包到账", "收到红包", "收到转账", "已收钱", "存入零钱", "入账金额", "转入成功"
    )
    private val expenseCues = listOf(
        "支付成功", "付款成功", "已支付", "已付款", "扣款", "消费", "付款", "支付",
        // 支付宝的「交易提醒」不写「支付成功」，写的是「你有一笔支出」「消费成功」。
        // 少了这几个词，一条 44 字、金额清清楚楚的通知会被整条判成「不是支付」扔进回收站
        "支出", "支出成功", "消费成功", "已扣款", "笔支出", "交易成功",
        // 发红包 / 转给朋友：这是实打实花出去的钱
        "发红包", "发出红包", "已转账给", "转账给"
    )
    /** 只有这些是「自己的钱换个口袋」：提现、还款、零钱转余额，不该进收支统计。 */
    private val transferCues = listOf(
        "还款成功", "已还款", "提现成功", "余额转入", "零钱转入", "信用卡还款", "自动还款",
        // 花呗、美团月付、京东白条还款时各说各的，这几句都得认
        "已还清", "还清了", "结清", "月付还款", "白条还款", "花呗还款", "还款完成"
    )
    /** 明确写给别人的，算支出 */
    private val outgoingCues = listOf("转账给", "转给", "已转出", "转出", "已转账给")
    private val refundCues = listOf("退款", "已退回", "退回")

    /** 这些通知肯定不是一笔消费，先挡掉，省得后面误判。 */
    private val noise = listOf(
        "验证码", "登录", "优惠券", "红包雨", "活动", "升级", "版本更新", "为您推荐",
        "账单已出", "还款提醒", "待还款", "即将到期", "签到", "积分",
        // 「活动」原来是单字一刀切，打车、外卖的通知里常带「参与活动」「活动详情」，
        // 真金白银的扣款也跟着被砍掉。收窄成三个明确的营销说法
        "限时活动", "活动规则", "活动详情"
    )

    private val payeePatterns = listOf(
        Regex("向(.{2,24}?)(?:付款|支付|转账)"),
        Regex("付款给(.{2,24}?)(?:\\s|，|,|$|。|的)"),
        Regex("在(.{2,24}?)(?:消费|付款|支付)"),
        Regex("(?:收款方|商户名称|交易对方|对方)[:：\\s]\\s*(.{2,24}?)(?:\\s|，|,|$|。|\\n)"),
        // 「你有一笔 12.00 元的支出，来自高德打车」—— 打车、外卖这类第三方下单常写「来自」，
        // 只认「向 XX 付款」会把商户名整段漏掉，最后记成「支付宝」
        Regex("来自(.{2,20}?)(?:的|，|,|$|。|\\s)"),
        Regex("微信支付[\\-—]\\s*(.{2,24}?)(?:\\s|，|,|$|。|\\n)"),
        Regex("付款[\\-—]\\s*(.{2,24}?)(?:\\s|，|,|$|。|\\n)")
    )

    /**
     * 购物 App 的支付通知。
     * @return 认不出来返回 null —— 下单提醒、物流、营销推送都会走到这里
     */
    fun parseShoppingApp(
        pkg: String,
        title: CharSequence?,
        text: CharSequence?,
        bigText: CharSequence?,
        postedAt: Long
    ): Captured? {
        if (!isShoppingApp(pkg)) return null

        val corpus = listOfNotNull(title?.toString(), text?.toString(), bigText?.toString())
            .joinToString("\n")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (corpus.isEmpty() || corpus.length > 400) return null

        // 闸一：营销文案、账单提醒、未付款状态，一律不算
        if (shoppingNoise.any { corpus.contains(it) }) return null

        // 闸二：必须命中明确的成交句
        var amountCents: Long? = null
        var account = ""
        for (p in shoppingExpensePatterns) {
            val m = p.find(corpus) ?: continue
            val rawAmount = m.groupValues.last()
            val cents = Money.parseToCents(rawAmount) ?: continue
            if (cents <= 0 || cents > 10_000_000_00L) continue
            amountCents = cents
            // 「使用【招商银行储蓄卡(7619)】支付16.90」—— 第一个捕获组是卡
            if (m.groupValues.size >= 3 && m.groupValues[1].length in 2..24 &&
                !m.groupValues[1].any { it.isDigit() }
            ) {
                account = m.groupValues[1]
            }
            break
        }
        val amount = amountCents ?: return null

        val isRefund = corpus.contains("退款成功") || corpus.contains("已退款") ||
            corpus.contains("退款到账")

        // 钱包：通知里写了走的哪条路就记哪条，没写就记未知，不猜
        val wallet = when {
            corpus.contains("微信支付") || corpus.contains("零钱") -> Wallet.WECHAT
            corpus.contains("支付宝") || corpus.contains("花呗") -> Wallet.ALIPAY
            corpus.contains("储蓄卡") || corpus.contains("信用卡") || corpus.contains("银行卡") ->
                Wallet.BANK
            else -> Wallet.UNKNOWN
        }

        val payee = extractShoppingPayee(corpus) ?: defaultShoppingPayee(pkg)

        return Captured(
            amountCents = amount,
            wallet = wallet,
            payee = payee,
            source = Source.NOTIFICATION,
            occurredAt = postedAt,
            account = account,
            raw = corpus.take(300),
            externalId = null,
            kindHint = if (isRefund) TxnKind.REFUND else TxnKind.EXPENSE,
            platformCategory = defaultShoppingPayee(pkg)
        )
    }

    private fun defaultShoppingPayee(pkg: String): String = when {
        pkg.contains("jingdong") || pkg.contains("jd.jr") || pkg.contains("jdlite") -> "京东"
        else -> "网店"
    }

    /** 通知里能认出店铺就认，认不出就用平台名。 */
    private fun extractShoppingPayee(corpus: String): String? {
        val shop = Regex("([\\u4e00-\\u9fa5A-Za-z0-9·]{2,20}(?:旗舰店|专营店|专卖店|官方店|小店))")
            .find(corpus)?.groupValues?.get(1)
        if (!shop.isNullOrBlank()) return IngestPipeline.cleanPayee(shop).takeIf { it.length in 2..20 }

        return listOfNotNull(
            Regex("商户[：:]\\s*(.{2,20}?)(?:\\s|，|,|$)").find(corpus)?.groupValues?.get(1),
            Regex("付款给(.{2,20}?)(?:\\s|，|,|$|。|的)").find(corpus)?.groupValues?.get(1)
        ).firstOrNull { it.isNotBlank() }?.let { IngestPipeline.cleanPayee(it) }
            ?.takeIf { it.length in 2..20 }
    }

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
            // 抖音、淘宝、美团、拼多多、云闪付自己的支付通知没有对应的钱包枚举，
            // 记「未知」，别硬猜是从哪张卡扣的
            else -> Wallet.UNKNOWN
        }

        val corpus = listOfNotNull(title?.toString(), text?.toString(), bigText?.toString())
            .joinToString("\n")
            .trim()
        if (corpus.isEmpty() || corpus.length > 400) return null
        if (noise.any { corpus.contains(it) }) return null
        // 微信、支付宝的通知本身就带着支付语义，可以放宽；
        // 购物类 App 的通知里一半是物流和营销，必须命中明确的成交句
        if (pkg != PKG_WECHAT && pkg != PKG_ALIPAY &&
            strongPayCues.none { corpus.contains(it) }
        ) return null

        val isIncome = incomeCues.any { corpus.contains(it) }
        val isExpense = expenseCues.any { corpus.contains(it) }
        val isTransfer = transferCues.any { corpus.contains(it) }
        val isOutgoing = outgoingCues.any { corpus.contains(it) }
        // 退款要单独算一路。以前只认收/支/转账三类，纯粹的退款通知
        // （「退款提醒：¥1.48」）一个词都不沾，明明带着金额也被判成「不是支付」扔掉。
        // 退回的钱也是钱，而且它决定这笔支出最后算多少。
        val isRefund = refundCues.any { corpus.contains(it) }
        // 「交易成功」这种话术写在 strongPayCues 里，却没进 expenseCues，结果在下面这道闸
        // 被当成「不是支付」扔掉 —— 白底上写着成交，人却以为没付过
        val strongPay = strongPayCues.any { corpus.contains(it) }
        if (!isIncome && !isExpense && !isTransfer && !isOutgoing && !isRefund && !strongPay) return null

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
                // 抖音、美团这些认不出来源的，至少把 App 名写上，
                // 否则流水里商户一栏是空的，用户根本不知道这笔是谁扣的
                else -> appName(pkg)
            }

        val kind = when {
            // 「退款到账」也会命中 incomeCues，得先在退款上判一次
            refundCues.any { corpus.contains(it) } -> TxnKind.REFUND
            // 提现、还款、零钱转余额：钱只是换了位置，不进收支统计
            isTransfer -> TxnKind.TRANSFER
            isIncome && !isExpense -> TxnKind.INCOME
            isExpense || isOutgoing || strongPay -> TxnKind.EXPENSE
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
        // 词表原先只有「余额/可用/优惠/立减/已省」，可微信的推送常写成
        // 「已优惠 5.00 元」「优惠金额 5 元」「减免 5 元」—— 这些写法一个都盖不住，
        // 于是「省下的 5 块」被当成「花掉的 5 块」记了一笔。宁可这一条认不出金额不记，
        // 也不能把折扣记成支出。
        val discountRegex = Regex(
            "(余额|可用|优惠|立减|已省|已优惠|优惠金额|优惠券|减免|折扣|抵扣|红包|满减|券)" +
                "[:：]?[ \\s]*[¥￥]?[ \\s]*[0-9][0-9,]*(?:\\.[0-9]{1,2})?[ \\s]*元?"
        )
        val sanitized = corpus.replace(discountRegex, " ")

        AMOUNT_WITH_SYMBOL.find(sanitized)?.let { m ->
            Money.parseToCents(m.groupValues[1])?.let { if (it > 0) return it }
        }
        AMOUNT_WITH_YUAN.find(sanitized)?.let { m ->
            Money.parseToCents(m.groupValues[1])?.let { if (it > 0) return it }
        }
        AMOUNT_WITH_CODE.find(sanitized)?.let { m ->
            Money.parseToCents(m.groupValues[1])?.let { if (it > 0) return it }
        }
        // 最后一档：有明确成交话术，金额却是光秃秃一个数（「金额 12.00」「实付12.00」）。
        // 只认这几种限定词后面的数字，裸数字一概不碰 —— 通知里数字太多了
        if (strongPayCues.any { sanitized.contains(it) }) {
            BARE_AMOUNT.find(sanitized)?.let { m ->
                Money.parseToCents(m.groupValues[2])?.let { if (it > 0) return it }
            }
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
