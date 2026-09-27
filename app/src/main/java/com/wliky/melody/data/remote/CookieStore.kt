package com.wliky.melody.data.remote

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

private val Context.cookieDataStore by preferencesDataStore(name = "netease_cookies")

/**
 * Cookie 存储：内存态 + DataStore 持久化。
 * 登录成功后 MUSIC_U 落盘，杀进程后登录态仍在。
 */
class CookieStore(private val context: Context) : CookieJar {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val key = stringPreferencesKey("cookies_merged")

    /** 内存镜像：name -> value（合并写入，读取时全量带上） */
    private val inMemory = linkedMapOf<String, String>()

    init {
        seedBaseCookies()
    }

    /** weapi 必备的基础 cookie（与请求头方案不同：种进 jar 才不会覆盖登录 cookie）。 */
    private fun seedBaseCookies() {
        inMemory["os"] = "pc"
        inMemory["appver"] = "8.9.70"
        inMemory["osver"] = "Microsoft-Windows-10"
        // NMTID：网页端由 JS 生成的随机指纹 cookie，红心等写接口的环境校验（524）依赖它
        inMemory["NMTID"] = java.util.UUID.randomUUID().toString().replace("-", "")
    }

    /** 登录态关键 cookie 是否存在 */
    fun hasLoginCookie(): Boolean = inMemory.containsKey("MUSIC_U")

    /** 游客态 cookie（MUSIC_A 匿名 token）是否存在：未登录时搜索等接口依赖它 */
    fun hasAnonymousCookie(): Boolean = inMemory.containsKey("MUSIC_A")

    /** 完整 cookie 头字符串 */
    fun cookieHeader(): String? =
        inMemory.takeIf { it.isNotEmpty() }?.entries?.joinToString("; ") { "${it.key}=${it.value}" }

    fun loginCookieFlow(): Flow<Boolean> = context.cookieDataStore.data
        .map { it[key]?.contains("MUSIC_U") == true }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val header = cookieHeader() ?: return emptyList()
        return header.split("; ")
            .mapNotNull { raw ->
                Cookie.parse(url, raw)
            }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        synchronized(inMemory) {
            cookies.forEach { inMemory[it.name] = it.value }
        }
        val merged = cookieHeader() ?: return
        scope.launch {
            context.cookieDataStore.edit { it[key] = merged }
        }
    }

    /** 启动时从磁盘恢复（Application 创建后调用一次）。 */
    fun restore(saved: String?) {
        if (saved.isNullOrBlank()) return
        synchronized(inMemory) {
            saved.split("; ").forEach { pair ->
                val idx = pair.indexOf('=')
                if (idx > 0) inMemory[pair.substring(0, idx)] = pair.substring(idx + 1)
            }
        }
    }

    /** 从 DataStore 读取并恢复（Application.onCreate 的协程中调用）。 */
    suspend fun restoreFromDisk() {
        val saved = context.cookieDataStore.data.first()[key]
        restore(saved)
    }

    /** 退出登录时清空。 */
    fun clear() {
        synchronized(inMemory) {
            inMemory.clear()
            // 重建基础 cookie：退出登录后游客注册 / 免登录接口仍需 os/NMTID 等
            seedBaseCookies()
        }
        scope.launch {
            context.cookieDataStore.edit { it.remove(key) }
        }
    }
}
