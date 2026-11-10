package com.dingzhang.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.dingzhang.app.share.ShareKit

/**
 * 长按桌面图标点「分享给朋友」时被拉起来的透明壳：
 * 弹一次系统分享面板就退掉，不进入 App 主界面。
 */
class ShareShortcutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            startActivity(Intent.createChooser(ShareKit.intent(), "分享叮账"))
        }
        finish()
    }
}
