package com.wliky.melody.data.netease

import com.wliky.melody.core.datastore.SettingsRepository
import com.wliky.melody.core.model.ApiMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 数据源解析器（文档 §17「插件化 Provider」）。
 *
 * **数据源已固定为自建 API 服务（api-enhanced）**，不再暴露「直连 / 演示」切换：
 * 用户部署的服务是纯 HTTP，登录最稳、兼容性最好。这里的 `mock` / `direct` 仍作为
 * 编译期依赖保留（避免破坏 Hilt 注入与既有测试），但运行期只会返回 [apiServer]。
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

        // 固定走自建 API 服务；即便历史设置里残留了 MOCK / DIRECT，也一律回落到 API_SERVER。
        val resolved: NeteaseDataSource = when (settings.apiMode) {
            ApiMode.MOCK, ApiMode.DIRECT -> apiServer
            ApiMode.API_SERVER -> apiServer
        }
        cacheKey = key
        cached = resolved
        return resolved
    }

    /** 当前模式是否需要登录（固定数据源下永远需要）。 */
    suspend fun requiresLogin(): Boolean = current().mode != ApiMode.MOCK
}
