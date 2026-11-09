package com.dingzhang.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 配色：奶油粉底 + 樱粉主色 + 丁香紫副色 + 薄荷点缀。
 * 二次元感主要来自「低对比 + 高饱和点缀 + 大圆角 + 贴纸描边」，
 * 而不是往界面里堆插画，这就是设计上的取舍。
 */
object Dz {
    val Sakura = Color(0xFFFF8FB1)
    val SakuraDeep = Color(0xFFF0638F)
    val SakuraSoft = Color(0xFFFFE4ED)
    val Lilac = Color(0xFFA78BFA)
    val LilacSoft = Color(0xFFEFE8FF)
    val Mint = Color(0xFF6FD9BC)
    val MintSoft = Color(0xFFE2FAF3)
    val Cream = Color(0xFFFFF7FA)
    val Card = Color(0xFFFFFFFF)
    val Ink = Color(0xFF3B2E45)
    val InkSoft = Color(0xFF8A7D96)
    val InkFaint = Color(0xFFBDB4C6)
    val Line = Color(0xFFF2E7ED)
    val Warn = Color(0xFFFFA63D)
    val WarnSoft = Color(0xFFFFF1DE)
    val Expense = Color(0xFFF2587A)
    val Income = Color(0xFF16BF95)
}

private val colors = lightColorScheme(
    primary = Dz.Sakura,
    onPrimary = Color.White,
    primaryContainer = Dz.SakuraSoft,
    onPrimaryContainer = Dz.SakuraDeep,
    secondary = Dz.Lilac,
    onSecondary = Color.White,
    secondaryContainer = Dz.LilacSoft,
    tertiary = Dz.Mint,
    background = Dz.Cream,
    onBackground = Dz.Ink,
    surface = Dz.Card,
    onSurface = Dz.Ink,
    surfaceVariant = Dz.SakuraSoft,
    onSurfaceVariant = Dz.InkSoft,
    outline = Dz.Line,
    error = Dz.Expense
)

/** 金额用等宽数字，避免列表里金额左右跳动 */
private val tabular = "tnum"

private val typography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold, fontSize = 42.sp,
        letterSpacing = (-0.6).sp, fontFeatureSettings = tabular
    ),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.2).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 19.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.5.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 0.6.sp)
)

private val shapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp)
)

@Composable
fun DingZhangTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colors,
        typography = typography,
        shapes = shapes,
        content = content
    )
}
