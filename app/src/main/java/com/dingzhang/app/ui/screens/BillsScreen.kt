package com.dingzhang.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
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
import com.dingzhang.app.ui.UndoBuffer
import com.dingzhang.app.core.model.TxnKind
import com.dingzhang.app.core.model.TxnStatus
import com.dingzhang.app.ingest.IngestPipeline
import com.dingzhang.app.ui.AppData
import com.dingzhang.app.ui.components.CategoryPickerDialog
import com.dingzhang.app.ui.components.CategoryBadge
import com.dingzhang.app.ui.components.EmptyHint
import com.dingzhang.app.ui.components.MascotFace
import com.dingzhang.app.ui.components.MonthPickerDialog
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
fun BillsScreen(
    data: AppData,
    month: String,
    onMonthChange: (String) -> Unit,
    onSelectModeChange: (Boolean) -> Unit = {}
) {
    var filter by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<Txn?>(null) }
    var quick by remember { mutableStateOf(0) }      // 0 按月份 / 1 今天 / 2 本周 / 3 本月 / 4 上月
    var query by remember { mutableStateOf("") }
    var rangeTxns by remember { mutableStateOf<List<Txn>>(emptyList()) }
    var hits by remember { mutableStateOf<List<Txn>>(emptyList()) }
    var showFilter by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }
    // 批量删账：进多选模式后点条目是勾选，不是打开详情
    var selectMode by remember { mutableStateOf(false) }
    LaunchedEffect(selectMode) { onSelectModeChange(selectMode) }
    var picked by remember { mutableStateOf(setOf<Long>()) }
    var confirmBatch by remember { mutableStateOf(false) }
    var batchPickCat by remember { mutableStateOf(false) }
    var minYuan by remember { mutableStateOf("") }   // 金额下限，单位元，空 = 不限
    var maxYuan by remember { mutableStateOf("") }   // 金额上限
    var catFilter by remember { mutableStateOf<Set<String>>(emptySet()) }
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
            // 搜「餐饮」「交通」这类词时，把对应分类的账也捞出来
            val catIds = Categories.all
                .filter { it.name.contains(q.trim()) }
                .map { it.id }
            val text = dao.search(q, catIds)
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

    val minCents = parseMinYuan(minYuan)
    val maxCents = parseMaxYuan(maxYuan)
    val filtered = remember(base, filter, minCents, maxCents, catFilter) {
        base.filter { t ->
            val kindOk = when (filter) {
                1 -> t.kind == TxnKind.EXPENSE || t.kind == TxnKind.REFUND
                2 -> t.kind == TxnKind.INCOME
                3 -> t.status == TxnStatus.AUTO
                else -> true
            }
            val amountOk = t.amountCents >= minCents && t.amountCents <= maxCents
            val catOk = catFilter.isEmpty() || t.categoryId in catFilter
            kindOk && amountOk && catOk
        }
    }
    val grouped = remember(filtered) { filtered.groupBy { Dates.dayOf(it.occurredAt) } }

    // 「支出 · ¥50~200 · 餐饮+购物」这种一行摘要，时间由标题显示，不重复
    val typeNames = listOf("全部", "支出", "收入", "待确认")
    val filterSummary = buildList {
        if (filter != 0) add(typeNames[filter])
        if (minYuan.isNotBlank() || maxYuan.isNotBlank()) {
            add("¥" + minYuan.ifBlank { "0" } + " ~ " + maxYuan.ifBlank { "不限" })
        }
        if (catFilter.isNotEmpty()) add(catFilter.joinToString("+") { Categories.of(it).name })
    }.joinToString(" · ")

    if (showMonthPicker) {
        MonthPickerDialog(
            month = month,
            onDismiss = { showMonthPicker = false },
            onPick = {
                onMonthChange(it)
                showMonthPicker = false
            }
        )
    }

    Column(Modifier.fillMaxSize()) {
        // 标题 + 搜索 + 筛选入口（时间、类型都收进筛选面板了，这一屏不再堆两排 chip）
        Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("明细", style = MaterialTheme.typography.headlineLarge, color = Dz.Ink)
                Spacer(Modifier.weight(1f))
                when {
                    searching -> Text(
                        "${hits.size} 条结果",
                        style = MaterialTheme.typography.titleMedium,
                        color = Dz.InkSoft
                    )
                    quick == 0 -> {
                        Icon(
                            Icons.Rounded.ChevronLeft, "上个月", tint = Dz.InkSoft,
                            modifier = Modifier
                                .size(26.dp)
                                .clickable { onMonthChange(shiftMonth(month, -1)) }
                        )
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showMonthPicker = true }
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                data.monthLabel,
                                color = Dz.Ink,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                Icons.Rounded.ArrowDropDown, "选月份", tint = Dz.InkSoft,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Icon(
                            Icons.Rounded.ChevronRight, "下个月", tint = Dz.InkSoft,
                            modifier = Modifier
                                .size(26.dp)
                                .clickable { onMonthChange(shiftMonth(month, 1)) }
                        )
                    }
                    else -> Text(
                        quickLabel(quick),
                        style = MaterialTheme.typography.titleMedium,
                        color = Dz.InkSoft
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (selectMode) "取消" else "批量",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selectMode) Dz.SakuraDeep else Dz.InkSoft,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selectMode) Dz.SakuraSoft else Dz.Card)
                        .clickable {
                            selectMode = !selectMode
                            picked = emptySet()
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 13.sp),
                    placeholder = {
                        Text("搜商户、备注、分类", color = Dz.InkFaint, fontSize = 13.sp)
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Search, null,
                            tint = Dz.InkFaint,
                            modifier = Modifier.size(18.dp)
                        )
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
                        .weight(1f)
                        .heightIn(min = 48.dp)
                )
                Spacer(Modifier.width(8.dp))
                FilterEntry(
                    active = filter != 0 || quick != 0 ||
                        minYuan.isNotBlank() || maxYuan.isNotBlank() || catFilter.isNotEmpty()
                ) { showFilter = true }
            }

            if (filterSummary.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    filterSummary,
                    fontSize = 11.sp,
                    color = Dz.InkFaint
                )
            }
        }

        // 批量模式下按系统返回键就退出，不用去点右上角
    BackHandler(enabled = selectMode) {
        selectMode = false
        picked = emptySet()
    }

    Box(Modifier.weight(1f)) {
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
                        TxnLine(
                            txn = txn,
                            pickMode = selectMode,
                            picked = txn.id in picked
                        ) {
                            if (selectMode) {
                                picked = if (txn.id in picked) picked - txn.id else picked + txn.id
                            } else {
                                selected = txn
                            }
                        }
                    }
                }
            }
        }
        if (selectMode) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // 批量模式下「记一笔」已经收起来了，这条可以贴着实底放
                    .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 18.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Dz.Card)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val allOn = filtered.isNotEmpty() && picked.size >= filtered.size
                Text(
                    if (picked.isEmpty()) "点条目勾选" else "已选 ${picked.size} 笔",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (picked.isEmpty()) Dz.InkFaint else Dz.Ink
                )
                Spacer(Modifier.width(8.dp))
                // 全选 / 全不选，按的是当前筛选出来的这些
                Text(
                    if (allOn) "全不选" else "全选",
                    style = MaterialTheme.typography.labelLarge,
                    color = Dz.SakuraDeep,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Dz.SakuraSoft)
                        .clickable {
                            picked = if (allOn) emptySet() else filtered.map { it.id }.toSet()
                        }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "改分类",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (picked.isEmpty()) Dz.InkFaint else Dz.SakuraDeep,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Dz.Cream)
                        .clickable(enabled = picked.isNotEmpty()) { batchPickCat = true }
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "删除",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (picked.isEmpty()) Dz.InkFaint else Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (picked.isEmpty()) Dz.Cream else Dz.Expense)
                        .clickable(enabled = picked.isNotEmpty()) { confirmBatch = true }
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                )
            }
        }
        }
    }

    if (batchPickCat) {
        CategoryPickerDialog(
            payee = "已选 ${picked.size} 笔",
            currentId = "",
            onPick = { catId ->
                val ids = picked
                batchPickCat = false
                selectMode = false
                picked = emptySet()
                // 只改选中的这些，顺手锁住，不让后面的自动重算再动它们
                ids.forEach { IngestPipeline.dao?.updateCategory(it, catId, true) }
                DingZhangApp.bumpDataVersion()
                Toast.makeText(
                    ctx,
                    "${ids.size} 笔已改成 ${Categories.of(catId).name}",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = { batchPickCat = false }
        )
    }

    if (confirmBatch) {
        Dialog(onDismissRequest = { confirmBatch = false }) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Dz.Card)
                    .padding(22.dp)
            ) {
                Text("删掉这 ${picked.size} 笔？", style = MaterialTheme.typography.titleLarge, color = Dz.Ink)
                Text(
                    "删了就找不回来了，这笔钱也不会再出现在统计里。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Dz.InkSoft,
                    modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "取消",
                        style = MaterialTheme.typography.labelLarge,
                        color = Dz.InkSoft,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Dz.Cream)
                            .clickable { confirmBatch = false }
                            .padding(vertical = 12.dp)
                    )
                    Text(
                        "删除",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Dz.Expense)
                            .clickable {
                                val ids = picked
                                confirmBatch = false
                                selectMode = false
                                picked = emptySet()
                                IngestPipeline.dao?.deleteMany(ids)
                                DingZhangApp.bumpDataVersion()
                                Toast.makeText(ctx, "已删除 ${ids.size} 笔", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 12.dp)
                    )
                }
            }
        }
    }

    if (showFilter) {
        FilterDialog(
            time = quick,
            type = filter,
            minYuan = minYuan,
            maxYuan = maxYuan,
            cats = catFilter,
            onTime = { quick = it },
            onType = { filter = it },
            onMin = { v -> minYuan = v.filter { it.isDigit() || it == '.' } },
            onMax = { v -> maxYuan = v.filter { it.isDigit() || it == '.' } },
            onToggleCat = { id -> catFilter = if (id in catFilter) catFilter - id else catFilter + id },
            onReset = {
                minYuan = ""
                maxYuan = ""
                catFilter = emptySet()
            },
            onDismiss = { showFilter = false }
        )
    }

    selected?.let { txn ->
        TxnDetailDialog(
            txn = txn,
            onDismiss = { selected = null },
            onPickCategory = { catId ->
                val r = IngestPipeline.correctCategory(txn.id, catId)
                DingZhangApp.bumpDataVersion()
                selected = null
                val name = Categories.of(catId).name
                // 只报「改成了什么 + 连带改了几笔」，不再把商户名和学到的规则念一遍
                val msg = buildString {
                    append("已改成 $name")
                    if (r.updated > 0) append("，同商户 $r.updated 笔一起改")
                }
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
            },
            onRenamePayee = { newName ->
                IngestPipeline.dao?.updatePayee(txn.id, newName)
                DingZhangApp.bumpDataVersion()
                // 弹窗里立刻显示新名字，不用退出去再进
                selected = txn.copy(payee = newName)
            },
            onDelete = {
                IngestPipeline.dao?.delete(txn.id)
                DingZhangApp.bumpDataVersion()
                selected = null
                UndoBuffer.put(txn)
            },
            onSetAmount = { cents ->
                IngestPipeline.dao?.updateAmount(txn.id, cents)
                DingZhangApp.bumpDataVersion()
                selected = txn.copy(amountCents = cents)
            },
            onSetKind = { k ->
                IngestPipeline.dao?.setKind(txn.id, k)
                DingZhangApp.bumpDataVersion()
                selected = txn.copy(kind = k)
                Toast.makeText(ctx, kindToast(k), Toast.LENGTH_SHORT).show()
            },
            onSetBig = { v ->
                IngestPipeline.dao?.setBigClass(txn.id, v)
                DingZhangApp.bumpDataVersion()
                selected = txn.copy(bigClass = v)
                Toast.makeText(
                    ctx,
                    when (v) {
                        1 -> "已算进日常"
                        2 -> "已算进大额"
                        else -> "已恢复按金额自动判"
                    },
                    Toast.LENGTH_SHORT
                ).show()
            }
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
private fun TxnLine(
    txn: Txn,
    pickMode: Boolean = false,
    picked: Boolean = false,
    onClick: () -> Unit
) {
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
            if (pickMode) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (picked) Dz.Sakura else Dz.Cream),
                    contentAlignment = Alignment.Center
                ) {
                    if (picked) {
                        Icon(
                            Icons.Rounded.Check, null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
            }
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

/** 一笔账的详情：改分类、改商户名、删除。明细页和首页「最近」共用这一个。 */
@Composable
fun TxnDetailDialog(
    txn: Txn,
    onDismiss: () -> Unit,
    onPickCategory: (String) -> Unit,
    onRenamePayee: (String) -> Unit,
    onDelete: () -> Unit,
    onSetKind: (TxnKind) -> Unit,
    onSetBig: (Int) -> Unit = {},
    onSetAmount: ((Long) -> Unit)? = null
) {
    var renameOpen by remember { mutableStateOf(false) }
    var amountOpen by remember { mutableStateOf(false) }
    var amountInput by remember(txn.id, txn.amountCents) { mutableStateOf(yuanEdit(txn.amountCents)) }
    var confirmDelete by remember { mutableStateOf(false) }
    var nameInput by remember(txn.id, txn.payee) { mutableStateOf(txn.payee) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.93f)
                .heightIn(max = 620.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Dz.Card)
                .padding(20.dp)
        ) {
            // 只有上半段滚，删除和关闭钉在底部 —— 删一笔不用先滑到底
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MascotFace(40.dp)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            txn.payee.ifBlank { "未知商户" },
                            style = MaterialTheme.typography.titleLarge,
                            color = Dz.Ink,
                            maxLines = 1,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clickable { renameOpen = !renameOpen }
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Rounded.Edit, "改商户名",
                            tint = Dz.InkFaint,
                            modifier = Modifier
                                .size(13.dp)
                                .clickable { renameOpen = !renameOpen }
                        )
                    }
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
                    color = if (txn.kind == TxnKind.INCOME || txn.kind == TxnKind.REFUND) Dz.Income else Dz.Ink,
                    // 金额本身就能点，跟商户名一样，不加「改」这种多余按钮
                    modifier = if (onSetAmount != null) {
                        Modifier.clickable {
                            amountInput = yuanEdit(txn.amountCents)
                            amountOpen = true
                            renameOpen = false
                        }
                    } else Modifier
                )
            }

            if (amountOpen && onSetAmount != null) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    singleLine = true,
                    label = { Text("金额（元）", color = Dz.InkFaint, fontSize = 12.sp) },
                    textStyle = TextStyle(fontSize = 14.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { amountOpen = false }) {
                        Text("取消", color = Dz.InkSoft)
                    }
                    val cents = yuanToCents(amountInput)
                    Button(
                        onClick = {
                            if (cents != null && cents > 0) {
                                onSetAmount(cents)
                                amountOpen = false
                            }
                        },
                        enabled = cents != null && cents > 0,
                        colors = ButtonDefaults.buttonColors(containerColor = Dz.Sakura)
                    ) { Text("保存") }
                }
            }

            if (renameOpen) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    singleLine = true,
                    label = { Text("商户名", color = Dz.InkFaint, fontSize = 12.sp) },
                    textStyle = TextStyle(fontSize = 14.sp),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { renameOpen = false }) {
                        Text("取消", color = Dz.InkSoft)
                    }
                    Button(
                        onClick = {
                            val v = nameInput.trim()
                            if (v.isNotEmpty()) {
                                onRenamePayee(v)
                                renameOpen = false
                            }
                        },
                        enabled = nameInput.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Dz.Sakura,
                            contentColor = Color.White
                        )
                    ) { Text("保存") }
                }
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

            if (txn.rawText.isNotBlank()) {
                var rawOpen by remember(txn.id) { mutableStateOf(false) }
                Spacer(Modifier.height(12.dp))
                Text(
                    if (rawOpen) "收起它收到的原文" else "看它收到的原文",
                    style = MaterialTheme.typography.labelMedium,
                    color = Dz.InkSoft,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { rawOpen = !rawOpen }
                        .padding(vertical = 3.dp)
                )
                if (rawOpen) {
                    Text(
                        txn.rawText,
                        style = MaterialTheme.typography.bodySmall,
                        color = Dz.InkFaint,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Dz.Cream)
                            .padding(10.dp)
                    )
                    Text(
                        "这句话就是系统给我的全部信息。记错的时候把它发给我，我按它改规则",
                        style = MaterialTheme.typography.labelSmall,
                        color = Dz.InkFaint,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
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
            Text("这笔算什么", style = MaterialTheme.typography.titleMedium, color = Dz.Ink)
            Text(
                "别人转来的 AA 钱标成「退款」，会从那一类支出里扣掉，不算成收入",
                style = MaterialTheme.typography.bodySmall,
                color = Dz.InkFaint,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KindChip("支出", txn.kind == TxnKind.EXPENSE) { onSetKind(TxnKind.EXPENSE) }
                KindChip("收入", txn.kind == TxnKind.INCOME) { onSetKind(TxnKind.INCOME) }
                KindChip("退款", txn.kind == TxnKind.REFUND) { onSetKind(TxnKind.REFUND) }
            }

            Spacer(Modifier.height(16.dp))
            Text("算日常还是大额", style = MaterialTheme.typography.titleMedium, color = Dz.Ink)
            Text(
                "统计里的日常/大额默认按金额切，不对的话在这儿改一笔。大餐算日常、电器算大额都行",
                style = MaterialTheme.typography.bodySmall,
                color = Dz.InkFaint,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KindChip("自动", txn.bigClass == 0) { onSetBig(0) }
                KindChip("日常", txn.bigClass == 1) { onSetBig(1) }
                KindChip("大额", txn.bigClass == 2) { onSetBig(2) }
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

            }

            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Dz.Cream)
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "删除这笔",
                    style = MaterialTheme.typography.labelLarge,
                    color = Dz.Expense,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Dz.Expense.copy(alpha = 0.08f))
                        .clickable { confirmDelete = true }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
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

    if (confirmDelete) {
        Dialog(onDismissRequest = { confirmDelete = false }) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Dz.Card)
                    .padding(22.dp)
            ) {
                Text("删掉这笔？", style = MaterialTheme.typography.titleLarge, color = Dz.Ink)
                Text(
                    "删了就找不回来了。如果是记重了、或者压根不该记，删掉正好。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Dz.InkSoft,
                    modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "取消",
                        style = MaterialTheme.typography.labelLarge,
                        color = Dz.InkSoft,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Dz.Cream)
                            .clickable { confirmDelete = false }
                            .padding(vertical = 12.dp)
                    )
                    Text(
                        "删除",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Dz.Expense)
                            .clickable {
                                confirmDelete = false
                                onDelete()
                            }
                            .padding(vertical = 12.dp)
                    )
                }
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

