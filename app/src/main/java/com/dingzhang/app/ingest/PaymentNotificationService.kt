package com.dingzhang.app.ingest

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.dingzhang.app.DingZhangApp
import com.dingzhang.app.core.model.Source

/**
 * 通道 A：通知监听服务。
 *
 * 用户需要在「设置 - 通知使用权」里手动勾选本应用。Android 13+ 侧载安装的 App
 * 可能被系统限制为「受限设置」，需要在应用信息里解除限制后才能授权（README 有说明）。
 *
 * 除了微信/支付宝的支付通知，这里还兼收「银行提醒」：
 * - 手机银行 App 的动账推送
 * - 银行公众号的微信提醒（短信收费以后，这两条是更省钱的路子）
 * 判断不靠包名白名单 —— 银行 App 太多，包名也各不相同 —— 而是靠文本特征，
 * 认不出就不记。
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
        ChannelHealth.markAlive("nls")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "通知监听已断开，可能是被系统回收了")
        // 断线可以自己拉回来，不用惊动用户
        try {
            requestRebind(android.content.ComponentName(this, PaymentNotificationService::class.java))
        } catch (t: Throwable) {
            Log.w(TAG, "重绑失败：${t.message}")
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val sbn = sbn ?: return
        val pkg = sbn.packageName ?: return

        if (!IngestPipeline.isReady) IngestPipeline.init(applicationContext)
        ChannelHealth.markAlive("nls")
        val settings = IngestPipeline.settings ?: return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank() && bigText.isBlank()) return

        val isPaymentApp = pkg == PaymentNotifierParser.PKG_WECHAT || pkg == PaymentNotifierParser.PKG_ALIPAY
        // 京东这类购物 App 自己的支付成功通知：只认严格成交句，见 parseShoppingApp
        val isShopping = PaymentNotifierParser.isShoppingApp(pkg)

        // 微信/支付宝/购物 App 之外的包，只在「银行提醒」开着时才看
        if (!isPaymentApp && !isShopping && !settings.bankPushChannel) return
        if ((isPaymentApp || isShopping) && !settings.notificationChannel && !settings.bankPushChannel) return

        // 绝大部分通知和钱无关，先用「元」粗筛，省下每条都跑正则的开销
        // （购物 App 的「使用【xx卡】支付16.90」不带「元」，所以它们跳过这道粗筛）
        if (!isPaymentApp && !isShopping && !(text + bigText).contains("元")) return

        // 同一条通知可能被重复回调，用 key + 内容摘要挡一下。
        // 注意要把 title 和 bigText 一起算进去：微信支付的通知会「更新」自己，
        // 标题变了、正文没变的情况只比 text 会把更新后的内容直接丢掉。
        val dedupKey = (sbn.key ?: "") + "|" + (title + "|" + text + "|" + bigText).hashCode()
        if (recentKeys.contains(dedupKey)) return
        recentKeys.addLast(dedupKey)
        if (recentKeys.size > 40) recentKeys.removeFirst()

        val postedAt = sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis()

        var captured: Captured? = null
        if (isPaymentApp && settings.notificationChannel) {
            captured = PaymentNotifierParser.parse(pkg, title, text, bigText, postedAt, settings)
        }
        // 微信里银行公众号的提醒、以及银行 App 的推送，交给银行解析器
        // 支付宝自己的通知已经由上面的解析器处理，不再往银行通道兜
        // 购物 App：京东自己发的支付成功通知，严格规则在 parseShoppingApp 里
        if (captured == null && isShopping && settings.notificationChannel) {
            captured = PaymentNotifierParser.parseShoppingApp(pkg, title, text, bigText, postedAt)
        }
        if (captured == null && settings.bankPushChannel && pkg != PaymentNotifierParser.PKG_ALIPAY &&
            !isShopping
        ) {
            captured = parseBankAlert(pkg, title, text, bigText, postedAt)
        }
        if (captured == null) return

        val outcome = IngestPipeline.submit(captured)
        Log.d(TAG, "通知记账: ${outcome.accepted} ${outcome.reason}")

        // 静默入账：不弹「记上了」的提示，付完钱不该被打扰
        if (outcome.accepted) {
            DingZhangApp.bumpDataVersion()
        } else if (outcome.merged) {
            DingZhangApp.bumpDataVersion()
        }
    }

    /**
     * 微信里聊天内容也可能出现「消费 xxx 元」，所以公众号提醒必须再过一道：
     * 通知标题里要有「银行」。银行 App 的推送没这个限制，但解析器本身就要求
     * 同时出现「银行/储蓄卡/账户」和金额、方向词，误判概率很低。
     */
    private fun parseBankAlert(
        pkg: String,
        title: String,
        text: String,
        bigText: String,
        at: Long
    ): Captured? {
        if (pkg == PaymentNotifierParser.PKG_WECHAT && !title.contains("银行")) return null
        val body = listOf(title, text, bigText).filter { it.isNotBlank() }.joinToString(" ")
        return BankAlertParser.parse(body, at, Source.NOTIFICATION)
    }

    companion object {
        private const val TAG = "NLS"
    }
}
