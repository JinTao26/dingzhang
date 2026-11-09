package com.dingzhang.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * 界面里所有颜色的入口。
 *
 * 这些属性都是「读当前主题包」的 getter，不是一个写死的常量表 ——
 * 好处是整个 App 用 Dz.Sakura 用了两百多处，换主题时它们全部跟着变，
 * 不用把每个界面改一遍（也不会漏）。
 */
object Dz {
    private val p: ThemePack get() = ThemeController.current

    val Sakura: Color get() = p.primary
    val SakuraDeep: Color get() = p.primaryDeep
    val SakuraSoft: Color get() = p.primarySoft
    val Lilac: Color get() = p.secondary
    val LilacSoft: Color get() = p.secondarySoft
    val Mint: Color get() = p.accent
    val MintSoft: Color get() = p.accentSoft
    val Cream: Color get() = p.bg
    val Card: Color get() = p.card
    val Ink: Color get() = p.ink
    val InkSoft: Color get() = p.inkSoft
    val InkFaint: Color get() = p.inkFaint
    val Line: Color get() = p.line
    val Warn: Color get() = p.warn
    val WarnSoft: Color get() = p.warnSoft
    val Expense: Color get() = p.expense
    val Income: Color get() = p.income
}

private fun schemeOf(p: ThemePack) = if (p.dark) {
    darkColorScheme(
        primary = p.primary,
        onPrimary = p.bg,
        primaryContainer = p.primarySoft,
        onPrimaryContainer = p.primaryDeep,
        secondary = p.secondary,
        onSecondary = p.bg,
        secondaryContainer = p.secondarySoft,
        tertiary = p.accent,
        background = p.bg,
        onBackground = p.ink,
        surface = p.card,
        onSurface = p.ink,
        surfaceVariant = p.primarySoft,
        onSurfaceVariant = p.inkSoft,
        outline = p.line,
        error = p.expense
    )
} else {
    lightColorScheme(
        primary = p.primary,
        onPrimary = Color.White,
        primaryContainer = p.primarySoft,
        onPrimaryContainer = p.primaryDeep,
        secondary = p.secondary,
        onSecondary = Color.White,
        secondaryContainer = p.secondarySoft,
        tertiary = p.accent,
        background = p.bg,
        onBackground = p.ink,
        surface = p.card,
        onSurface = p.ink,
        surfaceVariant = p.primarySoft,
        onSurfaceVariant = p.inkSoft,
        outline = p.line,
        error = p.expense
    )
}

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

@Composable
fun DingZhangTheme(content: @Composable () -> Unit) {
    val pack = ThemeController.current
    val scheme = remember(pack.id) { schemeOf(pack) }
    val shapes = remember(pack.id) {
        Shapes(
            small = RoundedCornerShape(pack.rSmall.dp),
            medium = RoundedCornerShape(pack.rMedium.dp),
            large = RoundedCornerShape(pack.rLarge.dp)
        )
    }

    // 深色主题下状态栏/导航栏图标得翻成白色，否则黑图标压在暗底上看不见
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            val bar = WindowCompat.getInsetsController(window, view)
            bar.isAppearanceLightStatusBars = !pack.dark
            bar.isAppearanceLightNavigationBars = !pack.dark
        }
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = typography,
        shapes = shapes,
        content = content
    )
}

private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
