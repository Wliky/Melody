package com.wliky.melody.data.netease

import com.wliky.melody.core.datastore.SettingsRepository
import com.wliky.melody.core.model.ApiMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 数据源解析器（文档 §17「插件化 Provider」）。
 *
 * **v0.4.0 起数据源固定为「官方直连」（weapi 加密）**，不再使用用户自建 api-enhanced 服务。
 * 理由：首页推荐（/homepage/block/page）、听歌足迹（/scrobble + /record/recent/song）、
 * 评论等接口都要求真实登录态（MUSIC_U），官方直连速度最快、数据最全，且不依赖第三方服务。
 * WebView 登录拿到的 Cookie 完全满足直连的登录态要求。
 *
 * `mock` / `apiServer` 仍作为编译期依赖保留（避免破坏 Hilt 注入与既有测试），
 * 但运行期只会返回 [direct]。
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

        // 固定走官方直连；即便历史设置里残留了 MOCK / API_SERVER，也一律回落到 DIRECT。
        val resolved: NeteaseDataSource = when (settings.apiMode) {
            ApiMode.MOCK, ApiMode.DIRECT, ApiMode.API_SERVER -> direct
        }
        cacheKey = key
        cached = resolved
        return resolved
    }

    /** 当前模式是否需要登录（固定官方直连下永远需要）。 */
    suspend fun requiresLogin(): Boolean = current().mode != ApiMode.MOCK
}
