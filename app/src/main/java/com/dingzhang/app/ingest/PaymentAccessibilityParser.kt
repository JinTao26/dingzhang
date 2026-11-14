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
    // 微信转账的收尾页上并不写「转账」二字，只写「待李梦达确认收款」，
    // 以前这类页面会被当成普通消费，2 块钱被算进支出里
    private val transferCues = listOf("还款", "提现", "转账", "确认收款", "待接收")
    /**
     * 「钱只是换了个口袋」：还款、提现、账单结清。这类**不分哪个钱包**都成立 ——
     * 花呗、美团月付、京东白条、信用卡还款，同一个道理。
     * 以前这条判定挂在 wallet == WECHAT 上，结果美团月付的还款被记成支出，
     * 加上消费那一笔，同一笔钱在流水里出现两次，支出凭空翻倍。
     */
    private val vaultCues = listOf("还款", "已还清", "还清了", "结清", "提现")
    /** 提醒/入口类，看到就别记 —— 「待还 20 元」不是已经还了 */
    private val vaultNotDone = listOf(
        "待还", "应还", "还款日", "账单日", "去还款", "立即还款", "确认还款", "还款提醒", "即将到期"
    )
    /** 「待李梦达确认收款」里的那个名字 */
    private val transferPeerPatterns = listOf(
        Regex("待\\s*(.{1,12}?)\\s*确认收款"),
        Regex("转账给\\s*(.{1,12})"),
        Regex("(.{1,12}?)\\s*已确认收款"),
        Regex("(.{1,12}?)\\s*等待确认收款")
    )

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
        val payee = transferPeer(texts)
            ?: findValue(texts, payeeLabels)
                ?.let { IngestPipeline.cleanPayee(it) }
                ?.takeIf { it.length >= 2 }
            ?: IngestPipeline.guessPayeeFromLines(texts)
            ?: if (vaultCues.any { flat.contains(it) }) "还款" else defaultPayee(wallet)

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

        // 「待还 20 元」这种提醒页上有金额、也有「还款」两个字，但它不是已经还了
        if (vaultNotDone.any { flat.contains(it) }) return null

        val kind = when {
            // 退款最优先：页面上写了退款成功，这笔就是退回来的钱
            refundCues.any { flat.contains(it) } -> TxnKind.REFUND
            // 还款、提现不分钱包都算资金转移，不进收支统计
            vaultCues.any { flat.contains(it) } -> TxnKind.TRANSFER
            // 金额带 + 号 = 钱进来了（收款、退款到零钱都在这条上）
            texts.any { amountPosSign.containsMatchIn(it.trim()) } ->
                if (wallet == Wallet.WECHAT && flat.contains("转账")) TxnKind.TRANSFER else TxnKind.INCOME
            transferCues.any { flat.contains(it) } && wallet == Wallet.WECHAT ->
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

    // ═══════════════════════════════════════════════════════
    // 内嵌收银台
    // ═══════════════════════════════════════════════════════
    /**
     * 在购物 App 里面直接把钱付掉的情况。
     *
     * 淘宝下单选支付宝付款时，收银台是支付宝的 SDK **弹在淘宝进程里**的，
     * 既不会跳到支付宝 App，支付宝也不会为此推一条通知 ——
     * 所以包名是 com.taobao.taobao 的这笔钱，只订阅微信/支付宝的通道永远看不到。
     * 京东、拼多多、闲鱼同理。
     *
     * 代价是这些 App 的页面不能当支付页来读，误记风险高（商品页上到处是价格），
     * 所以这里的规则比微信支付宝那套**严得多**：必须同时出现
     * 「已经付完的字」+「金额」，只要出现一个「还没付/付失败了」的字就整页丢弃。
     */
    val EMBEDDED_HOSTS = mapOf(
        "com.taobao.taobao" to "淘宝",
        "com.taobao.idlefish" to "闲鱼",
        "com.jingdong.app.mall" to "京东",
        "com.xunmeng.pinduoduo" to "拼多多",
        // 得物下单后常常直接在 App 内唤起支付宝 SDK 付款，
        // 不把它的包名加进来，连事件都收不到（这次「付了得物没记上」就吃了这个亏）
        "com.shizhuang.duapp" to "得物",
        "com.sankuai.meituan" to "美团",
        "com.xingin.xhs" to "小红书",
        "com.ss.android.ugc.aweme" to "抖音"
    )

    fun hostDisplayName(pkg: String): String? = EMBEDDED_HOSTS[pkg]

    /** 已经付完。必须命中最少一个，而且一个反例都不能有。 */
    private val embeddedDoneCues = listOf(
        "支付成功", "付款成功", "交易成功", "支付完成", "付款完成", "交易完成",
        "支付已成功", "付款已成功", "返回商家"
        // 刻意不认裸「已支付」：订单列表里每一笔历史订单都挂着这个状态
    )

    /** 还没付 / 付失败 / 跟付款无关的状态。出现任何一个，整页丢掉。 */
    private val embeddedNotDoneCues = listOf(
        "支付失败", "付款失败", "交易失败", "支付超时", "已取消", "交易关闭",
        "待付款", "待支付", "去支付", "立即支付", "立即付款", "确认付款",
        "收银台", "请确认", "正在支付", "支付中", "去结算", "提交订单",
        "确认订单", "加入购物车", "再买一单", "先去逛逛", "待收货",
        "我的订单", "全部订单", "待发货", "确认收货", "查看物流", "申请退款"
    )

    private val shopSuffixes = listOf(
        "旗舰店", "专营店", "专卖店", "官方店", "小店", "商行", "超市", "便利店", "药店", "书店"
    )

    /**
     * 内嵌收银台的成交页。
     * @return 认不出来返回 null —— 商品页、订单列表、店铺页绝大多数都会走到这里
     */
    fun parseEmbedded(pkg: String, texts: List<String>, now: Long): Captured? {
        val hostName = EMBEDDED_HOSTS[pkg] ?: return null
        if (texts.isEmpty()) return null

        val flat = texts.joinToString(" ").replace(Regex("\\s+"), " ").trim()
        if (flat.length < 4) return null

        // 闸一：出现任何「没付成」的字，整页不算
        if (embeddedNotDoneCues.any { flat.contains(it) }) return null
        // 「待还 20 元」这种提醒页上有金额、也有「还款」两个字，但它不是已经还了
        if (vaultNotDone.any { flat.contains(it) }) return null

        val vaultHit = vaultCues.any { flat.contains(it) }
        // 闸二：必须有明确的「付完了」。还款页不写「支付成功」，它写「还款成功」，
        // 所以这里对还款单独放一行 —— 否则月付还款根本进不来，
        // 或者更糟：被别的规则当成又一笔消费。
        if (!vaultHit && embeddedDoneCues.none { flat.contains(it) }) return null

        // 闸三：一页上冒出好几个价格，这是订单列表 / 商品列表，不是某一笔的成交页。
        // 这一条挡住的是最坏的情况：从列表页里随手抓一个金额，记成一笔不存在的消费。
        val priceCount = texts.count { amountWithSymbol.containsMatchIn(it) || it.contains("¥") }
        if (priceCount >= 3) return null

        // 闸四：必须有金额。过了前三闸，这里抓到的金额才可信
        val amount = extractAmount(texts) ?: return null
        if (amount <= 0 || amount > 10_000_000_00L) return null

        val kind = when {
            refundCues.any { flat.contains(it) } -> TxnKind.REFUND
            // 还款是「钱换个口袋」：消费那笔已经记过支出，这里再记一笔支出就翻倍了
            vaultHit -> TxnKind.TRANSFER
            else -> TxnKind.EXPENSE
        }

        // 钱包：App 里写了哪个就用哪个。都没写（京东支付之类）就记未知，
        // 宁可不知道走的哪张卡，也别猜
        val wallet = when {
            flat.contains("支付宝") || flat.contains("花呗") || flat.contains("余额宝") ->
                Wallet.ALIPAY
            flat.contains("微信") || flat.contains("零钱") -> Wallet.WECHAT
            else -> Wallet.UNKNOWN
        }

        // 商户名：先找店铺（「XX旗舰店」这种），找不到就记平台本身「淘宝」
        val shop = texts.firstOrNull { t ->
            t.length in 3..24 && shopSuffixes.any { t.endsWith(it) }
        }?.let { IngestPipeline.cleanPayee(it) }?.takeIf { it.length in 2..24 }

        val payee = shop ?: hostName

        val occurredAt = findValue(texts, timeLabels)
            ?.let { Dates.parseBillTime(it) }
            ?: now

        val tradeNo = findValue(texts, tradeNoLabels)
            ?.filter { it.isDigit() }
            ?.takeIf { it.length >= 10 }

        Log.d("A11y", "内嵌收银台 $hostName: $payee $amount")

        return Captured(
            amountCents = amount,
            wallet = wallet,
            payee = payee,
            source = Source.ACCESSIBILITY,
            occurredAt = occurredAt,
            account = "",
            raw = flat.take(300),
            externalId = tradeNo,
            kindHint = kind,
            // 平台名直接给分类用：「淘宝」会映射到购物，不用再猜
            platformCategory = hostName
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
        // 「原价 12 / 优惠 5 / 实付 7」这种页面，优惠那行本身也是一个金额。
        // 扫 ¥ 符号时按页面顺序走，很容易先抓到优惠的那个 5，记成一笔根本不存在的支出。
        // 凡是带折扣语义的行，一律不进金额候选 —— 宁可这一步找不到金额，
        // 也不能把「省了多少钱」记成「花了多少钱」。
        val discountHint = listOf(
            "优惠", "立减", "减免", "折扣", "满减", "抵扣", "红包", "券", "会员价", "活动价"
        )
        // 一行里同时写着「实付 7 元，已省 5 元」的，不能整行跳过 —— 那样会漏记一整笔。
        // 只有「纯粹在讲优惠」的行才算折扣行。
        val realHint = listOf(
            "实付", "实际支付", "实际付款", "实际扣款", "付款金额", "支付金额", "已支付", "支付成功"
        )
        fun isDiscountLine(t: String): Boolean =
            discountHint.any { t.contains(it) } && realHint.none { t.contains(it) }
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
            if (isDiscountLine(t)) continue
            val m = amountWithSymbol.find(t)
            if (m != null) {
                Money.parseToCents(m.groupValues[1])?.let { if (it > 0) return it }
            }
        }
        for (t in texts) {
            if (isDiscountLine(t)) continue
            val m = amountSigned.find(t)
            if (m != null) {
                Money.parseToCents(m.groupValues[1])?.let { if (it > 0) return it }
            }
        }
        for (t in texts) {
            if (isDiscountLine(t)) continue
            val m = amountPlain.find(t)
            if (m != null) {
                Money.parseToCents(m.groupValues[1])?.let { if (it > 0) return it }
            }
        }
        // 最后兜一层：页面上单独一行、只写了「20.00」的，基本就是这笔的金额
        for (t in texts) {
            if (isDiscountLine(t)) continue
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
    /**
     * 转账页面上「对方是谁」。微信写「待李梦达确认收款」，支付宝写「转账给XXX」。
     * 抓不到就返回 null，退回正常的商户名逻辑。
     */
    private fun transferPeer(texts: List<String>): String? {
        for (t in texts) {
            val line = t.trim()
            if (line.length < 4) continue
            for (re in transferPeerPatterns) {
                val m = re.find(line) ?: continue
                val name = m.groupValues[1].trim()
                if (name.length in 1..12 && name.none { it.isDigit() }) return name
            }
        }
        return null
    }

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
