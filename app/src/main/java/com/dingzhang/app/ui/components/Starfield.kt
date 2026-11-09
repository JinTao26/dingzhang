package com.dingzhang.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.random.Random

/** 一颗星：位置是按比例归一化的，半径是个系数，a 是亮度。 */
private data class Star(val x: Float, val y: Float, val r: Float, val a: Float)

/**
 * 「星空物语」主题的背景层：深空渐变 + 星屑。
 *
 * 星星用固定种子生成，重组多少次都是同一片天空，不会每次进首页星位都变。
 * 只画背景，不参与交互，也不接任何状态。
 */
@Composable
fun StarfieldBackdrop(modifier: Modifier = Modifier) {
    val stars = remember {
        val rnd = Random(20261109)
        List(150) {
            Star(
                x = rnd.nextFloat(),
                y = rnd.nextFloat(),
                r = 0.6f + rnd.nextFloat() * 1.8f,
                a = 0.30f + rnd.nextFloat() * 0.62f
            )
        }
    }

    Canvas(modifier) {
        // 深空渐变：顶上最暗，往下透一点紫，像天顶到地平线
        drawRect(
            Brush.verticalGradient(
                0.00f to Color(0xFF080D1F),
                0.45f to Color(0xFF101733),
                1.00f to Color(0xFF191231)
            )
        )

        // 右上角一团紫星云，左下角一圈冷蓝，让纯渐变有点层次
        val nebulaCenter = Offset(size.width * 0.86f, size.height * 0.10f)
        val nebulaRadius = size.width * 0.82f
        drawCircle(
            Brush.radialGradient(
                colors = listOf(Color(0x33B79CFF), Color(0x00B79CFF)),
                center = nebulaCenter,
                radius = nebulaRadius
            ),
            radius = nebulaRadius,
            center = nebulaCenter
        )
        val glowCenter = Offset(size.width * 0.04f, size.height * 0.62f)
        val glowRadius = size.width * 0.68f
        drawCircle(
            Brush.radialGradient(
                colors = listOf(Color(0x2292B8FF), Color(0x0092B8FF)),
                center = glowCenter,
                radius = glowRadius
            ),
            radius = glowRadius,
            center = glowCenter
        )

        stars.forEach { st ->
            val center = Offset(st.x * size.width, st.y * size.height)
            val radius = st.r * size.minDimension / 430f
            if (st.r > 1.7f) {
                // 亮星带一圈很淡的光晕，远看才有「星」的感觉
                drawCircle(
                    color = Color(0x1AFFFFFF),
                    radius = radius * 3.2f,
                    center = center
                )
            }
            drawCircle(
                color = Color(0xFFFFFFFF).copy(alpha = st.a),
                radius = radius,
                center = center
            )
        }
    }
}
