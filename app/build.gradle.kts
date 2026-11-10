plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.dingzhang.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dingzhang.app"
        minSdk = 26          // Android 8.0，覆盖绝大多数在用机型
        targetSdk = 35
        versionCode = 15
        versionName = "0.5.12"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }

    signingConfigs {
        create("release") {
            // 固定签名，必须一直用这一个。
            // 之前用 SDK 自带的 debug keystore 是个坑：CI 每次都在新机器上跑，AGP 会
            // 自动生成一个全新的 debug keystore，于是每个包的签名都不一样 ——
            // 手机上覆盖安装必然失败，用户只能卸载重装、账本数据全丢。
            // 自用/给朋友用的侧载场景，签名密钥直接放进仓库最省事。
            // 介意的话可以改成从环境变量或 GitHub Secrets 读。
            storeFile = rootProject.file("keystore/dingzhang.jks")
            storePassword = "dingzhang2026"
            keyAlias = "dingzhang"
            keyPassword = "dingzhang2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module", "DebugProbesKt.bin")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
