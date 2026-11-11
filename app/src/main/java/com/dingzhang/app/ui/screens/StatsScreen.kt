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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

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
    var detailDay by remember { mutableStateOf<Int?>(null) }        // 月视图：点了哪一天
    var detailMonthOfYear by remember { mutableStateOf<Int?>(null) } // 年视图：点了哪个月
    var detailTxn by remember { mutableStateOf<Txn?>(null) }
    // 只在这次看的时候过滤，不动库里的数据：单笔 ≥ 这个数的算大额
    var view by remember { mutableStateOf(0) }        // 0 全部 / 1 日常 / 2 大额
    var bigCut by remember { mutableStateOf(20_000L) }
    var editCut by remember { mutableStateOf(false) }
    var cutInput by remember { mutableStateOf("") }
    var flow by remember { mutableStateOf(0) }        // 0 支出 / 1 收入

    val catsBase = if (yearScope) data.yearByCategory else data.byCategory
    val incomeCatsBase = if (yearScope) data.yearIncomeByCategory else data.incomeByCategory
    val txns = if (yearScope) data.yearTxns else data.monthTxns
    // 默认按金额切，但用户在详情里指名过的（大餐→日常、电器→大额）以他为准
    fun isBig(t: Txn) = when (t.bigClass) {
        1 -> false
        2 -> true
        else -> t.amountCents >= bigCut
    }
    val viewTxns = when (view) {
        1 -> txns.filter { !isBig(it) }
        2 -> txns.filter { isBig(it) }
        else -> txns
    }

    val cats = if (view != 0) aggCats(viewTxns, false) else catsBase
    // 收入来源可能很多（工资、奖金、报销、红包、理财），单独一块列在下面
    val incomeCats = if (view != 0) aggCats(viewTxns, true) else incomeCatsBase
    val incomeTotal = incomeCats.sumOf { it.cents }
    // 环图与百分比的分母用分类合计，保证环内自洽；
    // 顶部那个「支出」用 SQL 的合计（它包含净额为负、被 HAVING 挡掉的分类）
    val ringTotal = cats.sumOf { it.cents }
    val expense = if (view != 0) netExpense(viewTxns)
    else if (yearScope) data.yearExpense else data.monthExpense
    val income = if (view != 0) viewTxns.filter { it.kind == TxnKind.INCOME }.sumOf { it.amountCents }
    else if (yearScope) data.yearIncome else data.monthIncome
    val count = if (view != 0) viewTxns.count { it.kind != TxnKind.TRANSFER }
    else if (yearScope) data.yearCount else data.monthCount
    // 柱状图也按同一口径重算，避免上下数字对不上
    val dailyMap = if (view == 0) data.daily else viewTxns
        .filter { it.kind == TxnKind.EXPENSE || it.kind == TxnKind.REFUND }
        .groupBy { Dates.dayOf(it.occurredAt).takeLast(2).toIntOrNull() ?: 0 }
        .filterKeys { it > 0 }
        .mapValues { e -> e.value.sumOf { if (it.kind == TxnKind.REFUND) -it.amountCents else it.amountCents } }
    val monthlyMap = if (view == 0) data.monthly else viewTxns
        .filter { it.kind == TxnKind.EXPENSE || it.kind == TxnKind.REFUND }
        .groupBy { Dates.monthOf(it.occurredAt) }
        .mapValues { e -> e.value.sumOf { if (it.kind == TxnKind.REFUND) -it.amountCents else it.amountCents } }

    val title = if (yearScope) "${data.yearLabel} 年" else data.monthLabel

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

    // 点柱子：先看这一天的总额和笔数，点条目还是老样子进详情
    detailDay?.let { d ->
        val list = data.monthTxns.filter { dayNum(it.occurredAt) == d }
        TxnSliceDialog(
            title = "${month.substringAfter("-").toIntOrNull() ?: 0} 月 $d 日",
            txns = list,
            onDismiss = { detailDay = null },
            onTxnClick = { detailTxn = it }
        )
    }

    detailMonthOfYear?.let { m ->
        val list = data.yearTxns.filter { monthNum(it.occurredAt) == m }
        TxnSliceDialog(
            title = "$m 月",
            txns = list,
            onDismiss = { detailMonthOfYear = null },
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
            },
            onSetBig = { v ->
                IngestPipeline.dao?.setBigClass(t.id, v)
                DingZhangApp.bumpDataVersion()
                detailTxn = t.copy(bigClass = v)
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            CutChip("全部", view == 0) { view = 0 }
            CutChip("日常", view == 1) { view = 1 }
            CutChip("大额", view == 2) { view = 2 }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "单笔 ≥ ¥${Money.short(bigCut)} 算大额，点这里改",
            style = MaterialTheme.typography.labelSmall,
            color = Dz.InkFaint,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable {
                    cutInput = (bigCut / 100).toString()
                    editCut = true
                }
                .padding(horizontal = 6.dp, vertical = 3.dp)
        )

        Spacer(Modifier.height(12.dp))
        SummaryCard(expense, income, yearScope, data.yearBudgetCents)

        // 支出分类一多，收入就被顶到很下面，所以这里直接切
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            CutChip("支出", flow == 0) { flow = 0 }
            CutChip("收入", flow == 1) { flow = 1 }
        }

        val flowCats = if (flow == 0) cats else incomeCats
        val flowTotal = if (flow == 0) ringTotal else incomeTotal
        val flowIsExpense = flow == 0

        if (flowCats.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            DzCard {
                EmptyHint(
                    if (flowIsExpense) {
                        if (yearScope) "这一年还没有支出记录" else "这个月还没有支出记录"
                    } else {
                        if (yearScope) "这一年还没有收入记录" else "这个月还没有收入记录"
                    },
                    if (flowIsExpense) "付几笔之后这里会有环形图和排行" else "工资、收款到账之后这里会有环形图和排行"
                )
            }
        } else {
            Spacer(Modifier.height(12.dp))
            RingCard(
                cats = flowCats,
                ringTotal = flowTotal,
                title = if (flowIsExpense) (if (yearScope) "全年支出" else "本月支出")
                else (if (yearScope) "全年收入" else "本月收入"),
                subLine = if (yearScope) "月均 ¥${Money.short(flowTotal / 12)}"
                else "日均 ¥${Money.short(flowTotal / 30)}",
                onCategoryClick = { detailCategory = it }
            )

            Spacer(Modifier.height(12.dp))
            RankCard(
                title = if (flowIsExpense) "花在哪儿了" else "钱从哪儿来",
                cats = flowCats,
                total = flowTotal
            ) { detailCategory = it }

            if (flowIsExpense) {
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
                    if (yearScope) MonthlyBars(monthlyMap, data.yearLabel) { detailMonthOfYear = it }
                    else DailyBars(dailyMap, data.daysInMonth) { detailDay = it }
                }
            }
        }

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

