package com.wliky.melody.ui.theme

/**
 * 主题模式。用户在 App 内手动选择，持久化到 DataStore。
 */
enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
}
