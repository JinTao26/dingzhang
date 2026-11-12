package com.dingzhang.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dingzhang.app.ui.theme.Dz
import com.dingzhang.app.update.UpdateChecker

/**
 * 版本更新弹窗。开屏自动提示和「我的」里手动检查共用这一个。
 *
 * - 自动弹出时给「暂不更新」：记住这个版本号，同版本不再打扰；下一版还会提示。
 * - 手动打开时两个按钮都只是"知道了"和"去下载"，不给"暂不更新"——
 *   用户既然主动点了检查，就不该被再问一次要不要跳过。
 */
@Composable
fun UpdateDialog(
    result: UpdateChecker.Result,
    fromAuto: Boolean,
    onDismiss: () -> Unit,
    onSkipVersion: () -> Unit,
    onDownload: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(26.dp))
                .background(Dz.Card)
                .padding(20.dp)
        ) {
            Text(
                if (result.hasUpdate) "有新版本了" else "版本检查",
                style = MaterialTheme.typography.titleLarge,
                color = Dz.Ink
            )
            Text(
                when {
                    result.hasUpdate -> "当前 ${result.currentVersion} → 最新 ${result.latestVersion.orEmpty()}"
                    result.latestVersion != null -> "已是最新版本 ${result.currentVersion}"
                    else -> result.message
                },
                style = MaterialTheme.typography.bodySmall,
                color = Dz.InkFaint,
                modifier = Modifier.padding(top = 4.dp)
            )

            // 更新说明：不管有没有更新都显示，这样「检查更新」本身就是一个看说明的入口
            val notes = plainNotes(result.notes)
            if (notes.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "更新说明",
                    style = MaterialTheme.typography.labelMedium,
                    color = Dz.InkSoft,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Text(
                    notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = Dz.InkSoft,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Dz.Cream)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                )
            }

            if (result.hasUpdate && result.apkUrl == null) {
                Text(
                    "这个版本没附 APK，去 Release 页看构建产物",
                    style = MaterialTheme.typography.labelSmall,
                    color = Dz.InkFaint,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Dz.Cream)
                        .clickable {
                            if (fromAuto && result.hasUpdate) onSkipVersion() else onDismiss()
                        }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (fromAuto && result.hasUpdate) "暂不更新" else "知道了",
                        style = MaterialTheme.typography.labelLarge,
                        color = Dz.InkSoft
                    )
                }
                if (result.hasUpdate) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Dz.Sakura)
                            .clickable { onDownload() }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("下载更新", style = MaterialTheme.typography.labelLarge, color = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * Release 正文是 Markdown，原样贴出来会满屏 `##` 和 `**`。
 * 这里只做最小的清理：去掉标记、保留文字，不追求排版还原。
 */
internal fun plainNotes(raw: String): String {
    if (raw.isBlank()) return ""
    var s = raw
    s = s.replace(Regex("""!\[[^\]]*]\([^)]*\)"""), "")
    s = s.replace(Regex("""\[([^\]]*)]\([^)]*\)"""), "\$1")
    s = s.replace(Regex("""^#{1,6}\s*""", RegexOption.MULTILINE), "")
    s = s.replace(Regex("""\*\*(.+?)\*\*"""), "\$1")
    s = s.replace(Regex("""`([^`]+)`"""), "\$1")
    s = s.replace(Regex("""^[-*]\s+""", RegexOption.MULTILINE), "· ")
    s = s.replace(Regex("""\n{3,}"""), "\n\n")
    return s.trim()
}
