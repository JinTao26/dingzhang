package com.dingzhang.app.ingest

import android.content.Context
import android.util.Log
import com.dingzhang.app.classify.CategoryEngine
import com.dingzhang.app.classify.RuleLearning
import com.dingzhang.app.core.model.Categories
import com.dingzhang.app.core.model.Source
import com.dingzhang.app.core.model.Txn
import com.dingzhang.app.core.model.TxnKind
import com.dingzhang.app.core.model.TxnStatus
import com.dingzhang.app.core.model.Wallet
import com.dingzhang.app.data.DbHelper
import com.dingzhang.app.data.Settings
import com.dingzhang.app.data.TxnDao
import kotlin.math.abs

/**
 * 所有通道的收口。去重、分类、落库只在这里发生一次，
 * 通道本身只负责「把屏幕上/通知里/文件里的信息变成 Captured」。
 */
object IngestPipeline {

    private const val TAG = "Ingest"

    /** 超过这个时间窗就不认为是同一笔，避免把「一天买两次同一瓶水」吞掉。 */
    private const val DUP_WINDOW_MS = 20 * 60 * 1000L
    /** 窗口内时间又很接近的，直接判为重复。 */
    private const val DUP_STRICT_MS = 6 * 60 * 1000L

    @Volatile private var daoRef: TxnDao? = null
    @Volatile private var settingsRef: Settings? = null
    @Volatile private var ready = false

    val dao: TxnDao? get() = daoRef
    val settings: Settings? get() = settingsRef
    val isReady: Boolean get() = ready

    fun init(context: Context) {
        if (ready) return
        synchronized(this) {
            if (ready) return
            val helper = DbHelper(context.applicationContext)
            daoRef = TxnDao(helper)
            settingsRef = Settings(context.applicationContext)
            ready = true
        }
    }

    /** 这些名字等于「不知道是谁」。后到的通道若带真商户名，应该覆盖掉它们。 */
    private val genericPayees = setOf(
        "", "未知", "未知商户", "微信支付", "支付宝", "云闪付", "银行", "银行卡", "储蓄卡"
    )

    private fun isGenericPayee(s: String): Boolean = s.trim() in genericPayees

    // ------------------------------------------------------------------ 公共入口

    /**
     * 所有通道的统一入口。
     * @param cachedRules 批量导入时把规则一次性取出来传进来，避免每行都查一次库。
     */
    @Synchronized
    fun submit(c: Captured, cachedRules: List<com.dingzhang.app.core.model.LearnedRule>? = null): Outcome {
        val dao = daoRef ?: return Outcome(false, "管道未初始化")
        val settings = settingsRef ?: return Outcome(false, "管道未初始化")

        if (c.amountCents <= 0) return Outcome(false, "金额无效")
        if (c.amountCents < settings.ignoreBelowCents && c.source != Source.BILL_IMPORT) {
            return Outcome(false, "低于忽略门槛（${settings.ignoreBelowCents / 100.0} 元）")
        }

        val payee = cleanPayee(c.payee)
        if (payee.isNotEmpty() && settings.isIgnored(payee)) {
            return Outcome(false, "「$payee」在忽略名单里")
        }

        // ---- 跨通道去重 / 对账 ----
        val dup = dao.findNearDuplicate(c.wallet, c.amountCents, c.occurredAt, DUP_WINDOW_MS)
        if (dup != null) {
            val incomingIsBill = c.source == Source.BILL_IMPORT
            val existingIsBill = dup.source == Source.BILL_IMPORT
            val gap = abs(dup.occurredAt - c.occurredAt)
            when {
                // 官方账单是权威数据，回头把通知/无障碍记的那笔补全成「已对账」
                incomingIsBill && !existingIsBill -> {
                    dao.promoteToMatched(dup.id, c.billNo ?: c.externalId, payee, c.account)
                    Log.d(TAG, "账单补全已有记录 #${dup.id} ${dup.payee} ${dup.amountCents / 100.0}")
                    return Outcome(false, "已与账单对上号：${dup.payee}", dup, merged = true)
                }
                !incomingIsBill && existingIsBill -> {
                    return Outcome(false, "已有更权威的账单记录", dup)
                }
                gap <= DUP_STRICT_MS -> {
                    // 同一笔钱被两条通道各记了一次。先记的那条常常只有泛称
                    // （银行短信里压根没有商户名），这次来了真名就顺手补上，
                    // 不必等用户去导账单
                    if (isGenericPayee(dup.payee) && !isGenericPayee(payee)) {
                        dao.updatePayee(dup.id, payee, c.account)
                        Log.d(TAG, "补商户名 #${dup.id} ${dup.payee} -> $payee")
                        return Outcome(
                            false, "已补上商户名：$payee",
                            dao.get(dup.id) ?: dup, merged = true
                        )
                    }
                    Log.d(TAG, "疑似重复，跳过 ${c.payee} ${c.amountCents / 100.0}")
                    return Outcome(false, "疑似重复，已跳过", dup)
                }
                // 时间差超过严格窗口但仍在 20 分钟内 —— 允许当成两笔（如连买两次）
            }
        }

        // ---- 分类 ----
        val rules = cachedRules ?: dao.rules()
        val cls = CategoryEngine.classify(payee, c.note, c.raw, rules)
        val kind = c.kindHint ?: cls.kind
        val categoryId = when (kind) {
            TxnKind.TRANSFER -> "other"
            // 退款挂回原来那笔支出的分类：统计时才从「餐饮」里扣，而不是跑到收入里去
            TxnKind.REFUND -> dao.findRefundTarget(payee, c.occurredAt)?.categoryId ?: "other"
            else -> cls.categoryId
        }

        val status = when {
            c.source == Source.BILL_IMPORT -> TxnStatus.CONFIRMED
            settings.autoCommit -> TxnStatus.CONFIRMED
            else -> TxnStatus.AUTO
        }

        val txn = Txn(
            amountCents = c.amountCents,
            kind = kind,
            categoryId = categoryId,
            wallet = c.wallet,
            payee = payee.ifBlank { "未知商户" },
            note = c.note.trim(),
            occurredAt = c.occurredAt,
            source = c.source,
            status = status,
            rawText = c.raw.take(300),
            externalId = c.billNo ?: c.externalId,
            account = c.account,
            fingerprint = fingerprintOf(c.wallet, c.amountCents, c.payee)
        )

        val id = dao.insert(txn)
        return if (id > 0) {
            Log.i(TAG, "记账成功 #$id ${txn.payee} ${txn.amountCents / 100.0} -> ${cls.categoryId} (${cls.matched ?: "未命中"})")
            Outcome(true, cls.matched?.let { "命中「$it」" } ?: "未命中词典，先归到其他", txn.copy(id = id))
        } else {
            Outcome(false, "写入失败")
        }
    }

