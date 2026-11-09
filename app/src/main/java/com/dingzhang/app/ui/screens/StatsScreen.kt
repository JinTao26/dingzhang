package com.dingzhang.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dingzhang.app.core.model.Categories
import com.dingzhang.app.core.model.CategoryTotal
import com.dingzhang.app.core.model.Money
import com.dingzhang.app.core.model.Source
import com.dingzhang.app.ui.AppData
import com.dingzhang.app.ui.components.CategoryBadge
import com.dingzhang.app.ui.components.DzCard
import com.dingzhang.app.ui.components.EmptyHint
import com.dingzhang.app.ui.components.ui
import com.dingzhang.app.ui.theme.Dz

@Composable
fun StatsScreen(data: AppData) {
    val cats = data.byCategory
    val total = cats.sumOf { it.cents }

    if (cats.isEmpty()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 18.dp, end = 18.dp, top = 14.dp)
        ) {
            Text("统计", style = MaterialTheme.typography.headlineLarge, color = Dz.Ink)
            Text(
                "${data.monthLabel} · 共 ${data.monthCount} 笔",
                style = MaterialTheme.typography.bodySmall,
                color = Dz.InkFaint,
                modifier = Modifier.padding(top = 2.dp)
            )
            EmptyHint("还没有可以统计的支出", "付几笔之后这里就会有环形图和排行啦")
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 96.dp)
    ) {
        Text("统计", style = MaterialTheme.typography.headlineLarge, color = Dz.Ink)
        Text(
            "${data.monthLabel} · 共 ${data.monthCount} 笔",
            style = MaterialTheme.typography.bodySmall,
            color = Dz.InkFaint,
            modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
        )

        // ---------- 环形图 ----------
        DzCard(padding = 20.dp) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(216.dp)) {
                    val stroke = 24.dp.toPx()
                    val inset = stroke / 2 + 3.dp.toPx()
                    val side = this.size.width - inset * 2
                    val sum = cats.sumOf { it.cents }.toFloat()
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
                    Text("本月支出", style = MaterialTheme.typography.labelSmall, color = Dz.InkFaint)
                    Text(
                        "¥${Money.yuan(total)}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Dz.Ink
                    )
                    Text(
                        "日均 ¥${Money.yuan(total / 30)}",
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
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
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
                                        "¥${Money.yuan(c.cents)} · ${(c.cents.toFloat() / total * 100).toInt()}%",
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

        Spacer(Modifier.height(12.dp))

        // ---------- 排行 ----------
        DzCard {
            Text("花在哪儿了", style = MaterialTheme.typography.titleLarge, color = Dz.Ink)
            Spacer(Modifier.height(10.dp))
            cats.forEachIndexed { i, c ->
                if (i > 0) {
                    HorizontalDivider(
                        color = Dz.Line,
                        thickness = 0.6.dp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
                CategoryBar(c, total)
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---------- 每日趋势 ----------
        DzCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("每天花了多少", style = MaterialTheme.typography.titleLarge, color = Dz.Ink)
                Spacer(Modifier.weight(1f))
                Text("单位：元", style = MaterialTheme.typography.labelSmall, color = Dz.InkFaint)
            }
            Spacer(Modifier.height(12.dp))
            DailyBars(data)
        }

        Spacer(Modifier.height(12.dp))

        // ---------- 自动程度 ----------
        DzCard {
            Text("记账的自动化程度", style = MaterialTheme.typography.titleLarge, color = Dz.Ink)
            Spacer(Modifier.height(10.dp))
            val auto = (data.sourceCounts[Source.ACCESSIBILITY] ?: 0) +
                (data.sourceCounts[Source.NOTIFICATION] ?: 0) +
                (data.sourceCounts[Source.BILL_IMPORT] ?: 0)
            val manual = data.sourceCounts[Source.MANUAL] ?: 0
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
            Text(
                when {
                    auto == 0 -> "还没有自动记账。去「我的」把无障碍和通知使用权打开试试"
                    auto > manual * 3 -> "自动记账已经很稳了，手动只是补充"
                    else -> "自动的还不够多，检查下权限是不是被系统限制了"
                },
                style = MaterialTheme.typography.bodySmall,
                color = Dz.InkFaint,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun CategoryBar(c: CategoryTotal, total: Long) {
    val cat = Categories.of(c.categoryId)
    val pct = if (total > 0) c.cents.toFloat() / total.toFloat() else 0f
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                    "¥${Money.yuan(c.cents)}",
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

@Composable
private fun DailyBars(data: AppData) {
    val max = (data.daily.values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        for (day in 1..data.daysInMonth) {
            val v = data.daily[day] ?: 0L
            val frac = v.toFloat() / max.toFloat()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(if (v == 0L) 0.04f else frac.coerceAtLeast(0.08f))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(
                            if (v == 0L) Dz.Cream
                            else Dz.Sakura.copy(alpha = 0.45f + 0.55f * frac)
                        )
                )
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("1", style = MaterialTheme.typography.labelSmall, color = Dz.InkFaint)
        Text("${data.daysInMonth / 2}", style = MaterialTheme.typography.labelSmall, color = Dz.InkFaint)
        Text("${data.daysInMonth}", style = MaterialTheme.typography.labelSmall, color = Dz.InkFaint)
    }
}
