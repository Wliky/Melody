package com.wliky.melody

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wliky.melody.home.HomeScreen
import com.wliky.melody.login.LoginScreen
import com.wliky.melody.main.MainScreen
import com.wliky.melody.main.MainViewModel
import com.wliky.melody.ui.theme.MelodyTheme
import dagger.hilt.android.AndroidEntryPoint

/** 顶层页面切换：主框架 ⇄ 登录页（启动直达首页；登录入口在「我的」页） */
private enum class AppScreen { Login, Main }

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 全局边到边：内容延伸到状态栏/导航条/挖孔之下，避让由各页面 insets 处理
        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            var screen by remember { mutableStateOf(AppScreen.Main) }
            MelodyTheme(themeMode = themeMode) {
                when (screen) {
                    AppScreen.Login -> LoginScreen(
                        onEnterHome = { screen = AppScreen.Main },
                    )
                    AppScreen.Main -> MainScreen(
                        onLogout = { screen = AppScreen.Login },
                        themeMode = themeMode,
                        onSelectThemeMode = viewModel::setThemeMode,
                    )
                }
            }
        }
    }
}
