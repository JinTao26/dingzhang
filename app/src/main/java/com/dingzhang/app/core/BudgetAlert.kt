package com.dingzhang.app.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.dingzhang.app.MainActivity
import com.dingzhang.app.R
import com.dingzhang.app.core.model.Money
import com.dingzhang.app.ingest.IngestPipeline
import java.util.Calendar

/**
 * 月度预算提醒。
 *
 * 花到「预算 × 设定比例」时在通知栏提醒一次，真超了预算再补一条 —— 一个月最多两条，
 * 同一档不会重复弹。检查挂在每次记账成功之后，所以不用打开 App 也能收到。
 *
 * 只在用户设了月预算、并且没把提醒关掉（比例为 0）的时候才工作。
 */
object BudgetAlert {

    private const val CHANNEL_ID = "budget_alert"
    private const val NOTIFY_ID = 1002

    /** 记账后、开 App 时都调这个。走后台线程，不拖慢采集。 */
    fun check() {
        val ctx = IngestPipeline.appContext ?: return
        Thread {
            try {
                checkNow(ctx)
            } catch (t: Throwable) {
                // 提醒失败不能影响记账
            }
        }.apply { priority = Thread.MIN_PRIORITY }.start()
    }

    private fun checkNow(ctx: Context) {
        val settings = IngestPipeline.settings ?: return
        val dao = IngestPipeline.dao ?: return

        val budget = settings.monthlyBudgetCents
        val percent = settings.budgetAlertPercent
        if (budget <= 0L || percent <= 0) return

        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        val monthKey = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH) + 1}"

        // 换月了，把上一月的档位清零，允许重新提醒
        if (settings.budgetAlertMonth != monthKey) {
            settings.budgetAlertMonth = monthKey
            settings.budgetAlertLevel = 0
        }

        val endCal = (cal.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
        val spent = dao.sumByKind(start, endCal.timeInMillis).first
        if (spent <= 0L) return

        val ratio = spent.toDouble() * 100.0 / budget.toDouble()
        val level = when {
            ratio >= 100.0 -> 2
            ratio >= percent.toDouble() -> 1
            else -> 0
        }
        if (level <= settings.budgetAlertLevel) return

        settings.budgetAlertLevel = level
        post(ctx, level, spent, budget, ratio)
    }

    private fun post(ctx: Context, level: Int, spent: Long, budget: Long, ratio: Double) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            nm.getNotificationChannel(CHANNEL_ID) == null
        ) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "预算提醒", NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "花到预算设定比例时提醒一次" }
            )
        }

        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (level >= 2) "这个月的预算已经花超了" else "预算用掉 ${ratio.toInt()}%"
        val text = "已花 ¥${Money.short(spent)}，预算 ¥${Money.short(budget)}"
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_note)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        try {
            nm.notify(NOTIFY_ID, n)
        } catch (e: SecurityException) {
            // 没给通知权限就算了，账本身没问题
        }
    }
}
