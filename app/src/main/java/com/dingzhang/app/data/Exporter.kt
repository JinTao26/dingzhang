package com.dingzhang.app.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.dingzhang.app.core.model.LearnedRule
import com.dingzhang.app.core.model.Source
import com.dingzhang.app.core.model.Txn
import com.dingzhang.app.core.model.TxnKind
import com.dingzhang.app.core.model.TxnStatus
import com.dingzhang.app.core.model.Wallet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 数据自主：能导出，也能搬回去。
 *
 * 参考的开源记账项目（Veri Fin、Fin、Ivy Wallet）都把「数据是你的」当卖点，
 * 而侧载 App 一旦换机就丢账，所以导出和恢复必须做，而且格式要能被别的工具读。
 *
 * 两种格式各有用处：
 * - CSV：给 Excel / Numbers / 其他记账 App 迁入用，尽量扁平、列名用中文
 * - JSON：完整备份，连分类规则和学习记录一起带走，恢复时能接上
 */
object Exporter {

    private const val TAG = "Exporter"
    private const val BACKUP_VERSION = "0.4.0"

    // ---------------------------------------------------------------- 导出

    /** 返回写入的行数（不含表头）。 */
    suspend fun writeCsv(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val dao = com.dingzhang.app.ingest.IngestPipeline.dao ?: return@withContext 0
        val all = dao.listAll()
        val sb = StringBuilder(1024)
        sb.append('\uFEFF')  // BOM：Excel 打开中文不乱码
        sb.append("日期,时间,类型,金额(元),分类,商户,备注,渠道,账户,来源,状态,交易单号\n")
        for (t in all) {
            sb.append(com.dingzhang.app.core.model.Dates.dayOf(t.occurredAt)).append(',')
            sb.append(com.dingzhang.app.core.model.Dates.timeOf(t.occurredAt)).append(',')
            sb.append(kindLabel(t.kind)).append(',')
            sb.append(com.dingzhang.app.core.model.Money.yuan(t.amountCents)).append(',')
            sb.append(com.dingzhang.app.core.model.Categories.of(t.categoryId).name).append(',')
            sb.append(esc(t.payee)).append(',')
            sb.append(esc(t.note)).append(',')
            sb.append(t.wallet.label).append(',')
            sb.append(esc(t.account)).append(',')
            sb.append(t.source.label).append(',')
            sb.append(statusLabel(t.status)).append(',')
            sb.append(esc(t.externalId ?: "")).append('\n')
        }
        write(context, uri, sb.toString())
        all.size
    }

    /** 完整备份。返回交易条数。 */
    suspend fun writeBackup(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val dao = com.dingzhang.app.ingest.IngestPipeline.dao ?: return@withContext 0
        val settings = com.dingzhang.app.ingest.IngestPipeline.settings
        val txns = dao.listAll()
        val rules = dao.rules()

        val root = JSONObject().apply {
            put("app", "dingzhang")
            put("backupVersion", BACKUP_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("txnCount", txns.size)
            put("settings", JSONObject().apply {
                put("monthlyBudgetCents", settings?.monthlyBudgetCents ?: 0L)
                put("ignoreBelowCents", settings?.ignoreBelowCents ?: 100L)
            })
            put("txns", JSONArray().apply {
                txns.forEach { t ->
                    put(JSONObject().apply {
                        put("amountCents", t.amountCents)
                        put("kind", t.kind.db)
                        put("categoryId", t.categoryId)
                        put("wallet", t.wallet.db)
                        put("payee", t.payee)
                        put("note", t.note)
                        put("occurredAt", t.occurredAt)
                        put("source", t.source.db)
                        put("status", t.status.db)
                        put("rawText", t.rawText)
                        put("externalId", t.externalId ?: "")
                        put("categoryLocked", t.categoryLocked)
                        put("account", t.account)
                        put("fingerprint", t.fingerprint)
                    })
                }
            })
            put("rules", JSONArray().apply {
                rules.forEach { r ->
                    put(JSONObject().apply {
                        put("pattern", r.pattern)
                        put("categoryId", r.categoryId)
                        put("kind", r.kind?.db ?: "")
                        put("hitCount", r.hitCount)
                        put("updatedAt", r.updatedAt)
                    })
                }
            })
        }
        write(context, uri, root.toString(2))
        txns.size
    }

    // ---------------------------------------------------------------- 恢复

    data class RestoreResult(val restored: Int, val skipped: Int, val rules: Int, val message: String)

    /**
     * 从 JSON 备份恢复。已存在的记录（同指纹）会被跳过，不会把账翻倍。
     */
    suspend fun restore(context: Context, uri: Uri): RestoreResult = withContext(Dispatchers.IO) {
        val text = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        } catch (e: Exception) {
            Log.e(TAG, "读备份失败", e)
            null
        } ?: return@withContext RestoreResult(0, 0, 0, "读不了这个文件")

        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            return@withContext RestoreResult(0, 0, 0, "这不是叮账的备份文件")
        }

