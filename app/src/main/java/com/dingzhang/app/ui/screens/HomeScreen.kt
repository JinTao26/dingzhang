package com.dingzhang.app.ui.screens

import android.widget.Toast

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ErrorOutline
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dingzhang.app.core.model.Categories
import com.dingzhang.app.core.model.Dates
import com.dingzhang.app.core.model.Money
import com.dingzhang.app.core.model.Source
import com.dingzhang.app.core.model.Txn
import com.dingzhang.app.core.model.TxnKind
import com.dingzhang.app.DingZhangApp
import com.dingzhang.app.data.RecurringDetector
import com.dingzhang.app.ingest.IngestPipeline
import com.dingzhang.app.ui.AppData
import com.dingzhang.app.ui.components.CategoryBadge
import com.dingzhang.app.ui.components.DzCard
import com.dingzhang.app.ui.components.EmptyHint
import com.dingzhang.app.ui.components.EnergyBar
import com.dingzhang.app.ui.components.MascotFace
import com.dingzhang.app.ui.components.MonthPickerDialog
import com.dingzhang.app.ui.components.SpeechBubble
import com.dingzhang.app.ui.shiftMonth
import com.dingzhang.app.ui.theme.Dz
import java.util.Calendar

@Composable
fun HomeScreen(
    data: AppData,
    month: String,
    onMonthChange: (String) -> Unit,
    onGoTab: (Int) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    var pickTxn by remember { mutableStateOf<Txn?>(null) }
    val ctx = LocalContext.current
    if (showPicker) {
        MonthPickerDialog(
            month = month,
            onDismiss = { showPicker = false },
            onPick = { onMonthChange(it) }
        )
    }

    // 首页「最近」点开的效果和明细页完全一样：改分类、改商户名、删除
    pickTxn?.let { txn ->
        TxnDetailDialog(
            txn = txn,
            onDismiss = { pickTxn = null },
            onPickCategory = { catId ->
                val r = IngestPipeline.correctCategory(txn.id, catId)
                DingZhangApp.bumpDataVersion()
                pickTxn = null
                val name = Categories.of(catId).name
                // 只报「改成了什么 + 连带改了几笔」，不念商户名和学到的规则
                val msg = buildString {
                    append("已改成 $name")
                    if (r.updated > 0) append("，同商户 $r.updated 笔一起改")
                }
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
            },
            onRenamePayee = { newName ->
                IngestPipeline.dao?.updatePayee(txn.id, newName)
                DingZhangApp.bumpDataVersion()
                pickTxn = txn.copy(payee = newName)
            },
            onDelete = {
                IngestPipeline.dao?.delete(txn.id)
                DingZhangApp.bumpDataVersion()
                pickTxn = null
                Toast.makeText(ctx, "已删除", Toast.LENGTH_SHORT).show()
            },
            onSetKind = { k ->
                IngestPipeline.dao?.setKind(txn.id, k)
                DingZhangApp.bumpDataVersion()
                pickTxn = txn.copy(kind = k)
                Toast.makeText(ctx, kindToast(k), Toast.LENGTH_SHORT).show()
            }
        )
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 90.dp)
    ) {
        // ---------- 问候 ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    greeting(),
                    style = MaterialTheme.typography.headlineLarge,
                    color = Dz.Ink
                )
                Text(
                    "${Dates.dayOf(System.currentTimeMillis())} ${Dates.weekdayOf(System.currentTimeMillis())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Dz.InkFaint,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Dz.Card)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) { MascotFace(46.dp) }
        }

        Spacer(Modifier.height(16.dp))

        // ---------- 月份切换 ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Dz.Card)
                    .clickable { onMonthChange(shiftMonth(month, -1)) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.ChevronLeft, "上个月", tint = Dz.InkSoft, modifier = Modifier.size(20.dp))
            }
            Text(
                data.monthLabel,
                style = MaterialTheme.typography.titleMedium,
                color = Dz.Ink,
                modifier = Modifier
                    // 点标题直接跳年月，不用一个月一个月翻
                    .clickable { showPicker = true }
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Dz.Card)
                    .clickable { onMonthChange(shiftMonth(month, 1)) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.ChevronRight, "下个月", tint = Dz.InkSoft, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---------- 主卡：本月支出 ----------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(Brush.linearGradient(listOf(Dz.Sakura, Dz.Lilac)))
        ) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(
                    Color.White.copy(alpha = 0.12f), radius = size.width * 0.34f,
                    center = Offset(size.width * 0.95f, -size.height * 0.10f)
                )
                drawCircle(
                    Color.White.copy(alpha = 0.09f), radius = size.width * 0.20f,
                    center = Offset(size.width * 0.72f, size.height * 0.30f)
                )
                drawCircle(
                    Color.White.copy(alpha = 0.10f), radius = size.width * 0.12f,
                    center = Offset(size.width * 0.08f, size.height * 1.02f)
                )
            }
            Column(Modifier.padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "本月支出",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(Modifier.weight(1f))
                    val budget = data.budgetCents
                    if (budget > 0) {
                        val left = budget - data.monthExpense
                        Text(
                            if (left >= 0) "预算还剩 ¥${Money.short(left)}" else "超了 ¥${Money.short(-left)}",
                            color = Color.White.copy(alpha = 0.92f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "¥",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 7.dp, end = 3.dp)
                    )
                    Text(
                        Money.short(data.monthExpense),
                        color = Color.White,
                        style = MaterialTheme.typography.displayLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(14.dp))
                val ratio = if (data.budgetCents > 0) {
                    data.monthExpense.toFloat() / data.budgetCents.toFloat()
                } else 0f
                EnergyBar(ratio, Modifier.fillMaxWidth(), 8.dp)
                Spacer(Modifier.height(10.dp))
                Text(
                    "共 ${data.monthCount} 笔 · 今天 ¥${Money.short(data.todayExpense)}",
                    color = Color.White.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.bodySmall
                )

                // 年预算放在这张卡最下面：看这个月的同时也能知道今年用了多少
                if (data.yearBudgetCents > 0) {
                    Spacer(Modifier.height(10.dp))
                    val yearRatio = data.yearExpense.toFloat() / data.yearBudgetCents.toFloat()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "年预算 ¥${Money.short(data.yearBudgetCents)} · 已用 ${(yearRatio * 100).coerceAtLeast(0f).toInt()}%",
                            color = Color.White.copy(alpha = 0.82f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.weight(1f))
                        val yLeft = data.yearBudgetCents - data.yearExpense
                        Text(
                            if (yLeft >= 0) "还剩 ¥${Money.short(yLeft)}" else "超了 ¥${Money.short(-yLeft)}",
                            color = Color.White.copy(alpha = 0.92f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    EnergyBar(yearRatio, Modifier.fillMaxWidth(), 5.dp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---------- 三个小指标 ----------
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricChip("今日支出", "¥" + Money.short(data.todayExpense), Dz.Sakura, Modifier.weight(1f))
            MetricChip("本月收入", "¥" + Money.short(data.monthIncome), Dz.Mint, Modifier.weight(1f))
            MetricChip("记账笔数", "${data.monthCount}", Dz.Lilac, Modifier.weight(1f))
        }

        // ---------- 待确认 ----------
        if (data.pendingCount > 0) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Dz.WarnSoft)
                    .clickable { onGoTab(1) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = Dz.Warn, modifier = Modifier.size(20.dp))
                Text(
                    "有 ${data.pendingCount} 笔还没确认分类，点我看看",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Dz.Ink,
                    modifier = Modifier.padding(start = 8.dp).weight(1f)
                )
                Icon(Icons.Rounded.ChevronRight, null, tint = Dz.Warn, modifier = Modifier.size(18.dp))
            }
        }

        // ---------- 精灵说一句话 ----------
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SpeechBubble(text = hintText(data), modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            MascotFace(44.dp)
        }

        // ---------- 在自动扣的钱 ----------
        if (data.recurring.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            DzCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("在自动扣的钱", style = MaterialTheme.typography.titleLarge, color = Dz.Ink)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "约 ¥${Money.short(RecurringDetector.monthlyTotal(data.recurring))}/月",
                        style = MaterialTheme.typography.labelMedium,
                        color = Dz.SakuraDeep
                    )
                }
                Text(
                    "同商户同金额反复出现，我猜是订阅。退订之后这里会自己消失",
                    style = MaterialTheme.typography.bodySmall,
                    color = Dz.InkFaint,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                )
                data.recurring.take(4).forEach { r ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CategoryBadge(r.categoryId, 30.dp)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(
                                r.payee,
                                style = MaterialTheme.typography.titleMedium,
                                color = Dz.Ink,
                                maxLines = 1
                            )
                            Text(
                                "${r.cycleLabel} · 已扣 ${r.count} 次",
                                style = MaterialTheme.typography.labelSmall,
                                color = Dz.InkFaint
                            )
                        }
                        Text(
                            "¥${Money.short(r.amountCents)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = Dz.Ink
                        )
                    }
                }
                if (data.recurring.size > 4) {
                    Text(
                        "还有 ${data.recurring.size - 4} 项",
                        style = MaterialTheme.typography.labelSmall,
                        color = Dz.InkFaint,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // ---------- 最近流水 ----------
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("最近", style = MaterialTheme.typography.titleLarge, color = Dz.Ink)
            Spacer(Modifier.weight(1f))
            Text(
                "全部明细",
                style = MaterialTheme.typography.labelMedium,
                color = Dz.SakuraDeep,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onGoTab(1) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(6.dp))

        if (data.recent.isEmpty()) {
            EmptyHint(
                "还没有账",
                "在微信或支付宝付完款，我会自动记下来。也可以点右下角的 + 手记一笔。"
            )
        } else {
            DzCard(padding = 6.dp) {
                data.recent.take(9).forEach { txn ->
                    TxnRow(txn, onClick = { pickTxn = txn })
                }
            }
        }
    }
}

