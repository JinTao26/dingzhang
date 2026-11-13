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

    /**
     * 月度预算花到这个比例就在通知栏提醒一次。0 = 关掉提醒，默认 80。
     * 一个月最多两条：到设定比例一条，超预算再一条，不会反复弹。
     */
    var budgetAlertPercent: Int
        get() = sp.getInt("budget_alert_percent", 80)
        set(v) = sp.edit().putInt("budget_alert_percent", v.coerceIn(0, 100)).apply()

    /** 上次提醒发生在哪个月（"2026-11"），换月就允许重新提醒。 */
    var budgetAlertMonth: String
        get() = sp.getString("budget_alert_month", "") ?: ""
        set(v) = sp.edit().putString("budget_alert_month", v).apply()

    /** 这个月已经提醒到哪一档：0 没提醒 / 1 到设定比例 / 2 超预算。 */
    var budgetAlertLevel: Int
        get() = sp.getInt("budget_alert_level", 0)
        set(v) = sp.edit().putInt("budget_alert_level", v).apply()

    /**
     * 两个自动通道最近一次真的收到东西的时间。
     *
     * 「系统里勾着」不等于「服务在跑」：进程被回收、binder 断开的时候，开关还是开着的，
     * 但一条事件都不会来。靠这个时间戳才能看出「看起来开着、其实早就死了」。
     */
    var a11yLastAt: Long
        get() = sp.getLong("a11y_last_at", 0L)
        set(v) = sp.edit().putLong("a11y_last_at", v).apply()

    var nlsLastAt: Long
        get() = sp.getLong("nls_last_at", 0L)
        set(v) = sp.edit().putLong("nls_last_at", v).apply()

    var lastBillImportAt: Long
        get() = sp.getLong("last_import", 0L)
        set(v) = sp.edit().putLong("last_import", v).apply()

    /** 已应用到库里的分类词典版本。词典扩了以后靠它触发一次「重新分类」。 */
    var dictVersion: Int
        get() = sp.getInt("dict_version", 1)
        set(v) = sp.edit().putInt("dict_version", v).apply()

    /** 历史上误记成支出的还款，修过一次就不再重复扫 */
    var repayFixed: Boolean
        get() = sp.getBoolean("fix_repay_v1", false)
        set(v) = sp.edit().putBoolean("fix_repay_v1", v).apply()

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
     * 银行 App 自己的动账推送，走通知监听，不需要额外权限，默认开。
     *
     * 这条覆盖的是微信支付宝都看不见的钱：POS 直接刷卡、云闪付、银行代扣。
     * 噪音低、价值高，所以默认开。
     */
    var bankPushChannel: Boolean
        get() = sp.getBoolean("channel_bank_push", true)
        set(v) = sp.edit().putBoolean("channel_bank_push", v).apply()

    /**
     * 银行公众号（在微信里）的动账提醒。默认关。
     *
     * 以前和上面那条捆在同一个开关里 —— 想关公众号就得连 App 推送一起关，
     * 等于为了躲噪音把唯一能看见 POS 刷卡的路也堵了。现在拆开单独管。
     * 关掉不影响银行 App 推送：银行自己的推送包名是银行 App，公众号走的是微信。
     */
    var bankWechatChannel: Boolean
        get() = sp.getBoolean("channel_bank_wechat", false)
        set(v) = sp.edit().putBoolean("channel_bank_wechat", v).apply()

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
