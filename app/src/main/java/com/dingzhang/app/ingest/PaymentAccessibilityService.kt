package com.dingzhang.app.ingest

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.dingzhang.app.DingZhangApp

/**
 * 通道 B：无障碍服务。
 *
 * 只订阅了微信和支付宝两个包（见 accessibility_service_config.xml 的 packageNames），
 * 系统层面就不会把其它 App 的页面内容送进来，这是隐私最小化的第一道闸。
 * 另外每次解析前还会再校验一次包名，双保险。
 */
class PaymentAccessibilityService : AccessibilityService() {

    private val lastParseAt = HashMap<String, Long>()
    private val recentPages = ArrayDeque<String>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        IngestPipeline.init(applicationContext)
        Log.i(TAG, "无障碍服务已连接，开始守支付页面")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val pkg = e.packageName?.toString() ?: return
        if (pkg != PaymentNotifierParser.PKG_WECHAT && pkg != PaymentNotifierParser.PKG_ALIPAY) return

        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            e.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        if (!IngestPipeline.isReady) IngestPipeline.init(applicationContext)
        val settings = IngestPipeline.settings ?: return
        if (!settings.accessibilityChannel) return

        // 页面内容变化回调非常频繁，按包节流，1.2 秒最多解析一次
        val now = System.currentTimeMillis()
        val last = lastParseAt[pkg] ?: 0L
        if (now - last < 1200) return
        lastParseAt[pkg] = now

        val root = rootInActiveWindow ?: return
        val texts = ArrayList<String>(64)
        collectTexts(root, texts, 0)
        if (texts.isEmpty()) return

        val captured = PaymentAccessibilityParser.parse(pkg, texts, now) ?: return

        // 同一个页面可能被反复解析（窗口内容变化会连续触发），按页面指纹挡一下
        val pageKey = "${captured.wallet.db}|${captured.amountCents}|${captured.payee}|${now / 60000}"
        if (recentPages.contains(pageKey)) return
        recentPages.addLast(pageKey)
        if (recentPages.size > 30) recentPages.removeFirst()

        val outcome = IngestPipeline.submit(captured)
        Log.i(TAG, "无障碍记账: accepted=${outcome.accepted} ${outcome.reason}")

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

    /** 深度优先收集可见文本，限深 12 层、最多 120 条，防止超大页面拖慢主线程。 */
    private fun collectTexts(node: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int) {
        if (node == null || depth > 12 || out.size >= 120) return

        node.text?.toString()?.trim()?.let { if (it.isNotEmpty() && it.length <= 60) out.add(it) }
        node.contentDescription?.toString()?.trim()?.let {
            if (it.isNotEmpty() && it.length <= 60 && it !in out) out.add(it)
        }

        for (i in 0 until node.childCount) {
            collectTexts(node.getChild(i), out, depth + 1)
            if (out.size >= 120) return
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "无障碍服务被中断")
    }

    companion object {
        private const val TAG = "A11y"
    }
}
