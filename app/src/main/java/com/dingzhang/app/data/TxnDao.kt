package com.dingzhang.app.data

import android.content.ContentValues
import android.database.Cursor
import com.dingzhang.app.core.model.CategoryTotal
import com.dingzhang.app.core.model.Dates
import com.dingzhang.app.core.model.LearnedRule
import com.dingzhang.app.core.model.Source
import com.dingzhang.app.core.model.Txn
import com.dingzhang.app.core.model.TxnKind
import com.dingzhang.app.core.model.TxnStatus
import com.dingzhang.app.core.model.Wallet
import kotlin.math.abs

class TxnDao(private val helper: DbHelper) {

    // ---------------- 写入 ----------------

    fun insert(txn: Txn): Long {
        val cv = ContentValues().apply {
            put("amount_cents", txn.amountCents)
            put("kind", txn.kind.db)
            put("category_id", txn.categoryId)
            put("wallet", txn.wallet.db)
            put("payee", txn.payee)
            put("note", txn.note)
            put("occurred_at", txn.occurredAt)
            put("source", txn.source.db)
            put("status", txn.status.db)
            put("raw_text", txn.rawText)
            put("external_id", txn.externalId)
            put("category_locked", if (txn.categoryLocked) 1 else 0)
            put("account", txn.account)
            put("fingerprint", txn.fingerprint)
        }
        return helper.writableDatabase.insert("txn", null, cv)
    }

    /**
     * 找一笔「大概率是同一笔」的已有记录。
     * 判据：同渠道 + 同金额 + 时间差在 windowMs 内。
     * 之所以不用唯一索引，是因为同一天同金额同商户连买两次是真实存在的，硬性唯一会吞单。
     */
    fun findNearDuplicate(wallet: Wallet, amountCents: Long, at: Long, windowMs: Long): Txn? {
        val cursor = helper.readableDatabase.rawQuery(
            "SELECT * FROM txn WHERE wallet = ? AND amount_cents = ? AND occurred_at BETWEEN ? AND ? " +
                "ORDER BY ABS(occurred_at - ?) ASC LIMIT 1",
            arrayOf(wallet.db, amountCents.toString(), (at - windowMs).toString(), (at + windowMs).toString(), at.toString())
        )
        cursor.use { return if (it.moveToFirst()) it.toTxn() else null }
    }

    /** 账单导入命中通知记录：升级成「已对账」，补上交易单号和更准确的商户名。 */
    fun promoteToMatched(id: Long, externalId: String?, payee: String?, account: String?) {
        val cv = ContentValues().apply {
            put("status", TxnStatus.MATCHED.db)
            if (!externalId.isNullOrBlank()) put("external_id", externalId)
            if (!payee.isNullOrBlank()) put("payee", payee)
            if (!account.isNullOrBlank()) put("account", account)
        }
        helper.writableDatabase.update("txn", cv, "id = ?", arrayOf(id.toString()))
    }

    fun updateCategory(id: Long, categoryId: String, locked: Boolean) {
        val cv = ContentValues().apply {
            put("category_id", categoryId)
            put("category_locked", if (locked) 1 else 0)
            if (categoryId == "income") {
                put("kind", TxnKind.INCOME.db)
            } else {
                // 从收入改成支出分类时，方向也跟着改回来
                val cur = get(id)
                if (cur != null && cur.kind == TxnKind.INCOME) put("kind", TxnKind.EXPENSE.db)
            }
        }
        helper.writableDatabase.update("txn", cv, "id = ?", arrayOf(id.toString()))
    }

    fun setStatus(id: Long, status: TxnStatus) {
        helper.writableDatabase.update(
            "txn", ContentValues().apply { put("status", status.db) }, "id = ?", arrayOf(id.toString())
        )
    }

    fun setKind(id: Long, kind: TxnKind) {
        helper.writableDatabase.update(
            "txn", ContentValues().apply { put("kind", kind.db) }, "id = ?", arrayOf(id.toString())
        )
    }

    fun delete(id: Long) {
        helper.writableDatabase.delete("txn", "id = ?", arrayOf(id.toString()))
    }

    fun clearAll() {
        helper.writableDatabase.delete("txn", null, null)
    }

    // ---------------- 查询 ----------------

    fun get(id: Long): Txn? {
        helper.readableDatabase.rawQuery("SELECT * FROM txn WHERE id = ?", arrayOf(id.toString())).use {
            return if (it.moveToFirst()) it.toTxn() else null
        }
    }

