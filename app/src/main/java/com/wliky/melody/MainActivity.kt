package com.wliky.melody

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wliky.melody.core.datastore.AppSettings
import com.wliky.melody.core.datastore.SettingsRepository
import com.wliky.melody.core.designsystem.RefreshRate
import com.wliky.melody.core.designsystem.theme.MelodyTheme
import com.wliky.melody.navigation.MelodyApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    /** 已生效的刷新率（Hz），仅用于让 [applyHighRefreshRate] 幂等。 */
    private var appliedRefreshRate: Float? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 拒绝也不影响播放 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyHighRefreshRate()
        requestNotificationPermission()

        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(AppSettings())
            MelodyTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MelodyApp()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 从后台回到前台时系统可能重置显示模式，这里补一次
        applyHighRefreshRate()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 折叠屏展开、旋转、分屏都会改变可用显示模式，需要重新挑一次
        appliedRefreshRate = null
        applyHighRefreshRate()
    }

    /** 见 [RefreshRate]：默认 60Hz 的窗口会让滚动与封面动画明显发涩。 */
    private fun applyHighRefreshRate() {
        if (appliedRefreshRate != null) return
        appliedRefreshRate = RefreshRate.applyTo(this)
    }

    /** Android 13+ 需要用户授权才能显示播放通知；拒绝了 App 依然能正常放歌。 */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
