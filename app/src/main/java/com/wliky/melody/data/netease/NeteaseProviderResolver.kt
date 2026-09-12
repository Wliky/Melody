package com.wliky.melody.data.netease

import com.wliky.melody.core.datastore.SettingsRepository
import com.wliky.melody.core.model.ApiMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 数据源解析器（文档 §17「插件化 Provider」）。
 *
 * 三个 Provider 都是单例，这里只负责按当前设置挑选其中一个，并带上缓存，
 * 让切换数据源后不需要重启 App。
 */
@Singleton
class NeteaseProviderResolver @Inject constructor(
    private val mock: MockNeteaseDataSource,
    private val direct: DirectNeteaseDataSource,
    private val apiServer: ApiServerNeteaseDataSource,
    private val settingsRepository: SettingsRepository,
) {

    @Volatile
    private var cacheKey: String? = null

    @Volatile
    private var cached: NeteaseDataSource? = null

    suspend fun current(): NeteaseDataSource {
        val settings = settingsRepository.current()
        val key = "${settings.apiMode}|${settings.apiBaseUrl}"
        cached?.takeIf { cacheKey == key }?.let { return it }
        val resolved: NeteaseDataSource = when (settings.apiMode) {
            ApiMode.MOCK -> mock
            ApiMode.DIRECT -> direct
            ApiMode.API_SERVER -> apiServer
        }
        cacheKey = key
        cached = resolved
        return resolved
    }

    /** 当前模式是否需要登录（演示模式不需要）。 */
    suspend fun requiresLogin(): Boolean = current().mode != ApiMode.MOCK
}
