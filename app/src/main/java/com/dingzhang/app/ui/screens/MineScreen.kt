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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.dingzhang.app.update.UpdateDownloader
import kotlinx.coroutines.launch

@Composable
fun MineScreen(data: AppData, month: String, onMonthChange: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = IngestPipeline.settings

    // 「我的」页里这些设置只存在本地偏好里，不进 AppData。
    // 点完之后 AppData 内容一模一样，Compose 判定「没变化」就不重组，
    // 界面看着像点了没反应 —— 预算提醒那几个档位就是这么卡的。
    // 这里直接订阅数据版本，版本一动就把设置重读一遍。
    val version by DingZhangApp.dataVersion.collectAsState()
    val monthlyBudgetCents = remember(version) { settings?.monthlyBudgetCents ?: 0L }
    val yearBudgetCents = remember(version) { settings?.yearBudgetCents ?: 0L }
    val alertPct = remember(version) { settings?.budgetAlertPercent ?: 80 }

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

    // 银行那几条用得少，收起来压在下面；主界面只留无障碍和通知两条
    var moreOpen by remember { mutableStateOf(false) }

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
    // 自动查出来的新版。不直接塞给 updateResult —— 那会一进页面就弹窗，
    // 用户嫌烦。先只在版本号下面挂一行醒目文字，他自己点开看。
    var autoNewer by remember { mutableStateOf<UpdateChecker.Result?>(null) }

    // 进「我的」页就静默查一次。以前只有冷启动查，App 挂在后台几天不重开就一直不查，
    // 用户看到的就是「从来没提醒过」。
    LaunchedEffect(Unit) {
        val lastAt = runCatching { settings?.lastUpdateCheckAt ?: 0L }.getOrDefault(0L)
        if (System.currentTimeMillis() - lastAt < 10 * 60 * 1000L) return@LaunchedEffect
        val r = runCatching { UpdateChecker.check(BuildConfig.VERSION_NAME) }.getOrNull()
            ?: return@LaunchedEffect
        if (r.ok) {
            runCatching { settings?.lastUpdateCheckAt = System.currentTimeMillis() }
            lastCheck = System.currentTimeMillis()
        }
        if (r.hasUpdate) {
            val skip = runCatching { settings?.skipVersion.orEmpty() }.getOrDefault("")
            if (r.latestVersion != skip) autoNewer = r
        }
    }

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
            // 通知这条最容易「看着开着、其实收不到」：权限给了，但微信的通知被系统折叠掉
            Text(
                "只要系统里微信的通知是「允许」的，叮账就收得到 —— 哪怕它被折叠进「其他通知」、" +
                    "被静音、没声音没横幅，都不影响。真正会断的是两种：微信里把这条消息设了免打扰，" +
                    "或者系统里把微信通知整个关掉。收付款、碰一碰这类没有支付页的付款，全靠这一条。",
                style = MaterialTheme.typography.labelSmall,
                color = Dz.InkFaint,
                modifier = Modifier.padding(top = 4.dp, start = 2.dp)
            )
            // 下面这三条管的都是「钱不经过微信支付宝」的情况，用得比上面少得多。
            // 收起来，别让它们把主界面撑满；要用的时候点开就行。
            val bankPushOn = settings?.bankPushChannel ?: true
            val bankWechatOn = settings?.bankWechatChannel ?: false
            val smsOn = settings?.smsChannel ?: false
            val otherOn = listOf(bankPushOn, bankWechatOn, smsOn).count { it }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { moreOpen = !moreOpen },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "其他来源",
                        style = MaterialTheme.typography.titleSmall,
                        color = Dz.InkSoft
                    )
                    Text(
                        "钱不经过微信支付宝的：银行 App 推送、银行公众号、银行短信 · 已开 " +
                            otherOn + " 条",
                        style = MaterialTheme.typography.labelSmall,
                        color = Dz.InkFaint
                    )
                }
                Text(
                    if (moreOpen) "收起" else "展开",
                    style = MaterialTheme.typography.labelSmall,
                    color = Dz.Sakura
                )
            }
            if (moreOpen) {
                Spacer(Modifier.height(8.dp))
                ChannelRow(
                    title = "银行 App 推送",
                    desc = "POS 直接刷卡、云闪付、银行自动代扣 —— 这些微信支付宝看不见，只有银行会告诉你",
                    systemGranted = nlsOn,
                    appEnabled = bankPushOn,
                    onToggle = { v ->
                        settings?.bankPushChannel = v
                        DingZhangApp.bumpDataVersion()
                    },
                    onGrant = { openSystem(ctx, android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }
                )
                SetupHints(
                    steps = listOf(
                        "在这个银行 App 里打开「动账提醒」或「消息通知」",
                        "系统设置 → 通知 → 找到该银行 App，允许它发通知",
                        "别把它的通知折叠或静音（MIUI、OriginOS 会把不常用的单独折叠）",
                        "注意：银行代扣、POS 刷卡走这条，微信支付宝看不到"
                    ),
                    selfTest = "自测：去超市刷卡一次，回来看看采集记录里有没有「记上了」"
                )
                Spacer(Modifier.height(6.dp))
                ChannelRow(
                    title = "银行公众号",
                    desc = "微信里银行公众号发的动账提醒。消息常被折叠，也容易和微信支付本身的记录重复，默认关",
                    systemGranted = nlsOn,
                    appEnabled = bankWechatOn,
                    onToggle = { v ->
                        settings?.bankWechatChannel = v
                        DingZhangApp.bumpDataVersion()
                    },
                    onGrant = { openSystem(ctx, android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }
                )
                SetupHints(
                    steps = listOf(
                        "先在微信里关注该行的公众号（没关注就什么都收不到）",
                        "这个公众号的消息不要设「消息免打扰」——设了微信就不发通知，叮账什么都收不到",
                        "系统设置里微信的通知要允许（折叠、静音都没关系，别整个关掉）",
                        "和微信支付本身的记录会重复，重复的自己会合并"
                    ),
                    selfTest = "自测：用微信付 0.01 元，回来看看采集记录里有没有相关一条"
                )
                Spacer(Modifier.height(6.dp))
                ChannelRow(
                    title = "银行短信",
                    desc = "工资到账、线下刷卡这类不经过微信支付宝的钱，从银行动账短信里读。只收实时广播，不读历史短信箱，余额和完整卡号不入库",
                    systemGranted = smsGranted,
                    appEnabled = smsOn,
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
                SetupHints(
                    steps = listOf(
                        "先在银行开通「短信动账提醒」——有的行收费、有的免费，没开就收不到",
                        "给叮账「短信」权限，上面那个开关会申请",
                        "只认动账短信，账单提醒、验证码、余额提醒都会被跳过"
                    ),
                    selfTest = "自测：转 0.01 元到另一个账户，回来看采集记录"
                )

                Text(
                    "这三条都还没有在你手机上验证过。第一次用建议照着上面的自测走一遍；" +
                        "不成的话，把「采集记录」整段复制发过来。",
                    style = MaterialTheme.typography.labelSmall,
                    color = Dz.InkFaint,
                    modifier = Modifier.padding(top = 10.dp, start = 2.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        PermissionGuideCard(ctx, a11yOn, nlsOn)

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
                cents = monthlyBudgetCents,
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
                cents = yearBudgetCents,
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
            // 不在这里亮邮箱地址：地址进了邮件客户端用户自然就看到了，摆在外面对谁都没好处
            ActionRow("意见反馈", "直接发到作者邮箱，收件人和机型信息自动填好") { subPage = 1 }
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
                    autoNewer?.let { nr ->
                        Text(
                            "有新版本 ${nr.latestVersion}，点这里看改了什么",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Dz.SakuraDeep,
                            modifier = Modifier
                                .padding(top = 3.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { updateResult = nr }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
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
            onSkipVersion = {
                runCatching { IngestPipeline.settings?.skipVersion = r.latestVersion.orEmpty() }
                skippedVer = r.latestVersion.orEmpty()
                autoNewer = null
            },
            onDownload = {
                val apk = r.apkUrl
                if (apk != null) {
                    // 下载器会先测哪条线路快再入队，和开屏弹窗那边走同一套
                    UpdateDownloader.startAsync(ctx, r.latestVersion.orEmpty(), apk) { ok ->
                        if (ok) {
                            Toast.makeText(
                                ctx,
                                "正在下载，进度看通知栏，下完点那条通知就能装",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            openUrl(ctx, apk)
                        }
                    }
                } else {
                    openUrl(ctx, r.pageUrl)
                }
                updateResult = null
                autoNewer = null
            },
            onOpenPage = { openUrl(ctx, r.pageUrl) }
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
                " / 银行推送 ${on(s?.bankPushChannel)} / 银行公众号 ${on(s?.bankWechatChannel)}" +
                " / 短信 ${on(s?.smsChannel)}"
        )
    }
}

/** 反馈直发作者邮箱。不走 GitHub —— 国内不一定连得上。 */
private const val FEEDBACK_MAIL = "2462634730@qq.com"

/** QQ 邮箱。装了就直接点名进它，不让系统出一道「三个选项挑一个」的题。 */
private const val QQ_MAIL_PKG = "com.tencent.androidqqmail"

/**
 * 在一堆 mailto 处理者里挑出真正「在写邮件」的那一个。
 *
 * 选择器上显示的文字就是这里的 label：QQ 邮箱会同时挂出
 * 「通过邮件发送」和「保存到记事本」，记事本那个只存东西不读收件人。
 * 先剔掉记事/便签/笔记这类，再优先 label 带「邮件」「发送」的，并且先看 QQ 邮箱包里。
 * 挑不出来就返回 null，交给后面的通用列表兜底 —— 宁可多问一次，不硬猜错一个。
 */
private fun pickMailActivity(
    ctx: Context,
    probe: Intent
): android.content.ComponentName? {
    val pm = ctx.packageManager
    val all = runCatching { pm.queryIntentActivities(probe, 0) }.getOrNull() ?: return null
    if (all.isEmpty()) return null
    val bad = listOf("记事", "便签", "笔记", "备忘录", "保存", "存到", "note", "memo", "save")
    val good = listOf("邮件", "发送", "写信", "邮箱", "mail", "email")

    fun labelOf(ri: android.content.pm.ResolveInfo): String =
        runCatching { ri.loadLabel(pm).toString() }.getOrDefault("")

    val usable = all.filter { ri ->
        val label = labelOf(ri)
        val cls = ri.activityInfo.name.lowercase()
        !bad.any { label.contains(it) || cls.contains(it) }
    }
    // QQ 邮箱排前面：用户的邮箱账号多半就在这儿
    val ordered = usable.filter { it.activityInfo.packageName == QQ_MAIL_PKG } + usable
    val hit = ordered.firstOrNull { ri ->
        val label = labelOf(ri).lowercase()
        good.any { label.contains(it) }
    } ?: ordered.firstOrNull()
    return hit?.let {
        android.content.ComponentName(it.activityInfo.packageName, it.activityInfo.name)
    }
}

private fun submitFeedback(ctx: Context, text: String) {
    if (text.isBlank()) return
    val subject = "叮账反馈：" + text.trim().take(24)
    val body = feedbackBody(ctx, text)

    // 剪贴板留给兜底，能直接唤起邮件 App 就不碰 ——
    // 用户剪贴板里常有自己的东西，为发一条反馈把它顶掉很不礼貌。

    // 再试着直接唤起邮件客户端。这里只认 mailto：声明处理 mailto 的只有邮件 App，
    // 系统不会再往里塞微信和备忘录。URI 里自带收件人，绝大多数客户端会照着填。
    val mailto = Intent(
        Intent.ACTION_SENDTO,
        Uri.parse(
            "mailto:" + FEEDBACK_MAIL +
                "?subject=" + Uri.encode(subject) +
                "&body=" + Uri.encode(body)
        )
    ).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_MAIL))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    // 系统为 mailto 拉出来的列表是「谁声明了就能进」，同一个邮箱自己也可能挂好几个入口
    // （QQ 邮箱就有这种情况：一个能带收件人，一个只是打开写信页）。用户没义务知道点哪个。
    // 这里改为点名：装了 QQ 邮箱就直接进它，收件人由 URI 带过去；没装或进不去才退回通用列表。
    // 点名到包还不够：QQ 邮箱一个包里就挂了三个 mailto 入口
    // （「通过邮件发送」「保存到记事本」…），系统照样再弹一次让用户挑，
    // 而「保存到记事本」只是把内容存下来，收件人字段根本不读，点了等于白点。
    // 所以按显示名挑：带「邮件」「发送」的那个才是在写邮件。
    val picked = pickMailActivity(ctx, mailto)
    val direct =
        (picked != null && runCatching {
            ctx.startActivity(Intent(mailto).setComponent(picked))
        }.isSuccess) ||
            runCatching { ctx.startActivity(Intent(mailto).setPackage(QQ_MAIL_PKG)) }.isSuccess

    if (direct) {
        Toast.makeText(ctx, "收件人和内容都填好了，确认一下直接发就行", Toast.LENGTH_SHORT).show()
        return
    }

    // 挑不出明确的邮件入口，才退一步：先把内容放进剪贴板，再让用户自己选。
    // 这一步会占掉剪贴板，所以放在最后，话也说明白。
    copyFeedbackSilently(ctx, withRecipient(text, subject, body))
    val chooser = runCatching {
        ctx.startActivity(Intent.createChooser(mailto, "发到作者的邮箱，选一个"))
    }.isSuccess
    Toast.makeText(
        ctx,
        if (chooser) {
            "内容已复制。收件人要是空的，长按粘一下"
        } else {
            "手机上没有能用的邮箱 App。整段（含收件地址）已复制，粘到任意地方发出去就行"
        },
        Toast.LENGTH_LONG
    ).show()
}

/** 仓库地址，提 Issue 用。国内打不开时下面会兜底成复制。 */
private const val ISSUES_NEW_URL = "https://github.com/JinTao26/dingzhang/issues/new"

/**
 * 去 GitHub 提一条。文字、环境信息都预填好，用户点开基本只要按一下提交。
 * 需要 GitHub 账号 —— 这也是为什么邮件那条路必须留着。
 */
private fun openGitHubIssue(ctx: Context, text: String) {
    if (text.isBlank()) return
    val subject = "叮账反馈：" + text.trim().take(24)
    // URL 有长度上限，太长了浏览器直接打不开，砍到 1600 字
    val body = feedbackBody(ctx, text).take(1600)
    val url = ISSUES_NEW_URL + "?title=" + Uri.encode(subject) + "&body=" + Uri.encode(body)
    try {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        copyFeedback(ctx, withRecipient(text, subject, feedbackBody(ctx, text)))
        Toast.makeText(ctx, "打不开浏览器。内容已复制", Toast.LENGTH_LONG).show()
    }
}

/** 复制用：把收件人和主题写在最前面，粘出去以后一眼知道发给谁。 */
private fun withRecipient(text: String, subject: String, body: String): String =
    "收件人：" + FEEDBACK_MAIL + "\n主题：" + subject + "\n\n" + body


/** 复制但不弹提示 —— 调用方自己会说一句更完整的话，别叠两个 Toast */
private fun copyFeedbackSilently(ctx: Context, text: String) {
    if (text.isBlank()) return
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    cm.setPrimaryClip(android.content.ClipData.newPlainText("叮账反馈", text))
}

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
                "反馈直接发到作者的邮箱，收件人和机型信息自动填好。",
                style = MaterialTheme.typography.bodySmall,
                color = Dz.InkFaint
            )
            Text(
                "只在找不到邮箱 App 时，才会把内容复制一份到剪贴板 —— 平时不会动你的剪贴板。",
                style = MaterialTheme.typography.bodySmall,
                color = Dz.InkFaint,
                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
            )
            // 「总是」是系统记住的默认应用，App 改不了它，但得让用户知道去哪儿改
            Text(
                "如果每次都直接跳到同一个 App、想换一个：手机设置 → 应用 → 默认应用 → 邮件，清掉默认值再试。",
                style = MaterialTheme.typography.labelSmall,
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
                    onClick = { openGitHubIssue(ctx, feedback) },
                    enabled = feedback.isNotBlank(),
                    modifier = Modifier.padding(top = 10.dp)
                ) { Text("去 GitHub 提", color = Dz.InkSoft) }
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
 * 「要用这条通道，你还得先去开这些」。
 * 只写「它干什么」没用 —— 权限只是其中一步，前面还有好几步在别的 App 里。
 */
@Composable
private fun SetupHints(steps: List<String>, selfTest: String) {
    Column(Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp)) {
        steps.forEach { step ->
            Text(
                "· " + step,
                style = MaterialTheme.typography.labelSmall,
                color = Dz.InkFaint,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
        Text(
            selfTest,
            style = MaterialTheme.typography.labelSmall,
            color = Dz.Sakura,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

/** 一行通道状态 */
@Composable
private fun ChannelStatusLine(name: String, enabled: Boolean, idleMs: Long) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            color = Dz.InkFaint,
            modifier = Modifier.width(44.dp)
        )
        Text(
            when {
                !enabled -> "没授权，这个通道不工作"
                idleMs < 0L -> "已开启，还没收到过东西"
                else -> "已连接 · " + idleText(idleMs) + "收到过"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) Dz.InkSoft else Dz.Sakura
        )
    }
}

private fun idleText(ms: Long): String {
    val min = ms / 60_000L
    return when {
        min < 1L -> "刚刚"
        min < 60L -> min.toString() + " 分钟前"
        min < 1440L -> (min / 60L).toString() + " 小时前"
        else -> (min / 1440L).toString() + " 天前"
    }
}

/** 把两份记录连机型版本一起拼好进剪贴板，用户直接发过来 */
private fun copyIngestLog(ctx: Context) {
    val ok = IngestLog.readOk()
    val diag = IngestLog.readDiag()
    if (ok.isEmpty() && diag.isEmpty()) {
        Toast.makeText(ctx, "还没有记录", Toast.LENGTH_SHORT).show()
        return
    }
    val ver = runCatching {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
    }.getOrNull().orEmpty()
    val sb = StringBuilder()
    sb.append("叮账 ").append(ver).append(" 采集记录\n")
    sb.append("机型 ").append(android.os.Build.MANUFACTURER).append(" ")
        .append(android.os.Build.MODEL).append(" | Android ")
        .append(android.os.Build.VERSION.RELEASE).append("\n\n")
    sb.append("【记上的账 ").append(ok.size).append(" 条】\n")
    ok.forEach { sb.append(IngestLog.pretty(it)).append("\n") }
    sb.append("\n【为什么没记上 ").append(diag.size).append(" 条】\n")
    diag.forEach { sb.append(IngestLog.pretty(it)).append("\n") }
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    cm.setPrimaryClip(android.content.ClipData.newPlainText("叮账采集记录", sb.toString()))
    Toast.makeText(ctx, "整段已复制，直接发给我就行", Toast.LENGTH_LONG).show()
}

/**
 * 最近采集记录。自动记账没记上的时候，这里能看到卡在哪一步：
 * 是无障碍事件没来、抓到了窗但认不出这页、还是被当成重复丢了。
 */
@Composable
private fun IngestLogCard() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var show by remember { mutableStateOf(false) }
    var diagOpen by remember { mutableStateOf(false) }
    // 点「清空」要立刻变空。以前 clear() 只删了存储，界面不重组，
    // 看着就是「按了没反应」—— 得靠这个计数把列表重新读一遍。
    var tick by remember { mutableStateOf(0) }

    val oks = remember(show, tick) { if (show) IngestLog.readOk() else emptyList() }
    val diags = remember(show, diagOpen, tick) {
        if (show && diagOpen) IngestLog.readDiag() else emptyList()
    }

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

            // 两个通道各自「活着没活着」。
            // 通知使用权在系统设置里是勾着的，但绑可能根本没连上 —— 一条都收不到，
            // 界面却毫无异常。付一笔之后看这里的「几分钟前收到过」有没有往前走，
            // 就能分清是通知没来、还是来了但没认出来。
            ChannelStatusLine(
                "无障碍",
                com.dingzhang.app.ingest.ChannelHealth.a11yEnabled(ctx),
                com.dingzhang.app.ingest.ChannelHealth.a11yIdleMs()
            )
            ChannelStatusLine(
                "通知",
                com.dingzhang.app.ingest.ChannelHealth.nlsEnabled(ctx),
                com.dingzhang.app.ingest.ChannelHealth.nlsIdleMs()
            )
            Spacer(Modifier.height(10.dp))

            // 先给记上的账。这部分单独存，不会被诊断信息挤掉。
            Text(
                "记上的账",
                style = MaterialTheme.typography.labelMedium,
                color = Dz.Sakura
            )
            Spacer(Modifier.height(6.dp))
            if (oks.isEmpty()) {
                Text(
                    "还没有记上的。开着无障碍付一笔，成功的话这里会出现一行，写着商户和金额。",
                    style = MaterialTheme.typography.labelMedium,
                    color = Dz.InkSoft
                )
            } else {
                oks.take(10).forEach { line ->
                    Text(
                        IngestLog.pretty(line),
                        style = MaterialTheme.typography.labelSmall,
                        color = Dz.Ink
                    )
                    Spacer(Modifier.height(7.dp))
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { diagOpen = !diagOpen },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "为什么没记上",
                    style = MaterialTheme.typography.labelMedium,
                    color = Dz.InkSoft
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (diagOpen) "收起" else "展开",
                    style = MaterialTheme.typography.labelSmall,
                    color = Dz.InkSoft
                )
            }
            if (diagOpen) {
                Spacer(Modifier.height(6.dp))
                if (diags.isEmpty()) {
                    Text(
                        "没有诊断记录。",
                        style = MaterialTheme.typography.labelSmall,
                        color = Dz.InkSoft
                    )
                } else {
                    diags.take(10).forEach { line ->
                        Text(
                            IngestLog.pretty(line),
                            style = MaterialTheme.typography.labelSmall,
                            color = Dz.InkSoft
                        )
                        Spacer(Modifier.height(7.dp))
                    }
                }
            }

            if (oks.isNotEmpty() || diags.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 反馈「没记上」时，截图只能说现象。这里把两段记录连机型、版本一起
                    // 复制成一段话，直接发过来就能照着查。
                    Text(
                        "复制全部",
                        style = MaterialTheme.typography.labelMedium,
                        color = Dz.Sakura,
                        modifier = Modifier.clickable {
                            copyIngestLog(ctx)
                            diagOpen = false
                        }
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "清空记录",
                        style = MaterialTheme.typography.labelMedium,
                        color = Dz.Sakura,
                        modifier = Modifier.clickable {
                            IngestLog.clear()
                            diagOpen = false
                            tick++
                        }
                    )
                }
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
            ExplainLine("③", "银行提醒", "手机银行推送默认开；公众号和短信默认关，要用再开")
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
            LimitLine(
                "淘宝、京东、拼多多这些 App 把自己的商品页屏蔽了无障碍，页面内容读不到 —— " +
                    "但这不影响记账：记账只关心付款那一步，而付款时不管是在 App 里弹收银台、" +
                    "还是跳去微信支付宝，都能认出来。淘宝用微信或支付宝付款是可以记的。"
            )
            LimitLine("云闪付的支付页不会主动弹出来，覆盖弱 —— 用云闪付付的钱，多半要靠银行推送或导入账单才补得上。")
            LimitLine(
                "从浏览器、聊天软件装进来的 App，安卓会把无障碍和通知使用权收进「受限设置」，" +
                    "要去那个详情页的右上角点 ⋮ 手动放开，否则开关点不动。这不是叮账的问题，所有侧载的自动记账 App 都一样"
            )
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
            HelpStep(
                "5",
                "受限设置（安卓 11 起、13 以后更严）：从浏览器或聊天软件装的 App 不许直接开无障碍和通知使用权。" +
                    "在「无障碍 → 叮账」这个详情页的右上角点 ⋮ → 允许受限制的设置；" +
                    "通知使用权那页同理，各做一次"
            )
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
                    "还是这类消息本来就拿不到 —— 微信红包和转账不出通知、通知里没写金额、" +
                    "云闪付的支付页不弹出来。" +
                    "后一种只能靠导入账单补。",
                style = MaterialTheme.typography.bodyMedium,
                color = Dz.InkSoft
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** 默认收起的说明卡：点标题展开，省得整页太长 */
/**
 * 开权限的具体路径。同一个开关各家藏在不同地方、叫不同名字，纯文字讲一遍没人找得到，
 * 所以这里认当前这台手机的品牌，直接给到能照着点的层级，再配一个跳到对应设置页的按钮。
 */
@Composable
private fun PermissionGuideCard(ctx: Context, a11yOn: Boolean, nlsOn: Boolean) {
    val mine = com.dingzhang.app.core.RomGuides.currentId()
    var romId by remember { mutableStateOf(mine) }
    val g = com.dingzhang.app.core.RomGuides.byId(romId)

    FoldCard(
        title = "开权限的具体路径",
        subtitle = if (romId == mine) "认到你这台是 ${g.label}（${g.rom}）" else "在看 ${g.label} 的路径"
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            com.dingzhang.app.core.RomGuides.all.forEach { r ->
                val sel = r.id == romId
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (sel) Dz.Sakura else Dz.Cream)
                        .clickable { romId = r.id }
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Text(
                        r.label,
                        fontSize = 11.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                        color = if (sel) Color.White else Dz.InkSoft,
                        maxLines = 1
                    )
                }
            }
        }

        // 安卓从 11 起就把侧载应用的这类高权限锁住了，13 以后更严：从浏览器、聊天软件
        // 装进来的 App，主动去开无障碍或通知使用权会被系统拦下，表现是「点不动」或者
        // 弹一句「受限设置」。这一步不放开，下面两步根本开不起来 —— 所以放在最前面。
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Dz.Warn.copy(alpha = 0.12f))
                    .padding(horizontal = 11.dp, vertical = 9.dp)
            ) {
                Text(
                    "点开关没反应？先把「受限设置」放开",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Dz.Warn
                )
                Text(
                    "安卓不允许从浏览器、聊天软件装进来的 App 直接开这两个权限，" +
                        "点开关会弹「受限设置」或者干脆没反应。就在同一个页面的右上角点三个点，" +
                        "选「允许受限制的设置」，再回来点开关。①② 两步各要做一次",
                    fontSize = 11.sp,
                    color = Dz.InkSoft,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }

        GuideStep(
            no = "①",
            title = "无障碍服务",
            path = g.a11y,
            state = if (a11yOn) 1 else 0,
            actionLabel = if (a11yOn) "已开启" else "去打开",
            onGo = { openSystem(ctx, android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS) }
        )
        GuideStep(
            no = "②",
            title = "通知使用权",
            path = g.nls,
            state = if (nlsOn) 1 else 0,
            actionLabel = if (nlsOn) "已开启" else "去打开",
            onGo = { openSystem(ctx, android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }
        )
        GuideStep(
            no = "③",
            title = "别让它被系统杀掉",
            path = if (g.keep2.isBlank()) g.keep else g.keep + "\n" + g.keep2,
            state = -1,
            actionLabel = "去应用信息",
            onGo = { openAppInfo(ctx) }
        )

        Text(
            "各家的系统版本之间有出入，上面找不到就在设置最上面的搜索框里搜「无障碍」或「通知使用权」，" +
                "一般都能直接跳过去。步骤 ③ 是自动记账能不能一直在后台说话的关键，比前两步更容易被忽略",
            fontSize = 11.sp,
            color = Dz.InkFaint,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

/** 一条路径。state：1 已开启、0 未开启、-1 没有系统状态可查。 */
@Composable
private fun GuideStep(
    no: String,
    title: String,
    path: String,
    state: Int,
    actionLabel: String,
    onGo: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$no $title",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Dz.Ink
            )
            if (state == 1) {
                Text(
                    "已开启",
                    fontSize = 11.sp,
                    color = Dz.Mint,
                    modifier = Modifier.padding(start = 6.dp)
                )
            } else if (state == 0) {
                Text(
                    "未开启",
                    fontSize = 11.sp,
                    color = Dz.Warn,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            if (state != 1) {
                TextButton(
                    onClick = onGo,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 8.dp, vertical = 0.dp
                    )
                ) {
                    Text(actionLabel, fontSize = 12.sp, color = Dz.SakuraDeep)
                }
            }
        }
        Text(
            path,
            fontSize = 12.sp,
            color = Dz.InkSoft,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

/** 保活设置大多挂在应用信息页里，从这里进最快。 */
private fun openAppInfo(ctx: Context) {
    try {
        ctx.startActivity(
            Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + ctx.packageName)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        Toast.makeText(ctx, "打不开应用信息，请到系统设置里找叮账", Toast.LENGTH_LONG).show()
    }
}

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
