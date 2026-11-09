package com.dingzhang.app.ingest

import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream

/**
 * 读 .xlsx。
 *
 * 为什么自己写：微信「用于个人对账」的账单，官方口径就是 **Excel 格式**
 * （pay.weixin.qq.com/doc/user/4021834826）。而 xlsx 本质是个 zip 包着几段 XML，
 * 我们只需要「取第一张表的所有单元格」，为这点需求引 Apache POI（十几 MB）不值得，
 * 也不想让一个记账 App 的依赖表变得不可审计。
 *
 * 结构：xl/sharedStrings.xml 存字符串池，xl/worksheets/sheetN.xml 存单元格。
 * 单元格是 `<c r="A1" t="s"><v>3</v></c>`：r 是坐标，t="s" 表示值是字符串池下标。
 * 空单元格不会出现在 XML 里，所以必须按 r 算出列号再补空，否则整列错位。
 */
object XlsxReader {

    private const val TAG = "Xlsx"
    private val TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** 返回 null 表示这不是 xlsx（交给 CSV 分支处理）。 */
    fun readRowsOrNull(bytes: ByteArray): List<List<String>>? {
        var sharedXml: ByteArray? = null
        var sheetXml: ByteArray? = null

        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name.lowercase()
                    when {
                        name == "xl/sharedstrings.xml" -> sharedXml = zis.readBytes()
                        name.startsWith("xl/worksheets/") && name.endsWith(".xml") && sheetXml == null ->
                            sheetXml = zis.readBytes()
                    }
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "解 xlsx 包失败", e)
            return null
        }

        val sheet = sheetXml ?: return null
        val shared = parseSharedStrings(sharedXml)
        val rows = parseSheet(sheet, shared)
        if (rows.isEmpty()) return null
        fixExcelDates(rows)
        return rows
    }

    /** 单元格表 → CSV 文本，复用现成的 BillCsv.parseRows / BillParsers。 */
    fun toCsv(rows: List<List<String>>): String {
        val sb = StringBuilder(4096)
        for (row in rows) {
            var first = true
            for (cell in row) {
                if (!first) sb.append(',')
                first = false
                val needsQuote = cell.contains(',') || cell.contains('"') || cell.contains('\n') || cell.contains('\r')
                if (needsQuote) {
                    sb.append('"').append(cell.replace("\"", "\"\"")).append('"')
                } else {
                    sb.append(cell)
                }
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    // ---------------------------------------------------------------- 解析

    private fun parseSharedStrings(xml: ByteArray?): List<String> {
        if (xml == null) return emptyList()
        val out = ArrayList<String>(512)
        try {
            val p = Xml.newPullParser()
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            p.setInput(ByteArrayInputStream(xml), null)

            var buf: StringBuilder? = null
            var inSi = false
            var inT = false
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                when (ev) {
                    XmlPullParser.START_TAG -> when (p.name) {
                        "si" -> {
                            buf = StringBuilder()
                            inSi = true
                        }
                        "t" -> if (inSi) inT = true
                    }
                    XmlPullParser.TEXT -> if (inT) buf?.append(p.text ?: "")
                    XmlPullParser.END_TAG -> when (p.name) {
                        "t" -> inT = false
                        "si" -> {
                            out.add(buf?.toString() ?: "")
                            buf = null
                            inSi = false
                        }
                    }
                }
                ev = p.next()
            }
        } catch (e: Exception) {
            Log.w(TAG, "sharedStrings 解析失败", e)
        }
        return out
    }

    private fun parseSheet(xml: ByteArray, shared: List<String>): List<List<String>> {
        val rows = ArrayList<List<String>>(1024)
        try {
            val p = Xml.newPullParser()
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            p.setInput(ByteArrayInputStream(xml), null)

            var cur: MutableList<String>? = null
            var cellBuf: StringBuilder? = null
            var cellType = ""
            var cellRef = ""
            var inV = false
            var inT = false
            var ev = p.eventType

            while (ev != XmlPullParser.END_DOCUMENT) {
                when (ev) {
                    XmlPullParser.START_TAG -> when (p.name) {
                        "row" -> cur = ArrayList(16)
                        "c" -> {
                            cellType = p.getAttributeValue(null, "t") ?: ""
                            cellRef = p.getAttributeValue(null, "r") ?: ""
                            cellBuf = StringBuilder()
                        }
                        "v" -> inV = true
                        "t" -> inT = true
                    }

                    XmlPullParser.TEXT -> if (inV || inT) cellBuf?.append(p.text ?: "")

                    XmlPullParser.END_TAG -> when (p.name) {
                        "v" -> inV = false
                        "t" -> inT = false
                        "c" -> {
                            val raw = cellBuf?.toString() ?: ""
                            val value = if (cellType == "s") {
                                val i = raw.trim().toIntOrNull() ?: -1
                                shared.getOrElse(i) { "" }
                            } else raw
                            val idx = colIndex(cellRef)
                            if (idx >= 0) {
                                val row = cur ?: ArrayList<String>(16).also { cur = it }
                                while (row.size <= idx) row.add("")
                                row[idx] = value
                            }
                            cellBuf = null
                        }
                        "row" -> {
                            cur?.let { rows.add(it) }
                            cur = null
                        }
                    }
                }
                ev = p.next()
            }
        } catch (e: Exception) {
            Log.w(TAG, "sheet 解析失败", e)
        }
        return rows
    }

    /** "A1" → 0，"AB12" → 27。 */
    private fun colIndex(ref: String): Int {
        var n = 0
        var seen = false
        for (ch in ref) {
            if (ch.isLetter()) {
                n = n * 26 + (ch.uppercaseChar() - 'A' + 1)
                seen = true
            } else if (seen) break
        }
        return if (seen) n - 1 else -1
    }

    /**
     * Excel 把日期存成序列号（1900 基准，25569 = 1970-01-01）。
     * 微信账单的「交易时间」在某些导出里就是数字，不转的话整张表都会被当成脏数据丢掉。
     *
     * 只对「表头含时间/日期」的列动手，且值必须落在 1954~2119 年区间，避免误伤金额列。
     */
    private fun fixExcelDates(rows: MutableList<List<String>>) {
        val hIdx = rows.indexOfFirst { r ->
            val joined = r.joinToString("|")
            joined.contains("交易时间") || joined.contains("交易日期") || joined.contains("记账时间")
        }
        if (hIdx < 0 || hIdx >= rows.size) return

        val header = rows[hIdx]
        for (col in header.indices) {
            val h = header[col].replace(" ", "")
            if (!h.contains("时间") && !h.contains("日期")) continue
            for (i in hIdx + 1 until rows.size) {
                val v = rows[i].getOrNull(col)?.trim() ?: continue
                val n = v.toDoubleOrNull() ?: continue
                if (n < 20000.0 || n > 80000.0) continue
                val fixed = rows[i].toMutableList()
                fixed[col] = excelSerialToText(n)
                rows[i] = fixed
            }
        }
    }

    private fun excelSerialToText(serial: Double): String {
        val seconds = (serial * 86400.0).toLong()
        val dt = LocalDateTime.of(1899, 12, 30, 0, 0).plusSeconds(seconds)
        return dt.format(TS)
    }
}
