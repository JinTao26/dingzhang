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

        // 等了 350ms，用户可能已经切到别的 App。窗口和事件包名对不上，
        // 就拿错页面去解析了（微信记的成了支付宝的页面），所以先对一次。
        val roots = rootsFor(pkg)
        if (roots.isEmpty()) {
            Log.d(TAG, "没有这个 App 的可读窗口")
            IngestLog.add(
                "无障碍", "没有可读的窗口",
                "活动窗口是 ${rootInActiveWindow?.packageName}，事件来自 $pkg"
            )
            return
        }

        val now = System.currentTimeMillis()
        val need = if (lastParseOk[pkg] == true) 1200L else 350L
        if (now - (lastParseAt[pkg] ?: 0L) < need) return
        // 拿到可用窗口才算一次解析，别让空窗口白白吃掉冷却时间
        lastParseAt[pkg] = now

        val texts = ArrayList<String>(200)
        roots.forEach { collectTexts(it, texts, 0) }
        if (texts.isEmpty()) {
            IngestLog.add("无障碍", "窗口里没有文字节点", "共 ${roots.size} 个窗口 / $pkg")
            return
        }

        val captured = PaymentAccessibilityParser.parse(pkg, texts, now)
        lastParseOk[pkg] = captured != null

        if (captured == null) {
            // 明显是支付/账单页却没认出来，记一行现场，方便照着补规则
            if (looksLikePaymentPage(texts)) {
                Log.d(TAG, "未命中规则 $pkg: " + texts.take(8).joinToString(" / ").take(160))
                IngestLog.add(
                    "无障碍", "认不出这页",
                    texts.take(6).joinToString(" / ")
                )
            } else {
                IngestLog.add("无障碍", "不是支付页", texts.take(4).joinToString(" / "))
            }
            return
        }

        // 同一个页面可能被反复解析（窗口内容变化会连续触发），按页面指纹挡一下。
        // 有交易单号就用单号；没有就用「秒」，不用「分钟」——
        // 分钟级指纹会把同一分钟内同店同金额的两笔真实消费当成同一页，第二笔直接被挡掉。
        val pageKey = captured.externalId?.let { "no|$it" }
            ?: "${captured.wallet.db}|${captured.amountCents}|${captured.payee}|${now / 1000}"
        if (recentPages.contains(pageKey)) {
            IngestLog.add("无障碍", "同一页刚处理过", pageKey)
            return
        }
        recentPages.addLast(pageKey)
        if (recentPages.size > 30) recentPages.removeFirst()

        val outcome = IngestPipeline.submit(captured)
        Log.i(TAG, "无障碍记账: accepted=${outcome.accepted} ${outcome.reason}")
        IngestLog.add(
            "无障碍",
            if (outcome.accepted) "记上了" else "没记上",
            "${captured.payee} ${captured.amountCents / 100.0} 元 · ${outcome.reason}"
        )

        if (outcome.accepted) {
            NotificationHelper.notifyAutoCaptured(
                this,
                outcome.txn?.payee ?: captured.payee,
                outcome.txn?.amountCents ?: captured.amountCents,
                outcome.txn?.categoryId ?: "other"
            )
            DingZhangApp.bumpDataVersion()
        } else if (outcome.merged) {
            // 这笔记过了，但商户名/分类刚被补全，界面也得跟着变
            DingZhangApp.bumpDataVersion()
        }
    }

    /**
     * 取这个 App 当前所有可读窗口的根节点。
     *
     * 支付结果页常常是盖在聊天页上面的独立窗口（浮层）。只读 rootInActiveWindow
     * 拿到的是下面那一层，上面那层写着「支付成功」「¥2.00」的窗口永远看不到 ——
     * 这是之前「扫码付了、账单里没有」的原因之一。
     */
    private fun rootsFor(pkg: String): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>(3)
        rootInActiveWindow?.let { r ->
            if (r.packageName?.toString() == pkg) out.add(r)
        }
        runCatching {
            windows?.forEach { w ->
                val r = w?.root ?: return@forEach
                if (r.packageName?.toString() == pkg && out.none { it === r }) out.add(r)
            }
        }
        return out
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
        if (node == null || depth > 16 || out.size >= 160) return

        node.text?.toString()?.trim()?.let { if (it.isNotEmpty() && it.length <= 60) out.add(it) }
        node.contentDescription?.toString()?.trim()?.let {
            if (it.isNotEmpty() && it.length <= 60 && it !in out) out.add(it)
        }

        for (i in 0 until node.childCount) {
            collectTexts(node.getChild(i), out, depth + 1)
            if (out.size >= 160) return
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
