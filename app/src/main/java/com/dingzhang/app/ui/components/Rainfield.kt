package com.dingzhang.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.random.Random

/** 一滴雨：位置归一化，len 是长度（占屏高比例），a 是透明度，w 是粗细系数。 */
private data class Drop(val x: Float, val y: Float, val len: Float, val a: Float, val w: Float)

/**
 * 「雨夜」主题的背景层：暖棕黑的夜 + 一层往左下斜的雨丝 + 远处一团路灯的暖光。
 *
 * 跟星空物语刻意拉开区别：那边是一颗颗亮点，这边是一条条细线，而且还是斜的、
 * 往下走的。远看是「在下雨」，不是「天上有星星」。
 *
 * 雨量克制到 88 条、最亮也不过一成 —— 再密就成噪点了，整天刷列表会累。
 * 固定种子，重组多少次都是同一场雨。
 */
@Composable
fun RainBackdrop(modifier: Modifier = Modifier) {
    val drops = remember {
        val rnd = Random(20261120)
        List(88) {
            Drop(
                x = rnd.nextFloat(),
                y = rnd.nextFloat(),
                len = 0.035f + rnd.nextFloat() * 0.085f,
                a = 0.050f + rnd.nextFloat() * 0.078f,
                w = 0.65f + rnd.nextFloat() * 0.75f
            )
        }
    }

    Canvas(modifier) {
        // 夜色：顶上最深，往下透一点暖，像路灯在地上的反光
        drawRect(
            Brush.verticalGradient(
                0.00f to Color(0xFF0E0B09),
                0.55f to Color(0xFF14100D),
                1.00f to Color(0xFF1B130D)
            )
        )

        // 右下一团路灯的暖光，跟琥珀主色是同一支光
        val lamp = Offset(size.width * 0.76f, size.height * 0.94f)
        val lampRadius = size.width * 0.95f
        drawCircle(
            Brush.radialGradient(
                colors = listOf(Color(0x26E9A964), Color(0x00E9A964)),
                center = lamp,
                radius = lampRadius
            ),
            radius = lampRadius,
            center = lamp
        )

        // 雨丝统一往左下斜，斜度跟屏幕高度无关，看着才是「同一场雨」
        val slant = -0.17f
        drops.forEach { d ->
            val sx = d.x * size.width
            val sy = d.y * size.height
            val len = d.len * size.height
            drawLine(
                color = Color(0xFFFFFFFF).copy(alpha = d.a),
                start = Offset(sx, sy),
                end = Offset(sx + slant * len, sy + len),
                strokeWidth = d.w * size.minDimension / 430f
            )
        }
    }
}