    /** 手动记一笔：直接确认、锁定分类，并且顺手把「这个说法」学下来。 */
    @Synchronized
    fun manual(
        amountCents: Long,
        kind: TxnKind,
        categoryId: String,
        payee: String,
        note: String,
        wallet: Wallet = Wallet.UNKNOWN,
        occurredAt: Long = System.currentTimeMillis(),
        learn: Boolean = true
    ): Txn? {
        val dao = daoRef ?: return null
        val txn = Txn(
            amountCents = amountCents,
            kind = kind,
            categoryId = categoryId,
            wallet = wallet,
            payee = cleanPayee(payee).ifBlank { note.take(12) },
            note = note,
            occurredAt = occurredAt,
            source = Source.MANUAL,
            status = TxnStatus.CONFIRMED,
            categoryLocked = true,
            fingerprint = fingerprintOf(wallet, amountCents, payee)
        )
        val id = dao.insert(txn)
        if (id <= 0) return null
        if (learn && kind != TxnKind.TRANSFER) {
            RuleLearning.best(txn.payee, note)?.let { dao.upsertRule(it, categoryId, kind) }
        }
        return txn.copy(id = id)
    }

    /** 用户改了某笔的分类：锁定它，并把「这个说法 -> 这个分类」记成规则。 */
    @Synchronized
    fun correctCategory(txnId: Long, newCategoryId: String): String? {
        val dao = daoRef ?: return null
        val txn = dao.get(txnId) ?: return null
        if (txn.categoryLocked && txn.categoryId == newCategoryId) return null
        dao.updateCategory(txnId, newCategoryId, locked = true)

        val kind = if (Categories.isIncome(newCategoryId)) TxnKind.INCOME else TxnKind.EXPENSE
        val learned = RuleLearning.best(txn.payee, txn.note)
        if (learned != null) {
            dao.upsertRule(learned, newCategoryId, kind)
            Log.d(TAG, "学会: 「$learned」 -> $newCategoryId")
        }
        return learned
    }

    fun confirm(txnId: Long) {
        daoRef?.setStatus(txnId, TxnStatus.CONFIRMED)
    }

    // ------------------------------------------------------------------ 工具

    private val prefixNoise = Regex("^(微信支付|支付宝|付款给|收款方|商户|交易对方)\\s*[\\-:：]?\\s*")
    private val suffixNoise = Regex("\\s*[\\-—]?\\s*(商户号|订单号|交易单号)[:：]?\\s*\\d+.*$")

    /**
     * 把各通道拿到的商户名洗成可读、可复用的形式。
     * 「微信支付-小杨生煎」-> 「小杨生煎」；「小杨生煎（北仑店）」原样保留（门店信息有用）。
     */
    fun cleanPayee(raw: String): String {
        var s = raw.trim()
            .replace("\u00a0", " ")
            .replace("\n", " ")
            .trim()
        s = prefixNoise.replace(s, "")
        s = suffixNoise.replace(s, "")
        s = s.trim(' ', '-', '—', '|', '·', ':', '：', ',', '，')
        return s.take(40)
    }

    fun fingerprintOf(wallet: Wallet, amountCents: Long, payee: String): String {
        val minuteBucket = System.currentTimeMillis() / (10 * 60 * 1000L)
        return "${wallet.db}|$amountCents|$minuteBucket|${payee.take(8)}"
    }
}
