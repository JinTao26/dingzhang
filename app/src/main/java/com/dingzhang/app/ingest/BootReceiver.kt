package com.dingzhang.app.ingest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 开机后推一把通知监听。
 *
 * 无障碍服务不用管 —— 开关还勾着的话系统会自己把它绑回来。
 * 通知监听的 binder 在重启、清理之后容易丢，丢了就一条通知都收不到，
 * 而且用户看不出来（设置里开关还亮着），所以开机时主动 requestRebind 一次。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.i("Boot", "开机，检查通道")
        try {
            ChannelHealth.ensureNotificationListening(context)
        } catch (t: Throwable) {
            Log.w("Boot", "开机重绑失败：${t.message}")
        }
        // 重启后无障碍最容易掉：Android 13 对侧载 App 有受限设置，
        // 澎湃/EMUI 还可能在开机阶段就把无障碍关掉。授过 adb 权限的话这里直接写回去，
        // 用户开机后什么都不用做。没授过就是一次快速失败，不会有副作用。
        try {
            ChannelHealth.tryRestoreA11y(context)
        } catch (t: Throwable) {
            Log.w("Boot", "无障碍自愈失败：${t.message}")
        }
    }
}
