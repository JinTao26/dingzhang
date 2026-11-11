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
 * 认四种：.zip（里面套 csv / txt / xlsx 都行）、.csv、.txt、.xlsx。
 * 微信「用于个人对账」给的是 Excel，支付宝多半是 GBK 的 csv，两种都要吃下来。
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
            // 以前这里一律报「压缩包里没找到账单文件」，用户根本不知道卡在哪。
            // 分三种情况说清楚，每种给一句能自己动手的话。
            val looked = BillCsv.decode(bytes).take(600)
            val msg = when {
                looked.contains("<table", true) || looked.contains("<tr", true) ->
                    "这个文件其实是网页表格（HTML），不少国内系统的「导出 Excel」都是这么做的。" +
                        "用电脑上的 Excel 打开，另存成 .csv 或真正的 .xlsx 再导进来就行"
                BillCsv.isZip(bytes) -> {
                    val brief = zipBrief(bytes)
                    if (brief.contains("xl/") || brief.contains("sheet")) {
                        "这个 Excel 能打开，但里面的表格读出来是空的。方便的话把这份原文件发给作者，照着补规则最快"
                    } else {
                        "这个压缩包里是「$brief」，没有 .csv 或 .xlsx。解压出来选里面的账单文件"
                    }
                }
                else ->
                    "读不出表格内容。可能不是标准的 Excel/CSV —— 用 Excel 打开另存一次再试"
            }
            val header = peekHeader(bytes)
            val msg2 = if (header != null) "$msg。\n读到的表头是：$header" else msg
            return@withContext ImportResult(fileName, Wallet.UNKNOWN, 0, 0, 0, 0, message = msg2)
        }
        val kind = BillParsers.detect(text)
        if (kind == BillKind.UNKNOWN) {
            return@withContext ImportResult(fileName, Wallet.UNKNOWN, 0, 0, 0, 0,
                message = buildString {
                    append("表头里没同时找到「时间」和「金额」两列，看着不像账单表格。")
                    val header = headerLine(BillCsv.parseRows(text))
                    if (header != null) append("\n读到的表头是：$header")
                })
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

    /**
     * 只为了报错时能把「表头长什么样」告诉用户。
     * 只取列名，不碰金额和商户 —— 用户截个图就能定位，文件不用外传。
     */
    private fun peekHeader(bytes: ByteArray): String? {
        val rows = try {
            XlsxReader.readRowsOrNull(bytes)
        } catch (t: Throwable) {
            Log.w(TAG, "探表头失败", t)
            null
        } ?: return null
        return headerLine(rows)
    }

    private fun headerLine(rows: List<List<String>>): String? {
        for (r in rows.take(30)) {
            val cols = r.map { it.trim() }.filter { it.isNotEmpty() }
            if (cols.size < 3) continue
            if (cols.none { it.contains("时间") || it.contains("日期") }) continue
            return cols.joinToString(" / ").take(140)
        }
        return null
    }

    /** 把压缩包里的条目名列出来几个，报错时带上，用户一看就知道里面是什么。 */
    private fun zipBrief(bytes: ByteArray): String {
        val names = ArrayList<String>(6)
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                var e = zis.nextEntry
                while (e != null && names.size < 6) {
                    names.add(if (e.isDirectory) e.name else e.name)
                    e = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "列压缩包条目失败", e)
        }
        return names.joinToString("、").take(160)
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
