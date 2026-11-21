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
    // 有背景层的主题在这里写上画哪一层，没有的留空走纯色底
    val backdrop: String? = null,
    // 卡片不透明度：带背景的主题调低一点，让背景从卡片后面透出来，
    // 不然整屏像糊了一层皮，背景只在边角露一点点，看着憋屈
    val cardAlpha: Float = 1f
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
        backdrop = "starfield", cardAlpha = 0.90f
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
     * 水乡夜：江南古镇的夜，河两岸的灯全亮着。
     *
     * 跟「星空物语」那种干净的深空是两回事 —— 这套要的是热闹：
     * 一串串灯笼、水面上的倒影、石桥上也挂满灯，灯火一层层往巷子里退。
     * 主色取灯笼的暖黄，次色取河的青蓝，强调色是灯笼红。
     */
    val RIVERNIGHT = ThemePack(
        id = "rivernight", name = "水乡夜", tagline = "河两岸灯全亮着，船停在水上", dark = true,
        primary = Color(0xFFE8B45A), primaryDeep = Color(0xFFFBD08A), primarySoft = Color(0xFF382C1A),
        secondary = Color(0xFF8FB4C9), secondarySoft = Color(0xFF1F2E3A),
        accent = Color(0xFFD9634F), accentSoft = Color(0xFF35211D),
        bg = Color(0xFF141821), card = Color(0xFF1E242E),
        ink = Color(0xFFF2EFE7), inkSoft = Color(0xFFABA69A), inkFaint = Color(0xFF75736C),
        line = Color(0xFF2A3038),
        warn = Color(0xFFE8A33C), warnSoft = Color(0xFF3A2C14),
        expense = Color(0xFFF08070), income = Color(0xFF7FD6B0),
        rLarge = 16, rMedium = 12, rSmall = 8,
        backdrop = "rivernight", cardAlpha = 0.82f
    )

    /**
     * 青绿：一张青绿山水 —— 石青石绿罩染的层山，绢面织纹，云气留白。
     *
     * 主色直接用画里的青绿，次色是石青，强调色是山脚的赭石。
     * 这是五套里唯一带「颜色」的浅色主题，之前几套浅色都是灰的，
     * 这套是照着老绢本配的：颜色有，但不艳，像旧画挂了很多年之后的那个调。
     */
    val QINGLV = ThemePack(
        id = "qinglv", name = "青绿", tagline = "石青石绿罩着的山，绢上旧色", dark = false,
        primary = Color(0xFF4A7C6F), primaryDeep = Color(0xFF37635A), primarySoft = Color(0xFFE2EDE7),
        secondary = Color(0xFF6B8FA8), secondarySoft = Color(0xFFE6EEF3),
        accent = Color(0xFFB08A5A), accentSoft = Color(0xFFF4EADD),
        bg = Color(0xFFF7F4EA), card = Color(0xFFFFFFFF),
        ink = Color(0xFF2A2F2C), inkSoft = Color(0xFF6E7A72), inkFaint = Color(0xFFA2ADA4),
        line = Color(0xFFE4E6DC),
        warn = Color(0xFFC08A3E), warnSoft = Color(0xFFF8EEDC),
        expense = Color(0xFFB84C38), income = Color(0xFF2F7A5E),
        rLarge = 12, rMedium = 8, rSmall = 6,
        backdrop = "qinglv", cardAlpha = 0.86f
    )

    val ALL = listOf(SAKURA, STARFIELD, MINT, RIVERNIGHT, QINGLV)

    fun byId(id: String?): ThemePack {
        // 老版本第二套叫 teyvat，改版后统一走星空物语，别让已经选过的人被重置回樱粉
        // yozakura（夜桜）已经换掉了，选过的人挪到新那套深色，别掉回樱粉
        val fixed = when (id) {
            "teyvat" -> "starfield"
            // 前面几版存在过、现在已经换掉的 id，一个个挪到现在的对应位置
            "yozakura", "deepsea", "mist", "shijing", "jiangnan" -> "rivernight"
            "dusk", "rainnight", "nightlamp", "coast", "shiji", "shuimo" -> "qinglv"
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
