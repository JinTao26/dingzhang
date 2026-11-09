package com.dingzhang.app.ingest

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.dingzhang.app.R
import com.dingzhang.app.core.model.Categories
import com.dingzhang.app.core.model.Money

/**
 * 自动记账成功的轻提示。低频、不打扰：
 * 60 秒内最多弹一条，避免在超市连买几样东西时刷屏。
 */
object NotificationHelper {

    private const val CHANNEL_ID = "auto_capture"
    private const val NOTIFY_ID = 1001

    @Volatile private var lastNotifyAt = 0L

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(
                CHANNEL_ID, "自动记账提醒", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "识别到一笔支付后轻轻告诉你一声"
                setShowBadge(false)
            }
            nm.createNotificationChannel(ch)
        }
    }

    fun notifyAutoCaptured(ctx: Context, payee: String, amountCents: Long, categoryId: String) {
        val now = System.currentTimeMillis()
        if (now - lastNotifyAt < 60_000) return
        lastNotifyAt = now

        ensureChannel(ctx)
        val cat = Categories.of(categoryId)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_note)
            .setContentTitle("记上了 · ${cat.name}")
            .setContentText("$payee  ¥${Money.yuan(amountCents)}")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        try {
            nm.notify(NOTIFY_ID, n)
        } catch (e: SecurityException) {
            // 用户没给通知权限，静默失败即可，账已经记上了
        }
    }
}
