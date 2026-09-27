package com.wliky.melody

import android.app.Application
import android.util.Log
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import com.wliky.melody.data.error.AppResult
import com.wliky.melody.data.remote.CookieStore
import com.wliky.melody.data.remote.NeteaseClient
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okio.Path.Companion.toPath
import javax.inject.Inject

@HiltAndroidApp
class MelodyApp : Application() {

    @Inject
    lateinit var cookieStore: CookieStore

    @Inject
    lateinit var client: NeteaseClient

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        configureImageLoader()
        // 启动即恢复持久化 cookie（登录态 MUSIC_U），与首页首屏请求存在轻微竞态可接受
        appScope.launch {
            cookieStore.restoreFromDisk()
            registerAnonymousTokenIfNeeded()
        }
    }

    /**
     * 全局图片加载配置：内存缓存 20% + 磁盘缓存 256MB。
     * 网易云封面 URL 稳定（配合 ?param 缩略参数），磁盘缓存命中率极高，
     * 二次进入列表秒出图、零流量。
     */
    private fun configureImageLoader() {
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .memoryCache {
                    MemoryCache.Builder()
                        .maxSizePercent(context, 0.20)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(context.cacheDir.resolve("image_cache").absolutePath.toPath())
                        .maxSizeBytes(256L * 1024 * 1024)
                        .build()
                }
                .build()
        }
    }

    /**
     * 游客 cookie：搜索等接口在无任何登录态（无 MUSIC_U）时会失败（301/风控）。
     * 启动时若既无登录态也无游客态（MUSIC_A），注册一次游客拿匿名 token，
     * 由 CookieJar 自动捕获 Set-Cookie 并持久化；已登录或已注册则跳过。
     */
    private suspend fun registerAnonymousTokenIfNeeded() {
        if (cookieStore.hasLoginCookie() || cookieStore.hasAnonymousCookie()) return
        val result = client.post("/weapi/register/anonimous", payloadJson = "{}")
        if (result is AppResult.Failure) {
            Log.w("MelodyApp", "anonymous token register failed: ${result.error}")
        }
    }
}
