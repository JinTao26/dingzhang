package com.dingzhang.app.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dingzhang.app.DingZhangApp
import com.dingzhang.app.core.model.CategoryTotal
import com.dingzhang.app.core.model.Dates
import com.dingzhang.app.core.model.LearnedRule
import com.dingzhang.app.core.model.Source
import com.dingzhang.app.core.model.Txn
import com.dingzhang.app.data.RecurringDetector
import com.dingzhang.app.data.RecurringItem
import com.dingzhang.app.ingest.IngestPipeline
import com.dingzhang.app.ui.screens.AddScreen
import com.dingzhang.app.ui.screens.BillsScreen
import com.dingzhang.app.ui.screens.HomeScreen
import com.dingzhang.app.ui.screens.MineScreen
import com.dingzhang.app.ui.screens.StatsScreen
import com.dingzhang.app.ui.theme.Dz
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/** 一次性把界面要用的数据查出来。本地 SQLite，几十条记录量级。 */
data class AppData(
    val monthLabel: String = "",
    val monthExpense: Long = 0,
    val monthIncome: Long = 0,
    val monthCount: Int = 0,
    val todayExpense: Long = 0,
    val todayCount: Int = 0,
    val pendingCount: Int = 0,
    val byCategory: List<CategoryTotal> = emptyList(),
    val daily: Map<Int, Long> = emptyMap(),
    val recent: List<Txn> = emptyList(),
    val monthTxns: List<Txn> = emptyList(),
    val daysInMonth: Int = 30,
    val budgetCents: Long = 0,
    val sourceCounts: Map<Source, Int> = emptyMap(),
    val rules: List<LearnedRule> = emptyList(),
    val ignoredPayees: Set<String> = emptySet(),
    val lastImportAt: Long = 0,
    val recurring: List<RecurringItem> = emptyList()
)

@Composable
fun rememberAppData(month: String): AppData {
    val version by DingZhangApp.dataVersion.collectAsState()
    val state by produceState(initialValue = AppData(monthLabel = month), version, month) {
        value = withContext(Dispatchers.IO) { loadAppData(month) }
    }
    return state
}

private fun loadAppData(month: String): AppData {
    val dao = IngestPipeline.dao ?: return AppData(monthLabel = month)
    val settings = IngestPipeline.settings
    val now = System.currentTimeMillis()

    val year = month.substringBefore("-").toIntOrNull() ?: 2026
    val mon = month.substringAfter("-").toIntOrNull() ?: 1
    val cal = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, mon - 1)
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val start = cal.timeInMillis
    val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    cal.add(Calendar.MONTH, 1)
    val end = cal.timeInMillis

    val todayStart = Dates.startOfDay(now)
    val (expense, income) = dao.sumByKind(start, end)
    val byCat = dao.sumByCategory(start, end)
    val daily = dao.dailyExpense(start, end)
    val todayList = dao.listBetween(todayStart, todayStart + 86_400_000L)
    val monthList = dao.listBetween(start, end)

    return AppData(
        monthLabel = month.replace("-", "."),
        monthExpense = expense,
        monthIncome = income,
        monthCount = byCat.sumOf { it.count },
        todayExpense = todayList.filter { it.kind == com.dingzhang.app.core.model.TxnKind.EXPENSE }
            .sumOf { it.amountCents },
        todayCount = todayList.size,
        pendingCount = dao.countAuto(),
        byCategory = byCat,
        daily = daily,
        recent = dao.recent(30),
        monthTxns = monthList,
        daysInMonth = days,
        budgetCents = settings?.monthlyBudgetCents ?: 0,
        sourceCounts = dao.countBySource(start, end),
        rules = dao.rules(),
        ignoredPayees = settings?.ignoredPayees() ?: emptySet(),
        lastImportAt = settings?.lastBillImportAt ?: 0,
        // 订阅要跨月才看得出来，回看 500 天（最长周期按年算）
        recurring = RecurringDetector.detect(
            dao.listBetween(now - 500L * 86_400_000L, now + 86_400_000L),
            now
        )
    )
}

@Composable
fun AppRoot() {
    var tab by remember { mutableStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var month by remember { mutableStateOf(Dates.monthOf(System.currentTimeMillis())) }
    val data = rememberAppData(month)

    if (showAdd) {
        AddScreen(
            onClose = { showAdd = false },
            onSaved = { showAdd = false }
        )
        return
    }

    Scaffold(
        containerColor = Dz.Cream,
        bottomBar = { DzBottomBar(tab) { tab = it } },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAdd = true },
                containerColor = Dz.Sakura,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .size(58.dp)
                    .padding(bottom = 6.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "记一笔", modifier = Modifier.size(26.dp))
            }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { pad ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(pad)
        ) {
            when (tab) {
                0 -> HomeScreen(data, month, onMonthChange = { month = it }, onGoTab = { tab = it })
                1 -> BillsScreen(data, month, onMonthChange = { month = it })
                2 -> StatsScreen(data)
                else -> MineScreen(data, month, onMonthChange = { month = it })
            }
        }
    }
}

private data class TabItem(val icon: ImageVector, val label: String)

/** "2026-11" 前后翻月 */
internal fun shiftMonth(month: String, delta: Int): String {
    val y = month.substringBefore("-").toIntOrNull()
    val m = month.substringAfter("-").toIntOrNull()
    if (y == null || m == null) return Dates.monthOf(System.currentTimeMillis())
    val cal = Calendar.getInstance().apply {
        set(Calendar.YEAR, y)
        set(Calendar.MONTH, m - 1)
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MONTH, delta)
    }
    return Dates.monthOf(cal.timeInMillis)
}

private val tabs = listOf(
    TabItem(Icons.Rounded.Home, "首页"),
    TabItem(Icons.Rounded.ReceiptLong, "明细"),
    TabItem(Icons.Rounded.PieChart, "统计"),
    TabItem(Icons.Rounded.Person, "我的")
)

@Composable
private fun DzBottomBar(current: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Dz.Cream)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, item ->
            val on = current == index
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (on) Dz.SakuraSoft else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    item.icon,
                    contentDescription = item.label,
                    tint = if (on) Dz.SakuraDeep else Dz.InkFaint,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    item.label,
                    fontSize = 11.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) Dz.SakuraDeep else Dz.InkFaint
                )
            }
        }
    }
}
