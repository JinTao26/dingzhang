package com.dingzhang.app.core

import android.os.Build

/**
 * 各家 ROM 把同一个开关藏在不同的地方，名字也不一样 —— 这是自动记账开不起来最常见的原因。
 * 这里的路径按各家系统设置的真实层级写，同一家的不同版本会有出入，所以每条都补了一句
 * 「找不到就在设置顶部搜」。
 */
data class RomGuide(
    val id: String,
    /** 界面上的名字，例如「小米 / 红米」 */
    val label: String,
    /** 系统名，例如「HyperOS / MIUI」 */
    val rom: String,
    /** 无障碍服务怎么开 */
    val a11y: String,
    /** 通知使用权怎么开 */
    val nls: String,
    /** 怎么让系统别把它杀掉（第一处） */
    val keep: String,
    /** 保活的第二处，各家写法不一样 */
    val keep2: String = ""
)

object RomGuides {

    /** 认当前这台是哪家。荣耀要在华为之前判 —— 有些机型的 manufacturer 还是 HUAWEI。 */
    fun currentId(): String {
        val m = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return when {
            "honor" in m -> "honor"
            "huawei" in m -> "huawei"
            "xiaomi" in m || "redmi" in m || "poco" in m -> "xiaomi"
            "oppo" in m || "oneplus" in m || "realme" in m || "one plus" in m -> "coloros"
            "vivo" in m || "iqoo" in m -> "originos"
            "samsung" in m -> "samsung"
            "meizu" in m -> "flyme"
            else -> "stock"
        }
    }

    val all: List<RomGuide> = listOf(
        RomGuide(
            id = "xiaomi",
            label = "小米 / 红米",
            rom = "HyperOS、MIUI",
            a11y = "设置 → 无障碍 → 已下载的应用 → 叮账 → 打开\n" +
                "（MIUI 旧版本在「更多设置」里找无障碍）",
            nls = "设置 → 通知和控制中心 → 通知使用权 → 叮账\n" +
                "（找不到就在「应用设置 → 特殊权限」里）",
            keep = "设置 → 应用设置 → 应用管理 → 叮账 → 省电策略 → 改成「无限制」",
            keep2 = "设置 → 应用设置 → 授权管理 → 自启动管理 → 打开叮账\n" +
                "再从最近任务里把叮账往下拉，挂上小锁"
        ),
        RomGuide(
            id = "huawei",
            label = "华为",
            rom = "HarmonyOS、EMUI",
            a11y = "设置 → 辅助功能 → 无障碍 → 已安装的服务 → 叮账 → 打开",
            nls = "设置 → 通知和状态栏 → 通知使用权 → 叮账",
            keep = "设置 → 应用 → 应用启动管理 → 叮账 → 关掉「自动管理」",
            keep2 = "关掉自动管理后会弹出三个开关，自启动、关联启动、后台活动全部打开"
        ),
        RomGuide(
            id = "honor",
            label = "荣耀",
            rom = "MagicOS",
            a11y = "设置 → 辅助功能 → 无障碍 → 已安装的服务 → 叮账 → 打开",
            nls = "设置 → 通知和状态栏 → 通知使用权 → 叮账",
            keep = "设置 → 应用 → 应用启动管理 → 叮账 → 手动管理",
            keep2 = "自启动、关联启动、后台活动三个都打开，再在最近任务里给它加锁"
        ),
        RomGuide(
            id = "coloros",
            label = "OPPO / 一加 / realme",
            rom = "ColorOS、OxygenOS",
            a11y = "设置 → 系统设置（部分版本叫「其他设置」）→ 无障碍 → 已下载的服务 → 叮账",
            nls = "设置 → 通知与状态栏 → 通知使用权 → 叮账",
            keep = "设置 → 电池 → 应用耗电管理 → 叮账 → 允许后台活动",
            keep2 = "设置 → 应用 → 应用自启动 → 打开叮账"
        ),
        RomGuide(
            id = "originos",
            label = "vivo / iQOO",
            rom = "OriginOS、Funtouch",
            a11y = "设置 → 快捷与辅助 → 无障碍 → 已下载的服务 → 叮账 → 打开",
            nls = "设置 → 通知与状态栏 → 通知使用权 → 叮账",
            keep = "设置 → 电池 → 后台耗电管理 → 叮账 → 允许后台高耗电",
            keep2 = "打开「i 管家」→ 应用管理 → 权限管理 → 自启动，把叮账也打开"
        ),
        RomGuide(
            id = "samsung",
            label = "三星",
            rom = "One UI",
            a11y = "设置 → 辅助功能 → 已安装的服务 → 叮账 → 打开",
            nls = "设置 → 通知 → 高级设置 → 通知使用权 → 叮账",
            keep = "设置 → 电池 → 后台使用限制 → 把叮账从「休眠应用」里移出去",
            keep2 = "同一页把「深度休眠应用」也检查一遍，别让叮账在里面"
        ),
        RomGuide(
            id = "flyme",
            label = "魅族",
            rom = "Flyme",
            a11y = "设置 → 辅助功能 → 无障碍 → 已安装的服务 → 叮账 → 打开",
            nls = "设置 → 通知管理 → 通知使用权 → 叮账",
            keep = "手机管家 → 权限管理 → 后台运行 → 叮账 → 允许",
            keep2 = "手机管家 → 权限管理 → 自启动 → 打开叮账"
        ),
        RomGuide(
            id = "stock",
            label = "原生 / 其他",
            rom = "Pixel、类原生系统",
            a11y = "设置 → 无障碍 → 已下载的应用 → 叮账 → 打开",
            nls = "设置 → 通知 → 设备与应用通知 → 特殊应用权限 → 通知使用权 → 叮账",
            keep = "设置 → 应用 → 叮账 → 电池 → 改成「不受限制」"
        )
    )

    fun byId(id: String): RomGuide = all.firstOrNull { it.id == id } ?: all.last()
}
