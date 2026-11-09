package com.dingzhang.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dingzhang.app.DingZhangApp
import com.dingzhang.app.core.model.Categories
import com.dingzhang.app.core.model.Dates
import com.dingzhang.app.core.model.Money
import com.dingzhang.app.core.model.Txn
import com.dingzhang.app.core.model.TxnKind
import com.dingzhang.app.core.model.TxnStatus
import com.dingzhang.app.ingest.IngestPipeline
import com.dingzhang.app.ui.AppData
import com.dingzhang.app.ui.components.CategoryBadge
import com.dingzhang.app.ui.components.EmptyHint
import com.dingzhang.app.ui.components.MascotFace
import com.dingzhang.app.ui.components.ui
import com.dingzhang.app.ui.shiftMonth
import com.dingzhang.app.ui.theme.Dz
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Calendar

private val expenseIds = listOf(
    "food", "drink", "transport", "shopping", "home", "fun",
    "health", "study", "social", "pet", "digital", "other"
)

@Composable
fun BillsScreen(data: AppData, month: String, onMonthChange: (String) -> Unit) {
    var filter by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<Txn?>(null) }
    var quick by remember { mutableStateOf(0) }      // 0 按月份 / 1 今天 / 2 本周 / 3 本月 / 4 上月
    var query by remember { mutableStateOf("") }
    var rangeTxns by remember { mutableStateOf<List<Txn>>(emptyList()) }
    var hits by remember { mutableStateOf<List<Txn>>(emptyList()) }
    val ctx = LocalContext.current

    // 快捷区间：直接按时间从库里取，跟当前浏览的月份无关
    LaunchedEffect(quick, data.monthLabel, data.monthTxns.size) {
        if (quick == 0) {
            rangeTxns = emptyList()
            return@LaunchedEffect
        }
        val (from, to) = quickRange(quick)
        rangeTxns = withContext(Dispatchers.IO) {
            IngestPipeline.dao?.listBetween(from, to).orEmpty()
        }
    }

    // 搜索：跨全部流水，不受月份限制。停 250ms 再查，别每敲一个字就扫一遍库
    LaunchedEffect(query, data.monthLabel, data.monthTxns.size) {
        val q = query.trim()
        if (q.isEmpty()) {
            hits = emptyList()
            return@LaunchedEffect
        }
        delay(250)
        hits = withContext(Dispatchers.IO) {
            val dao = IngestPipeline.dao ?: return@withContext emptyList<Txn>()
            val text = dao.search(q)
            // 分类中文名也能搜：「餐饮」「工资」这类词不在 payee/note 里
            val catIds = Categories.all.filter { it.name.contains(q) }.map { it.id }.toSet()
            if (catIds.isEmpty()) text
            else (text + dao.listAll().filter { it.categoryId in catIds })
                .distinctBy { it.id }
                .sortedByDescending { it.occurredAt }
        }
    }

    val searching = query.isNotBlank()
    val base = when {
        searching -> hits
        quick == 0 -> data.monthTxns
        else -> rangeTxns
    }

    val filtered = remember(base, filter) {
        when (filter) {
            1 -> base.filter { it.kind == TxnKind.EXPENSE || it.kind == TxnKind.REFUND }
            2 -> base.filter { it.kind == TxnKind.INCOME }
            3 -> base.filter { it.status == TxnStatus.AUTO }
            else -> base
        }
    }
    val grouped = remember(filtered) { filtered.groupBy { Dates.dayOf(it.occurredAt) } }

    Column(Modifier.fillMaxSize()) {
        // 标题 + 搜索 + 时间 / 类型筛选
        Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("明细", style = MaterialTheme.typography.headlineLarge, color = Dz.Ink)
                Spacer(Modifier.weight(1f))
                if (!searching && quick == 0) {
                    Icon(
                        Icons.Rounded.ChevronLeft, "上个月", tint = Dz.InkSoft,
                        modifier = Modifier
                            .size(26.dp)
                            .clickable { onMonthChange(shiftMonth(month, -1)) }
                    )
                    Text(
                        data.monthLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = Dz.Ink,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Icon(
                        Icons.Rounded.ChevronRight, "下个月", tint = Dz.InkSoft,
                        modifier = Modifier
                            .size(26.dp)
                            .clickable { onMonthChange(shiftMonth(month, 1)) }
                    )
                } else {
                    Text(
                        if (searching) "${hits.size} 条结果" else quickLabel(quick),
                        style = MaterialTheme.typography.titleMedium,
                        color = Dz.InkSoft
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // 搜索框
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 13.sp),
                placeholder = {
                    Text("搜商户、备注、分类，跨全部时间", color = Dz.InkFaint, fontSize = 13.sp)
                },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, null, tint = Dz.InkFaint, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Rounded.Close, "清空",
                            tint = Dz.InkFaint,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { query = "" }
                        )
                    }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            )

            Spacer(Modifier.height(8.dp))

            // 快捷时间
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                QuickChip("按月份", quick == 0) { quick = 0 }
                QuickChip("今天", quick == 1) { quick = 1 }
                QuickChip("本周", quick == 2) { quick = 2 }
                QuickChip("本月", quick == 3) { quick = 3 }
                QuickChip("上月", quick == 4) { quick = 4 }
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip("全部", filter == 0) { filter = 0 }
                FilterChip("支出", filter == 1) { filter = 1 }
                FilterChip("收入", filter == 2) { filter = 2 }
                FilterChip("待确认", filter == 3) { filter = 3 }
            }
        }

        if (filtered.isEmpty()) {
            when {
                searching -> EmptyHint("没搜到", "换个关键词试试。金额、卡号也可以搜")
                quick != 0 -> EmptyHint("${quickLabel(quick)}没有记录", "换个时间段，或者去「我的」里导入账单补全历史")
                else -> EmptyHint("这个月这里是空的", "换个筛选条件看看，或者去「我的」里导入账单补全历史")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 18.dp, end = 18.dp, bottom = 96.dp
                )
            ) {
                grouped.forEach { (day, list) ->
                    item(key = "h-$day") {
                        DayHeader(day, list)
                    }
                    items(list, key = { it.id }) { txn ->
                        TxnLine(txn) { selected = txn }
                    }
                }
            }
        }
    }

    selected?.let { txn ->
        TxnDetailDialog(
            txn = txn,
            onDismiss = { selected = null },
            onPickCategory = { catId ->
                val learned = IngestPipeline.correctCategory(txn.id, catId)
                DingZhangApp.bumpDataVersion()
                selected = null
                Toast.makeText(
                    ctx,
                    if (learned != null) "记住了：以后「$learned」算 ${Categories.of(catId).name}" else "已改分类",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDelete = {
                IngestPipeline.dao?.delete(txn.id)
                DingZhangApp.bumpDataVersion()
                selected = null
                Toast.makeText(ctx, "已删除", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun FilterChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (active) Dz.SakuraSoft else Dz.Card)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) Dz.SakuraDeep else Dz.InkFaint
        )
    }
}

@Composable
private fun DayHeader(day: String, list: List<Txn>) {
    val spent = list.filter { it.kind == TxnKind.EXPENSE }.sumOf { it.amountCents } -
        list.filter { it.kind == TxnKind.REFUND }.sumOf { it.amountCents }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            day.substring(5).replace("-", " 月 ") + " 日",
            style = MaterialTheme.typography.titleMedium,
            color = Dz.InkSoft
        )
        Spacer(Modifier.weight(1f))
        if (spent > 0) {
            Text(
                "支出 ¥${Money.yuan(spent)}",
                style = MaterialTheme.typography.bodySmall,
                color = Dz.InkFaint
            )
        }
    }
}

