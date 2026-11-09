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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.dingzhang.app.core.model.Dates
import com.dingzhang.app.ui.theme.Dz

/**
 * 年月选择器：一个月一个月地翻太累，直接点开一整年的 12 个月。
 * 年份也能往前翻，所以去年的账、年初的账都能两步跳过去。
 */
@Composable
fun MonthPickerDialog(
    month: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    val curYear = month.substringBefore("-").toIntOrNull() ?: 2026
    val curMon = month.substringAfter("-").toIntOrNull() ?: 1
    var year by remember(month) { mutableStateOf(curYear) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Dz.Card)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                YearArrow("‹") { year -= 1 }
                Text(
                    "$year 年",
                    style = MaterialTheme.typography.titleMedium,
                    color = Dz.Ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                YearArrow("›") { year += 1 }
            }

            Spacer(Modifier.height(12.dp))

            (1..12).chunked(3).forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { m ->
                        val active = year == curYear && m == curMon
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) Dz.Sakura else Dz.Cream)
                                .clickable {
                                    onPick("$year-${m.toString().padStart(2, '0')}")
                                    onDismiss()
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "$m 月",
                                fontSize = 13.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                color = if (active) Dz.Card else Dz.InkSoft
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        onPick(Dates.monthOf(System.currentTimeMillis()))
                        onDismiss()
                    }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "回到这个月",
                    style = MaterialTheme.typography.labelLarge,
                    color = Dz.InkFaint
                )
            }
        }
    }
}

@Composable
private fun YearArrow(symbol: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Dz.Cream)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, fontSize = 18.sp, color = Dz.InkSoft)
    }
}
