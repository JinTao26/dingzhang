package com.dingzhang.app.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dingzhang.app.DingZhangApp
import com.dingzhang.app.BuildConfig
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
import com.dingzhang.app.ui.components.StarfieldBackdrop
import com.dingzhang.app.ui.components.UpdateDialog
import com.dingzhang.app.ui.theme.Dz
import com.dingzhang.app.ui.theme.ThemeController
import com.dingzhang.app.update.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    val incomeByCategory: List<CategoryTotal> = emptyList(),
    val daily: Map<Int, Long> = emptyMap(),
    val recent: List<Txn> = emptyList(),
    val monthTxns: List<Txn> = emptyList(),
    val daysInMonth: Int = 30,
    val budgetCents: Long = 0,
    val sourceCounts: Map<Source, Int> = emptyMap(),
    // ---- 年视图 ----
    val yearLabel: String = "",
    val yearExpense: Long = 0,
    val yearIncome: Long = 0,
    val yearCount: Int = 0,
    val yearByCategory: List<CategoryTotal> = emptyList(),
    val yearIncomeByCategory: List<CategoryTotal> = emptyList(),
    val monthly: Map<String, Long> = emptyMap(),
    val yearSourceCounts: Map<Source, Int> = emptyMap(),
    /** 全年流水，点分类看明细时用 */
    val yearTxns: List<Txn> = emptyList(),
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
    val incomeByCat = dao.sumIncomeByCategory(start, end)
    val daily = dao.dailyExpense(start, end)
    val todayList = dao.listBetween(todayStart, todayStart + 86_400_000L)
    val monthList = dao.listBetween(start, end)

    // 年汇总：统计页切「年」时用。同一次查询顺手做掉，本地库里几千行也不费时
    val yCal = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, 0)
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val yStart = yCal.timeInMillis
    yCal.add(Calendar.YEAR, 1)
    val yEnd = yCal.timeInMillis
    val (yExpense, yIncome) = dao.sumByKind(yStart, yEnd)
    val yByCat = dao.sumByCategory(yStart, yEnd)
    val yIncomeByCat = dao.sumIncomeByCategory(yStart, yEnd)

    return AppData(
        monthLabel = month.replace("-", "."),
        monthExpense = expense,
        monthIncome = income,
        monthCount = byCat.sumOf { it.count },
        // 今天花的钱要把今天的退款扣掉，否则「退款 30、又花 30」会被算成花了 30
        todayExpense = todayList.filter { it.kind == com.dingzhang.app.core.model.TxnKind.EXPENSE }
            .sumOf { it.amountCents } -
            todayList.filter { it.kind == com.dingzhang.app.core.model.TxnKind.REFUND }
                .sumOf { it.amountCents },
        todayCount = todayList.size,
        pendingCount = dao.countAuto(),
        byCategory = byCat,
        incomeByCategory = incomeByCat,
        daily = daily,
        recent = dao.recent(30),
        monthTxns = monthList,
        daysInMonth = days,
        budgetCents = settings?.monthlyBudgetCents ?: 0,
        sourceCounts = dao.countBySource(start, end),
        yearLabel = year.toString(),
        yearExpense = yExpense,
        yearIncome = yIncome,
        yearCount = yByCat.sumOf { it.count },
        yearByCategory = yByCat,
        yearIncomeByCategory = yIncomeByCat,
        monthly = dao.monthlyExpense(yStart, yEnd),
        yearSourceCounts = dao.countBySource(yStart, yEnd),
        yearTxns = dao.listBetween(yStart, yEnd),
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
    // 星空物语要多铺一层背景，其它主题走原来的纯色底
    val starry = ThemeController.current.id == "starfield"
    var tab by remember { mutableStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var month by remember { mutableStateOf(Dates.monthOf(System.currentTimeMillis())) }
    val data = rememberAppData(month)
    val ctx = LocalContext.current
    var autoUpdate by remember { mutableStateOf<UpdateChecker.Result?>(null) }

    // 检查更新。
    //
    // 以前只在冷启动查一次、失败还写掉时间戳，结果「装了两天一次都没看到提醒」：
    // App 常驻后台时几天都不会冷启动，加上国内直连 GitHub API 经常超时，
    // 两个原因叠在一起，界面上永远是空的。
    //
    // 现在改成常驻循环：进程活着就一直按 12 小时的节奏查；查成功才记时间，
    // 失败（网络不通）下次进前台还能再试，不会因为一次超时消停半天。
    LaunchedEffect(Unit) {
        val s = IngestPipeline.settings ?: return@LaunchedEffect
        while (true) {
            val due = s.autoCheckUpdate &&
                System.currentTimeMillis() - s.lastUpdateCheckAt >= 6L * 3600_000
            if (due) {
                delay(1200)   // 别跟首屏抢帧
                val r = try {
                    UpdateChecker.check(BuildConfig.VERSION_NAME)
                } catch (e: Exception) {
                    null
                }
                if (r != null && r.ok) {
                    s.lastUpdateCheckAt = System.currentTimeMillis()
                    // 点过「暂不更新」的版本不再弹，等下一版
                    if (r.hasUpdate && r.latestVersion != s.skipVersion) autoUpdate = r
                }
            }
            delay(30L * 60 * 1000)
        }
    }

    if (showAdd) {
        Box(Modifier.fillMaxSize().background(Dz.Cream)) {
            if (starry) StarfieldBackdrop(Modifier.fillMaxSize())
            AddScreen(
                onClose = { showAdd = false },
                onSaved = { showAdd = false }
            )
        }
        return
    }

    Box(Modifier.fillMaxSize().background(Dz.Cream)) {
        if (starry) StarfieldBackdrop(Modifier.fillMaxSize())
        Scaffold(
        containerColor = if (starry) Color.Transparent else Dz.Cream,
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
                2 -> StatsScreen(data, month, onMonthChange = { month = it })
                else -> MineScreen(data, month, onMonthChange = { month = it })
            }
        }
    }
    }

    // 开屏的更新提示。用户选「暂不更新」就记住这个版本，下次打开不再问
    autoUpdate?.let { r ->
        UpdateDialog(
            result = r,
            fromAuto = true,
            onDismiss = { autoUpdate = null },
            onSkipVersion = {
                IngestPipeline.settings?.skipVersion = r.latestVersion.orEmpty()
                autoUpdate = null
            },
            onDownload = {
                try {
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(r.apkUrl ?: r.pageUrl))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (e: Exception) {
                    // 没有浏览器就算了，别用 Toast 打断
                }
                autoUpdate = null
            }
        )
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
