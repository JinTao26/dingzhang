package com.dingzhang.app.ingest

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log

/**
 * 两个自动通道的「活着没活着」。
 *
 * 这是自动记账最隐蔽的失败方式：手机把服务收掉了，但系统设置里的开关依然是勾着的，
 * 看着一切正常，实际一条事件都不来。用户只会觉得「又不记了」，查不出原因。
 *
 * 这里做三件事：
 * 1. 每个通道收到东西就记一次心跳；
 * 2. 通知监听的 binder 断了可以直接 requestRebind 拉回来，不用用户动手；
 * 3. 无障碍被系统关了没法程序化重新打开（Android 不允许），
 *    只能靠心跳判断「勾着但早就不干活了」，然后在界面上明说。
 */
object ChannelHealth {

    private const val TAG = "Health"
    private const val A11Y = "a11y"
    private const val NLS = "nls"

    /** 超过这个时长没有心跳，就认为通道其实已经死了。 */
    private const val STALE_MS = 24 * 60 * 60 * 1000L
    /** 心跳落盘的最小间隔，避免每条通知都写一次 SharedPreferences。 */
    private const val FLUSH_MS = 60_000L

    @Volatile private var memA11y = 0L
    @Volatile private var memNls = 0L
    @Volatile private var lastFlush = 0L

    fun markAlive(source: String) {
        val now = System.currentTimeMillis()
        when (source) {
            A11Y -> memA11y = now
            else -> memNls = now
        }
        val settings = IngestPipeline.settings ?: return
        if (now - lastFlush < FLUSH_MS) return
        lastFlush = now
        settings.a11yLastAt = memA11y
        settings.nlsLastAt = memNls
    }

    private fun lastAt(source: String): Long {
        val settings = IngestPipeline.settings ?: return 0L
        val mem = if (source == A11Y) memA11y else memNls
        val disk = if (source == A11Y) settings.a11yLastAt else settings.nlsLastAt
        return maxOf(mem, disk)
    }

    /** 系统设置里勾着这个无障碍服务。 */
    fun a11yEnabled(ctx: Context): Boolean = try {
        val s = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        s?.contains(ctx.packageName) == true
    } catch (e: Throwable) {
        false
    }

    /** 通知使用权给没给。 */
    fun nlsEnabled(ctx: Context): Boolean = try {
        val flat = Settings.Secure.getString(
            ctx.contentResolver, "enabled_notification_listeners"
        )
        flat?.contains(ctx.packageName) == true
    } catch (e: Throwable) {
        false
    }

    /**
     * 勾着，但很久没收到过任何事件 —— 多半是被系统停了。
     * 只在「以前收到过」时才判断，避免刚装好还没用过就误报。
     */
    fun a11yLooksDead(ctx: Context): Boolean {
        if (!a11yEnabled(ctx)) return false
        val last = lastAt(A11Y)
        if (last <= 0L) return false
        return System.currentTimeMillis() - last > STALE_MS
    }

    fun nlsLooksDead(ctx: Context): Boolean {
        if (!nlsEnabled(ctx)) return false
        val last = lastAt(NLS)
        if (last <= 0L) return false
        return System.currentTimeMillis() - last > STALE_MS
    }

    /** 最近一次无障碍事件距今多久（没有记录返回 -1）。 */
    fun a11yIdleMs(): Long {
        val last = lastAt(A11Y)
        return if (last <= 0L) -1L else System.currentTimeMillis() - last
    }

    /**
     * 最近一次收到通知距今多久（没有记录返回 -1）。
     *
     * 这是给用户自证用的：通知使用权看着是开着的，但绑根本没连上，
     * 一条通知都收不到，界面上却毫无异常。付一笔之后看这个数字
     * 有没有往前走，就能区分「通知没来」和「通知来了但没认出」。
     */
    fun nlsIdleMs(): Long {
        val last = lastAt(NLS)
        return if (last <= 0L) -1L else System.currentTimeMillis() - last
    }

    /**
     * 通知监听的 binder 掉了可以自己拉回来。这是唯一能「无需用户操作」自愈的通道，
     * 所以每次打开 App 都顺手检查一次。
     */
    fun ensureNotificationListening(ctx: Context) {
        if (!nlsEnabled(ctx)) return
        try {
            NotificationListenerService.requestRebind(
                ComponentName(ctx, PaymentNotificationService::class.java)
            )
        } catch (t: Throwable) {
            Log.w(TAG, "requestRebind 失败：${t.message}")
        }
    }
}
