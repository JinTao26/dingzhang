package com.dingzhang.app.ingest

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.dingzhang.app.core.model.Dates
import com.dingzhang.app.core.model.Wallet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

data class ImportResult(
    val fileName: String,
    val wallet: Wallet,
    val dataRows: Int,
    val inserted: Int,
    val merged: Int,
    val skipped: Int,
    val earliest: Long? = null,
    val latest: Long? = null,
    val message: String = ""
)

/**
 * 通道 C：官方账单导入。
 *
 * 为什么这是最重要的通道：
 * - 通知和无障碍都可能漏、可能重，账单是官方的全量流水，可以用来「对账」。
 * - 导入时如果发现某笔已经由通知/无障碍记过，不新增记录，而是把那笔升级成「已对账」
 *   并补上交易单号和准确的商户名（见 IngestPipeline 的去重分支）。
 *
 * 支持两种文件：微信/支付宝导出的 .zip（内含 csv），以及直接选的 .csv。
 */
object BillImporter {

    private const val TAG = "BillImporter"
    private const val MAX_BYTES = 20 * 1024 * 1024

    suspend fun import(context: Context, uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val fileName = queryName(context, uri)

        val bytes = try {
            resolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            Log.e(TAG, "读文件失败", e)
            null
        } ?: return@withContext ImportResult(fileName, Wallet.UNKNOWN, 0, 0, 0, 0, message = "读不了这个文件")

        if (bytes.size > MAX_BYTES) {
            return@withContext ImportResult(fileName, Wallet.UNKNOWN, 0, 0, 0, 0, message = "文件超过 20MB，请按月导出")
        }
        if (bytes.size < 32) {
            return@withContext ImportResult(fileName, Wallet.UNKNOWN, 0, 0, 0, 0, message = "文件是空的")
        }

        val text = toBillText(bytes)
        if (text == null) {
            return@withContext ImportResult(fileName, Wallet.UNKNOWN, 0, 0, 0, 0,
                message = "压缩包里没找到账单文件（csv 或 xlsx）")
        }
        val kind = BillParsers.detect(text)
        if (kind == BillKind.UNKNOWN) {
            return@withContext ImportResult(fileName, Wallet.UNKNOWN, 0, 0, 0, 0,
                message = "认不出这是哪家的账单，请选微信或支付宝导出的原始文件")
        }

        val batch = BillParsers.parse(text, kind)
        if (batch.rows.isEmpty()) {
            return@withContext ImportResult(
                fileName,
                walletOf(kind),
                batch.totalDataRows,
                0, 0, batch.skipped,
                message = batch.notes.firstOrNull() ?: "这个文件里没有可导入的交易"
            )
        }

        // 规则取一次，所有行共用
        val rules = IngestPipeline.dao?.rules().orEmpty()

        var inserted = 0
        var merged = 0
        var skipped = batch.skipped
        var earliest = Long.MAX_VALUE
        var latest = Long.MIN_VALUE
        val touched = HashSet<Long>()

        batch.rows.forEachIndexed { index, captured ->
            val outcome = IngestPipeline.submit(captured, rules)
            when {
                outcome.accepted -> {
                    inserted++
                    // 同一条账单里可能有多笔相似交易被合并，只统计一次时间范围
                    outcome.txn?.let { t ->
                        if (touched.add(t.id)) {
                            earliest = minOf(earliest, t.occurredAt)
                            latest = maxOf(latest, t.occurredAt)
                        }
                    }
                }
                outcome.merged -> {
                    merged++
                    skipped++
                }
                else -> skipped++
            }

            if (index % 200 == 0 && index > 0) {
                Log.d(TAG, "已处理 $index/${batch.rows.size}")
            }
        }

        IngestPipeline.settings?.lastBillImportAt = System.currentTimeMillis()
        Log.i(TAG, "导入完成：新增 $inserted，对账 $merged，跳过 $skipped")

        ImportResult(
            fileName = fileName,
            wallet = walletOf(kind),
            dataRows = batch.totalDataRows,
            inserted = inserted,
            merged = merged,
            skipped = skipped,
            earliest = earliest.takeIf { it != Long.MAX_VALUE },
            latest = latest.takeIf { it != Long.MIN_VALUE },
            message = buildSummary(inserted, merged)
        )
    }

    private fun buildSummary(inserted: Int, merged: Int): String = buildString {
        append("新增 $inserted 笔")
        if (merged > 0) append("，$merged 笔与之前的记录对上号")
    }

    private fun walletOf(kind: BillKind): Wallet = when (kind) {
        BillKind.WECHAT -> Wallet.WECHAT
        BillKind.ALIPAY -> Wallet.ALIPAY
        else -> Wallet.UNKNOWN
    }

    /**
     * 把任意一种账单文件变成 CSV 文本。
     * 微信「用于个人对账」给的是 Excel（xlsx），支付宝/微信的另一种导出是 zip 套 csv，两种都要认。
     */
    private fun toBillText(bytes: ByteArray): String? {
        // 1) 本身就是 xlsx
        XlsxReader.readRowsOrNull(bytes)?.let { return XlsxReader.toCsv(it) }

        // 2) zip：里面可能是 csv/txt，也可能是 xlsx
        if (BillCsv.isZip(bytes)) {
            val inner = extractFirstBillEntry(bytes) ?: return null
            XlsxReader.readRowsOrNull(inner)?.let { return XlsxReader.toCsv(it) }
            return BillCsv.decode(inner)
        }

        // 3) 普通 csv / txt
        return BillCsv.decode(bytes)
    }

    private fun extractFirstBillEntry(bytes: ByteArray): ByteArray? {
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name.lowercase()
                    if (!entry.isDirectory &&
                        (name.endsWith(".csv") || name.endsWith(".txt") || name.endsWith(".xlsx"))
                    ) {
                        return zis.readBytes()
                    }
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解压失败", e)
        }
        return null
    }

    private fun queryName(context: Context, uri: Uri): String {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) {
                    return c.getString(idx) ?: "账单文件"
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "取文件名失败", e)
        }
        return uri.lastPathSegment ?: "账单文件"
    }

    /** 给导入页展示「上次导入到现在多久」 */
    fun describeRange(earliest: Long?, latest: Long?): String {
        if (earliest == null || latest == null) return ""
        return "${Dates.dayOf(earliest)} ~ ${Dates.dayOf(latest)}"
    }
}
