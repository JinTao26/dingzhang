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
    /**
     * 授权「掉了」：以前确实跑过（有心跳落盘），现在系统里却勾不上了。
     *
     * 这跟「从来不认识这功能」是两回事 —— 前者要提醒，后者闭嘴。
     * 澎湃/EMUI 这类系统会自己关无障碍：闲置自动关闭、省电模式、
     * 安全中心一键清理、重启后受限设置重置，都会出现这种情况。
     * 用户那边只会觉得「怎么又突然不记了」，所以首页要说一句。
     */
    /** 我们自己那个无障碍服务的组件名，写进系统设置时要用这种 "包名/类名" 形式 */
    private fun a11yComponent(ctx: Context): String =
        android.content.ComponentName(ctx, PaymentAccessibilityService::class.java)
            .flattenToString()

    /**
     * 尝试把被系统关掉的无障碍自己写回去。
     *
     * 需要用户用 adb 授过一次 WRITE_SECURE_SETTINGS：
     * `adb shell pm grant com.dingzhang.app android.permission.WRITE_SECURE_SETTINGS`
     * 没授过就写不进去，这里直接返回 false，调用方照常走「首页提示 → 点一下去开」。
     *
     * 只往前加，不改别的服务的条目：系统里可能还勾着 TalkBack 之类的东西，
     * 覆盖整串会把别人的开关弄丢，那是比不记账严重得多的事故。
     */
    fun tryRestoreA11y(ctx: Context): Boolean {
        // 从没开过就不去碰系统设置：那是用户自己的选择，不是「掉了」
        val settings = IngestPipeline.settings ?: return false
        if (settings.a11yLastAt <= 0L) return false
        if (a11yEnabled(ctx)) return false
        return try {
            val cr = ctx.contentResolver
            val self = a11yComponent(ctx)
            val cur = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            if (cur.split(':').any { it.trim().equals(self, true) }) return false
            val next = (cur.split(':').filter { it.isNotBlank() } + self).joinToString(":")
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next)
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            Log.i(TAG, "无障碍被系统关掉了，已自己写回去")
            true
        } catch (t: Throwable) {
            // 多数情况是 SecurityException：没有 WRITE_SECURE_SETTINGS
            Log.d(TAG, "无障碍无法自愈（没授 adb 权限）：${t.javaClass.simpleName}")
            false
        }
    }

    fun a11yRevoked(ctx: Context): Boolean {
        val settings = IngestPipeline.settings ?: return false
        return settings.a11yLastAt > 0L && !a11yEnabled(ctx)
    }

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
