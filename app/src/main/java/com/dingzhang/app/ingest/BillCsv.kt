package com.dingzhang.app.ingest

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * 极简 CSV 工具。不引第三方 CSV 库，因为只需要做三件事：
 * 1. 猜编码（支付宝导出的账单是 GBK/GB18030，微信通常是带 BOM 的 UTF-8）
 * 2. 按 RFC4180 拆字段（处理双引号包裹和 "" 转义）
 * 3. 按列名而不是列序号取值（两家 App 的列顺序和列数都会变）
 */
object BillCsv {

    private val GB18030: Charset = Charset.forName("GB18030")

    /**
     * 先按 UTF-8 严格解码（遇到非法字节直接抛错），失败再退回 GB18030。
     * 不能用「宽松解码」——那样 GBK 文件会被静默解成乱码而不报错。
     */
    fun decode(bytes: ByteArray): String {
        val stripped = stripBom(bytes)
        return try {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(stripped)).toString()
        } catch (e: CharacterCodingException) {
            String(stripped, GB18030)
        }
    }

    private fun stripBom(bytes: ByteArray): ByteArray {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return bytes.copyOfRange(3, bytes.size)
        }
        return bytes
    }

    fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()

    fun parseRows(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>(512)
        var fields = ArrayList<String>(12)
        val cur = StringBuilder(64)
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                inQuotes -> {
                    if (ch == '"') {
                        if (i + 1 < text.length && text[i + 1] == '"') {
                            cur.append('"'); i++
                        } else inQuotes = false
                    } else cur.append(ch)
                }
                ch == '"' -> inQuotes = true
                ch == ',' -> { fields.add(cur.toString()); cur.setLength(0) }
                ch == '\r' -> { /* 交给 \n 收尾 */ }
                ch == '\n' -> {
                    fields.add(cur.toString()); cur.setLength(0)
                    if (fields.size > 1 || fields[0].isNotBlank()) rows.add(fields)
                    fields = ArrayList(12)
                }
                else -> cur.append(ch)
            }
            i++
        }
        if (cur.isNotEmpty() || fields.isNotEmpty()) {
            fields.add(cur.toString())
            if (fields.size > 1 || fields[0].isNotBlank()) rows.add(fields)
        }
        return rows
    }

    /** 表头行：同时含「交易时间」和「金额」，两家都认。 */
    fun findHeaderRow(rows: List<List<String>>): Int {
        // 以前只认「交易时间 + 金额」这个微信/支付宝的固定组合，别家的表头换个说法就整张表作废。
        // 现在时间列和金额列各给一组候选，只要两样都在就算表头 —— vivo、银行这类导出也能进来。
        val timeKeys = listOf("交易时间", "交易日期", "记账时间", "发生时间", "入账时间", "时间", "日期")
        val amountKeys = listOf("金额", "发生额", "交易额", "收支金额")
        for (i in rows.indices.take(60)) {
            val flat = rows[i].joinToString(",").replace(" ", "")
            if (flat.isBlank()) continue
            val hasTime = timeKeys.any { flat.contains(it) }
            val hasAmount = amountKeys.any { flat.contains(it) }
            if (hasTime && hasAmount) return i
        }
        return -1
    }

    /**
     * 按列名找索引，key 支持部分匹配（「金额(元)」能命中「金额」）。
     * 多个候选名按顺序尝试。
     */
    fun col(header: List<String>, vararg keys: String): Int {
        for (key in keys) {
            val idx = header.indexOfFirst { h ->
                h.replace(" ", "").replace("\t", "").contains(key)
            }
            if (idx >= 0) return idx
        }
        return -1
    }

    /** 取值：去空白、去引号、去微信账单可能出现的反引号前缀，"/" 视为空。 */
    fun value(row: List<String>, idx: Int): String {
        if (idx < 0 || idx >= row.size) return ""
        return row[idx]
            .trim()
            .trimStart('`')
            .trim()
            .trim('"')
            .trim()
            .let { if (it == "/" || it == "-" || it == "—") "" else it }
    }
}
