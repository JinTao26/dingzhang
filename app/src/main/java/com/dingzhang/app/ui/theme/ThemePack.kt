package com.dingzhang.app.ui.theme

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import com.dingzhang.app.data.Settings

/**
 * 一整套外观：配色 + 圆角 + 明暗。
 * 换主题换的是整个设计语言，不是只把主色改一下 —— 否则「原神风」会变成
 * 粉底上贴一块金黄，看着就不对。
 */
data class ThemePack(
    val id: String,
    val name: String,
    val tagline: String,
    val dark: Boolean,
    val primary: Color,
    val primaryDeep: Color,
    val primarySoft: Color,
    val secondary: Color,
    val secondarySoft: Color,
    val accent: Color,
    val accentSoft: Color,
    val bg: Color,
    val card: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkFaint: Color,
    val line: Color,
    val warn: Color,
    val warnSoft: Color,
    val expense: Color,
    val income: Color,
    val rLarge: Int,
    val rMedium: Int,
    val rSmall: Int
)

object ThemePacks {

    /** 樱粉：默认那套奶油粉底 + 大圆角，软萌感来自圆角和粉色，不靠堆插画。 */
    val SAKURA = ThemePack(
        id = "sakura", name = "樱粉", tagline = "奶油底 + 樱粉，默认", dark = false,
        primary = Color(0xFFFF8FB1), primaryDeep = Color(0xFFF0638F), primarySoft = Color(0xFFFFE4ED),
        secondary = Color(0xFFA78BFA), secondarySoft = Color(0xFFEFE8FF),
        accent = Color(0xFF6FD9BC), accentSoft = Color(0xFFE2FAF3),
        bg = Color(0xFFFFF7FA), card = Color(0xFFFFFFFF),
        ink = Color(0xFF3B2E45), inkSoft = Color(0xFF8A7D96), inkFaint = Color(0xFFBDB4C6),
        line = Color(0xFFF2E7ED),
        warn = Color(0xFFFFA63D), warnSoft = Color(0xFFFFF1DE),
        expense = Color(0xFFF2587A), income = Color(0xFF16BF95),
        rLarge = 30, rMedium = 22, rSmall = 14
    )

    /**
     * 提瓦特：深蓝夜空 + 鎏金描边 + 元素色点缀，圆角收紧。
     * 只取「星图 / 金线 / 元素」这套视觉语法，不含任何官方角色、立绘或 Logo。
     */
    val TEYVAT = ThemePack(
        id = "teyvat", name = "提瓦特", tagline = "深蓝夜空 + 鎏金，圆角收窄", dark = true,
        primary = Color(0xFFD9B96F), primaryDeep = Color(0xFFF0D9A0), primarySoft = Color(0xFF2E3550),
        secondary = Color(0xFFB39BEA), secondarySoft = Color(0xFF2C2850),
        accent = Color(0xFF74C8AC), accentSoft = Color(0xFF1E3632),
        bg = Color(0xFF131A2B), card = Color(0xFF1D2540),
        ink = Color(0xFFEDE4D0), inkSoft = Color(0xFFA6A292), inkFaint = Color(0xFF6C6A5E),
        line = Color(0xFF313D5C),
        warn = Color(0xFFE8B33C), warnSoft = Color(0xFF3A2F18),
        expense = Color(0xFFE8806F), income = Color(0xFF79D6BE),
        rLarge = 12, rMedium = 10, rSmall = 6
    )

    /** 薄荷：白底 + 薄荷绿，白天看着最省眼的浅色方案。 */
    val MINT = ThemePack(
        id = "mint", name = "薄荷", tagline = "白底薄荷绿，清爽浅色", dark = false,
        primary = Color(0xFF35BE9B), primaryDeep = Color(0xFF1F8E72), primarySoft = Color(0xFFDCF5EE),
        secondary = Color(0xFF6FA8E8), secondarySoft = Color(0xFFE3EFFC),
        accent = Color(0xFFF2A65A), accentSoft = Color(0xFFFDEEDC),
        bg = Color(0xFFF6FBF9), card = Color(0xFFFFFFFF),
        ink = Color(0xFF1F2E2A), inkSoft = Color(0xFF74847F), inkFaint = Color(0xFFAEBAB5),
        line = Color(0xFFE2EDE9),
        warn = Color(0xFFE8912F), warnSoft = Color(0xFFFCF0DE),
        expense = Color(0xFFE5607B), income = Color(0xFF12B88F),
        rLarge = 24, rMedium = 18, rSmall = 12
    )

    /** 夜桜：深色版的樱粉，夜里记账不刺眼。 */
    val YOZAKURA = ThemePack(
        id = "yozakura", name = "夜桜", tagline = "暗紫底 + 樱粉，夜间用", dark = true,
        primary = Color(0xFFFF9DBE), primaryDeep = Color(0xFFFFC2D6), primarySoft = Color(0xFF3B2533),
        secondary = Color(0xFFB79BF5), secondarySoft = Color(0xFF2C2540),
        accent = Color(0xFF74D6BE), accentSoft = Color(0xFF1D3330),
        bg = Color(0xFF17131B), card = Color(0xFF221B27),
        ink = Color(0xFFF5EAF1), inkSoft = Color(0xFFA79AA6), inkFaint = Color(0xFF6C6070),
        line = Color(0xFF352B3A),
        warn = Color(0xFFF2B34C), warnSoft = Color(0xFF3A2C1A),
        expense = Color(0xFFFF809A), income = Color(0xFF4FD1AE),
        rLarge = 28, rMedium = 20, rSmall = 14
    )

    val ALL = listOf(SAKURA, TEYVAT, MINT, YOZAKURA)

    fun byId(id: String?): ThemePack = ALL.firstOrNull { it.id == id } ?: SAKURA
}

/**
 * 主题的单一真相来源。
 * 用 Compose 的 snapshot state 存当前包：界面里读 Dz.xxx → 读到 state → 自动重组，
 * 所以 200 多处颜色引用一个字都不用改，换主题当场生效。
 */
object ThemeController {

    private val state = mutableStateOf(ThemePacks.SAKURA)

    val current: ThemePack get() = state.value

    fun init(context: Context) {
        state.value = ThemePacks.byId(Settings(context).themeId)
    }

    fun select(context: Context, id: String) {
        Settings(context).themeId = id
        state.value = ThemePacks.byId(id)
    }
}
