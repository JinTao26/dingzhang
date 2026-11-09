package com.dingzhang.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dingzhang.app.DingZhangApp
import com.dingzhang.app.core.model.Categories
import com.dingzhang.app.core.model.Money
import com.dingzhang.app.core.model.TxnKind
import com.dingzhang.app.core.model.Wallet
import com.dingzhang.app.ingest.IngestPipeline
import com.dingzhang.app.ui.components.CategoryBadge
import com.dingzhang.app.ui.components.ui
import com.dingzhang.app.ui.theme.Dz

private val addCategoryIds = listOf(
    "food", "drink", "transport", "shopping", "home", "fun",
    "health", "study", "social", "pet", "digital", "other", "income"
)

@Composable
fun AddScreen(onClose: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    var amountText by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf("food") }
    var note by remember { mutableStateOf("") }

    val isIncome = categoryId == "income"
    val accent = if (isIncome) Dz.Income else Dz.Sakura

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Dz.Cream)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        // 顶栏
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Dz.Card)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "取消", tint = Dz.InkSoft, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.weight(1f))
            Text("记一笔", style = MaterialTheme.typography.titleLarge, color = Dz.Ink)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(34.dp))
        }

        Spacer(Modifier.height(24.dp))

        // 金额
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(
                    listOf(
                        accent.copy(alpha = 0.16f),
                        Dz.Lilac.copy(alpha = 0.12f)
                    )
                ))
                .padding(horizontal = 20.dp, vertical = 22.dp)
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "¥",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    modifier = Modifier.padding(bottom = 6.dp, end = 4.dp)
                )
                BasicTextField(
                    value = amountText,
                    onValueChange = { s ->
                        if (s.length <= 9 && s.matches(Regex("^\\d{0,7}(\\.\\d{0,2})?$"))) amountText = s
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isIncome) Dz.Income else Dz.Ink
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box {
                            if (amountText.isEmpty()) {
                                Text(
                                    "0.00",
                                    fontSize = 38.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Dz.InkFaint.copy(alpha = 0.6f)
                                )
                            }
                            inner()
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // 分类网格
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("算什么", style = MaterialTheme.typography.titleMedium, color = Dz.Ink)
            Spacer(Modifier.weight(1f))
            Text(
                if (isIncome) "这笔记成收入" else "选一个最像的",
                style = MaterialTheme.typography.labelSmall,
                color = Dz.InkFaint
            )
        }
        Spacer(Modifier.height(10.dp))

        for (row in 0 until 4) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (col in 0 until 4) {
                    val idx = row * 4 + col
                    if (idx < addCategoryIds.size) {
                        val id = addCategoryIds[idx]
                        val on = categoryId == id
                        val cat = Categories.of(id)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (on) cat.ui.copy(alpha = 0.20f) else Dz.Card)
                                .clickable { categoryId = id }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CategoryBadge(id, 30.dp)
                            Text(
                                cat.name,
                                fontSize = 11.sp,
                                color = if (on) Dz.Ink else Dz.InkSoft,
                                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(10.dp))

        // 备注 / 商户
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Dz.Card)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text("商户或备注", style = MaterialTheme.typography.labelSmall, color = Dz.InkFaint)
            BasicTextField(
                value = note,
                onValueChange = { if (it.length <= 30) note = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = Dz.Ink),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box {
                        if (note.isEmpty()) {
                            Text(
                                "比如「楼下便利店」，下次它就认得",
                                fontSize = 15.sp,
                                color = Dz.InkFaint.copy(alpha = 0.7f)
                            )
                        }
                        inner()
                    }
                }
            )
        }

        Spacer(Modifier.height(22.dp))

        // 保存
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(accent)
                .clickable {
                    val cents = Money.parseToCents(amountText)
                    if (cents == null || cents <= 0) {
                        Toast.makeText(ctx, "先填一个金额", Toast.LENGTH_SHORT).show()
                        return@clickable
                    }
                    val kind = if (isIncome) TxnKind.INCOME else TxnKind.EXPENSE
                    val payeeText = note.trim().ifBlank { Categories.of(categoryId).name }
                    val ok = IngestPipeline.manual(
                        amountCents = cents,
                        kind = kind,
                        categoryId = categoryId,
                        payee = payeeText,
                        note = note.trim(),
                        wallet = Wallet.UNKNOWN,
                        learn = true
                    )
                    if (ok != null) {
                        DingZhangApp.bumpDataVersion()
                        Toast.makeText(ctx, "记好了", Toast.LENGTH_SHORT).show()
                        onSaved()
                    } else {
                        Toast.makeText(ctx, "没记上，稍后再试", Toast.LENGTH_SHORT).show()
                    }
                }
                .padding(vertical = 15.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (isIncome) "记成收入" else "记成支出",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "手记的这一笔会顺手把「$note」这个说法学成 ${Categories.of(categoryId).name}",
            style = MaterialTheme.typography.bodySmall,
            color = Dz.InkFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
