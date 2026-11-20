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
    val rSmall: Int,
    // 有背景层的主题在这里写上画哪一层（starfield / rain），没有的留空走纯色底
    val backdrop: String? = null
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
     * 星空物语：深空蓝底 + 一层星屑。星星本身由 StarfieldBackdrop 画在背景层，
     * 这里只管配色 —— 底色压得够深，星星才浮得出来。
     */
    val STARFIELD = ThemePack(
        id = "starfield", name = "星空物语", tagline = "深空底 + 星屑，夜里记账像看星空", dark = true,
        primary = Color(0xFF8FB4FF), primaryDeep = Color(0xFFC9DCFF), primarySoft = Color(0xFF22305C),
        secondary = Color(0xFFB79CFF), secondarySoft = Color(0xFF2B2352),
        accent = Color(0xFFFFD98A), accentSoft = Color(0xFF3A3320),
        bg = Color(0xFF0C1228), card = Color(0xFF161E3E),
        ink = Color(0xFFEDF2FF), inkSoft = Color(0xFFA3AFD6), inkFaint = Color(0xFF6A76A0),
        line = Color(0xFF242E56),
        warn = Color(0xFFF5C05A), warnSoft = Color(0xFF3A2F18),
        expense = Color(0xFFFF8DA6), income = Color(0xFF6EE0BE),
        rLarge = 22, rMedium = 16, rSmall = 11,
        backdrop = "starfield"
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

    /**
     * 深海：墨蓝发青的底 + 薄荷青主色。
     *
     * 深色主题最容易做坏的地方，是把底压成纯黑、再放一个高饱和的亮色 ——
     * 那就是霓虹灯，不是界面上班看的。这套的底带明显的青绿倾向（0A1519），
     * 主色压到中明度的薄荷青，跟底之间留出层，看得久不累。
     */
    val DEEPSEA = ThemePack(
        id = "deepsea", name = "深海", tagline = "墨蓝底 + 薄荷青，冷调耐看", dark = true,
        primary = Color(0xFF5FD9C7), primaryDeep = Color(0xFFA5F2E6), primarySoft = Color(0xFF123732),
        secondary = Color(0xFF7FA8FF), secondarySoft = Color(0xFF1A2B4C),
        accent = Color(0xFFFFC978), accentSoft = Color(0xFF392D17),
        bg = Color(0xFF0A1519), card = Color(0xFF132229),
        ink = Color(0xFFE8F5F2), inkSoft = Color(0xFF9BB5B0), inkFaint = Color(0xFF62787A),
        line = Color(0xFF1E3038),
        warn = Color(0xFFF0BE5C), warnSoft = Color(0xFF392D17),
        expense = Color(0xFFFF8FA5), income = Color(0xFF4FD9B0),
        rLarge = 20, rMedium = 15, rSmall = 10
    )

    /**
     * 夜灯：暖棕黑底 + 琥珀金主色 + 一整幅夜景插画（少女站在路灯下的旧街）。
     *
     * 跟深海正好相反的一套暖调。底色不是灰黑也不是冷黑，是带木色调的暖棕黑。
     * 背景层是画出来的整幅夜景 —— 人物的轮廓光、地上的反光、远处的灯
     * 都在同一张图里，铺满不会像贴纸。跟星空物语也拉开得很开：
     * 那边是抽象的星点，这边是具体的人和街。
     * 圆角给得比深海大一点，整体更软。
     */
    val NIGHT = ThemePack(
        id = "nightlamp", name = "夜灯", tagline = "暖棕黑 + 夜街插画，路灯下有个人", dark = true,
        primary = Color(0xFFE9A964), primaryDeep = Color(0xFFFCD3A3), primarySoft = Color(0xFF362818),
        secondary = Color(0xFFC1A2EC), secondarySoft = Color(0xFF2C2340),
        accent = Color(0xFF7FD6C0), accentSoft = Color(0xFF1A332F),
        bg = Color(0xFF14100D), card = Color(0xFF201A15),
        ink = Color(0xFFF5EDE4), inkSoft = Color(0xFFB0A090), inkFaint = Color(0xFF7A6C60),
        line = Color(0xFF302720),
        warn = Color(0xFFFFB347), warnSoft = Color(0xFF362714),
        expense = Color(0xFFFF8A7A), income = Color(0xFF6FD9A8),
        rLarge = 24, rMedium = 18, rSmall = 12,
        backdrop = "character"
    )

    val ALL = listOf(SAKURA, STARFIELD, MINT, DEEPSEA, NIGHT)

    fun byId(id: String?): ThemePack {
        // 老版本第二套叫 teyvat，改版后统一走星空物语，别让已经选过的人被重置回樱粉
        // yozakura（夜桜）已经换掉了，选过的人挪到新那套深色，别掉回樱粉
        val fixed = when (id) {
            "teyvat" -> "starfield"
            "yozakura" -> "deepsea"
            else -> id
        }
        return ALL.firstOrNull { it.id == fixed } ?: SAKURA
    }
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
