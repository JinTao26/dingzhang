package com.dingzhang.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.window.Dialog
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
    val yearBudgetCents: Long = 0,
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
        yearBudgetCents = settings?.yearBudgetCents ?: 0,
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

/** 直接给 apk 直链，点开就能下；浏览器都没法用就提示页面地址。 */
private fun openRelease(ctx: android.content.Context, r: UpdateChecker.Result) {
    val url = r.apkUrl ?: r.pageUrl
    try {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        android.widget.Toast.makeText(
            ctx,
            "打不开浏览器。可以手动打开这个地址下载：${r.pageUrl}",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }
}

@Composable
fun AppRoot() {
    // 星空物语要多铺一层背景，其它主题走原来的纯色底
    val starry = ThemeController.current.id == "starfield"
    var tab by remember { mutableStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var month by remember { mutableStateOf(Dates.monthOf(System.currentTimeMillis())) }
    // 明细进批量模式时，让「记一笔」的加号躲开，省得压住底部的批量条
    var billsSelect by remember { mutableStateOf(false) }
    // 第一次进来给个说明，只弹一次
    var showIntro by remember { mutableStateOf(!(IngestPipeline.settings?.introSeen ?: true)) }
    val data = rememberAppData(month)
    val ctx = LocalContext.current

    // 记账后的检查负责「不打开 App 也能提醒」，这里补一次「打开 App 时兜底」
    LaunchedEffect(Unit) { runCatching { com.dingzhang.app.core.BudgetAlert.check() } }
    // 通知监听的 binder 掉了能自己拉回来，每次都顺手试一下（无害）
    LaunchedEffect(Unit) {
        runCatching { com.dingzhang.app.ingest.ChannelHealth.ensureNotificationListening(ctx) }
    }

    // 开屏查一次新版。以前这段被删掉过，加上更新源又只打 api.github.com（手机上基本连不上），
    // 所以用户那边从来没收到过提醒。现在源换成了 CDN 上的清单，这里补回来。
    var autoUpdate by remember { mutableStateOf<UpdateChecker.Result?>(null) }
    // 每次回到前台都重新判一次，而不是只在整个进程起来时查一次。
    // 这台手机上的 App 常常挂后台好几天不重开，只在冷启动查等于「永远不提醒」。
    var resumeTick by remember { mutableStateOf(0) }
    val lcOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lcOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lcOwner.lifecycle.addObserver(obs)
        onDispose { lcOwner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(resumeTick) {
        val last = runCatching { IngestPipeline.settings?.lastUpdateCheckAt ?: 0L }.getOrDefault(0L)
        // 一小时查一次就够了：清单只有 500 字节，查一次几乎没成本
        if (System.currentTimeMillis() - last < 60 * 60 * 1000L) return@LaunchedEffect
        val r = runCatching {
            withContext(Dispatchers.IO) { UpdateChecker.check(BuildConfig.VERSION_NAME) }
        }.getOrNull()
        if (r != null && r.ok) {
            runCatching { IngestPipeline.settings?.lastUpdateCheckAt = System.currentTimeMillis() }
        }
        if (r != null && r.hasUpdate) {
            // 用户点过「暂不更新」就安静待着，等下一版再来问
            val skipped = runCatching { IngestPipeline.settings?.skipVersion ?: "" }.getOrDefault("")
            if (r.latestVersion != skipped) autoUpdate = r
        }
    }

    if (showIntro) {
        IntroDialog {
            IngestPipeline.settings?.introSeen = true
            showIntro = false
        }
    }

    autoUpdate?.let { r ->
        UpdateDialog(
            result = r,
            fromAuto = true,
            onDismiss = { autoUpdate = null },
            onSkipVersion = {
                runCatching { IngestPipeline.settings?.skipVersion = r.latestVersion.orEmpty() }
                autoUpdate = null
            },
            onDownload = {
                openRelease(ctx, r)
                autoUpdate = null
            }
        )
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
            if (!(tab == 1 && billsSelect)) {
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
                1 -> BillsScreen(
                    data, month,
                    onMonthChange = { month = it },
                    onSelectModeChange = { billsSelect = it }
                )
                2 -> StatsScreen(data, month, onMonthChange = { month = it })
                else -> MineScreen(data, month, onMonthChange = { month = it })
            }
        }
    }

        // 删掉的账能撤销。删一笔要滑到底、点确认，手滑一下就没了：
        // 分类、商户名、发生时间全跟着丢，只能重新记一遍。
        UndoBuffer.lastDeleted?.let { gone ->
            UndoBar(
                txn = gone,
                onUndo = {
                    IngestPipeline.dao?.restore(gone)
                    DingZhangApp.bumpDataVersion()
                    UndoBuffer.consume()
                },
                onExpire = { UndoBuffer.consume() }
            )
        }
    }
}


/** 装好第一次打开：告诉他权限在哪开、帮助在哪、更新在哪，只弹这一次。 */
@Composable
private fun IntroDialog(onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Dz.Card)
                .padding(20.dp)
        ) {
            Text("叮账装好了", style = MaterialTheme.typography.headlineLarge, color = Dz.Ink)
            Text(
                "三件事，两分钟就能用起来",
                style = MaterialTheme.typography.bodySmall,
                color = Dz.InkFaint,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(16.dp))
            IntroLine("1", "开权限", "「我的」→「自动记账通道」，按提示打开无障碍和通知监听，之后付款就自动记账")
            IntroLine("2", "看帮助", "微信、支付宝怎么导账单、权限怎么开，都写在「我的」→「更多」→「帮助」里")
            IntroLine("3", "查更新", "新版本在「我的」最下面「检查更新」，每一版改了什么也写在里面")
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onClose,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Dz.Sakura,
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            ) { Text("知道了") }
        }
    }
}

@Composable
private fun IntroLine(no: String, title: String, body: String) {
    Row(Modifier.padding(bottom = 12.dp)) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Dz.SakuraSoft),
            contentAlignment = Alignment.Center
        ) {
            Text(no, style = MaterialTheme.typography.labelMedium, color = Dz.SakuraDeep)
        }
        Column(Modifier.padding(start = 10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Dz.Ink)
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = Dz.InkSoft,
                modifier = Modifier.padding(top = 2.dp)
            )
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

/**
 * 刚删掉的那一笔。三个屏（首页、明细、统计）共用一份，所以放在这里而不是各自的 state。
 * 8 秒后自动作废 —— 够长到能反应过来，又不会一直挂着挡视线。
 */
object UndoBuffer {
    var lastDeleted by mutableStateOf<Txn?>(null)
        private set
    private var at = 0L

    fun put(t: Txn) {
        lastDeleted = t
        at = System.currentTimeMillis()
    }

    fun consume() {
        lastDeleted = null
        at = 0L
    }

    fun expired(now: Long = System.currentTimeMillis()): Boolean =
        lastDeleted == null || now - at > 8000L
}

@Composable
private fun BoxScope.UndoBar(txn: Txn, onUndo: () -> Unit, onExpire: () -> Unit) {
    LaunchedEffect(txn.id) {
        kotlinx.coroutines.delay(8000)
        if (UndoBuffer.lastDeleted === txn) onExpire()
    }
    Row(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 96.dp, start = 20.dp, end = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Dz.Ink.copy(alpha = 0.92f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "已删除「${txn.payee.take(12)}」",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
        Text(
            "撤销",
            style = MaterialTheme.typography.titleMedium,
            color = Dz.Sakura,
            modifier = Modifier.clickable { onUndo() }
        )
    }
}
