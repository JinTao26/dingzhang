package com.dingzhang.app.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dingzhang.app.core.RomGuides
import com.dingzhang.app.ui.components.DzCard
import com.dingzhang.app.ui.theme.Dz

/**
 * 「怎么开权限」单独一页。
 *
 * 这些东西原来全摊在「我的」页里：三套系统的路径、四个通道各自的四步、每条的自测，
 * 加起来能滑三四屏，看预算、看版本号都得先划过去。现在这页只管讲清楚怎么开，
 * 「我的」那边只留一个入口。
 *
 * 只写必要的：路径、要不要额外开什么、唯一的坑（系统把侧载应用的高权限锁住了）。
 * 不列举「淘宝、拼多多」这类具体 App —— 用户要的不是清单，是「照这个点几下就行」。
 */
@Composable
fun PermissionGuideScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val mine = remember { RomGuides.currentId() }
    var romId by remember { mutableStateOf(mine) }
    val g = RomGuides.byId(romId)

    // 系统返回键和侧滑返回也得关这一页。没有这一句，按返回直接把整个 App 退掉了
    BackHandler { onClose() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Dz.Cream.copy(alpha = 0.94f))
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 40.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.ArrowBack, "返回",
                tint = Dz.Ink,
                modifier = Modifier
                    .size(40.dp)
                    .clickable { onClose() }
                    .padding(8.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text("怎么开权限", style = MaterialTheme.typography.headlineLarge, color = Dz.Ink)
        }
        Text(
            "两个开关都在系统设置里，找不到就直接在设置顶部搜「无障碍」「通知使用权」",
            style = MaterialTheme.typography.bodySmall,
            color = Dz.InkSoft,
            modifier = Modifier.padding(top = 6.dp, start = 46.dp)
        )

        Spacer(Modifier.height(16.dp))

        DzCard {
            Text(
                "你这台是 " + g.label + "（" + g.rom + "）",
                style = MaterialTheme.typography.titleMedium,
                color = Dz.Ink
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RomGuides.all.forEach { r ->
                    val sel = r.id == romId
                    Box(
                        Modifier
                            .background(
                                if (sel) Dz.Sakura else Dz.Cream,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { romId = r.id }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            r.label,
                            fontSize = 13.sp,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            color = if (sel) Color.White else Dz.InkSoft,
                            maxLines = 1
                        )
                    }
                }
            }

            // 安卓 11 起就把侧载应用的高权限锁住了，13 以后更严。这一关不放开，
            // 后面两个开关点上去毫无反应，人只会以为 App 坏了。
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Dz.Warn.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    "先把「受限设置」放开",
                    fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Dz.Warn
                )
                Text(
                    "点开关只弹一句「受限设置」或者干脆没反应，就是这个。同一页右上角三个点 → 「允许受限制的设置」，再回来点",
                    fontSize = 12.5.sp, color = Dz.InkSoft, lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
            PathStep(
                no = "①", title = "无障碍服务", path = g.a11y,
                note = "读支付结果页，这是主力通道",
                onGo = { go(ctx, Settings.ACTION_ACCESSIBILITY_SETTINGS) }
            )
            Spacer(Modifier.height(12.dp))
            PathStep(
                no = "②", title = "通知使用权", path = g.nls,
                note = "没有支付页的付款全靠这条",
                onGo = { go(ctx, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }
            )
            Spacer(Modifier.height(12.dp))
            PathStep(
                no = "③", title = "别让它被系统杀掉", path = if (g.keep2.isBlank()) g.keep else g.keep + "\n" + g.keep2,
                note = "误手清了后台就收不到消息",
                onGo = { goAppInfo(ctx) }
            )
            // 只在系统真有这个开关的 ROM 上出现。用户反馈「开了一阵子又失效」，
            // 十条里有八条是这条 —— 系统嫌它闲着就把无障碍关了
            if (g.a11yAutoOff.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                PathStep(
                    no = "④", title = "无障碍会被系统自己关掉", path = g.a11yAutoOff,
                    note = "开了几天又失效，八成是这里",
                    onGo = { go(ctx, Settings.ACTION_ACCESSIBILITY_SETTINGS) }
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "开完回「我的」看看三个通道是不是都显示「已连接」。第一次建议各付一笔 0.01 元试一试。",
                style = MaterialTheme.typography.labelSmall,
                color = Dz.InkSoft
            )
        }

        Spacer(Modifier.height(12.dp))

        DzCard {
            Text("钱不走微信支付宝的", style = MaterialTheme.typography.titleLarge, color = Dz.Ink)
            Text(
                "刷卡、代扣、工资到账这类，得靠银行那头",
                style = MaterialTheme.typography.bodySmall,
                color = Dz.InkSoft,
                modifier = Modifier.padding(top = 3.dp)
            )
            Spacer(Modifier.height(14.dp))
            MiniStep("银行 App 推送", "在那个银行 App 里打开「动账提醒」，系统通知别整个关掉")
            MiniStep("银行公众号", "微信里关注该行公众号，别给它设「消息免打扰」")
            MiniStep("银行短信", "在银行开通短信动账提醒，再给叮账短信权限")
            Spacer(Modifier.height(6.dp))
            Text(
                "这三条默认只开了银行推送，另外两条要用再开。",
                style = MaterialTheme.typography.labelSmall,
                color = Dz.InkSoft
            )
        }
    }
}

@Composable
private fun PathStep(
    no: String,
    title: String,
    path: String,
    note: String,
    onGo: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(no, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Dz.SakuraDeep)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = Dz.Ink)
            Spacer(Modifier.weight(1f))
            Text(
                "去打开",
                fontSize = 13.sp,
                color = Dz.SakuraDeep,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable { onGo() }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
        Text(
            path,
            fontSize = 12.5.sp,
            color = Dz.InkSoft,
            lineHeight = 18.sp,
            modifier = Modifier.padding(start = 25.dp, top = 3.dp)
        )
        Text(
            note,
            fontSize = 12.sp,
            color = Dz.InkSoft.copy(alpha = 0.85f),
            modifier = Modifier.padding(start = 25.dp, top = 2.dp)
        )
    }
}

@Composable
private fun MiniStep(title: String, body: String) {
    Column(Modifier.padding(bottom = 10.dp)) {
        Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Dz.Ink)
        Text(body, fontSize = 12.5.sp, color = Dz.InkSoft, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

private fun go(ctx: Context, action: String) {
    runCatching { ctx.startActivity(Intent(action)) }
}

private fun goAppInfo(ctx: Context) {
    runCatching {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + ctx.packageName))
        )
    }
}
