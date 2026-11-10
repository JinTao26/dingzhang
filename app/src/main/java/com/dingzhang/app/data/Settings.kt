package com.dingzhang.app.data

import android.content.Context

/** 轻量开关。真正重要的数据都在 SQLite 里。 */
class Settings(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences("dingzhang_settings", Context.MODE_PRIVATE)

    /** 装好第一次打开时那个说明弹窗，看过一次就不再弹。 */
    var introSeen: Boolean
        get() = sp.getBoolean("intro_seen", false)
        set(v) = sp.edit().putBoolean("intro_seen", v).apply()

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit().putBoolean("onboarded", v).apply()

    /** 自动识别到一笔后，直接落库（true）还是先挂起等确认（false）。 */
    var autoCommit: Boolean
        get() = sp.getBoolean("auto_commit", true)
        set(v) = sp.edit().putBoolean("auto_commit", v).apply()

    /** 小于这个金额不进账。默认 0（不过滤），这个设置项已经从界面上拿掉了。 */
    var ignoreBelowCents: Long
        get() = sp.getLong("ignore_below", 0)
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

    /** 每年预算。0 表示没设，不参与任何显示。 */
    var yearBudgetCents: Long
        get() = sp.getLong("budget_year", 0L)
        set(v) = sp.edit().putLong("budget_year", v).apply()

    var lastBillImportAt: Long
        get() = sp.getLong("last_import", 0L)
        set(v) = sp.edit().putLong("last_import", v).apply()

    /** 已应用到库里的分类词典版本。词典扩了以后靠它触发一次「重新分类」。 */
    var dictVersion: Int
        get() = sp.getInt("dict_version", 1)
        set(v) = sp.edit().putInt("dict_version", v).apply()

    /** 当前主题包 id，见 ui/theme/ThemePacks.ALL */
    var themeId: String
        get() = sp.getString("theme_id", "sakura") ?: "sakura"
        set(v) = sp.edit().putString("theme_id", v).apply()

    /**
     * 银行短信通道。默认关：要一条 RECEIVE_SMS 运行时权限（部分机型需要在系统设置里手动放开）。
     * 适合「钱不经过微信支付宝」的场景：工资到账、线下刷卡、银行间转账。
     * 注意：只收实时广播，不申请 READ_SMS，不读历史短信箱。
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
