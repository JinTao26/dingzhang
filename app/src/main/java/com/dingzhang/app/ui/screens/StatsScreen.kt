package com.dingzhang.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.dingzhang.app.DingZhangApp
import com.dingzhang.app.core.model.Categories
import com.dingzhang.app.core.model.CategoryTotal
import com.dingzhang.app.core.model.Dates
import com.dingzhang.app.core.model.Money
import com.dingzhang.app.core.model.Source
import com.dingzhang.app.core.model.Txn
import com.dingzhang.app.core.model.TxnKind
import com.dingzhang.app.ui.AppData
import com.dingzhang.app.ui.components.CategoryBadge
import com.dingzhang.app.ui.components.DzCard
import com.dingzhang.app.ui.components.EmptyHint
import com.dingzhang.app.ui.components.MonthPickerDialog
import com.dingzhang.app.ui.components.ui
import com.dingzhang.app.ui.shiftMonth
import com.dingzhang.app.ingest.IngestPipeline
import com.dingzhang.app.ui.theme.Dz
import kotlin.math.abs

/**
 * 统计页：「月」和「年」两套口径，同一个界面。
 *
 * 月看的是「这个月花在哪」，年看的是「这一年怎么过来的」——
 * 所以趋势图也跟着换：月看每天，年看每个月。
 * 顶部标题可以点，直接跳到任意年月；点某个分类能看到那个分类的明细。
 */
