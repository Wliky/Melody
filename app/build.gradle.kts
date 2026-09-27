plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.legacy.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.wliky.melody"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.wliky.melody"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 测试版先用 debug 签名，保证 release APK 可直接安装
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Compose BOM 必须以 platform 声明，其余 compose 库才能省略版本号
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // 毛玻璃（Haze 2.0：核心 + 模糊实现 + 材料预设）
    implementation(libs.haze)
    implementation(libs.haze.blur)
    implementation(libs.haze.blur.materials)

    // 图片加载（Coil 3）
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // 封面取色（Palette，播放器莫奈配色）
    implementation(libs.androidx.palette)

    // 主题偏好持久化（DataStore）
    implementation(libs.datastore.preferences)

    // 网络层（weapi 自实现）：OkHttp + kotlinx.serialization
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // 二维码生成（ZXing，纯 JVM）
    implementation(libs.zxing.core)
    // Hilt + Compose 集成（hiltViewModel）
    implementation(libs.androidx.hilt.navigation.compose)
    // Media3 播放内核（ExoPlayer + MediaSession）
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.common)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