    fun listAll(): List<Txn> {
        helper.readableDatabase.rawQuery("SELECT * FROM txn ORDER BY occurred_at DESC", null).use {
            return it.toList()
        }
    }

    fun recent(limit: Int = 60): List<Txn> {
        helper.readableDatabase.rawQuery(
            "SELECT * FROM txn ORDER BY occurred_at DESC LIMIT ?", arrayOf(limit.toString())
        ).use { return it.toList() }
    }

    fun listBetween(start: Long, end: Long, onlyAuto: Boolean = false): List<Txn> {
        // 注意：额外条件必须拼在第二个占位符之后，拼在中间会把 SQL 拆坏
        val extra = if (onlyAuto) " AND status = 'auto'" else ""
        helper.readableDatabase.rawQuery(
            "SELECT * FROM txn WHERE occurred_at >= ? AND occurred_at < ?$extra ORDER BY occurred_at DESC",
            arrayOf(start.toString(), end.toString())
        ).use { return it.toList() }
    }

    /** 按分类聚合支出，用于统计页环形图和排行榜。退款从同一分类里扣掉，不另算收入。 */
    fun sumByCategory(start: Long, end: Long): List<CategoryTotal> {
        helper.readableDatabase.rawQuery(
            """
            SELECT category_id,
                   SUM(CASE WHEN kind = 'expense' THEN amount_cents ELSE -amount_cents END) AS s,
                   SUM(CASE WHEN kind = 'expense' THEN 1 ELSE 0 END) AS c
            FROM txn
            WHERE occurred_at >= ? AND occurred_at < ? AND kind IN ('expense', 'refund')
            GROUP BY category_id
            HAVING s > 0
            ORDER BY s DESC
            """.trimIndent(),
            arrayOf(start.toString(), end.toString())
        ).use {
            val out = ArrayList<CategoryTotal>()
            while (it.moveToNext()) {
                out.add(CategoryTotal(it.getString(0), it.getLong(1), it.getInt(2)))
            }
            return out
        }
    }

    /** 返回 (支出合计, 收入合计)，单位分。 */
    fun sumByKind(start: Long, end: Long): Pair<Long, Long> {
        helper.readableDatabase.rawQuery(
            """
            SELECT kind, SUM(amount_cents) FROM txn
            WHERE occurred_at >= ? AND occurred_at < ? AND kind != 'transfer'
            GROUP BY kind
            """.trimIndent(),
            arrayOf(start.toString(), end.toString())
        ).use {
            var exp = 0L
            var inc = 0L
            while (it.moveToNext()) {
                when (it.getString(0)) {
                    "expense" -> exp += it.getLong(1)
                    "refund" -> exp -= it.getLong(1)
                    "income" -> inc += it.getLong(1)
                }
            }
            return exp to inc
        }
    }

    /** 每日支出，返回「日 -> 分」。退款当天记成负数，曲线才对得上。 */
    fun dailyExpense(start: Long, end: Long): Map<Int, Long> {
        helper.readableDatabase.rawQuery(
            "SELECT occurred_at, amount_cents, kind FROM txn WHERE occurred_at >= ? AND occurred_at < ? AND kind IN ('expense', 'refund')",
            arrayOf(start.toString(), end.toString())
        ).use {
            val map = HashMap<Int, Long>()
            while (it.moveToNext()) {
                val d = Dates.dayIndex(it.getLong(0))
                val v = if (it.getString(2) == "refund") -it.getLong(1) else it.getLong(1)
                map[d] = (map[d] ?: 0L) + v
            }
            return map
        }
    }

    /**
     * 退款该算到哪个分类？找 90 天内同商户最近的一笔支出，沿用它的分类。
     * 找不到返回 null，调用方会挂到「其他」。
     */
    fun findRefundTarget(payee: String, at: Long): Txn? {
        if (payee.isBlank()) return null
        val from = at - 90L * 24 * 60 * 60 * 1000
        helper.readableDatabase.rawQuery(
            "SELECT * FROM txn WHERE kind = 'expense' AND occurred_at BETWEEN ? AND ? AND payee LIKE ? " +
                "ORDER BY occurred_at DESC LIMIT 1",
            arrayOf(from.toString(), at.toString(), "%$payee%")
        ).use { if (it.moveToFirst()) return it.toTxn() }
        return null
    }

