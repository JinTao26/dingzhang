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
    /** 三次采样的时间点（相对第一次事件）。 */
    private val probeDelays = longArrayOf(150L, 450L, 1000L)
    private var probePkg: String? = null
    private var probeStep = 0
    private var probeRun: Runnable? = null

    /** 事件源爬到的那颗窗口根。getWindows() 读不到时拿它顶上。 */
    private var pendingRoot: AccessibilityNodeInfo? = null

    /** 事件到达那一刻抄下来的页面文字。 */
    private var pendingTexts: List<String> = emptyList()

    private var lastSnapAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        IngestPipeline.init(applicationContext)
        Log.i(TAG, "无障碍服务已连接，开始守支付页面")
        // 也写进采集记录。开关看着是开的、服务其实早就被系统收了，
        // 这两种情况在设置里长得一模一样，只有这条记录能分开。
        IngestLog.add("无障碍", "服务已连接", "开始守支付页面")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val pkg = e.packageName?.toString() ?: return
        val isDirect = pkg == PaymentNotifierParser.PKG_WECHAT || pkg == PaymentNotifierParser.PKG_ALIPAY
        val isHost = PaymentAccessibilityParser.hostDisplayName(pkg) != null
        if (!isDirect && !isHost) return

        // 只要这两类事件。购物 App 以前只放行「窗口状态变化」，赌的是收银台是独立窗口 ——
        // 赌错了：得物这类是在同一个 Activity 里弹出支付宝 SDK 的，
        // 窗口没换，就只有内容变化事件，只认状态变化等于一条都收不到。
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            e.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            e.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            return
        }

        val settings = IngestPipeline.settings ?: return
        if (!settings.accessibilityChannel) return

        // 事件刚到这一刻是唯一能百分之百拿到这一页的时机。
        // 等 0.15s 之后再去 getWindows()，页面可能已经切走、或被系统权限弹窗盖住，
        // 拿到就是一个空列表 —— 现场看到的一整屏「没有可读的窗口」就是这么来的。
        // 内容变化事件最快 120ms 一次，每次都走一遍整棵树太重，这里限一下频率
        // 购物 App 的页面变化极其密集（商品页、列表页一直在刷），限流放宽一点；
        // 微信支付宝自己的收银台是独立窗口，事件没那么多，可以紧一些。
        val minGap = if (isHost) HOST_SNAP_MIN_MS else SNAP_MIN_MS
        val tick = System.currentTimeMillis()
        var snapped = false
        if (tick - lastSnapAt >= minGap) {
            lastSnapAt = tick

            // 事件自带的文字先收下来。操作系统的无障碍事件本身就挂着这一页的文本，
            // 不用顺着节点爬 —— 购物 App 的事件源节点经常是空的，爬树这条路走不通，
            // 但事件里的文字一直都在。
            val t = ArrayList<String>(160)
            e.text?.mapNotNullTo(t) { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() && s.length <= 60 } }
            e.contentDescription?.toString()?.trim()
                ?.takeIf { it.isNotEmpty() && it.length <= 60 }?.let { t.add(it) }

            val snap = rootFromWindowId(pkg, e)
                ?: rootFromEvent(pkg, e)
                ?: rootInActiveWindow?.takeIf { it.packageName?.toString() == pkg }
            if (snap != null) {
                pendingRoot = snap
                collectTexts(snap, t, 0)
            }
            if (t.isNotEmpty()) {
                pendingTexts = t.distinct()
                snapped = true
            }
        }

        // 拿到东西就当场判一次，不等采样。支付结果页只活一两秒，
        // 用户付完立刻退出的话，0.15 秒后的第一次采样已经读不到它了。
        if (snapped && parsePage(pkg)) return

        scheduleProbe(pkg)
    }

    /**
     * 连打三次采样：0.15s / 0.45s / 1.0s，哪次抓到就停。
     *
     * 支付结果页只停留一两秒（用户点「完成」就关了），固定等 350ms 一次采样太赌运气：
     * 早了页面还没渲染完，晚了页面已经没了。三次窗口覆盖「刚出现」到「快要关掉」。
     */
    private fun scheduleProbe(pkg: String) {
        // 同一个 App 正在采样中，就让它自己走完，别被密集的内容变化回调无限重排
        if (probePkg == pkg && probeStep in 0 until probeDelays.size) return

        probeRun?.let { handler.removeCallbacks(it) }
        probePkg = pkg
        probeStep = 0

        val run = object : Runnable {
            override fun run() {
                val p = probePkg ?: return
                if (parsePage(p)) {
                    probeStep = probeDelays.size
                    probeRun = null
                    return
                }
                probeStep++
                if (probeStep < probeDelays.size) {
                    handler.postDelayed(this, probeDelays[probeStep] - probeDelays[probeStep - 1])
                } else {
                    probeRun = null
                    pendingRoot = null
                    pendingTexts = emptyList()
                }
            }
        }
        probeRun = run
        handler.postDelayed(run, probeDelays[0])
    }

    private fun parsePage(pkg: String): Boolean {
        if (!IngestPipeline.isReady) IngestPipeline.init(applicationContext)

        // 等了 350ms，用户可能已经切到别的 App。窗口和事件包名对不上，
        // 就拿错页面去解析了（微信记的成了支付宝的页面），所以先对一次。
        var roots = rootsFor(pkg)
        if (roots.isEmpty()) {
            // 活动窗口读不到、getWindows() 也空（权限弹窗盖在上面时就是这样），
            // 退回到事件到达那一刻从事件源爬出来的那颗根
            pendingRoot?.let { r ->
                // 快照窗口可能本身就是支付宝/微信那一层（购物 App 内嵌付款），一样算数
                val accept = if (PaymentAccessibilityParser.hostDisplayName(pkg) != null)
                    setOf(pkg, PaymentNotifierParser.PKG_ALIPAY, PaymentNotifierParser.PKG_WECHAT)
                else setOf(pkg)
                if (r.packageName?.toString() in accept) roots = listOf(r)
            }
        }
        if (roots.isEmpty() && pendingTexts.isEmpty()) {
            val windowCount = runCatching { windows?.size }.getOrNull() ?: -1
            // 光说「几个窗口」看不出问题。把窗口各自的包名列出来：
            // 全是叮账自己，说明用户已经切走了；有一个支付宝的，说明差点就能读到。
            val pkgs: List<String> = runCatching {
                val ws = windows ?: return@runCatching emptyList<String>()
                ws.mapNotNull { w -> w.root?.packageName?.toString() }.distinct()
            }.getOrElse { emptyList() }
            Log.d(TAG, "没有这个 App 的可读窗口")
            IngestLog.add(
                "无障碍", "没有可读的窗口",
                "活动窗口是 ${rootInActiveWindow?.packageName ?: "无"}，事件来自 $pkg，" +
                    "可读窗口 $windowCount 个" +
                    if (pkgs.isEmpty()) "" else "（${pkgs.joinToString("、").take(90)}）"
            )
            return false
        }

        val now = System.currentTimeMillis()
        val need = if (lastParseOk[pkg] == true) 1200L else 0L
        if (now - (lastParseAt[pkg] ?: 0L) < need) return false
        // 拿到可用窗口才算一次解析，别让空窗口白白吃掉冷却时间
        lastParseAt[pkg] = now

        // 能读到窗口说明服务确实活着一一这一笔心跳比任何「开关是开的」都可信
        ChannelHealth.markAlive("a11y")

        val texts = ArrayList<String>(200)
        roots.forEach { collectTexts(it, texts, 0) }

        // 现在这一页不像支付页，但事件刚发生时那一页像 —— 用当时抄的那份。
        // 支付结果页停留一两秒就没了，晚一点读到的已经是别的页面了。
        //
        // 窗口完全读不到的时候还要更干脆：这时手上只有快照这一份东西，
        // 没有「像不像」可挑，拿它去解析就是了。漏掉这一步的后果就是——
        // 文字明明已经抄到手，却在前面按「没有可读的窗口」直接放弃了。
        val onlySnapshot = roots.isEmpty()
        if (pendingTexts.isNotEmpty()) {
            val snapshotLooksLikePay = looksLikePaymentPage(pendingTexts)
            if (onlySnapshot || ((texts.isEmpty() || !looksLikePaymentPage(texts)) && snapshotLooksLikePay)) {
                texts.clear()
                texts.addAll(pendingTexts)
            }
        }

        if (texts.isEmpty()) {
            IngestLog.add("无障碍", "窗口里没有文字节点", "共 ${roots.size} 个窗口 / $pkg")
            return false
        }

        val captured = if (PaymentAccessibilityParser.hostDisplayName(pkg) != null) {
            PaymentAccessibilityParser.parseEmbedded(pkg, texts, now)
        } else {
            PaymentAccessibilityParser.parse(pkg, texts, now)
        }
        lastParseOk[pkg] = captured != null

        if (captured == null) {
            // 收付款（出示付款码让商家扫）这个场景一直没记上过，而且什么都看不到：
            // 这一页既不像支付页、也不是账单页，走到这里就被静默丢掉。
            // 只要页面上有付款码的味道，就把读到的开头留下来 ——
            // 付一笔 0.01 元就能看出，是微信压根没写「成功」，还是写了但规则没认。
            val scanPayHint = texts.any { t ->
                t.contains("付款码") || t.contains("向商家付款") ||
                    t.contains("收付款") || t.contains("数字码")
            }
            if (scanPayHint) {
                IngestLog.add(
                    "无障碍", "收付款页没认出来",
                    texts.take(6).joinToString(" / ").take(120)
                )
                return false
            }
            // 明显是支付/账单页却没认出来，记一行现场，方便照着补规则
            if (looksLikePaymentPage(texts)) {
                Log.d(TAG, "未命中规则 $pkg: " + texts.take(8).joinToString(" / ").take(160))
                IngestLog.add(
                    "无障碍", "认不出这页",
                    texts.take(6).joinToString(" / ")
                )
            } else if (PaymentAccessibilityParser.hostDisplayName(pkg) != null) {
                // 购物 App 里绝大多数页面都不是支付页，只留一行最简的，免得把记录刷爆
                if (looksLikePaymentPage(texts)) {
                    IngestLog.add("无障碍", "购物 App 里有付款字样但没认出来", texts.take(5).joinToString(" / "))
                }
            } else {
                // 「不是支付页」不记了：用户不关心刚才瞟过一个商品页，
                // 这种记录一多就把真记上的账全挤掉。
            }
            return false
        }

        // 同一个页面可能被反复解析（窗口内容变化会连续触发），按页面指纹挡一下。
        // 有交易单号就用单号；没有就用「秒」，不用「分钟」——
        // 分钟级指纹会把同一分钟内同店同金额的两笔真实消费当成同一页，第二笔直接被挡掉。
        val pageKey = captured.externalId?.let { "no|$it" }
            ?: "${captured.wallet.db}|${captured.amountCents}|${captured.payee}|${now / 1000}"
        if (recentPages.contains(pageKey)) {
            Log.d(TAG, "同一页刚处理过，跳过")
            return true
        }
        recentPages.addLast(pageKey)
        if (recentPages.size > 30) recentPages.removeFirst()

        // 入库丢后台：主线程只负责在这一刻把页面文字交出去，不碰数据库
        IngestPipeline.submitAsync(captured) { outcome ->
            Log.i(TAG, "无障碍记账: accepted=${outcome.accepted} ${outcome.reason}")
            IngestLog.add(
                "无障碍",
                if (outcome.accepted) "记上了" else "没记上",
                "${captured.payee} ${captured.amountCents / 100.0} 元 · ${outcome.reason}"
            )
        }
        // 记完什么都不弹。用户要的是「无感」：付完钱该干嘛干嘛，账已经躺在里面了。
        // 只有预算超线这种用户主动要求的事才发通知（见 core/BudgetAlert）。
        return true
    }

    /**
     * 取这个 App 当前所有可读窗口的根节点。
     *
     * 支付结果页常常是盖在聊天页上面的独立窗口（浮层）。只读 rootInActiveWindow
     * 拿到的是下面那一层，上面那层写着「支付成功」「¥2.00」的窗口永远看不到 ——
     * 这是之前「扫码付了、账单里没有」的原因之一。
     */
    /**
     * 从事件源节点往上爬到窗口根。
     *
     * 这条路不依赖 rootInActiveWindow，也不依赖 getWindows() ——
     * 支付宝付款时会盖一层系统权限弹窗，活动窗口变成 com.android.permissioncontroller，
     * 但事件的源节点还在支付宝那棵树里，顺着 parent 一路爬就能摸到整页。
     */
    private fun rootFromEvent(pkg: String, event: AccessibilityEvent?): AccessibilityNodeInfo? {
        return runCatching {
            val src = event?.source ?: return@runCatching null
            if (src.packageName?.toString() != pkg) return@runCatching null
            var cur = src
            var guard = 0
            while (guard < 80) {
                val parent = cur.parent ?: break
                cur = parent
                guard++
            }
            if (cur.packageName?.toString() == pkg) cur else null
        }.getOrNull()
    }

    /**
     * 按事件自带的窗口 ID 去取那棵树。
     *
     * 比按包名翻窗口可靠：事件说「窗口 37 变了」，那就直接取 37 号窗，
     * 哪怕它已经不是活动窗口（支付宝付完会立刻把前台交回给商家 App）。
     * 支付结果页往往只活几百毫秒，等 150 毫秒后再按包名找，它早就不在窗口列表里了。
     */
    private fun rootFromWindowId(pkg: String, event: AccessibilityEvent?): AccessibilityNodeInfo? {
        val wid = event?.windowId ?: return null
        if (wid < 0) return null
        return runCatching {
            windows?.firstOrNull { it.id == wid }?.root
                ?.takeIf { r -> r.packageName?.toString() == pkg }
        }.getOrNull()
    }

    private fun rootsFor(pkg: String): List<AccessibilityNodeInfo> {
        // 购物 App 在自家页面里唤起支付宝/微信 SDK 付款时，收银台那一层的窗口
        // 包名常常是支付宝或微信自己的。只认宿主包名，就只能读到下面那层商品页，
        // 收银台和结果页永远是「没有可读的窗口」。
        val accept = if (PaymentAccessibilityParser.hostDisplayName(pkg) != null) {
            setOf(pkg, PaymentNotifierParser.PKG_ALIPAY, PaymentNotifierParser.PKG_WECHAT)
        } else {
            setOf(pkg)
        }

        val out = ArrayList<AccessibilityNodeInfo>(4)
        rootInActiveWindow?.let { r ->
            if (r.packageName?.toString() in accept && out.none { it === r }) out.add(r)
        }
        runCatching {
            windows?.forEach { w ->
                val r = w?.root ?: return@forEach
                if (r.packageName?.toString() in accept && out.none { it === r }) out.add(r)
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
        probeRun?.let { handler.removeCallbacks(it) }
        probeRun = null
        probePkg = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "A11y"

        /** 事件快照的最小间隔，内容变化事件最快 120ms 一次，没必要每次都走全树 */
        private const val SNAP_MIN_MS = 400L

        /**
         * 购物 App 的快照间隔放宽到 800ms。
         *
         * 商品页、列表页、评论区一直在刷内容变化事件，和微信支付宝的收银台完全不是一个量级。
         * 但也不能关掉：内嵌收银台就是在这些内容变化里出现的。
         */
        private const val HOST_SNAP_MIN_MS = 800L

        private val moneyPatterns = listOf(
            Regex("[¥￥]\\s*[0-9]"),
            Regex("[0-9]\\s*元"),
            Regex("[−\\-－+＋]\\s*[0-9]+\\.[0-9]{2}")
        )
    }
}