/** 口径切换：全部 / 日常 / 大额。只影响这次看，库里的数据一个字不动。 */
@Composable
private fun RowScope.CutChip(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) Dz.SakuraSoft else Dz.Cream)
            .clickable { onClick() }
            .padding(vertical = 9.dp),
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
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
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
    title: String,
    subLine: String,
    onCategoryClick: (String) -> Unit
) {
    val sum = cats.sumOf { it.cents }.toFloat().takeIf { it > 0f } ?: 1f
    DzCard(padding = 20.dp) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier
                    .size(216.dp)
                    .pointerInput(cats) {
                        // 点环上的哪一段，就开那一段的明细
                        detectTapGestures { off ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val dist = (off - center).getDistance()
                            val outer = size.width / 2f
                            val inner = outer - 24.dp.toPx() - 4.dp.toPx()
                            if (dist in inner..outer) {
                                var deg = Math.toDegrees(
                                    atan2(
                                        (off.y - center.y).toDouble(),
                                        (off.x - center.x).toDouble()
                                    )
                                ).toFloat()
                                deg = (deg + 450f) % 360f   // 环从正上方开始
                                var acc = 0f
                                for (c in cats) {
                                    val sweep = c.cents.toFloat() / sum * 360f
                                    if (deg >= acc && deg < acc + sweep) {
                                        onCategoryClick(c.categoryId)
                                        break
                                    }
                                    acc += sweep
                                }
                            }
                        }
                    }
            ) {
                val stroke = 24.dp.toPx()
                val inset = stroke / 2 + 3.dp.toPx()
                val side = this.size.width - inset * 2
                val r = this.size.width / 2f
                var start = -90f
                cats.forEach { c ->
                    val sweep = c.cents.toFloat() / sum * 360f
                    drawArc(
                        color = Categories.of(c.categoryId).ui,
                        startAngle = start,
                        sweepAngle = sweep.coerceAtLeast(0.4f),
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(side, side),
                        style = Stroke(width = stroke, cap = StrokeCap.Butt)
                    )
                    start += sweep
                }
                // 段与段之间压一条和卡片底色同色的细线，边界一眼看清
                if (cats.size > 1) {
                    var edge = -90f
                    cats.forEach { c ->
                        val a = Math.toRadians(edge.toDouble())
                        val from = Offset(
                            r + cos(a).toFloat() * (r - stroke),
                            r + sin(a).toFloat() * (r - stroke)
                        )
                        val to = Offset(
                            r + cos(a).toFloat() * (r + 1.dp.toPx()),
                            r + sin(a).toFloat() * (r + 1.dp.toPx())
                        )
                        drawLine(
                            color = Dz.Card,
                            start = from,
                            end = to,
                            strokeWidth = 1.5.dp.toPx()
                        )
                        edge += c.cents.toFloat() / sum * 360f
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    title,
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
                    subLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = Dz.InkFaint
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "点环上的色块或下面任意一行都能看明细",
            style = MaterialTheme.typography.labelSmall,
            color = Dz.InkFaint,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        // 图例：全部分类，两列铺开 —— 只列前四名会让「剩下那 30% 是谁」永远找不到
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            cats.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    pair.forEach { c ->
                        val cat = Categories.of(c.categoryId)
                        Row(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onCategoryClick(c.categoryId) }
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(11.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(cat.ui)
                            )
                            Column(Modifier.weight(1f).padding(start = 7.dp)) {
                                Text(
                                    cat.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Dz.Ink,
                                    maxLines = 1
                                )
                                Text(
                                    "¥${Money.short(c.cents)} · ${(c.cents.toFloat() / ringTotal * 100).toInt()}%",
                                    fontSize = 12.sp,
                                    color = Dz.InkSoft,
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
private fun RankCard(
    title: String,
    cats: List<CategoryTotal>,
    total: Long,
    onCategoryClick: (String) -> Unit
) {
    DzCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Dz.Ink)
            Spacer(Modifier.weight(1f))
            Text(
                "共 ${cats.sumOf { it.count }} 笔",
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
        cats.forEachIndexed { i, c ->
            if (i > 0) {
                HorizontalDivider(
                    color = Dz.Line,
                    thickness = 0.6.dp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            CategoryBar(c, total) { onCategoryClick(c.categoryId) }
        }
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

/** 左侧三档刻度：最高、一半、0。金额用短格式，别把轴撑爆 */
@Composable
private fun AxisLabels(max: Long, height: androidx.compose.ui.unit.Dp) {
    Column(
        Modifier
            .height(height)
            .padding(end = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End
    ) {
        Text(Money.short(max), style = MaterialTheme.typography.labelSmall, color = Dz.InkFaint)
        Text(Money.short(max / 2), style = MaterialTheme.typography.labelSmall, color = Dz.InkFaint)
        Text("0", style = MaterialTheme.typography.labelSmall, color = Dz.InkFaint)
    }
}

private fun dayNum(ts: Long): Int = Dates.dayOf(ts).takeLast(2).toIntOrNull() ?: 0

private fun monthNum(ts: Long): Int = Dates.monthOf(ts).takeLast(2).toIntOrNull() ?: 0

/** 点柱子后的小结：这一天的总额、笔数，点条目进详情（跟分类明细一样的操作） */
@Composable
private fun TxnSliceDialog(
    title: String,
    txns: List<Txn>,
    onDismiss: () -> Unit,
    onTxnClick: (Txn) -> Unit
) {
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
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = Dz.Ink)
                    Text(
                        "${txns.size} 笔 · 合计 ¥${Money.short(net)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Dz.InkSoft,
                        modifier = Modifier.padding(top = 2.dp)
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
                EmptyHint("这${if (title.contains("日")) "一天" else "个月"}没有记录", "点别处看看")
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
                    Text(
                        "点任意一条能改分类、改商户名、改这笔记成什么",
                        style = MaterialTheme.typography.labelSmall,
                        color = Dz.InkFaint,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                    )
                }
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
private fun DailyBars(daily: Map<Int, Long>, daysInMonth: Int, onDayClick: (Int) -> Unit = {}) {
    val max = (daily.values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {
        AxisLabels(max, 88.dp)
        Row(
            modifier = Modifier
                .weight(1f)
                .height(88.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            for (day in 1..daysInMonth) {
                val v = (daily[day] ?: 0L).coerceAtLeast(0L)
                Bar(v, max) { onDayClick(day) }
            }
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
private fun MonthlyBars(monthly: Map<String, Long>, year: String, onMonthClick: (Int) -> Unit = {}) {
    val vals = (1..12).map { m ->
        (monthly["$year-${m.toString().padStart(2, '0')}"] ?: 0L).coerceAtLeast(0L)
    }
    val max = (vals.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {
        AxisLabels(max, 96.dp)
        Row(
            modifier = Modifier
                .weight(1f)
                .height(96.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            vals.forEachIndexed { i, v -> Bar(v, max) { onMonthClick(i + 1) } }
        }
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
private fun RowScope.Bar(v: Long, max: Long, onClick: () -> Unit = {}) {
    val frac = if (max > 0) v.toFloat() / max.toFloat() else 0f
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(onClick = onClick),
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
