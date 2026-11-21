package com.dingzhang.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

/**
 * 照片型背景层：一张画好的风景铺满，上面压一层从透明到黑的竖向渐变。
 *
 * 为什么不做成「抠出来贴在纯色底上」：贴上去的东西永远像张纸片，
 * 边缘一圈生硬的白，光和底也对不上。整幅画出来的一条海岸、一层山，
 * 本身就带着自己的光和雾，铺满怎么裁都成立。
 *
 * 蒙版是必需的：底部那块要留给底部栏和「记一笔」，不压暗的话
 * 按钮压在亮处会糊成一片；顶部压得轻，风景还能透出来。
 */
@Composable
fun PhotoBackdrop(resId: Int, light: Boolean = false, modifier: Modifier = Modifier) {
    Box(modifier) {
        Image(
            painter = painterResource(resId),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            Modifier.fillMaxSize().background(
                // 暗色主题压黑，浅色主题压白 —— 浅底上的深色字，只能靠白雾把图推远
                if (light) {
                    Brush.verticalGradient(
                        0.00f to Color(0x3CFFFFFF),
                        0.50f to Color(0x60FFFFFF),
                        1.00f to Color(0x8CFFFFFF)
                    )
                } else {
                    // 蒙版比之前松：原来底下一路压到 0xA6，图本身又是压暗过的，
                    // 两层叠起来就是一团黑，得把手机亮度拉满才看得见。现在图留亮，
                    // 蒙版只负责把白字托住
                    Brush.verticalGradient(
                        0.00f to Color(0x14000000),
                        0.50f to Color(0x2E000000),
                        1.00f to Color(0x5A000000)
                    )
                }
            )
        )
    }
}
