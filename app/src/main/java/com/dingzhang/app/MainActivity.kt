package com.dingzhang.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dingzhang.app.ingest.IngestPipeline
import com.dingzhang.app.ui.AppRoot
import com.dingzhang.app.ui.theme.DingZhangTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 也是无障碍服务里 settingsActivity 的目标：用户从这里点进去授权
        IngestPipeline.init(applicationContext)
        setContent {
            DingZhangTheme {
                AppRoot()
            }
        }
    }
}
