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
import com.dingzhang.app.R

/**
 * 「夜灯」主题的背景层：一张画好的夜景 —— 暖黄路灯的旧街，少女站在右侧。
 *
 * 为什么用整幅插画而不是「抠出来贴在底色上」：
 * 贴在纯色底上的人像永远像张纸片，边缘一圈生硬的白，灯也跟底对不上。
 * 画成一整幅，人物的轮廓光、地上的反光、远处的灯斑本来就是一张图里的东西，
 * 怎么铺都不会假。
 *
 * 图上再压一层从透明到黑的竖向渐变：底部那块要留给底部栏和「记一笔」，
 * 不压暗的话按钮压在灯上会糊成一片。
 */
@Composable
fun NightCharBackdrop(modifier: Modifier = Modifier) {
    Box(modifier) {
        Image(
            painter = painterResource(R.drawable.theme_night_char),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.00f to Color(0x10000000),
                    0.50f to Color(0x30000000),
                    1.00f to Color(0x9E000000)
                )
            )
        )
    }
}
