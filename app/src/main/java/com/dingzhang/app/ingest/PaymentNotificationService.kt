package com.dingzhang.app.ingest

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.dingzhang.app.DingZhangApp
import com.dingzhang.app.data.Settings

/**
 * 通道 A：通知监听服务。
 *
 * 用户需要在「设置 - 通知使用权」里手动勾选本应用。Android 13+ 侧载安装的 App
 * 可能被系统限制为「受限设置」，需要在应用信息里解除限制后才能授权（README 有说明）。
 */
class PaymentNotificationService : NotificationListenerService() {

    private val recentKeys = ArrayDeque<String>()

    override fun onCreate() {
        super.onCreate()
        IngestPipeline.init(applicationContext)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "通知监听已连接")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "通知监听已断开，可能是被系统回收了")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val sbn = sbn ?: return

        val pkg = sbn.packageName ?: return
        if (pkg != PaymentNotifierParser.PKG_WECHAT && pkg != PaymentNotifierParser.PKG_ALIPAY) return

        if (!IngestPipeline.isReady) IngestPipeline.init(applicationContext)
        val settings = IngestPipeline.settings ?: return
        if (!settings.notificationChannel) return

        // 同一条通知可能被重复回调，用 key + 文本摘要挡一下
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
        val dedupKey = (sbn.key ?: "") + "|" + (text?.toString() ?: "").hashCode()
        if (recentKeys.contains(dedupKey)) return
        recentKeys.addLast(dedupKey)
        if (recentKeys.size > 40) recentKeys.removeFirst()

        val captured = PaymentNotifierParser.parse(
            pkg = pkg,
            title = title,
            text = text,
            bigText = bigText,
            postedAt = sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis(),
            settings = settings
        ) ?: run {
            // 正常情况下大量通知都不是交易，这里不打日志刷屏
            return
        }

        val outcome = IngestPipeline.submit(captured)
        Log.d(TAG, "通知记账: ${outcome.accepted} ${outcome.reason}")

        if (outcome.accepted) {
            NotificationHelper.notifyAutoCaptured(
                this,
                outcome.txn?.payee ?: captured.payee,
                outcome.txn?.amountCents ?: captured.amountCents,
                outcome.txn?.categoryId ?: "other"
            )
            DingZhangApp.bumpDataVersion()
        }
    }

    companion object {
        private const val TAG = "NLS"
    }
}
