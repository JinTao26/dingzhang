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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

import com.dingzhang.app.R

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
    // 卡片带一点透明：背景主题下让图从卡片后面透出来
    val Card: Color get() = if (p.cardAlpha >= 1f) p.card else p.card.copy(alpha = p.cardAlpha)
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
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    // 小字往上提了一档：11-sp 那种在带背景的主题上根本吃不住，
    // 而且这 App 也不是给大屏平板看的，都按最小的手机算
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.5.sp, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.3.sp)
)

/**
 * 青绿那套的标题字体：霞鹜文楷。
 *
 * 完整字库十九兆，为了一套主题的标题背那么一大包不值，所以子集到 App 里
 * 真正会出现的字（约一千二百个）。金额那一档故意不换 —— 楷体的数字是
 * 比例宽度，列表里金额会左右跳，而且数字本来就是无衬线的更清楚。
 */
private val WenKai = FontFamily(Font(R.font.wenkai))

private val typographySerif = typography.copy(
    headlineLarge = typography.headlineLarge.copy(
        fontFamily = WenKai, fontWeight = FontWeight.Normal,
        fontSize = 27.sp, letterSpacing = 0.8.sp
    ),
    headlineSmall = typography.headlineSmall.copy(
        fontFamily = WenKai, fontWeight = FontWeight.Normal,
        fontSize = 20.sp, letterSpacing = 0.6.sp
    ),
    titleLarge = typography.titleLarge.copy(
        fontFamily = WenKai, fontWeight = FontWeight.Normal,
        fontSize = 18.sp, letterSpacing = 0.5.sp
    ),
    titleMedium = typography.titleMedium.copy(
        fontFamily = WenKai, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, letterSpacing = 0.3.sp
    ),
    titleSmall = typography.titleSmall.copy(
        fontFamily = WenKai, fontWeight = FontWeight.Normal
    )
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
        typography = if (pack.serifTitle) typographySerif else typography,
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
