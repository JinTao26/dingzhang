package com.dingzhang.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

private val expenseIds = listOf(
    "food", "drink", "transport", "shopping", "home", "fun",
    "health", "study", "social", "pet", "digital", "other"
)

@Composable
fun BillsScreen(data: AppData, month: String, onMonthChange: (String) -> Unit) {
    var filter by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<Txn?>(null) }
    val ctx = LocalContext.current

    val filtered = remember(data, filter) {
        when (filter) {
            1 -> data.monthTxns.filter { it.kind == TxnKind.EXPENSE || it.kind == TxnKind.REFUND }
            2 -> data.monthTxns.filter { it.kind == TxnKind.INCOME }
            3 -> data.monthTxns.filter { it.status == TxnStatus.AUTO }
            else -> data.monthTxns
        }
    }
    val grouped = remember(filtered) { filtered.groupBy { Dates.dayOf(it.occurredAt) } }

    Column(Modifier.fillMaxSize()) {
        // 月份 + 筛选
        Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("明细", style = MaterialTheme.typography.headlineLarge, color = Dz.Ink)
                Spacer(Modifier.weight(1f))
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
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip("全部", filter == 0) { filter = 0 }
                FilterChip("支出", filter == 1) { filter = 1 }
                FilterChip("收入", filter == 2) { filter = 2 }
                FilterChip("待确认", filter == 3) { filter = 3 }
            }
        }

        if (filtered.isEmpty()) {
            EmptyHint("这个月这里是空的", "换个筛选条件看看，或者去「我的」里导入账单补全历史")
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

            val allIds = expenseIds + "income"
            for (row in 0 until 4) {
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
