package com.dingzhang.app.data

import android.content.Context

/** 轻量开关。真正重要的数据都在 SQLite 里。 */
class Settings(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences("dingzhang_settings", Context.MODE_PRIVATE)

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit().putBoolean("onboarded", v).apply()

    /** 自动识别到一笔后，直接落库（true）还是先挂起等确认（false）。 */
    var autoCommit: Boolean
        get() = sp.getBoolean("auto_commit", true)
        set(v) = sp.edit().putBoolean("auto_commit", v).apply()

    /** 小于这个金额不进账，避免 1 分钱测试单和订阅试扣款刷屏。单位：分。 */
    var ignoreBelowCents: Long
        get() = sp.getLong("ignore_below", 100)
        set(v) = sp.edit().putLong("ignore_below", v).apply()

    /** 无障碍通道是否启用（用户在 App 内开关，真正生效还要系统授权）。 */
    var accessibilityChannel: Boolean
        get() = sp.getBoolean("channel_a11y", true)
        set(v) = sp.edit().putBoolean("channel_a11y", v).apply()

    var notificationChannel: Boolean
        get() = sp.getBoolean("channel_nls", true)
        set(v) = sp.edit().putBoolean("channel_nls", v).apply()

    var monthlyBudgetCents: Long
        get() = sp.getLong("budget", 300_00L)
        set(v) = sp.edit().putLong("budget", v).apply()

    var lastBillImportAt: Long
        get() = sp.getLong("last_import", 0L)
        set(v) = sp.edit().putLong("last_import", v).apply()

    /** 当前主题包 id，见 ui/theme/ThemePacks.ALL */
    var themeId: String
        get() = sp.getString("theme_id", "sakura") ?: "sakura"
        set(v) = sp.edit().putString("theme_id", v).apply()

    /**
     * 银行短信通道。默认关：它要一条短信权限，而且只对「钱不经过微信支付宝」的场景有用
     * （工资到账、线下刷卡、银行间转账）。
     */
    var smsChannel: Boolean
        get() = sp.getBoolean("channel_sms", false)
        set(v) = sp.edit().putBoolean("channel_sms", v).apply()

    /**
     * 银行提醒（App 推送 + 公众号）走通知监听，不需要额外权限，默认开。
     * 短信收费以后，这条比短信更划算。
     */
    var bankPushChannel: Boolean
        get() = sp.getBoolean("channel_bank_push", true)
        set(v) = sp.edit().putBoolean("channel_bank_push", v).apply()

    /** 开 App 时顺带查一次更新。默认开，嫌烦可以在「我的」里关掉。 */
    var autoCheckUpdate: Boolean
        get() = sp.getBoolean("auto_check_update", true)
        set(v) = sp.edit().putBoolean("auto_check_update", v).apply()

    /** 点了「暂不更新」的版本号。同一版本不再自动弹，手动点「检查更新」照样能查。 */
    var skipVersion: String
        get() = sp.getString("skip_version", "") ?: ""
        set(v) = sp.edit().putString("skip_version", v).apply()

    /** 上次自动检查的时间。冷启动很频繁，6 小时才打一次 GitHub API，免得白耗流量。 */
    var lastUpdateCheckAt: Long
        get() = sp.getLong("last_update_check_at", 0L)
        set(v) = sp.edit().putLong("last_update_check_at", v).apply()

    fun addIgnoredPayee(payee: String) {
        val cur = sp.getStringSet("ignored_payees", emptySet()) ?: emptySet()
        sp.edit().putStringSet("ignored_payees", cur + payee).apply()
    }

    fun ignoredPayees(): Set<String> = sp.getStringSet("ignored_payees", emptySet()) ?: emptySet()

    fun isIgnored(payee: String): Boolean = ignoredPayees().any { payee.contains(it) }
}
