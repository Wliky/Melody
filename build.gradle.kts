// 根构建脚本：只声明插件（apply false），实际应用在 :app 模块里
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.legacy.kapt) apply false
    alias(libs.plugins.hilt) apply false
}
