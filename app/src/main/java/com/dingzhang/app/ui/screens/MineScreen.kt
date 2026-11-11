package com.dingzhang.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationManagerCompat
import com.dingzhang.app.DingZhangApp
import com.dingzhang.app.BuildConfig
import com.dingzhang.app.core.model.Categories
import com.dingzhang.app.core.model.Dates
import com.dingzhang.app.core.model.Money
import com.dingzhang.app.data.Exporter
import com.dingzhang.app.ingest.BillImporter
import com.dingzhang.app.ingest.ImportResult
import com.dingzhang.app.ingest.IngestLog
import com.dingzhang.app.ingest.IngestPipeline
import com.dingzhang.app.share.ShareKit
import com.dingzhang.app.ui.AppData
import com.dingzhang.app.ui.components.DzCard
import com.dingzhang.app.ui.components.MascotFace
import com.dingzhang.app.ui.components.UpdateDialog
import com.dingzhang.app.ui.theme.Dz
import com.dingzhang.app.ui.theme.ThemeController
import com.dingzhang.app.ui.theme.ThemePack
import com.dingzhang.app.ui.theme.ThemePacks
import com.dingzhang.app.update.UpdateChecker
import kotlinx.coroutines.launch

@Composable
fun MineScreen(data: AppData, month: String, onMonthChange: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = IngestPipeline.settings

    var importing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ImportResult?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var budgetEdit by remember { mutableStateOf(0) }   // 0 关，1 每月，2 每年
    var budgetInput by remember { mutableStateOf("") }

    // 预算自己填：常用档给四个快选，想填多少填多少
    if (budgetEdit != 0) {
        Dialog(onDismissRequest = { budgetEdit = 0 }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Dz.Card)
                    .padding(18.dp)
            ) {
                Text(
                    when (budgetEdit) {
                        1 -> "每月预算"
                        2 -> "每年预算"
                        else -> "预算提醒"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = Dz.Ink
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = budgetInput,
                    onValueChange = { v ->
                        budgetInput = v.filter { it.isDigit() }.take(if (budgetEdit == 3) 3 else 7)
                    },
                    label = { Text("整元，不用填小数") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (budgetEdit == 3) {
                        "花到月预算的这个百分比就在通知栏提醒一次，0 就是关掉提醒。一个月最多提醒两次"
                    } else {
                        "填 0 就是不设。每月预算会出现在首页的「预算还剩」上"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Dz.InkFaint
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { budgetEdit = 0 }) {
                        Text("取消", color = Dz.InkSoft)
                    }
                    TextButton(onClick = {
                        val n = budgetInput.toLongOrNull() ?: 0L
                        if (budgetEdit == 3) {
                            settings?.budgetAlertPercent = n.toInt().coerceIn(0, 100)
                        } else {
                            val y = n * 100
                            if (budgetEdit == 1) {
                                settings?.monthlyBudgetCents = y
                            } else {
                                settings?.yearBudgetCents = y
                            }
                        }
                        budgetEdit = 0
                        DingZhangApp.bumpDataVersion()
                    }) {
                        Text("保存", color = Dz.SakuraDeep, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // 0 = 主页，1 = 意见反馈，2 = 自动记账原理
    var subPage by remember { mutableStateOf(0) }

    // 每次进入本页实时读系统权限状态，用户从设置页回来就能看到变化
    val a11yOn = isA11yEnabled(ctx)
    val nlsOn = isNlsEnabled(ctx)
    // 「系统里勾着」和「真的在跑」是两件事，这里分开判断
    val a11yDead = com.dingzhang.app.ingest.ChannelHealth.a11yLooksDead(ctx)
    val a11yIdleMs = com.dingzhang.app.ingest.ChannelHealth.a11yIdleMs()

    // 银行短信要一条运行时权限；部分机型（小米/华为等）需要用户自己去系统设置里放开
    var smsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECEIVE_SMS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val smsPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        smsGranted = granted
        if (granted) {
            settings?.smsChannel = true
            DingZhangApp.bumpDataVersion()
        } else {
            Toast.makeText(
                ctx,
                "短信权限没给成。到 系统设置 → 应用 → 叮账 → 权限 里手动允许「短信」再来开这条",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            importing = true
            val r = BillImporter.import(ctx, uri)
            importing = false
            result = r
            DingZhangApp.bumpDataVersion()
        }
    }

    var checking by remember { mutableStateOf(false) }
    var lastCheck by remember { mutableStateOf(settings?.lastUpdateCheckAt ?: 0L) }
    var skippedVer by remember { mutableStateOf(settings?.skipVersion.orEmpty()) }
    var updateResult by remember { mutableStateOf<UpdateChecker.Result?>(null) }

    // 导出走系统的「新建文档」，用户自己选存哪儿，不申请存储权限
    val csvSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val n = try {
                Exporter.writeCsv(ctx, uri)
            } catch (e: Exception) {
                -1
            }
            Toast.makeText(
                ctx,
                if (n > 0) "已导出 $n 笔到 CSV" else "导出失败了",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val backupSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val n = try {
                Exporter.writeBackup(ctx, uri)
            } catch (e: Exception) {
                -1
            }
            Toast.makeText(
                ctx,
                if (n > 0) "已备份 $n 笔（含分类规则）" else "备份失败了",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val r = Exporter.restore(ctx, uri)
            DingZhangApp.bumpDataVersion()
            Toast.makeText(ctx, r.message, Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 96.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("我的", style = MaterialTheme.typography.headlineLarge, color = Dz.Ink)
                Text(
                    "让自动记账更准一点的开关都在这儿",
                    style = MaterialTheme.typography.bodySmall,
                    color = Dz.InkFaint,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            MascotFace(52.dp)
        }

        Spacer(Modifier.height(16.dp))

        // ---------- 通道 ----------
        DzCard {
            Text("自动记账通道", style = MaterialTheme.typography.titleLarge, color = Dz.Ink)
            Spacer(Modifier.height(12.dp))

            ChannelRow(
                title = "无障碍读取",
                desc = "读微信 / 支付宝的支付结果页和账单详情页；淘宝、闲鱼、京东、拼多多里直接付掉的钱也能认出来。能拿到商户、金额和单号",
                systemGranted = a11yOn,
                appEnabled = settings?.accessibilityChannel ?: true,
                onToggle = { v ->
                    settings?.accessibilityChannel = v
                    DingZhangApp.bumpDataVersion()
                },
                onGrant = { openSystem(ctx, android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS) }
            )
            if (a11yOn && a11yDead) {
                Text(
                    "系统里是开着的，但已经很久没收到过页面事件了，多半被系统停掉了。点这里关掉再开一次",
                    fontSize = 11.sp,
                    color = Dz.SakuraDeep,
                    modifier = Modifier
                        .padding(top = 4.dp, start = 2.dp)
                        .clickable {
                            openSystem(ctx, android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        }
                )
            } else if (a11yOn && a11yIdleMs in 1..86_400_000L) {
                Text(
                    "上次收到页面事件：${a11yIdleMs / 60_000} 分钟前",
                    fontSize = 11.sp,
                    color = Dz.InkFaint,
                    modifier = Modifier.padding(top = 4.dp, start = 2.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            ChannelRow(
                title = "通知监听",
                desc = "解析支付通知做实时兜底。部分机型微信只推「支付凭证」不带金额，所以只当补充",
                systemGranted = nlsOn,
                appEnabled = settings?.notificationChannel ?: true,
                onToggle = { v ->
                    settings?.notificationChannel = v
                    DingZhangApp.bumpDataVersion()
                },
                onGrant = { openSystem(ctx, android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }
            )
            Spacer(Modifier.height(6.dp))
            ChannelRow(
                title = "银行推送 / 公众号",
                desc = "手机银行 App 的动账推送、银行公众号的微信提醒。短信开始收费以后，走这条更省",
                systemGranted = nlsOn,
                appEnabled = settings?.bankPushChannel ?: true,
                onToggle = { v ->
                    settings?.bankPushChannel = v
                    DingZhangApp.bumpDataVersion()
                },
                onGrant = { openSystem(ctx, android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }
            )
            Spacer(Modifier.height(6.dp))
            ChannelRow(
                title = "银行短信",
                desc = "工资到账、线下刷卡这类不经过微信支付宝的钱，从银行动账短信里读。只收实时广播，不读历史短信箱，余额和完整卡号不入库",
                systemGranted = smsGranted,
                appEnabled = settings?.smsChannel ?: false,
                onToggle = { v ->
                    if (v && !smsGranted) {
                        smsPermission.launch(Manifest.permission.RECEIVE_SMS)
                    } else {
                        settings?.smsChannel = v
                        DingZhangApp.bumpDataVersion()
                    }
                },
                onGrant = { smsPermission.launch(Manifest.permission.RECEIVE_SMS) }
            )
        }

        Spacer(Modifier.height(12.dp))

        // ---------- 导入账单 ----------
        DzCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("导入官方账单", style = MaterialTheme.typography.titleLarge, color = Dz.Ink)
                Spacer(Modifier.weight(1f))
                if (data.lastImportAt > 0) {
                    Text(
                        Dates.relativeLabel(data.lastImportAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = Dz.InkFaint
                    )
                }
            }
            Text(
                "支持 zip · csv · txt · xlsx",
                style = MaterialTheme.typography.labelMedium,
                color = Dz.InkFaint,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { picker.launch(arrayOf("*/*")) },
                enabled = !importing,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Dz.Sakura, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (importing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("正在导入…")
                } else {
                    Icon(Icons.Rounded.FileDownload, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("选择账单文件")
                }
            }
            result?.let { r ->
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (r.dataRows > 0) Dz.MintSoft else Dz.WarnSoft)
                        .padding(12.dp)
                ) {
                    Text(r.fileName, style = MaterialTheme.typography.labelMedium, color = Dz.Ink, maxLines = 1)
                    Text(
                        r.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = Dz.InkSoft,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    val range = BillImporter.describeRange(r.earliest, r.latest)
                    if (range.isNotBlank()) {
                        Text(range, style = MaterialTheme.typography.labelSmall, color = Dz.InkFaint)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---------- 预算 ----------
        DzCard {
            Text("预算", style = MaterialTheme.typography.titleLarge, color = Dz.Ink)
            Spacer(Modifier.height(10.dp))

            BudgetRow(
                label = "每月预算",
                cents = settings?.monthlyBudgetCents ?: 0L,
                quick = listOf(
                    1_000_00L to "1000", 3_000_00L to "3000",
                    5_000_00L to "5000", 10_000_00L to "1万"
                ),
                onQuick = {
                    settings?.monthlyBudgetCents = it
                    DingZhangApp.bumpDataVersion()
                },
                onCustom = {
                    budgetInput = ((settings?.monthlyBudgetCents ?: 0L) / 100)
                        .takeIf { it > 0 }?.toString() ?: ""
                    budgetEdit = 1
                }
            )

            Spacer(Modifier.height(16.dp))

            BudgetRow(
                label = "每年预算",
                cents = settings?.yearBudgetCents ?: 0L,
                quick = listOf(
                    30_000_00L to "3万", 50_000_00L to "5万", 100_000_00L to "10万"
                ),
                onQuick = {
                    settings?.yearBudgetCents = it
                    DingZhangApp.bumpDataVersion()
                },
                onCustom = {
                    budgetInput = ((settings?.yearBudgetCents ?: 0L) / 100)
                        .takeIf { it > 0 }?.toString() ?: ""
                    budgetEdit = 2
                }
            )

            Spacer(Modifier.height(16.dp))

            val alertPct = settings?.budgetAlertPercent ?: 80
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("预算提醒", style = MaterialTheme.typography.bodyMedium, color = Dz.InkSoft)
                Spacer(Modifier.weight(1f))
                Text(
                    if (alertPct > 0) "花到 $alertPct% 时提醒" else "已关闭",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (alertPct > 0) Dz.Ink else Dz.InkFaint
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(50, 80, 100).forEach { p ->
                    SettingChip(label = "$p%", active = alertPct == p) {
                        settings?.budgetAlertPercent = p
                        DingZhangApp.bumpDataVersion()
                    }
                }
                SettingChip(label = "自定义", active = alertPct !in listOf(0, 50, 80, 100)) {
                    budgetInput = alertPct.toString()
                    budgetEdit = 3
                }
                SettingChip(label = "关闭", active = alertPct == 0) {
                    settings?.budgetAlertPercent = 0
                    DingZhangApp.bumpDataVersion()
                }
            }

            if (data.ignoredPayees.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("不记账的商户", style = MaterialTheme.typography.bodyMedium, color = Dz.InkSoft)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    data.ignoredPayees.take(6).forEach { p ->
                        Text(
                            p,
                            fontSize = 11.sp,
                            color = Dz.InkSoft,
                            modifier = Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .background(Dz.Cream)
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---------- 外观 ----------
        DzCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("外观", style = MaterialTheme.typography.titleLarge, color = Dz.Ink)
                Spacer(Modifier.weight(1f))
                Text(
                    ThemeController.current.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = Dz.SakuraDeep
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemePacks.ALL.forEach { pack ->
                    ThemeSwatch(
                        pack = pack,
                        active = pack.id == ThemeController.current.id,
                        modifier = Modifier.weight(1f)
                    ) {
                        ThemeController.select(ctx, pack.id)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                ThemeController.current.tagline,
                style = MaterialTheme.typography.labelSmall,
                color = Dz.InkFaint
            )
        }

        Spacer(Modifier.height(12.dp))

        // ---------- 数据与备份 ----------
        DzCard {
            Text("你的数据，你带走", style = MaterialTheme.typography.titleLarge, color = Dz.Ink)
            Spacer(Modifier.height(10.dp))
            ActionRow("导出明细 CSV", "给 Excel 或别的记账 App 用") {
                csvSaver.launch(Exporter.defaultCsvName())
            }
            Spacer(Modifier.height(6.dp))
            ActionRow("完整备份 JSON", "含流水、分类规则和预算设置") {
                backupSaver.launch(Exporter.defaultBackupName())
            }
            Spacer(Modifier.height(6.dp))
            ActionRow("从备份恢复", "已存在的记录自动跳过，不会记重") {
                restorePicker.launch(arrayOf("application/json", "*/*"))
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---------- 两个次级页面的入口 ----------
        DzCard {
            Text("更多", style = MaterialTheme.typography.titleLarge, color = Dz.Ink)
            Spacer(Modifier.height(10.dp))
            ActionRow("分享给朋友", "发到微信、QQ 都行，只带下载地址") { ShareKit.share(ctx) }
            Spacer(Modifier.height(6.dp))
            ActionRow("帮助", "微信和支付宝怎么导账单、各项权限怎么开") { subPage = 3 }
            Spacer(Modifier.height(6.dp))
            ActionRow("意见反馈", "发到作者邮箱，自动带上机型和各通道开关") { subPage = 1 }
            Spacer(Modifier.height(6.dp))
            ActionRow("自动记账原理与限制", "五条通道各管什么、哪些做不到") { subPage = 2 }
        }

        // ---------- 危险操作 ----------
        DzCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("清空所有流水", style = MaterialTheme.typography.titleMedium, color = Dz.Ink)
                    Text(
                        "共 ${data.monthTxns.size} 笔（本月）。清空后不可恢复",
                        style = MaterialTheme.typography.bodySmall,
                        color = Dz.InkFaint
                    )
                }
                Text(
                    "清空",
                    style = MaterialTheme.typography.labelLarge,
                    color = Dz.Expense,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Dz.Expense.copy(alpha = 0.10f))
                        .clickable { confirmClear = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "叮账 ${BuildConfig.VERSION_NAME} · 数据只存在这台手机，不上传",
                        style = MaterialTheme.typography.labelSmall,
                        color = Dz.InkFaint
                    )
                    Text(
                        "上次检查：${agoText(lastCheck)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Dz.InkFaint,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                    if (skippedVer.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "已跳过 $skippedVer 的更新提示",
                                style = MaterialTheme.typography.labelSmall,
                                color = Dz.Warn
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "恢复提示",
                                style = MaterialTheme.typography.labelSmall,
                                color = Dz.SakuraDeep,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        settings?.skipVersion = ""
                                        skippedVer = ""
                                        Toast.makeText(ctx, "已恢复更新提示", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    if (checking) "检查中…" else "检查更新",
                    style = MaterialTheme.typography.labelMedium,
                    color = Dz.SakuraDeep,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = !checking) {
                            scope.launch {
                                checking = true
                                val r = UpdateChecker.check(BuildConfig.VERSION_NAME)
                                if (r.ok) {
                                    settings?.lastUpdateCheckAt = System.currentTimeMillis()
                                    lastCheck = System.currentTimeMillis()
                                    if (r.latestVersion != null && r.latestVersion != skippedVer) {
                                        settings?.skipVersion = ""
                                        skippedVer = ""
                                    }
                                }
                                updateResult = r
                                checking = false
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }

    if (confirmClear) {
        Dialog(onDismissRequest = { confirmClear = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(
                Modifier
                    .fillMaxWidth(0.9f)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Dz.Card)
                    .padding(20.dp)
            ) {
                Text("确定要清空吗", style = MaterialTheme.typography.titleLarge, color = Dz.Ink)
                Text(
                    "所有流水都会被删掉，分类规则会保留。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Dz.InkSoft,
                    modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Dz.Cream)
                            .clickable { confirmClear = false }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("算了", style = MaterialTheme.typography.labelLarge, color = Dz.InkSoft)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Dz.Expense)
                            .clickable {
                                IngestPipeline.dao?.clearAll()
                                DingZhangApp.bumpDataVersion()
                                confirmClear = false
                                Toast.makeText(ctx, "已清空", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("清空", style = MaterialTheme.typography.labelLarge, color = Color.White)
                    }
                }
            }
        }
    }

    // ---------- 次级页面 ----------
    if (subPage == 1) {
        Dialog(
            onDismissRequest = { subPage = 0 },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            FeedbackPage(ctx) { subPage = 0 }
        }
    }
    if (subPage == 2) {
        Dialog(
            onDismissRequest = { subPage = 0 },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            PrinciplePage { subPage = 0 }
        }
    }
    if (subPage == 3) {
        Dialog(
            onDismissRequest = { subPage = 0 },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            HelpPage { subPage = 0 }
        }
    }

    // ---------- 版本更新 ----------
    // 弹窗和开屏提示共用同一套，免得两处文案越改越不一样
    updateResult?.let { r ->
        UpdateDialog(
            result = r,
            fromAuto = false,
            onDismiss = { updateResult = null },
            onSkipVersion = { IngestPipeline.settings?.skipVersion = r.latestVersion.orEmpty() },
            onDownload = {
                openUrl(ctx, r.apkUrl ?: r.pageUrl)
                updateResult = null
            }
        )
    }
}

@Composable
private fun ActionRow(title: String, desc: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Dz.Cream)
            .clickable { onClick() }
            .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Dz.Ink)
            Text(desc, style = MaterialTheme.typography.labelSmall, color = Dz.InkFaint)
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = Dz.InkSoft,
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun openUrl(ctx: Context, url: String) {
    try {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        Toast.makeText(ctx, "打不开链接：$url", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun ChannelRow(
    title: String,
    desc: String,
    systemGranted: Boolean,
    appEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onGrant: () -> Unit
) {
    // 开关点下去得立刻动，不能等下次刷新数据才追上
    var on by remember(appEnabled) { mutableStateOf(appEnabled) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Dz.Cream)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            !on -> Dz.InkFaint
                            systemGranted -> Dz.Mint
                            else -> Dz.Warn
                        }
                    )
            )
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Dz.Ink)
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = Dz.InkFaint
                )
            }
            Switch(
                checked = on,
                onCheckedChange = {
                    on = it
                    onToggle(it)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Dz.Sakura
                )
            )
        }
        if (!systemGranted) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Dz.WarnSoft)
                    .clickable { onGrant() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "系统还没授权，点这里去开",
                    style = MaterialTheme.typography.bodySmall,
                    color = Dz.Warn,
                    modifier = Modifier.weight(1f)
                )
                Text("去开启", style = MaterialTheme.typography.labelMedium, color = Dz.Warn)
            }
        }
    }
}

/** 反馈正文带上环境信息：用户描述「它不记账」时，八成是某条通道没开。 */
private fun feedbackBody(ctx: Context, text: String): String {
    val s = IngestPipeline.settings
    fun on(v: Boolean?) = if (v == true) "开" else "关"
    return buildString {
        appendLine(text.trim())
        appendLine()
        appendLine("---")
        appendLine("版本：${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("设备：${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.SDK_INT}")
        appendLine(
            "通道：无障碍 ${on(s?.accessibilityChannel)} / 通知 ${on(s?.notificationChannel)}" +
                " / 银行推送 ${on(s?.bankPushChannel)} / 短信 ${on(s?.smsChannel)}"
        )
    }
}

/** 反馈直发作者邮箱。不走 GitHub —— 国内不一定连得上。 */
private const val FEEDBACK_MAIL = "2462634730@qq.com"

private fun submitFeedback(ctx: Context, text: String) {
    if (text.isBlank()) return
    val subject = "叮账反馈：" + text.trim().take(24)
    val body = feedbackBody(ctx, text)

    // 收件人写进 mailto 的 URI，同时也塞一份 EXTRA_EMAIL：
    // 各家邮件 App 认的不一样，有的一看 URI 就把收件人带进去了，有的只读 EXTRA_，
    // 以前只给 URI，碰上不认的那家打开就是个空草稿，收件人栏还得自己填。
    val uri = Uri.parse(
        "mailto:" + FEEDBACK_MAIL +
            "?subject=" + Uri.encode(subject) +
            "&body=" + Uri.encode(body)
    )
    val mail = Intent(Intent.ACTION_SENDTO, uri).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_MAIL))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    try {
        ctx.startActivity(mail.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        // 没装邮件 App，或者装了但没登录账号 —— 这时候跳过去也是个发不出去的草稿，
        // 直接给可粘贴的整段（带收件人），随便找个地方发出去都能到
        copyFeedback(ctx, withRecipient(text, subject, body))
        Toast.makeText(
            ctx,
            "没有可用的邮件 App。收件地址和内容已一起复制，粘到任意邮箱或聊天窗口发出去就行",
            Toast.LENGTH_LONG
        ).show()
    }
}

/** 复制用：把收件人和主题写在最前面，粘出去以后一眼知道发给谁。 */
private fun withRecipient(text: String, subject: String, body: String): String =
    "收件人：" + FEEDBACK_MAIL + "\n主题：" + subject + "\n\n" + body


private fun copyFeedback(ctx: Context, text: String) {
    if (text.isBlank()) return
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    cm.setPrimaryClip(android.content.ClipData.newPlainText("叮账反馈", text))
    Toast.makeText(ctx, "已复制", Toast.LENGTH_SHORT).show()
}

@Composable
private fun ThemeSwatch(
    pack: ThemePack,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val base = modifier
        .clip(shape)
        .background(pack.bg)
    val box = if (active) base.border(1.5.dp, pack.primary, shape) else base

    Column(box.clickable { onClick() }.padding(horizontal = 8.dp, vertical = 9.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(pack.primary, pack.secondary, pack.accent).forEach { c ->
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(c)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            pack.name,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = pack.ink,
            maxLines = 1
        )
    }
}

@Composable
private fun BudgetRow(
    label: String,
    cents: Long,
    quick: List<Pair<Long, String>>,
    onQuick: (Long) -> Unit,
    onCustom: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Dz.InkSoft)
        Spacer(Modifier.weight(1f))
        Text(
            if (cents > 0) "¥${Money.short(cents)}" else "未设置",
            style = MaterialTheme.typography.labelLarge,
            color = if (cents > 0) Dz.Ink else Dz.InkFaint
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Dz.SakuraSoft)
                .clickable { onCustom() }
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text("自定义", style = MaterialTheme.typography.labelSmall, color = Dz.SakuraDeep)
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        quick.forEach { (c, l) ->
            SettingChip(label = l, active = cents == c) { onQuick(c) }
        }
    }
}

@Composable
private fun SettingChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) Dz.SakuraSoft else Dz.Cream)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) Dz.SakuraDeep else Dz.InkSoft
        )
    }
}

@Composable
private fun ExplainLine(mark: String, name: String, desc: String) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text(mark, fontSize = 12.sp, color = Dz.Sakura, fontWeight = FontWeight.Bold)
        Text(
            name,
            fontSize = 12.sp,
            color = Dz.Ink,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 6.dp, end = 6.dp)
        )
        Text(desc, fontSize = 12.sp, color = Dz.InkFaint, modifier = Modifier.weight(1f))
    }
}

private fun isA11yEnabled(ctx: Context): Boolean {
    return try {
        val s = android.provider.Settings.Secure.getString(
            ctx.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        s.contains(ctx.packageName)
    } catch (e: Exception) {
        false
    }
}

private fun isNlsEnabled(ctx: Context): Boolean = try {
    NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
} catch (e: Exception) {
    false
}

private fun openSystem(ctx: Context, action: String) {
    try {
        ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(ctx, "打不开设置页，请到系统设置里手动开启", Toast.LENGTH_LONG).show()
    }
}

/** 次级页顶栏：返回 + 标题。 */
@Composable
private fun SubPageBar(title: String, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Dz.Card)
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.ArrowBack,
                contentDescription = "返回",
                tint = Dz.InkSoft,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = Dz.Ink)
    }
}

@Composable
private fun FeedbackPage(ctx: Context, onClose: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Dz.Cream)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        SubPageBar("意见反馈", onClose)
        Spacer(Modifier.height(14.dp))

        var feedback by remember { mutableStateOf("") }
        DzCard {
            Text(
                "哪个分类认错了、哪笔没记上、哪句文案别扭，都能从这儿发。" +
                    "会自动附上机型和开关状态，省得你再描述环境。" +
                    "手机上没登录邮箱的话点「复制」——收件地址和内容一起复制好了，粘到任意邮箱发出去就行",
                style = MaterialTheme.typography.bodySmall,
                color = Dz.InkFaint,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            OutlinedTextField(
                value = feedback,
                onValueChange = { feedback = it },
                placeholder = { Text("说说哪里不对…", color = Dz.InkFaint, fontSize = 13.sp) },
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { submitFeedback(ctx, feedback) },
                    enabled = feedback.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Dz.Sakura,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.padding(top = 10.dp)
                ) { Text("发到邮箱") }
                TextButton(
                    onClick = {
                        val subject = "叮账反馈：" + feedback.trim().take(24)
                        copyFeedback(ctx, withRecipient(feedback, subject, feedbackBody(ctx, feedback)))
                    },
                    enabled = feedback.isNotBlank(),
                    modifier = Modifier.padding(top = 10.dp)
                ) { Text("复制", color = Dz.InkSoft) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * 最近采集记录。自动记账没记上的时候，这里能看到卡在哪一步：
 * 是无障碍事件没来、抓到了窗但认不出这页、还是被当成重复丢了。
 */
@Composable
private fun IngestLogCard() {
    var show by remember { mutableStateOf(false) }
    val lines = if (show) IngestLog.read() else emptyList()

    DzCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { show = !show },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("最近采集记录", style = MaterialTheme.typography.titleMedium, color = Dz.Ink)
            Spacer(Modifier.weight(1f))
            Text(
                if (show) "收起" else "展开",
                style = MaterialTheme.typography.labelMedium,
                color = Dz.InkSoft
            )
        }
        if (show) {
            Spacer(Modifier.height(10.dp))
            if (lines.isEmpty()) {
                Text(
                    "还没有记录。开着无障碍付一笔钱，这里就会出现一行，写着记上了还是没认出来。",
                    style = MaterialTheme.typography.labelMedium,
                    color = Dz.InkSoft
                )
            } else {
                lines.take(12).forEach { line ->
                    Text(
                        IngestLog.pretty(line),
                        style = MaterialTheme.typography.labelSmall,
                        color = Dz.InkSoft
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "清空记录",
                    style = MaterialTheme.typography.labelMedium,
                    color = Dz.Sakura,
                    modifier = Modifier.clickable { IngestLog.clear() }
                )
            }
        }
    }
}

@Composable
private fun PrinciplePage(onClose: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Dz.Cream)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        SubPageBar("自动记账原理与限制", onClose)
        Spacer(Modifier.height(14.dp))

        DzCard {
            ExplainLine("①", "无障碍", "读支付结果页，最准，实时")
            ExplainLine("②", "通知", "读支付通知，最快，但有的机型不带金额")
            ExplainLine("③", "银行提醒", "手机银行推送、公众号、短信，管不经过微信支付宝的钱")
            ExplainLine("④", "账单", "你导出的官方流水，最权威，用来补历史和核对")
            ExplainLine("⑤", "手记", "兜底，记不起的账自己补一笔")
        }

        Spacer(Modifier.height(12.dp))

        IngestLogCard()

        Spacer(Modifier.height(12.dp))

        DzCard {
            Text("说清楚做不到的", style = MaterialTheme.typography.titleMedium, color = Dz.Ink)
            Spacer(Modifier.height(8.dp))
            LimitLine("微信红包和转账，多数在聊天界面里完成，不产生系统支付通知。通知这条通道抓不到收红包、收转账这类账。")
            LimitLine("红包、转账想自动记上，可靠的办法是导一次微信账单：账单里「微信红包」「转账」是独立交易类型，还标了收还是支。")
            LimitLine("微信和支付宝没有面向个人的开放流水接口，所以不存在「后台自动同步」这种东西。")
            LimitLine("淘宝页面被它自己屏蔽了无障碍，读不到；云闪付的支付页也不会主动弹出来。")
            LimitLine("Android 13 以上侧载的 App，通知使用权和无障碍会被收进「受限设置」，要去系统设置里手动解除。")
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * 帮助页。文字版步骤，后续换成截图版：
 * 每张图直接放 Image(painterResource(R.drawable.help_xxx))，替换 HelpStep 即可。
 */
@Composable
private fun HelpPage(onClose: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Dz.Cream)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        SubPageBar("帮助", onClose)
        Spacer(Modifier.height(6.dp))
        Text(
            "点一下标题展开步骤，看完再收起来，页面不会太长",
            style = MaterialTheme.typography.bodyMedium,
            color = Dz.InkFaint,
            modifier = Modifier.padding(start = 2.dp, bottom = 10.dp)
        )

        FoldCard("微信账单怎么导出", "6 步，导出 csv 或 xlsx，最长一年") {
            HelpStep("1", "微信 → 我 → 服务 → 钱包")
            HelpStep("2", "右上角「账单」，再点右上角「常见问题」")
            HelpStep("3", "「下载账单」→ 用途选「用于个人对账」")
            HelpStep("4", "选时间范围（最长一年），填邮箱")
            HelpStep("5", "邮件里收到压缩包，解压得到 csv 或 xlsx")
            HelpStep("6", "回「我的 → 导入账单」，选那个文件")
        }
        Spacer(Modifier.height(10.dp))

        FoldCard("支付宝账单怎么导出", "5 步，压缩包带密码") {
            HelpStep("1", "支付宝 → 我的 → 账单")
            HelpStep("2", "右上角「…」→「开具交易流水证明」")
            HelpStep("3", "用途选「用于个人对账」，选时间范围，填邮箱")
            HelpStep("4", "邮件里收到压缩包（带解压密码，密码在短信或邮件正文里）")
            HelpStep("5", "解压后回「我的 → 导入账单」")
        }
        Spacer(Modifier.height(10.dp))

        FoldCard("权限怎么开", "五个权限，缺一个就会漏账") {
            HelpStep("1", "通知使用权：设置 → 应用 → 特殊应用权限 → 通知使用权 → 叮账 → 允许")
            HelpStep("2", "无障碍：设置 → 无障碍（辅助功能）→ 已下载的服务 → 叮账 → 开启")
            HelpStep("3", "短信：首次弹窗点「允许」；之前拒过就去 设置 → 应用 → 叮账 → 权限 → 短信")
            HelpStep("4", "后台存活：设置 → 电池 → 应用耗电管理 → 叮账 → 允许后台活动")
            HelpStep("5", "Android 13 以上侧载：设置 → 应用 → 叮账 → 右上角 ⋮ → 允许受限设置")
        }
        Spacer(Modifier.height(10.dp))

        FoldCard("怎么更新叮账", "没有应用商店，新版只能自己装") {
            HelpStep("1", "「我的」页拉到最下面，版本号旁边点「检查更新」")
            HelpStep("2", "有新版本会告诉你版本号和这次改了什么，点「去下载」")
            HelpStep("3", "会在浏览器打开下载页，下那个 apk 文件")
            HelpStep("4", "GitHub 打不开的话，在网址前面加 https://ghproxy.net/ 再试")
            HelpStep("5", "装的时候系统问「是否更新」，点更新就行，账都在")
        }
        Spacer(Modifier.height(10.dp))

        FoldCard("还是没记上？", "先分清是没开通道，还是这类消息本来就拿不到") {
            Text(
                "先看「自动记账原理与限制」那一页。两种情况要分清：通道没开（回到上一块把权限开上），" +
                    "还是这类消息本来就拿不到 —— 淘宝页面被屏蔽、微信红包和转账不出通知、通知里没写金额。" +
                    "后一种只能靠导入账单补。",
                style = MaterialTheme.typography.bodyMedium,
                color = Dz.InkSoft
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** 默认收起的说明卡：点标题展开，省得整页太长 */
@Composable
private fun FoldCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }
    DzCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Dz.Ink)
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = Dz.InkFaint,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Text(
                if (open) "收起" else "展开",
                style = MaterialTheme.typography.labelLarge,
                color = Dz.SakuraDeep
            )
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/** 限制说明的一条。字号用 bodyMedium，别再拿最小的字讲最重要的事。 */
@Composable
private fun LimitLine(text: String) {
    Row(Modifier.padding(vertical = 5.dp)) {
        Text(
            "·",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Dz.SakuraDeep
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Dz.InkSoft)
    }
}

@Composable
private fun HelpStep(no: String, text: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(
            "$no. ",
            fontSize = 12.sp,
            color = Dz.SakuraDeep,
            fontWeight = FontWeight.Bold
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = Dz.InkSoft)
    }
}

/** 「还没检查过 / 刚刚 / 12 分钟前 / 3 小时前 / 5 天前」 */
private fun agoText(ms: Long): String {
    if (ms <= 0L) return "还没检查过"
    val d = System.currentTimeMillis() - ms
    return when {
        d < 60_000L -> "刚刚"
        d < 3600_000L -> "${d / 60_000} 分钟前"
        d < 86_400_000L -> "${d / 3600_000} 小时前"
        else -> "${d / 86_400_000L} 天前"
    }
}
