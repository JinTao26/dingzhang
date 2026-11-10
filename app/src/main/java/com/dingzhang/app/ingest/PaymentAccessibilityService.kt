package com.dingzhang.app.ingest

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
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

    /** 上一次是否真的认出了支付页。认出来才上长冷却，没认出来别堵住后面的真页面 */
    private val lastParseOk = HashMap<String, Boolean>()
    private val recentPages = ArrayDeque<String>()
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

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

        val settings = IngestPipeline.settings ?: return
        if (!settings.accessibilityChannel) return

        // 支付页刚跳出来时节点常常还没渲染完，第一次回调往往抓到空页面。
        // 以前的写法是「1.2 秒内的事件直接丢掉」，结果页面晚 300ms 加载好就再也抓不到。
        // 现在不丢事件：密集回调合并成一次，等页面稳定 350ms 后解析最后一次。
        pending?.let { handler.removeCallbacks(it) }
        val task = Runnable { parsePage(pkg) }
        pending = task
        handler.postDelayed(task, 350)
    }

    private fun parsePage(pkg: String) {
        if (!IngestPipeline.isReady) IngestPipeline.init(applicationContext)

        val now = System.currentTimeMillis()
        val need = if (lastParseOk[pkg] == true) 1200L else 350L
        if (now - (lastParseAt[pkg] ?: 0L) < need) return
        lastParseAt[pkg] = now

        val root = rootInActiveWindow ?: return
        val texts = ArrayList<String>(64)
        collectTexts(root, texts, 0)
        if (texts.isEmpty()) return

        val captured = PaymentAccessibilityParser.parse(pkg, texts, now)
        lastParseOk[pkg] = captured != null

        if (captured == null) {
            // 明显是支付/账单页却没认出来，记一行现场，方便照着补规则
            if (looksLikePaymentPage(texts)) {
                Log.d(TAG, "未命中规则 $pkg: " + texts.take(8).joinToString(" / ").take(160))
            }
            return
        }

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

    /** 判断这页「像是在付钱」，只用于决定要不要留诊断日志。 */
    private fun looksLikePaymentPage(texts: List<String>): Boolean {
        val flat = texts.joinToString(" ")
        val hasMoney = moneyPatterns.any { it.containsMatchIn(flat) }
        val hasPayWord = listOf("支付", "付款", "账单", "交易", "收款", "碰一下").any { flat.contains(it) }
        return hasMoney && hasPayWord
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

    override fun onDestroy() {
        pending?.let { handler.removeCallbacks(it) }
        pending = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "A11y"

        private val moneyPatterns = listOf(
            Regex("[¥￥]\\s*[0-9]"),
            Regex("[0-9]\\s*元"),
            Regex("[−\\-－+＋]\\s*[0-9]+\\.[0-9]{2}")
        )
    }
}
