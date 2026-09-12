package com.wliky.melody.data.repository

import com.wliky.melody.core.common.Clock
import com.wliky.melody.core.database.PlaybackEventDao
import com.wliky.melody.core.database.PlaybackEventEntity
import com.wliky.melody.core.model.PlaybackEvent
import com.wliky.melody.core.model.SyncState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** 同步结果，用于在 UI 上给出「本次同步成功几条」的反馈。 */
data class SyncReport(
    val synced: Int = 0,
    val failed: Int = 0,
    val skipped: Int = 0,
    val providerName: String? = null,
) {
    val total: Int get() = synced + failed + skipped
    val nothingToDo: Boolean get() = total == 0
}

enum class SyncOutcome { SUCCESS, FAILED, UNSUPPORTED }

/**
 * 同步 Provider（文档 §9 / §17）。
 *
 * 这是「可插拔」的关键：客户端不硬编码任何不可控的私有接口，
 * 只要求实现方确认接口合法、稳定、允许第三方客户端调用。
 * 要接入新的同步目标，只需实现本接口并用 @IntoSet 注册。
 */
interface SyncProvider {
    val name: String

    /** 当前是否满足上报条件（用户开关 + 接口模式 + 能力探测）。 */
    fun isAvailable(): Boolean

    suspend fun push(events: List<PlaybackEvent>): SyncOutcome
}

/**
 * 播放事件队列（文档 §9）。
 *
 * 事件先落 Room，再在「网络可用 + 登录有效 + 用户开启」时批量提交；
 * 失败按 retryCount 退避重试，超过上限就停在 FAILED，不会无限重试打接口。
 */
@Singleton
class SyncRepository @Inject constructor(
    private val dao: PlaybackEventDao,
    private val providers: Set<@JvmSuppressWildcards SyncProvider>,
    private val clock: Clock,
) {

    val pendingCount: Flow<Int> = dao.observeCount(SyncState.PENDING.name)
    val failedCount: Flow<Int> = dao.observeCount(SyncState.FAILED.name)
    val skippedCount: Flow<Int> = dao.observeCount(SyncState.SKIPPED.name)
    val totalCount: Flow<Int> = dao.observeTotal()

    suspend fun enqueue(event: PlaybackEvent) {
        runCatching { dao.insert(event.toEntity()) }
    }

    /** 立即尝试同步一次。没有可用 Provider 时什么都不做（不清队列）。 */
    suspend fun syncNow(): SyncReport {
        val provider = providers.firstOrNull { runCatching { it.isAvailable() }.getOrDefault(false) }
            ?: return SyncReport()
        val rows = runCatching { dao.pending(MAX_RETRY, BATCH_SIZE) }.getOrDefault(emptyList())
        if (rows.isEmpty()) return SyncReport(providerName = provider.name)

        val ids = rows.map { it.eventId }
        val outcome = runCatching { provider.push(rows.map { it.toDomain() }) }
            .getOrDefault(SyncOutcome.FAILED)

        return when (outcome) {
            SyncOutcome.SUCCESS -> {
                dao.updateState(ids, SyncState.SYNCED.name, 0)
                SyncReport(synced = ids.size, providerName = provider.name)
            }

            SyncOutcome.FAILED -> {
                dao.updateState(ids, SyncState.FAILED.name, 1)
                SyncReport(failed = ids.size, providerName = provider.name)
            }

            SyncOutcome.UNSUPPORTED -> {
                dao.updateState(ids, SyncState.SKIPPED.name, 0)
                SyncReport(skipped = ids.size, providerName = provider.name)
            }
        }
    }

    suspend fun clear() {
        runCatching { dao.clear() }
    }

    /** 只清理已经同步成功的旧事件，未同步的一条都不动。 */
    suspend fun pruneSynced() {
        runCatching {
            dao.pruneSyncedBefore(clock.now() - PRUNE_OLDER_THAN_MS)
        }
    }

    private fun PlaybackEvent.toEntity(): PlaybackEventEntity = PlaybackEventEntity(
        eventId = eventId,
        songId = songId,
        songName = songName,
        artistName = artistName,
        startAt = startAt,
        durationSeconds = durationSeconds,
        positionMs = positionMs,
        completed = completed,
        syncState = syncState.name,
        retryCount = retryCount,
    )

    private fun PlaybackEventEntity.toDomain(): PlaybackEvent = PlaybackEvent(
        eventId = eventId,
        songId = songId,
        songName = songName,
        artistName = artistName,
        startAt = startAt,
        durationSeconds = durationSeconds,
        positionMs = positionMs,
        completed = completed,
        syncState = runCatching { SyncState.valueOf(syncState) }.getOrDefault(SyncState.PENDING),
        retryCount = retryCount,
    )

    private companion object {
        const val MAX_RETRY = 5
        const val BATCH_SIZE = 50
        const val PRUNE_OLDER_THAN_MS = 7 * 24 * 60 * 60 * 1000L
    }
}