    fun countBySource(start: Long, end: Long): Map<Source, Int> {
        helper.readableDatabase.rawQuery(
            "SELECT source, COUNT(*) FROM txn WHERE occurred_at >= ? AND occurred_at < ? GROUP BY source",
            arrayOf(start.toString(), end.toString())
        ).use {
            val m = HashMap<Source, Int>()
            while (it.moveToNext()) m[Source.from(it.getString(0))] = it.getInt(1)
            return m
        }
    }

    fun countAuto(): Int {
        helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM txn WHERE status = 'auto'", null).use {
            return if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    /** 备份恢复时用：同指纹的记录已经存在就跳过，避免把账翻倍。 */
    fun existsFingerprint(fp: String): Boolean {
        if (fp.isBlank()) return false
        helper.readableDatabase.rawQuery(
            "SELECT 1 FROM txn WHERE fingerprint = ? LIMIT 1", arrayOf(fp)
        ).use { return it.moveToFirst() }
    }

    // ---------------- 学习规则 ----------------

    fun rules(): List<LearnedRule> {
        helper.readableDatabase.rawQuery(
            "SELECT * FROM rule ORDER BY hit_count DESC, updated_at DESC", null
        ).use {
            val out = ArrayList<LearnedRule>()
            while (it.moveToNext()) {
                out.add(
                    LearnedRule(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        pattern = it.getString(it.getColumnIndexOrThrow("pattern")),
                        categoryId = it.getString(it.getColumnIndexOrThrow("category_id")),
                        kind = it.getString(it.getColumnIndexOrThrow("kind"))?.let { k -> TxnKind.from(k) },
                        hitCount = it.getInt(it.getColumnIndexOrThrow("hit_count")),
                        updatedAt = it.getLong(it.getColumnIndexOrThrow("updated_at"))
                    )
                )
            }
            return out
        }
    }

    /** 同样的说法再教一次就覆盖分类，并把命中次数 +1。 */
    fun upsertRule(pattern: String, categoryId: String, kind: TxnKind? = null) {
        val p = pattern.trim()
        if (p.isEmpty() || p.length > 24) return
        val cv = ContentValues().apply {
            put("pattern", p)
            put("category_id", categoryId)
            put("kind", kind?.db)
            put("updated_at", System.currentTimeMillis())
        }
        val existing = helper.writableDatabase.rawQuery("SELECT id, hit_count FROM rule WHERE pattern = ?", arrayOf(p))
        existing.use {
            if (it.moveToFirst()) {
                cv.put("hit_count", it.getInt(1) + 1)
                helper.writableDatabase.update("rule", cv, "id = ?", arrayOf(it.getLong(0).toString()))
                return
            }
        }
        cv.put("hit_count", 1)
        helper.writableDatabase.insert("rule", null, cv)
    }

    fun deleteRule(id: Long) {
        helper.writableDatabase.delete("rule", "id = ?", arrayOf(id.toString()))
    }

    fun clearRules() {
        helper.writableDatabase.delete("rule", null, null)
    }

    // ---------------- 游标转换 ----------------

    private fun Cursor.toTxn(): Txn = Txn(
        id = getLong(getColumnIndexOrThrow("id")),
        amountCents = getLong(getColumnIndexOrThrow("amount_cents")),
        kind = TxnKind.from(getString(getColumnIndexOrThrow("kind"))),
        categoryId = getString(getColumnIndexOrThrow("category_id")),
        wallet = Wallet.from(getString(getColumnIndexOrThrow("wallet"))),
        payee = getString(getColumnIndexOrThrow("payee")),
        note = getString(getColumnIndexOrThrow("note")),
        occurredAt = getLong(getColumnIndexOrThrow("occurred_at")),
        source = Source.from(getString(getColumnIndexOrThrow("source"))),
        status = TxnStatus.from(getString(getColumnIndexOrThrow("status"))),
        rawText = getString(getColumnIndexOrThrow("raw_text")) ?: "",
        externalId = getString(getColumnIndexOrThrow("external_id")),
        categoryLocked = getInt(getColumnIndexOrThrow("category_locked")) == 1,
        account = getString(getColumnIndexOrThrow("account")) ?: "",
        fingerprint = getString(getColumnIndexOrThrow("fingerprint")) ?: ""
    )

    private fun Cursor.toList(): List<Txn> {
        val out = ArrayList<Txn>(count)
        while (moveToNext()) out.add(toTxn())
        return out
    }
}