@Composable
fun StatsScreen(data: AppData, month: String, onMonthChange: (String) -> Unit) {
    var yearScope by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var detailCategory by remember { mutableStateOf<String?>(null) }
    var detailTxn by remember { mutableStateOf<Txn?>(null) }
    // 只在这次看的时候过滤，不动库里的数据：单笔 ≥ 这个数的算大额
    var skipBig by remember { mutableStateOf(false) }
    var bigCut by remember { mutableStateOf(20_000L) }
    var editCut by remember { mutableStateOf(false) }
    var cutInput by remember { mutableStateOf("") }
    var showBigList by remember { mutableStateOf(false) }

    val catsBase = if (yearScope) data.yearByCategory else data.byCategory
    val incomeCatsBase = if (yearScope) data.yearIncomeByCategory else data.incomeByCategory
    val txns = if (yearScope) data.yearTxns else data.monthTxns
    val viewTxns = if (skipBig) txns.filter { it.amountCents < bigCut } else txns
    val bigTxns = txns.filter { it.amountCents >= bigCut && it.kind != TxnKind.TRANSFER }

    val cats = if (skipBig) aggCats(viewTxns, false) else catsBase
    // 收入来源可能很多（工资、奖金、报销、红包、理财），单独一块列在下面
    val incomeCats = if (skipBig) aggCats(viewTxns, true) else incomeCatsBase
    val incomeTotal = incomeCats.sumOf { it.cents }
    // 环图与百分比的分母用分类合计，保证环内自洽；
    // 顶部那个「支出」用 SQL 的合计（它包含净额为负、被 HAVING 挡掉的分类）
    val ringTotal = cats.sumOf { it.cents }
    val expense = if (skipBig) netExpense(viewTxns)
    else if (yearScope) data.yearExpense else data.monthExpense
    val income = if (skipBig) viewTxns.filter { it.kind == TxnKind.INCOME }.sumOf { it.amountCents }
    else if (yearScope) data.yearIncome else data.monthIncome
    val count = if (skipBig) viewTxns.count { it.kind != TxnKind.TRANSFER }
    else if (yearScope) data.yearCount else data.monthCount
    // 柱状图也按同一口径重算，避免上下数字对不上
    val dailyMap = if (!skipBig) data.daily else viewTxns
        .filter { it.kind == TxnKind.EXPENSE || it.kind == TxnKind.REFUND }
        .groupBy { Dates.dayOf(it.occurredAt).takeLast(2).toIntOrNull() ?: 0 }
        .filterKeys { it > 0 }
        .mapValues { e -> e.value.sumOf { if (it.kind == TxnKind.REFUND) -it.amountCents else it.amountCents } }
    val monthlyMap = if (!skipBig) data.monthly else viewTxns
        .filter { it.kind == TxnKind.EXPENSE || it.kind == TxnKind.REFUND }
        .groupBy { Dates.monthOf(it.occurredAt) }
        .mapValues { e -> e.value.sumOf { if (it.kind == TxnKind.REFUND) -it.amountCents else it.amountCents } }

    val title = if (yearScope) "${data.yearLabel} 年" else data.monthLabel
    val sources = if (yearScope) data.yearSourceCounts else data.sourceCounts

    if (showPicker) {
        MonthPickerDialog(
            month = month,
            onDismiss = { showPicker = false },
            onPick = { onMonthChange(it) }
        )
    }

    if (editCut) {
        Dialog(onDismissRequest = { editCut = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Dz.Card)
                    .padding(18.dp)
            ) {
                Text("多大算大额", style = MaterialTheme.typography.titleLarge, color = Dz.Ink)
                Spacer(Modifier.height(4.dp))
                Text(
                    "超过这个数的单笔，打开「只看日常」就不算进去",
                    style = MaterialTheme.typography.bodySmall,
                    color = Dz.InkFaint
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = cutInput,
                    onValueChange = { v -> cutInput = v.filter { it.isDigit() }.take(7) },
                    label = { Text("整元") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { editCut = false }) {
                        Text("取消", color = Dz.InkSoft)
                    }
                    TextButton(onClick = {
                        val y = (cutInput.toLongOrNull() ?: 0L) * 100
                        if (y > 0) bigCut = y
                        editCut = false
                    }) {
                        Text("保存", color = Dz.SakuraDeep, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showBigList) {
        BigListDialog(
            txns = bigTxns,
            cutCents = bigCut,
            onDismiss = { showBigList = false },
            onTxnClick = { detailTxn = it }
        )
    }

    detailCategory?.let { cid ->
        CategoryDetailDialog(
            categoryId = cid,
            txns = viewTxns.filter { it.categoryId == cid },
            isYear = yearScope,
            onDismiss = { detailCategory = null },
            onTxnClick = { detailTxn = it }
        )
    }

    // 统计页里点开一笔：和明细页一样能改分类、改商户名、改这笔记成什么
    detailTxn?.let { t ->
        TxnDetailDialog(
            txn = t,
            onDismiss = { detailTxn = null },
            onPickCategory = { catId ->
                IngestPipeline.correctCategory(t.id, catId)
                DingZhangApp.bumpDataVersion()
                detailTxn = null
            },
            onRenamePayee = { name ->
                IngestPipeline.dao?.updatePayee(t.id, name)
                DingZhangApp.bumpDataVersion()
                detailTxn = t.copy(payee = name)
            },
            onDelete = {
                IngestPipeline.dao?.delete(t.id)
                DingZhangApp.bumpDataVersion()
                detailTxn = null
            },
            onSetKind = { k ->
                IngestPipeline.dao?.setKind(t.id, k)
                DingZhangApp.bumpDataVersion()
                detailTxn = t.copy(kind = k)
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 96.dp)
    ) {
        Text("统计", style = MaterialTheme.typography.headlineLarge, color = Dz.Ink)

        // 周期导航：年视图下箭头翻整年，月视图下翻单月；点中间直接选年月
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            NavArrow("‹") {
                onMonthChange(shiftMonth(month, if (yearScope) -12 else -1))
            }
            Column(
                Modifier
                    .weight(1f)
                    .clickable { showPicker = true }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Dz.Ink
                )
                Text(
                    "共 $count 笔",
                    style = MaterialTheme.typography.labelSmall,
                    color = Dz.InkFaint
                )
            }
            NavArrow("›") {
                onMonthChange(shiftMonth(month, if (yearScope) 12 else 1))
            }
        }

        Spacer(Modifier.height(10.dp))
        ScopeTabs(yearScope) { yearScope = it }

        Spacer(Modifier.height(10.dp))
        DailyOnlyRow(
            on = skipBig,
            cutCents = bigCut,
            bigCount = bigTxns.size,
            bigSum = netExpense(bigTxns),
            onToggle = { skipBig = it },
            onEditCut = {
                cutInput = (bigCut / 100).toString()
                editCut = true
            },
            onShowExcluded = { showBigList = true }
        )

        Spacer(Modifier.height(12.dp))
        SummaryCard(expense, income, yearScope, data.yearBudgetCents)

        if (cats.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            DzCard {
                EmptyHint(
                    if (yearScope) "这一年还没有支出记录" else "这个月还没有支出记录",
                    "付几笔之后这里会有环形图和排行"
                )
            }
        } else {
            Spacer(Modifier.height(12.dp))
            RingCard(cats, ringTotal, yearScope) { detailCategory = it }

            Spacer(Modifier.height(12.dp))
            DzCard {
                Text("花在哪儿了", style = MaterialTheme.typography.titleLarge, color = Dz.Ink)
                Text(
                    "点一行看明细",
                    style = MaterialTheme.typography.labelSmall,
                    color = Dz.InkFaint,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Spacer(Modifier.height(10.dp))
                cats.forEachIndexed { i, c ->
                    if (i > 0) {
                        HorizontalDivider(
                            color = Dz.Line,
                            thickness = 0.6.dp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                    CategoryBar(c, ringTotal) { detailCategory = c.categoryId }
                }
            }

            Spacer(Modifier.height(12.dp))
            DzCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (yearScope) "每月花了多少" else "每天花了多少",
                        style = MaterialTheme.typography.titleLarge,
                        color = Dz.Ink
                    )
                    Spacer(Modifier.weight(1f))
                    Text("单位：元", style = MaterialTheme.typography.labelSmall, color = Dz.InkFaint)
                }
                Spacer(Modifier.height(12.dp))
                if (yearScope) MonthlyBars(monthlyMap, data.yearLabel) else DailyBars(dailyMap, data.daysInMonth)
            }
        }

        if (incomeCats.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            DzCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "钱从哪儿来",
                        style = MaterialTheme.typography.titleLarge,
                        color = Dz.Ink
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "共 ${incomeCats.sumOf { it.count }} 笔",
                        style = MaterialTheme.typography.labelSmall,
                        color = Dz.InkFaint
                    )
                }
                Text(
                    "点一行看明细",
                    style = MaterialTheme.typography.labelSmall,
                    color = Dz.InkFaint,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Spacer(Modifier.height(10.dp))
                incomeCats.forEachIndexed { i, c ->
                    if (i > 0) {
                        HorizontalDivider(
                            color = Dz.Line,
                            thickness = 0.6.dp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                    CategoryBar(c, incomeTotal) { detailCategory = c.categoryId }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        AutoCard(sources)
    }
}

@Composable
private fun NavArrow(symbol: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Dz.Card)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, fontSize = 17.sp, color = Dz.InkSoft)
    }
}

@Composable
private fun ScopeTabs(isYear: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Dz.Cream)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        ScopeChip("月", !isYear) { onChange(false) }
        ScopeChip("年", isYear) { onChange(true) }
    }
}

@Composable
private fun RowScope.ScopeChip(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(11.dp))
            .background(if (active) Dz.Card else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (active) Dz.SakuraDeep else Dz.InkFaint,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/** 把一批流水按分类归拢，退款冲减支出。skipBig 打开时用它重算，避免数字对不上。 */
private fun aggCats(txns: List<Txn>, income: Boolean): List<CategoryTotal> {
    val sums = HashMap<String, Long>()
    val counts = HashMap<String, Int>()
    txns.forEach { t ->
        val hit = if (income) t.kind == TxnKind.INCOME
        else (t.kind == TxnKind.EXPENSE || t.kind == TxnKind.REFUND)
        if (!hit) return@forEach
        val v = if (t.kind == TxnKind.REFUND) -t.amountCents else t.amountCents
        sums[t.categoryId] = (sums[t.categoryId] ?: 0L) + v
        counts[t.categoryId] = (counts[t.categoryId] ?: 0) + 1
    }
    return sums.entries
        .filter { it.value > 0 }
        .sortedByDescending { it.value }
        .map { CategoryTotal(it.key, it.value, counts[it.key] ?: 0) }
}

/** 支出净额：支出减去退款，转账不算。 */
private fun netExpense(txns: List<Txn>): Long = txns.fold(0L) { acc, t ->
    acc + when (t.kind) {
        TxnKind.EXPENSE -> t.amountCents
        TxnKind.REFUND -> -t.amountCents
        else -> 0L
    }
}

/** 「只看日常」开关。只在这次看的时候过滤，库里的数据一个字不动。 */
@Composable
private fun DailyOnlyRow(
    on: Boolean,
    cutCents: Long,
    bigCount: Int,
    bigSum: Long,
    onToggle: (Boolean) -> Unit,
    onEditCut: () -> Unit,
    onShowExcluded: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (on) Dz.SakuraSoft else Dz.Cream)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onToggle(!on) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (on) "只看日常消费 · 已排除大额" else "只看日常消费",
                style = MaterialTheme.typography.titleSmall,
                color = if (on) Dz.SakuraDeep else Dz.InkSoft,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (on) "开 ＞" else "关",
                style = MaterialTheme.typography.labelLarge,
                color = if (on) Dz.SakuraDeep else Dz.InkFaint
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "单笔 ≥ ¥${Money.short(cutCents)} 算大额",
                style = MaterialTheme.typography.labelSmall,
                color = Dz.InkFaint
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "改",
                style = MaterialTheme.typography.labelSmall,
                color = Dz.SakuraDeep,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Dz.Card)
                    .clickable { onEditCut() }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
            if (on) {
                Spacer(Modifier.weight(1f))
                Text(
                    "大额 $bigCount 笔 · ¥${Money.short(bigSum)} 查看",
                    style = MaterialTheme.typography.labelSmall,
                    color = Dz.SakuraDeep,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Dz.Card)
                        .clickable { onShowExcluded() }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

/** 被「只看日常」排除掉的那批，单独列出来看。 */
@Composable
private fun BigListDialog(
    txns: List<Txn>,
    cutCents: Long,
    onDismiss: () -> Unit,
    onTxnClick: (Txn) -> Unit
) {
    val sorted = txns.sortedByDescending { it.occurredAt }
    val total = netExpense(txns)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Dz.Card)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("大额消费", style = MaterialTheme.typography.titleMedium, color = Dz.Ink)
                    Text(
                        "${txns.size} 笔 · 合计 ¥${Money.short(total)}（≥ ¥${Money.short(cutCents)}）",
                        style = MaterialTheme.typography.labelSmall,
                        color = Dz.InkFaint
                    )
                }
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Dz.Cream)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("×", fontSize = 16.sp, color = Dz.InkSoft)
                }
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Dz.Line, thickness = 0.6.dp)
            if (sorted.isEmpty()) {
                EmptyHint("还没有够得着这条线的支出", "把边界调小一点再看")
            } else {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(top = 4.dp)
                ) {
                    sorted.forEach { t ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onTxnClick(t) }
                                .padding(vertical = 9.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    t.payee.ifBlank { t.note.ifBlank { "未命名" } },
                                    fontSize = 13.sp,
                                    color = Dz.Ink,
                                    maxLines = 1
                                )
                                Text(
                                    "${Dates.dayOf(t.occurredAt).takeLast(5)} ${Dates.timeOf(t.occurredAt)}",
                                    fontSize = 10.sp,
                                    color = Dz.InkFaint
                                )
                            }
                            Text(
                                signedText(t),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (t.kind == TxnKind.REFUND) Dz.Mint else Dz.Ink
                            )
                        }
                        HorizontalDivider(color = Dz.Line, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

/** 支出 / 收入 / 结余。收入以前只能靠手记，现在银行入账提醒也能进来。 */
@Composable
private fun SummaryCard(expense: Long, income: Long, isYear: Boolean, yearBudget: Long) {
    val net = income - expense
    DzCard(padding = 18.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (isYear) "全年支出" else "本月支出",
                    style = MaterialTheme.typography.labelSmall,
                    color = Dz.InkFaint
                )
                Text(
                    "¥${Money.short(expense)}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Dz.Ink,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (isYear) "全年收入" else "本月收入",
                    style = MaterialTheme.typography.labelSmall,
                    color = Dz.InkFaint
                )
                Text(
                    "¥${Money.short(income)}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Dz.Income,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("结余", style = MaterialTheme.typography.labelSmall, color = Dz.InkFaint)
                Text(
                    (if (net >= 0) "+" else "-") + "¥${Money.short(abs(net))}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (net >= 0) Dz.Income else Dz.Expense,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        if (isYear && yearBudget > 0) {
            val used = expense * 100f / yearBudget
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "年预算 ¥${Money.short(yearBudget)} · 已用 ${used.toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (used > 100f) Dz.Expense else Dz.InkSoft
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (expense > yearBudget) "超了 ¥${Money.short(expense - yearBudget)}"
                    else "还剩 ¥${Money.short(yearBudget - expense)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (expense > yearBudget) Dz.Expense else Dz.Income
                )
            }
        }
    }
}

