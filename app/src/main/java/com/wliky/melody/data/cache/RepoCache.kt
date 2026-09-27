package com.wliky.melody.data.cache

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 页面数据快照缓存：内存 + 磁盘（Preferences DataStore，落 cacheDir，可被系统/用户清理）。
 * 策略对齐官方网易云：
 * - 进页先读缓存秒显（无骨架屏）；
 * - 缓存在 TTL 内视为新鲜，直接用缓存、不发请求；
 * - 缓存过期先展示旧值再静默拉新（stale-while-revalidate）；
 * - 下拉刷新强制走网络并写回缓存。
 */
@Singleton
class RepoCache @Inject constructor(
    @ApplicationContext context: Context,
) {
    /** 缓存命中：值 + 落盘时间。 */
    data class Hit<T>(val value: T, val savedAtMs: Long) {
        /** 是否仍在新鲜期内（TTL 内进页直接用缓存，不请求网络）。 */
        fun isFresh(ttlMs: Long): Boolean =
            System.currentTimeMillis() - savedAtMs < ttlMs
    }

    private class Entry(val payload: String, val savedAtMs: Long)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val memory = ConcurrentHashMap<String, Entry>()

    /** DataStore 自身的读写调度作用域（与调用方协程解耦）。 */
    private val diskScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val datastore = PreferenceDataStoreFactory.create(scope = diskScope) {
        File(context.cacheDir, "repo_cache.preferences_pb")
    }

    /** 读：内存优先，未命中读磁盘并回填内存；损坏数据自动作废。 */
    suspend fun <T> read(key: String, serializer: KSerializer<T>): Hit<T>? {
        memory[key]?.let { entry ->
            return decode(entry, serializer).also { if (it == null) memory.remove(key) }
        }
        val prefs = datastore.data.first()
        val payload = prefs[stringPreferencesKey(key)] ?: return null
        val entry = Entry(payload, prefs[longPreferencesKey("$key.at")] ?: 0L)
        val hit = decode(entry, serializer)
        if (hit == null) {
            memory.remove(key)
            return null
        }
        memory[key] = entry
        return hit
    }

    /** 写：内存立即生效，磁盘随后落盘（失败不影响内存命中）。 */
    suspend fun <T> write(key: String, value: T, serializer: KSerializer<T>) {
        val entry = Entry(json.encodeToString(serializer, value), System.currentTimeMillis())
        memory[key] = entry
        runCatching {
            datastore.edit {
                it[stringPreferencesKey(key)] = entry.payload
                it[longPreferencesKey("$key.at")] = entry.savedAtMs
            }
        }
    }

    /** 删除单个 key（退出登录 / 登录态失效时清对应页面缓存）。 */
    suspend fun remove(key: String) {
        memory.remove(key)
        runCatching {
            datastore.edit {
                it.remove(stringPreferencesKey(key))
                it.remove(longPreferencesKey("$key.at"))
            }
        }
    }

    /** 清空全部缓存（存储管理页「清理应用缓存」）。 */
    suspend fun clearAll() {
        memory.clear()
        runCatching { datastore.edit { it.clear() } }
    }

    private fun <T> decode(entry: Entry, serializer: KSerializer<T>): Hit<T>? =
        runCatching { Hit(json.decodeFromString(serializer, entry.payload), entry.savedAtMs) }
            .getOrNull()
}