internal fun kindToast(k: TxnKind): String = when (k) {
    TxnKind.REFUND -> "已标成退款，会从这一类支出里扣掉"
    TxnKind.INCOME -> "已标成收入"
    TxnKind.EXPENSE -> "已标成支出"
    TxnKind.TRANSFER -> "已标成转账"
}

@Composable
private fun KindChip(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = if (active) Color.White else Dz.InkSoft,
        modifier = Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (active) Dz.Sakura else Dz.Cream)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
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

/** 金额下限：空或乱填都当 0。 */
private fun parseMinYuan(s: String): Long =
    s.trim().toDoubleOrNull()?.let { maxOf(0L, (it * 100).toLong()) } ?: 0L

/** 金额上限：空当无穷大。 */
private fun parseMaxYuan(s: String): Long =
    s.trim().toDoubleOrNull()?.let { maxOf(0L, (it * 100).toLong()) } ?: Long.MAX_VALUE

/** 「筛选」小按钮，有生效条件时高亮。 */
@Composable
private fun FilterEntry(active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) Dz.Sakura else Dz.Card)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.Tune, null,
            tint = if (active) Color.White else Dz.InkSoft,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "筛选",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) Color.White else Dz.Ink
        )
    }
}

/** 金额区间 + 分类多选的筛选面板。全屏，跟次级页一个路数。 */
@Composable
private fun FilterDialog(
    time: Int,
    type: Int,
    minYuan: String,
    maxYuan: String,
    cats: Set<String>,
    onTime: (Int) -> Unit,
    onType: (Int) -> Unit,
    onMin: (String) -> Unit,
    onMax: (String) -> Unit,
    onToggleCat: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Dz.Cream)
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Dz.Card)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Close, "关闭",
                        tint = Dz.InkSoft,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text("筛选", style = MaterialTheme.typography.titleLarge, color = Dz.Ink)
                Spacer(Modifier.weight(1f))
                Text(
                    "重置全部",
                    style = MaterialTheme.typography.labelLarge,
                    color = Dz.SakuraDeep,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Dz.Card)
                        .clickable { onReset() }
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            // 卡片区自己滚，底部按钮固定在屏幕下方，小屏也点得到
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {

            // ---------- 时间 ----------
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Dz.Card)
                    .padding(14.dp)
            ) {
                Text("时间", style = MaterialTheme.typography.titleMedium, color = Dz.Ink)
                Text(
                    "选「按月份」就用标题上那对左右箭头翻月",
                    style = MaterialTheme.typography.bodySmall,
                    color = Dz.InkFaint,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CatChip("按月份", time == 0) { onTime(0) }
                    CatChip("本周", time == 2) { onTime(2) }
                    CatChip("本月", time == 3) { onTime(3) }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---------- 类型 ----------
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Dz.Card)
                    .padding(14.dp)
            ) {
                Text("类型", style = MaterialTheme.typography.titleMedium, color = Dz.Ink)
                Text(
                    "待确认 = 系统自动记下、但还没核对过的",
                    style = MaterialTheme.typography.bodySmall,
                    color = Dz.InkFaint,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CatChip("全部", type == 0) { onType(0) }
                    CatChip("支出", type == 1) { onType(1) }
                    CatChip("收入", type == 2) { onType(2) }
                    CatChip("待确认", type == 3) { onType(3) }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---------- 金额区间 ----------
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Dz.Card)
                    .padding(14.dp)
            ) {
                Text("金额区间", style = MaterialTheme.typography.titleMedium, color = Dz.Ink)
                Text(
                    "单位元，两位小数也认。只填一边也行，留空就是不限",
                    style = MaterialTheme.typography.bodySmall,
                    color = Dz.InkFaint,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AmountField(minYuan, "最低", onMin, Modifier.weight(1f))
                    Text("~", color = Dz.InkFaint, fontSize = 14.sp)
                    AmountField(maxYuan, "最高", onMax, Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---------- 分类 ----------
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Dz.Card)
                    .padding(14.dp)
            ) {
                Text("分类", style = MaterialTheme.typography.titleMedium, color = Dz.Ink)
                Text(
                    "不选就是全部。可以多选，比如「餐饮 + 购物」一起看",
                    style = MaterialTheme.typography.bodySmall,
                    color = Dz.InkFaint,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                )
                Text(
                    "支出",
                    style = MaterialTheme.typography.labelMedium,
                    color = Dz.InkFaint
                )
                Spacer(Modifier.height(6.dp))
                Categories.expenseAll.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { c ->
                            CatChip(c.name, c.id in cats) { onToggleCat(c.id) }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "收入",
                    style = MaterialTheme.typography.labelMedium,
                    color = Dz.InkFaint
                )
                Spacer(Modifier.height(6.dp))
                Categories.incomeAll.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { c ->
                            CatChip(c.name, c.id in cats) { onToggleCat(c.id) }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

                // 多留这一截，最后一行分类能滚到手指够得着的地方，
                // 而不是贴着屏幕底边 / 被手势条压住
                Spacer(Modifier.height(52.dp))
            }  // 卡片区结束

            // 重置已经挪到顶栏，底部不再放按钮，省得被手势条压住
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun AmountField(
    value: String,
    hint: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text(hint, fontSize = 12.sp, color = Dz.InkFaint) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = TextStyle(fontSize = 14.sp),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    )
}

@Composable
private fun RowScope.CatChip(name: String, active: Boolean, onClick: () -> Unit) {
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
            name,
            fontSize = 12.sp,
            maxLines = 1,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) Dz.SakuraDeep else Dz.InkSoft
        )
    }
}

/** 金额转成输入框里的文字：整元不补 .00 */
private fun yuanEdit(cents: Long): String =
    if (cents % 100 == 0L) (cents / 100).toString() else String.format(java.util.Locale.US, "%.2f", cents / 100.0)

/** 输入框里的金额转成分。解析不出来返回 null，别让人存进一个奇怪的数 */
private fun yuanToCents(text: String): Long? {
    val t = text.trim()
    if (t.isEmpty()) return null
    val d = t.toDoubleOrNull() ?: return null
    if (d <= 0.0 || d > 100_000_000.0) return null
    return Math.round(d * 100)
}
