package com.dingzhang.app.ingest

import com.dingzhang.app.core.model.Source
import com.dingzhang.app.core.model.Txn
import com.dingzhang.app.core.model.TxnKind
import com.dingzhang.app.core.model.Wallet

/**
 * 各通道抓到的「原始候选交易」。还没分类、还没落库。
 * 四个通道（无障碍 / 通知 / 账单 / 手动）都产出这个结构，后面走同一条管线。
 */
data class Captured(
    val amountCents: Long,
    val wallet: Wallet,
    val payee: String,
    val source: Source,
    val occurredAt: Long = System.currentTimeMillis(),
    val note: String = "",
    val raw: String = "",
    val externalId: String? = null,
    val account: String = "",
    /** 有些通道能直接判定方向（比如账单的「收/支」列），给了就优先用它 */
    val kindHint: TxnKind? = null,
    /** 账单里的交易单号，用于跨通道对账 */
    val billNo: String? = null
)

data class Outcome(
    val accepted: Boolean,
    val reason: String,
    val txn: Txn? = null,
    /** 是否与已有记录合并（账单补全了通知记录） */
    val merged: Boolean = false
)