@Composable
private fun RingCard(
    cats: List<CategoryTotal>,
    ringTotal: Long,
    isYear: Boolean,
    onCategoryClick: (String) -> Unit
) {
    DzCard(padding = 20.dp) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(216.dp)) {
                val stroke = 24.dp.toPx()
                val inset = stroke / 2 + 3.dp.toPx()
                val side = this.size.width - inset * 2
                val sum = cats.sumOf { it.cents }.toFloat().takeIf { it > 0f } ?: 1f
                var start = -90f
                cats.forEach { c ->
                    val sweep = c.cents.toFloat() / sum * 360f
                    drawArc(
                        color = Categories.of(c.categoryId).ui,
                        // 每段之间留一条缝，颜色再接近也不会连成一片
                        startAngle = start + 2f,
                        sweepAngle = (sweep - 4f).coerceAtLeast(1.5f),
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(side, side),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (isYear) "全年支出" else "本月支出",
                    style = MaterialTheme.typography.labelSmall,
                    color = Dz.InkFaint
                )
                Text(
                    "¥${Money.short(ringTotal)}",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Dz.Ink
                )
                Text(
                    if (isYear) "月均 ¥${Money.short(ringTotal / 12)}"
                    else "日均 ¥${Money.short(ringTotal / 30)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Dz.InkFaint
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        // 图例：全部分类，两列铺开 —— 只列前四名会让「剩下那 30% 是谁」永远找不到
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            cats.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    pair.forEach { c ->
                        val cat = Categories.of(c.categoryId)
                        Row(
                            Modifier
                                .weight(1f)
                                .clickable { onCategoryClick(c.categoryId) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(9.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(cat.ui)
                            )
                            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                                Text(
                                    cat.name,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Dz.Ink,
                                    maxLines = 1
                                )
                                Text(
                                    "¥${Money.short(c.cents)} · ${(c.cents.toFloat() / ringTotal * 100).toInt()}%",
                                    fontSize = 10.sp,
                                    color = Dz.InkFaint,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AutoCard(sources: Map<Source, Int>) {
    DzCard {
        Text("记账的自动化程度", style = MaterialTheme.typography.titleLarge, color = Dz.Ink)
        Spacer(Modifier.height(10.dp))
        val auto = (sources[Source.ACCESSIBILITY] ?: 0) +
            (sources[Source.NOTIFICATION] ?: 0) +
            (sources[Source.BILL_IMPORT] ?: 0)
        val manual = sources[Source.MANUAL] ?: 0
        val all = (auto + manual).coerceAtLeast(1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$auto", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Dz.SakuraDeep)
            Text(" 笔自动记账", style = MaterialTheme.typography.bodyMedium, color = Dz.InkSoft)
            Spacer(Modifier.weight(1f))
            Text("$manual 笔手记", style = MaterialTheme.typography.bodySmall, color = Dz.InkFaint)
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(9.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Dz.SakuraSoft)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(auto.toFloat() / all.toFloat())
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(Brush.horizontalGradient(listOf(Dz.Mint, Dz.Sakura)))
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "自动 $auto · 手记 $manual",
            style = MaterialTheme.typography.labelSmall,
            color = Dz.InkFaint
        )
    }
}

@Composable
private fun CategoryBar(c: CategoryTotal, total: Long, onClick: () -> Unit) {
    val cat = Categories.of(c.categoryId)
    val pct = if (total > 0) c.cents.toFloat() / total.toFloat() else 0f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryBadge(c.categoryId, 34.dp)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cat.name, style = MaterialTheme.typography.titleMedium, color = Dz.Ink)
                Text(
                    " ${c.count}笔",
                    style = MaterialTheme.typography.labelSmall,
                    color = Dz.InkFaint
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "¥${Money.short(c.cents)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Dz.Ink,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(76.dp)
                )
                Text(
                    "${(pct * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = Dz.InkFaint,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(36.dp)
                )
            }
            Spacer(Modifier.height(5.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Dz.Cream)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(pct.coerceIn(0.02f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(cat.ui)
                )
            }
        }
    }
}

/** 点分类弹出来的明细。列表最多摆 200 条，再多也没人在手机上翻。 */
@Composable
private fun CategoryDetailDialog(
    categoryId: String,
    txns: List<Txn>,
    isYear: Boolean,
    onDismiss: () -> Unit,
    onTxnClick: (Txn) -> Unit
) {
    val cat = Categories.of(categoryId)
    val net = txns.fold(0L) { acc, t ->
        acc + if (t.kind == TxnKind.REFUND) -t.amountCents else t.amountCents
    }
    val sorted = txns.sortedByDescending { it.occurredAt }
    val shown = sorted.take(200)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Dz.Card)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(categoryId, 32.dp)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(cat.name, style = MaterialTheme.typography.titleMedium, color = Dz.Ink)
                    Text(
                        "${txns.size} 笔 · 合计 ¥${Money.short(net)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Dz.InkFaint
                    )
                }
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Dz.Cream)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("×", fontSize = 16.sp, color = Dz.InkSoft)
                }
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Dz.Line, thickness = 0.6.dp)

            if (sorted.isEmpty()) {
                EmptyHint(
                    if (isYear) "这一年没有${cat.name}的记录" else "这个月没有${cat.name}的记录",
                    "换个周期再看"
                )
            } else {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(top = 4.dp)
                ) {
                    shown.forEach { t ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onTxnClick(t) }
                                .padding(vertical = 9.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    t.payee.ifBlank { t.note.ifBlank { "未命名" } },
                                    fontSize = 13.sp,
                                    color = Dz.Ink,
                                    maxLines = 1
                                )
                                Text(
                                    "${Dates.dayOf(t.occurredAt).takeLast(5)} ${Dates.timeOf(t.occurredAt)}",
                                    fontSize = 10.sp,
                                    color = Dz.InkFaint
                                )
                            }
                            Text(
                                signedText(t),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = when (t.kind) {
                                    TxnKind.REFUND, TxnKind.INCOME -> Dz.Mint
                                    else -> Dz.Ink
                                }
                            )
                        }
                        HorizontalDivider(color = Dz.Line, thickness = 0.5.dp)
                    }
                    if (sorted.size > shown.size) {
                        Text(
                            "只显示最近 200 条，其余 ${sorted.size - shown.size} 条没展开",
                            style = MaterialTheme.typography.labelSmall,
                            color = Dz.InkFaint,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun signedText(t: Txn): String = when (t.kind) {
    TxnKind.REFUND -> "+¥${Money.short(t.amountCents)}"
    TxnKind.INCOME -> "+¥${Money.short(t.amountCents)}"
    TxnKind.TRANSFER -> "¥${Money.short(t.amountCents)}"
    TxnKind.EXPENSE -> "-¥${Money.short(t.amountCents)}"
}

/** 月视图：这个月每天花了多少。 */
@Composable
private fun DailyBars(daily: Map<Int, Long>, daysInMonth: Int) {
    val max = (daily.values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        for (day in 1..daysInMonth) {
            val v = (daily[day] ?: 0L).coerceAtLeast(0L)
            Bar(v, max)
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("1", style = MaterialTheme.typography.labelSmall, color = Dz.InkFaint)
        Text("${daysInMonth / 2}", style = MaterialTheme.typography.labelSmall, color = Dz.InkFaint)
        Text("$daysInMonth", style = MaterialTheme.typography.labelSmall, color = Dz.InkFaint)
    }
}

/** 年视图：这一年每个月花了多少。12 根柱子，比不了更粗的粒度有意义。 */
@Composable
private fun MonthlyBars(monthly: Map<String, Long>, year: String) {
    val vals = (1..12).map { m ->
        (monthly["$year-${m.toString().padStart(2, '0')}"] ?: 0L).coerceAtLeast(0L)
    }
    val max = (vals.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        vals.forEach { Bar(it, max) }
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("1月", style = MaterialTheme.typography.labelSmall, color = Dz.InkFaint)
        Text("6月", style = MaterialTheme.typography.labelSmall, color = Dz.InkFaint)
        Text("12月", style = MaterialTheme.typography.labelSmall, color = Dz.InkFaint)
    }
}

/** 一根柱子。0 也要留一丝底座，否则那一天/那一月在横轴上会"消失"。 */
@Composable
private fun RowScope.Bar(v: Long, max: Long) {
    val frac = if (max > 0) v.toFloat() / max.toFloat() else 0f
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(
                    when {
                        v > 0L -> frac.coerceIn(0.08f, 1f)
                        // 退款把那一天/那一月冲成负数：给个小柱示意方向，别按 0 处理
                        v < 0L -> 0.12f
                        else -> 0.04f
                    }
                )
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .background(
                    when {
                        v < 0L -> Dz.Mint.copy(alpha = 0.5f)
                        v == 0L -> Dz.Cream
                        else -> Dz.Sakura.copy(alpha = 0.45f + 0.55f * frac)
                    }
                )
        )
    }
}