@Composable
private fun TxnLine(txn: Txn, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Dz.Card)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(txn.categoryId, 38.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    txn.payee.ifBlank { "未知商户" },
                    style = MaterialTheme.typography.titleMedium,
                    color = Dz.Ink,
                    maxLines = 1
                )
                Text(
                    buildString {
                        append(Categories.of(txn.categoryId).name)
                        // 退款和普通支出长得像，不给个标记会以为记重了
                        if (txn.kind == TxnKind.REFUND) append(" · 退款")
                        if (txn.account.isNotBlank()) append(" · ${txn.account}")
                        append(" · ${txn.source.label}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Dz.InkFaint,
                    maxLines = 1
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Money.signed(txn.amountCents, txn.kind),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = when (txn.kind) {
                        TxnKind.EXPENSE -> Dz.Ink
                        TxnKind.INCOME -> Dz.Income
                        TxnKind.TRANSFER -> Dz.InkFaint
                        TxnKind.REFUND -> Dz.Income
                    }
                )
                Text(Dates.timeOf(txn.occurredAt), style = MaterialTheme.typography.labelSmall, color = Dz.InkFaint)
            }
        }
        if (txn.note.isNotBlank() && txn.note != txn.payee) {
            Text(
                txn.note,
                style = MaterialTheme.typography.bodySmall,
                color = Dz.InkFaint,
                maxLines = 1,
                modifier = Modifier.padding(start = 50.dp, top = 2.dp)
            )
        }
    }
}

