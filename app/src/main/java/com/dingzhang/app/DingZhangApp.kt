package com.dingzhang.app

import android.app.Application
import com.dingzhang.app.ingest.IngestPipeline
import com.dingzhang.app.ui.theme.ThemeController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DingZhangApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // 数据库、设置在这里就位，后台服务随时可以写账
        IngestPipeline.init(this)
        // 主题也要在前台界面起来之前就定好，否则首帧会是默认配色再跳一次
        ThemeController.init(this)
    }

    companion object {
        /**
         * 数据变更计数器。后台服务改了库之后 +1，UI 收集它来刷新。
         * 比 LiveData 轻，也不用为「服务 + 界面」同进程这种场景引一整套 Flow 架构。
         */
        private val _dataVersion = MutableStateFlow(0)
        val dataVersion: StateFlow<Int> = _dataVersion

        fun bumpDataVersion() {
            _dataVersion.value = _dataVersion.value + 1
        }
    }
}
