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

    fun addIgnoredPayee(payee: String) {
        val cur = sp.getStringSet("ignored_payees", emptySet()) ?: emptySet()
        sp.edit().putStringSet("ignored_payees", cur + payee).apply()
    }

    fun ignoredPayees(): Set<String> = sp.getStringSet("ignored_payees", emptySet()) ?: emptySet()

    fun isIgnored(payee: String): Boolean = ignoredPayees().any { payee.contains(it) }
}
