package com.dingzhang.app.ingest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.dingzhang.app.DingZhangApp
import com.dingzhang.app.core.model.Source
import com.dingzhang.app.data.Settings

/**
 * 收银行短信。
 *
 * 只接系统广播（RECEIVE_SMS），不读历史短信箱 —— 少一条 READ_SMS 权限，
 * 也不用把过去几年的短信翻一遍。用户自用或小范围分发足够。
 */
class BankSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val app = context.applicationContext
        IngestPipeline.init(app)

        if (!Settings(app).smsChannel) return

        val parts = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (e: Exception) {
            Log.w(TAG, "取短信内容失败", e)
            null
        } ?: return

        val body = StringBuilder()
        for (p in parts) body.append(p?.messageBody ?: "")
        if (body.isBlank()) return

        val captured = BankAlertParser.parse(body.toString(), System.currentTimeMillis(), Source.SMS)
        if (captured == null) {
            Log.d(TAG, "不是可识别的动账短信，跳过")
            return
        }

        // 广播回调也在主线程，而且系统只给十秒，不能拿来跑 SQL
        IngestPipeline.submitAsync(captured) { outcome ->
            Log.d(TAG, "短信入账：${captured.payee} ${captured.amountCents / 100.0} -> ${outcome.reason}")
        }
    }

    private companion object {
        const val TAG = "BankSms"
    }
}