@Composable
private fun MetricChip(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Dz.Card)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Dz.InkFaint)
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun TxnRow(txn: Txn, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val cat = Categories.of(txn.categoryId)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryBadge(txn.categoryId, 40.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    txn.payee.ifBlank { "未知商户" },
                    style = MaterialTheme.typography.titleMedium,
                    color = Dz.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (txn.source != Source.MANUAL) {
                    Text(
                        "自动",
                        fontSize = 9.sp,
                        color = Dz.Mint.copy(alpha = 0.95f),
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Dz.MintSoft)
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cat.name, style = MaterialTheme.typography.bodySmall, color = Dz.InkFaint)
                Text(
                    " · ${Dates.timeOf(txn.occurredAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Dz.InkFaint
                )
                if (txn.status == com.dingzhang.app.core.model.TxnStatus.AUTO) {
                    Text(
                        "待确认",
                        fontSize = 9.sp,
                        color = Dz.Warn,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }
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
    }
}

private fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (h) {
        in 5..10 -> "早上好呀"
        in 11..13 -> "中午好"
        in 14..18 -> "下午好"
        else -> "晚上好"
    }
}

private fun hintText(data: AppData): String = when {
    data.pendingCount > 0 -> "有 ${data.pendingCount} 笔我拿不准分类，你点一下告诉我吧"
    data.monthCount == 0 -> "先付一笔试试？我会自动记下来并分好类"
    data.monthExpense > data.budgetCents && data.budgetCents > 0 -> "这个月花超了，要不要看看哪一类花得最凶"
    else -> "本月已经记了 ${data.monthCount} 笔，继续加油"
}
