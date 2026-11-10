package com.dingzhang.app.share

import android.content.Context
import android.content.Intent

/**
 * 把叮账分享给别人：应用内点「分享给朋友」，或者长按桌面图标都走这里。
 * 只带一段文字和一串下载地址，不涉及任何账目数据。
 */
object ShareKit {

    // 固定名的最新版直链：点一下就下载，不用让人去 Release 页里找文件
    private const val APK =
        "https://github.com/JinTao26/dingzhang/releases/latest/download/dingzhang.apk"
    private const val APK_MIRROR =
        "https://ghproxy.net/https://github.com/JinTao26/dingzhang/releases/latest/download/dingzhang.apk"

    fun text(): String = buildString {
        append("叮账 · 自动记账（开源、免费、无广告）\n")
        append("微信、支付宝、银行的通知自动记账，红包转账这类记不到的导一次账单就补齐。\n")
        append("点开直接装（安卓）：\n")
        append(APK)
        append("\nGitHub 打不开就用镜像：\n")
        append(APK_MIRROR)
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
