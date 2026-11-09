package com.dingzhang.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dingzhang.app.core.model.Categories
import com.dingzhang.app.core.model.Category
import com.dingzhang.app.ui.theme.Dz

/** 数据层里分类色是 ARGB Int（不依赖 Compose），界面统一用这个转成 Color */
val Category.ui: Color get() = Color(color)

/** 分类 id -> Material 图标。图标语言统一走圆角线性风格，和整体调性一致。 */
fun categoryIcon(id: String): ImageVector = when (id) {
    "food" -> Icons.Rounded.Restaurant
    "drink" -> Icons.Rounded.LocalCafe
    "transport" -> Icons.Rounded.DirectionsBus
    "shopping" -> Icons.Rounded.ShoppingBag
    "home" -> Icons.Rounded.Home
    "fun" -> Icons.Rounded.SportsEsports
    "health" -> Icons.Rounded.MedicalServices
    "study" -> Icons.Rounded.MenuBook
    "social" -> Icons.Rounded.CardGiftcard
    "pet" -> Icons.Rounded.Pets
    "digital" -> Icons.Rounded.Memory
    "salary" -> Icons.Rounded.Work
    "bonus" -> Icons.Rounded.Star
    "invest" -> Icons.Rounded.TrendingUp
    "reimburse" -> Icons.Rounded.ReceiptLong
    "gift" -> Icons.Rounded.Redeem
    "income" -> Icons.Rounded.Savings
    else -> Icons.Rounded.MoreHoriz
}

@Composable
fun DzCard(
    modifier: Modifier = Modifier,
    corner: Dp = 26.dp,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(corner),
                ambientColor = Dz.Sakura.copy(alpha = 0.30f),
                spotColor = Dz.Sakura.copy(alpha = 0.20f)
            )
            .clip(RoundedCornerShape(corner))
            .background(Dz.Card)
            .border(1.dp, Dz.Line, RoundedCornerShape(corner))
            .padding(padding),
        content = content
    )
}

/** 分类小徽章：胶囊色块 + 图标，二次元 App 里最常用的「贴纸」处理。 */
@Composable
fun CategoryBadge(categoryId: String, size: Dp = 42.dp) {
    val cat = Categories.of(categoryId)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.36f))
            .background(cat.ui.copy(alpha = 0.15f))
            .border(1.2.dp, cat.ui.copy(alpha = 0.32f), RoundedCornerShape(size * 0.36f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = categoryIcon(cat.id),
            contentDescription = cat.name,
            tint = cat.ui,
            modifier = Modifier.size(size * 0.52f)
        )
    }
}

@Composable
fun EnergyBar(ratio: Float, modifier: Modifier = Modifier, height: Dp = 9.dp) {
    Box(
        modifier = modifier
            .height(height)
            .clip(CircleShape)
            .background(Dz.SakuraSoft)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(ratio.coerceIn(0.02f, 1f))
                .fillMaxHeight()
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(Dz.Sakura, Dz.Lilac)))
        )
    }
}

/**
 * 自绘的 Q 版「账本精灵」。不用图片素材：不同 DPI、不同机型下都不会糊，
 * 也让这个 App 没有一张外来的版权素材。
 */
@Composable
fun MascotFace(size: Dp = 56.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val cx = w / 2f
        val cy = w * 0.56f
        val r = w * 0.38f

        // 呆毛
        val ahoge = Path().apply {
            moveTo(cx - r * 0.10f, cy - r * 0.92f)
            quadraticBezierTo(cx + r * 0.10f, cy - r * 1.55f, cx + r * 0.40f, cy - r * 1.05f)
        }
        drawPath(ahoge, color = Dz.Ink, style = Stroke(width = w * 0.045f, cap = StrokeCap.Round))

        // 脸
        drawCircle(color = Color(0xFFFFFDFE), radius = r, center = Offset(cx, cy))
        drawCircle(
            color = Dz.SakuraSoft, radius = r, center = Offset(cx, cy),
            style = Stroke(width = w * 0.038f)
        )

        // 眼睛 + 高光
        val eyeR = r * 0.14f
        drawCircle(Dz.Ink, eyeR, Offset(cx - r * 0.34f, cy + r * 0.04f))
        drawCircle(Dz.Ink, eyeR, Offset(cx + r * 0.34f, cy + r * 0.04f))
        drawCircle(Color.White, eyeR * 0.40f, Offset(cx - r * 0.30f, cy - r * 0.04f))
        drawCircle(Color.White, eyeR * 0.40f, Offset(cx + r * 0.38f, cy - r * 0.04f))

        // 腮红
        drawCircle(Dz.Sakura.copy(alpha = 0.26f), r * 0.17f, Offset(cx - r * 0.64f, cy + r * 0.34f))
        drawCircle(Dz.Sakura.copy(alpha = 0.26f), r * 0.17f, Offset(cx + r * 0.64f, cy + r * 0.34f))

        // 微笑
        val mouth = Path().apply {
            moveTo(cx - r * 0.13f, cy + r * 0.36f)
            quadraticBezierTo(cx, cy + r * 0.56f, cx + r * 0.13f, cy + r * 0.36f)
        }
        drawPath(mouth, color = Dz.Ink, style = Stroke(width = w * 0.030f, cap = StrokeCap.Round))
    }
}

@Composable
fun SpeechBubble(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(
                RoundedCornerShape(
                    topStart = 18.dp, topEnd = 18.dp, bottomEnd = 6.dp, bottomStart = 18.dp
                )
            )
            .background(Dz.LilacSoft)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Dz.Ink)
    }
}

@Composable
fun EmptyHint(title: String, sub: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MascotFace(64.dp)
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = Dz.Ink,
            modifier = Modifier.padding(top = 10.dp)
        )
        Text(
            sub,
            style = MaterialTheme.typography.bodySmall,
            color = Dz.InkFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp)
        )
    }
}
