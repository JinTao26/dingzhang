package com.dingzhang.app.share

import android.content.Context
import android.content.Intent

/**
 * 把叮账分享给别人：应用内点「分享给朋友」，或者长按桌面图标都走这里。
 * 只带一段文字和一串下载地址，不涉及任何账目数据。
 */
object ShareKit {

    private const val PAGE =
        "https://github.com/JinTao26/dingzhang/releases/latest"
    private const val MIRROR =
        "https://ghproxy.net/https://github.com/JinTao26/dingzhang/releases/latest"

    fun text(): String = buildString {
        append("推荐一个自用的记账 App「叮账」。\n")
        append("微信、支付宝、银行 App 的支付通知能自动记账，顺手把分类也分好；")
        append("红包、转账这种抓不到的，导一次官方账单就补齐。\n\n")
        append("下载地址：\n")
        append(PAGE)
        append("\n\n国内打不开 GitHub 就用这个：\n")
        append(MIRROR)
    }

    fun intent(): Intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "叮账 · 自动记账")
        putExtra(Intent.EXTRA_TEXT, text())
    }

    /** 弹系统的分享面板，微信、QQ、短信都在里面 */
    fun share(ctx: Context, title: String = "分享叮账") {
        val chooser = Intent.createChooser(intent(), title)
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(chooser)
    }
}