        if (root.optString("app") != "dingzhang") {
            return@withContext RestoreResult(0, 0, 0, "这个文件不是叮账导出的")
        }

        val dao = com.dingzhang.app.ingest.IngestPipeline.dao
            ?: return@withContext RestoreResult(0, 0, 0, "数据库还没准备好")

        var restored = 0
        var skipped = 0

        root.optJSONArray("txns")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val fp = o.optString("fingerprint")
                if (fp.isNotBlank() && dao.existsFingerprint(fp)) {
                    skipped++
                    continue
                }
                val txn = Txn(
                    amountCents = o.optLong("amountCents"),
                    kind = TxnKind.from(o.optString("kind")),
                    categoryId = o.optString("categoryId", "other"),
                    wallet = Wallet.from(o.optString("wallet")),
                    payee = o.optString("payee"),
                    note = o.optString("note"),
                    occurredAt = o.optLong("occurredAt"),
                    source = Source.from(o.optString("source")),
                    status = TxnStatus.from(o.optString("status")),
                    rawText = o.optString("rawText"),
                    externalId = o.optString("externalId").ifBlank { null },
                    categoryLocked = o.optBoolean("categoryLocked"),
                    account = o.optString("account"),
                    fingerprint = fp
                )
                if (txn.amountCents <= 0 || txn.occurredAt <= 0) {
                    skipped++
                    continue
                }
                if (dao.insert(txn) > 0) restored++ else skipped++
            }
        }

        var ruleCount = 0
        root.optJSONArray("rules")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val p = o.optString("pattern")
                val c = o.optString("categoryId")
                if (p.isBlank() || c.isBlank()) continue
                dao.upsertRule(p, c, o.optString("kind").takeIf { it.isNotBlank() }?.let { TxnKind.from(it) })
                ruleCount++
            }
        }

        Log.i(TAG, "恢复完成：新增 $restored，跳过 $skipped，规则 $ruleCount")
        RestoreResult(
            restored, skipped, ruleCount,
            "恢复 $restored 笔${if (skipped > 0) "，跳过 $skipped 笔重复" else ""}"
        )
    }

    // ---------------------------------------------------------------- 工具

    private fun write(context: Context, uri: Uri, content: String) {
        context.contentResolver.openOutputStream(uri)?.use {
            it.write(content.toByteArray(Charsets.UTF_8))
            it.flush()
        }
    }

    private fun kindLabel(k: TxnKind): String = when (k) {
        TxnKind.EXPENSE -> "支出"
        TxnKind.INCOME -> "收入"
        TxnKind.TRANSFER -> "不计收支"
    }

    private fun statusLabel(s: TxnStatus): String = when (s) {
        TxnStatus.AUTO -> "待确认"
        TxnStatus.CONFIRMED -> "已确认"
        TxnStatus.MATCHED -> "已对账"
        TxnStatus.IGNORED -> "已忽略"
    }

    /** CSV 转义：逗号、引号、换行都必须包引号 */
    private fun esc(v: String): String {
        val s = v.replace("\"", "\"\"")
        return if (s.contains(',') || s.contains('"') || s.contains('\n')) "\"$s\"" else s
    }

    fun defaultCsvName(): String = "叮账-明细-${com.dingzhang.app.core.model.Dates.dayOf(System.currentTimeMillis())}.csv"

    fun defaultBackupName(): String = "叮账-备份-${com.dingzhang.app.core.model.Dates.dayOf(System.currentTimeMillis())}.json"
}
