package com.dingzhang.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dingzhang.app.core.model.Categories
import com.dingzhang.app.ui.theme.Dz

/**
 * 只负责「换个分类」的弹窗。首页的最近明细、明细页都能用同一个，
 * 免得两处的分类网格长得不一样。
 *
 * 选完就回调，调用方负责落库和刷新（见 IngestPipeline.correctCategory，
 * 它会顺手把这个商户的历史记录和退款一起改掉）。
 */
@Composable
fun CategoryPickerDialog(
    payee: String,
    currentId: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 620.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Dz.Card)
                .verticalScroll(rememberScrollState())
                .padding(18.dp)
        ) {
            Text("改分类", style = MaterialTheme.typography.titleLarge, color = Dz.Ink)
            Text(
                payee.ifBlank { "这一笔" },
                fontSize = 13.sp,
                color = Dz.SakuraDeep,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                "改一次，这个商户以前的记录会一起跟着改，退款也算在内",
                style = MaterialTheme.typography.bodySmall,
                color = Dz.InkFaint,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )

            Text("支出", style = MaterialTheme.typography.labelMedium, color = Dz.InkFaint)
            Spacer(Modifier.height(6.dp))
            PickerGrid(Categories.expenseAll.map { it.id }, currentId, onPick)

            Spacer(Modifier.height(10.dp))
            Text("收入", style = MaterialTheme.typography.labelMedium, color = Dz.InkFaint)
            Spacer(Modifier.height(6.dp))
            PickerGrid(Categories.incomeAll.map { it.id }, currentId, onPick)

            Spacer(Modifier.height(14.dp))
            Text(
                "取消",
                style = MaterialTheme.typography.labelLarge,
                color = Dz.InkSoft,
                modifier = Modifier
                    .align(Alignment.End)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Dz.Cream)
                    .clickable { onDismiss() }
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun PickerGrid(ids: List<String>, currentId: String, onPick: (String) -> Unit) {
    for (row in ids.chunked(4)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { id ->
                PickerCell(id, id == currentId, Modifier.weight(1f)) { onPick(id) }
            }
            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PickerCell(
    id: String,
    active: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val cat = Categories.of(id)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) cat.ui.copy(alpha = 0.18f) else Dz.Cream)
            .clickable { onClick() }
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CategoryBadge(id, 30.dp)
        Text(
            cat.name,
            fontSize = 11.sp,
            color = if (active) Dz.Ink else Dz.InkSoft,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
