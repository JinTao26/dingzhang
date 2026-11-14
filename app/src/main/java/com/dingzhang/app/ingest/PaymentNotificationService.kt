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
        IngestLog.add("通知", "监听已连接", "开始接收支付通知")
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

    /**
     * 用户从最近任务里把 App 划掉。
     * 一个 Activity 都没有了，进程随时可能被收走；趁现在还在，先把自己重新绑上，
     * 免得划一下之后安静到用户以为「又不记了」。
     */
    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i(TAG, "任务被划掉，重新绑定通知监听")
        try {
            requestRebind(android.content.ComponentName(this, PaymentNotificationService::class.java))
        } catch (t: Throwable) {
            Log.w(TAG, "划掉后重绑失败：${t.message}")
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val sbn = sbn ?: return
        val pkg = sbn.packageName ?: return

        if (!IngestPipeline.isReady) IngestPipeline.init(applicationContext)
        ChannelHealth.markAlive("nls")
        val settings = IngestPipeline.settings ?: return

        if (sbn.notification?.extras == null) return
        // 一次把所有可能带字的地方都读出来（含 InboxStyle 的行数组、聊天式消息列表），
        // 不再只看 TITLE / TEXT / BIG_TEXT 三个字段 —— 微信支付凭证的金额经常不在那三个里
        val snap = NotificationText.read(sbn.notification)
        val title = snap.title
        val text = snap.body
        val bigText = ""
        if (title.isBlank() && text.isBlank()) return

        val isPaymentApp = pkg == PaymentNotifierParser.PKG_WECHAT || pkg == PaymentNotifierParser.PKG_ALIPAY
        // 京东这类购物 App 自己的支付成功通知：只认严格成交句，见 parseShoppingApp
        val isShopping = PaymentNotifierParser.isShoppingApp(pkg)

        // 付款码付完到底有没有通知、通知里写了什么，以前只能猜。
        // 微信付款码那一笔既没有支付页、通知又说不到，结果就是「什么都没记、也看不出为什么」。
        // 这里把所有看起来和钱有关的通知留一行现场：有标题和字数就够判断了。
        // 只记标题前 12 个字，不记正文 —— 微信通知里可能夹着聊天内容。
        if (isPaymentApp || isShopping) {
            val looksMoney = Regex("[¥￥]|[0-9]+\\.[0-9]{2}|元|支付|付款|收款|消费|扣款|转账")
                .containsMatchIn(title + text)
            if (looksMoney) {
                IngestLog.addProbe(
                    "通知", "收到一条",
                    PaymentNotifierParser.appLabel(pkg) + " · 标题「" + title.take(12) + "」 · " +
                        text.length + " 字"
                )
            }
        }

        // 微信/支付宝/购物 App 之外的包，只在「银行提醒」开着时才看
        if (!isPaymentApp && !isShopping && !settings.bankPushChannel && !settings.bankWechatChannel) return
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
        if (settings.notificationChannel) {
            // 购物类先走严格规则（京东那种「使用【xx卡】支付16.90」只有它认得出）；
            // 认不出再交给通用解析器 —— 抖音、淘宝、美团、拼多多、云闪付的成交句
            // 跟微信支付宝不一样，以前一律被当成「不是支付 App」扔掉。
            if (isShopping) {
                captured = PaymentNotifierParser.parseShoppingApp(pkg, title, text, bigText, postedAt)
            }
            if (captured == null) {
                captured = PaymentNotifierParser.parse(pkg, title, text, bigText, postedAt, settings)
            }
        }
        // 银行这两条拆开管：银行 App 自己的推送看 bankPushChannel，
        // 微信里的银行公众号看 bankWechatChannel（默认关）。
        // 想躲公众号的噪音，不该把 POS 刷卡那条路一起堵掉。
        val bankWanted = if (pkg == PaymentNotifierParser.PKG_WECHAT) {
            settings.bankWechatChannel
        } else {
            settings.bankPushChannel
        }
        if (captured == null && bankWanted && pkg != PaymentNotifierParser.PKG_ALIPAY &&
            !isShopping
        ) {
            captured = parseBankAlert(pkg, title, text, bigText, postedAt)
        }
        // 没认出来就一声不吭，用户那边只能看到「一条通知记录都没有」，
        // 分不清是通知压根没来、还是来了但没认出来。付款类 App 的通知留一行现场。
        // 只写判断结果，不写正文原文 —— 微信通知里可能夹着聊天内容。
        if (captured == null) {
            if (isPaymentApp || isShopping) {
                val body = title + " " + text + " " + bigText
                val looksPay = listOf("支付", "付款", "转账", "收款", "¥", "元").any { body.contains(it) }
                if (looksPay) {
                    val hasMoney = Regex("[¥￥]\\s*[0-9]|[0-9]\\s*元|[0-9]+\\.[0-9]{2}").containsMatchIn(body)
                    IngestLog.add(
                        "通知", if (hasMoney) "有金额但没认出" else "付款通知没带金额",
                        "${if (pkg == PaymentNotifierParser.PKG_WECHAT) "微信" else if (pkg == PaymentNotifierParser.PKG_ALIPAY) "支付宝" else pkg.takeLast(14)}" +
                            " · 标题「${title.take(10)}」· 读到 ${snap.fields.ifBlank { "空" }}" +
                            " · 正文 ${text.length} 字"
                    )
                }
            }
            return
        }

        // 静默入账：不弹「记上了」的提示，付完钱不该被打扰。
        // 入库走后台队列，通知回调在主线程，不能拿来跑 SQL。
        IngestPipeline.submitAsync(captured) { outcome ->
            Log.d(TAG, "通知记账: ${outcome.accepted} ${outcome.reason}")
            IngestLog.add(
                "通知",
                if (outcome.accepted) "记上了" else "没记上",
                "${captured.payee} ${captured.amountCents / 100.0} 元 · ${outcome.reason}"
            )
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
        // 以前只认标题里的「银行」两个字。但微信里银行公众号的显示名很随意：
        // 「招商银行信用卡」有银行、「工行信用卡」「交行微银行」没有标准写法，
        // 一律要求「银行」会把它们全挡在外面。这里放宽成「银行 / 常见行名 / 卡种」，
        // 后面还有金额 + 动账动词两道校验兜着，误判率不会变高。
        if (pkg == PaymentNotifierParser.PKG_WECHAT && !looksLikeBankAccount(title)) return null
        val body = listOf(title, text, bigText).filter { it.isNotBlank() }.joinToString(" ")
        return BankAlertParser.parse(body, at, Source.NOTIFICATION)
    }

    /** 微信里这个公众号是不是银行自己的（标题太随意的，靠这几个词认） */
    private fun looksLikeBankAccount(title: String): Boolean {
        if (title.contains("银行") || title.contains("信用卡") || title.contains("储蓄卡") ||
            title.contains("借记卡")
        ) return true
        return BANK_SHORT_NAMES.any { title.contains(it) }
    }

    companion object {
        private const val TAG = "NLS"
        /** 银行简称。公众号叫「工行信用卡」「交行微银行」时，标题里并没有「银行」二字 */
        private val BANK_SHORT_NAMES = listOf(
            "工行", "建行", "农行", "中行", "交行", "招行", "浦发", "民生", "兴业",
            "光大", "中信", "平安", "华夏", "广发", "邮储", "邮政储蓄", "浙商", "恒丰",
            "渤海", "北京银行", "上海银行", "宁波银行", "江苏银行", "南京银行", "杭州银行"
        )
    }
}