@Composable
private fun TxnDetailDialog(
    txn: Txn,
    onDismiss: () -> Unit,
    onPickCategory: (String) -> Unit,
    onDelete: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.93f)
                .clip(RoundedCornerShape(28.dp))
                .background(Dz.Card)
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MascotFace(40.dp)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(
                        txn.payee.ifBlank { "未知商户" },
                        style = MaterialTheme.typography.titleLarge,
                        color = Dz.Ink,
                        maxLines = 1
                    )
                    Text(
                        "${Dates.relativeLabel(txn.occurredAt)} ${Dates.timeOf(txn.occurredAt)} · ${txn.wallet.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Dz.InkFaint
                    )
                }
                Text(
                    Money.signed(txn.amountCents, txn.kind),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = if (txn.kind == TxnKind.INCOME || txn.kind == TxnKind.REFUND) Dz.Income else Dz.Ink
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniTag(txn.source.label)
                MiniTag(
                    when (txn.status) {
                        TxnStatus.MATCHED -> "已对账"
                        TxnStatus.AUTO -> "待确认"
                        TxnStatus.CONFIRMED -> "已确认"
                        TxnStatus.IGNORED -> "已忽略"
                    }
                )
                if (txn.categoryLocked) MiniTag("分类已锁定")
            }

            if (txn.externalId != null) {
                Text(
                    "单号 ${txn.externalId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Dz.InkFaint,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
            Text("换个分类", style = MaterialTheme.typography.titleMedium, color = Dz.Ink)
            Text(
                "改一次，以后同类说法会沿用你的选择",
                style = MaterialTheme.typography.bodySmall,
                color = Dz.InkFaint,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )

            // 支出 12 个 + 收入 6 个，改分类时都能选到
            val allIds = expenseIds + Categories.incomeAll.map { it.id }
            val allRows = (allIds.size + 3) / 4
            for (row in 0 until allRows) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (col in 0 until 4) {
                        val idx = row * 4 + col
                        if (idx < allIds.size) {
                            val id = allIds[idx]
                            CategoryCell(
                                id = id,
                                active = txn.categoryId == id,
                                modifier = Modifier.weight(1f),
                                onClick = { onPickCategory(id) }
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "删除这笔",
                    style = MaterialTheme.typography.labelLarge,
                    color = Dz.Expense,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onDelete() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "关闭",
                    style = MaterialTheme.typography.labelLarge,
                    color = Dz.InkSoft,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Dz.Cream)
                        .clickable { onDismiss() }
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun CategoryCell(id: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cat = Categories.of(id)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) cat.ui.copy(alpha = 0.18f) else Dz.Cream)
            .clickable { onClick() }
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CategoryBadge(id, 30.dp)
        Text(
            cat.name,
            fontSize = 11.sp,
            color = if (active) Dz.Ink else Dz.InkSoft,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun MiniTag(text: String) {
    Text(
        text,
        fontSize = 10.sp,
        color = Dz.InkSoft,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Dz.Cream)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    )
}

/** 快捷区间的名字。 */
private fun quickLabel(quick: Int): String = when (quick) {
    1 -> "今天"
    2 -> "本周"
    3 -> "本月"
    4 -> "上月"
    else -> "按月份"
}

/**
 * 快捷区间的起止时间（含头不含尾）。
 * 周一算一周的开头，跟国内习惯一致。
 */
private fun quickRange(quick: Int): Pair<Long, Long> {
    val cal = Calendar.getInstance()
    cal.firstDayOfWeek = Calendar.MONDAY
    cal.set(Calendar.MILLISECOND, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    val end = { from: Calendar, days: Int ->
        (from.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, days) }.timeInMillis
    }
    val monthEnd = { from: Calendar ->
        (from.clone() as Calendar).apply { add(Calendar.MONTH, 1) }.timeInMillis
    }
    return when (quick) {
        1 -> cal.timeInMillis to end(cal, 1)
        2 -> {
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            cal.timeInMillis to end(cal, 7)
        }
        3 -> {
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.timeInMillis to monthEnd(cal)
        }
        4 -> {
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.add(Calendar.MONTH, -1)
            cal.timeInMillis to monthEnd(cal)
        }
        else -> cal.timeInMillis to end(cal, 1)
    }
}

@Composable
private fun QuickChip(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (active) Dz.LilacSoft else Dz.Card)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) Dz.Ink else Dz.InkSoft,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}
