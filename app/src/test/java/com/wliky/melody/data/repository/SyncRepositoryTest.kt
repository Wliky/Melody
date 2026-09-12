package com.wliky.melody.data.repository

import com.wliky.melody.core.common.Clock
import com.wliky.melody.core.database.PlaybackEventDao
import com.wliky.melody.core.database.PlaybackEventEntity
import com.wliky.melody.core.model.PlaybackEvent
import com.wliky.melody.core.model.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 播放事件队列（文档 §9）的核心契约：
 * 幂等、失败重试有上限、不支持时标记 SKIPPED 而不是无限重试。
 */
class SyncRepositoryTest {

    private class FakeClock : Clock {
        override fun now(): Long = 0L
    }

    /** 用内存实现模拟 Room DAO：insert 用 IGNORE 语义，保证幂等。 */
    private class FakeEventDao : PlaybackEventDao {
        val rows = linkedMapOf<String, PlaybackEventEntity>()

        override suspend fun insert(entity: PlaybackEventEntity): Long {
            if (rows.containsKey(entity.eventId)) return -1L
            rows[entity.eventId] = entity
            return 1L
        }

        override suspend fun pending(maxRetry: Int, limit: Int): List<PlaybackEventEntity> =
            rows.values
                .filter {
                    it.syncState == SyncState.PENDING.name ||
                        (it.syncState == SyncState.FAILED.name && it.retryCount < maxRetry)
                }
                .sortedBy { it.startAt }
                .take(limit)

        override fun observeCount(state: String): Flow<Int> = flowOf(rows.values.count { it.syncState == state })

        override fun observeTotal(): Flow<Int> = flowOf(rows.size)

        override suspend fun updateState(eventIds: List<String>, state: String, retryDelta: Int) {
            eventIds.forEach { id ->
                rows[id]?.let { rows[id] = it.copy(syncState = state, retryCount = it.retryCount + retryDelta) }
            }
        }

        override suspend fun pruneSyncedBefore(before: Long) {
            rows.entries.removeIf { it.value.syncState == SyncState.SYNCED.name && it.value.startAt < before }
        }

        override suspend fun clear() = rows.clear()

        override suspend fun find(eventId: String): PlaybackEventEntity? = rows[eventId]
    }

    private class FakeProvider(
        override val name: String = "fake",
        private val available: Boolean = true,
        private val outcome: SyncOutcome = SyncOutcome.SUCCESS,
    ) : SyncProvider {
        var pushedBatches = 0
        var lastPushed: List<PlaybackEvent> = emptyList()

        override fun isAvailable(): Boolean = available

        override suspend fun push(events: List<PlaybackEvent>): SyncOutcome {
            pushedBatches++
            lastPushed = events
            return outcome
        }
    }

    private fun event(id: String, startAt: Long = 0L) = PlaybackEvent(
        eventId = id,
        songId = "song-$id",
        startAt = startAt,
        durationSeconds = 30,
    )

    @Test
    fun `重复入队同一事件不会产生第二条`() = runTest {
        val dao = FakeEventDao()
        val repository = SyncRepository(dao, setOf(FakeProvider()), FakeClock())

        repository.enqueue(event("e1"))
        repository.enqueue(event("e1"))
        repository.enqueue(event("e2"))

        assertEquals(2, dao.rows.size)
    }

    @Test
    fun `没有可用 Provider 时不动队列`() = runTest {
        val dao = FakeEventDao()
        val provider = FakeProvider(available = false)
        val repository = SyncRepository(dao, setOf(provider), FakeClock())
        repository.enqueue(event("e1"))

        val report = repository.syncNow()

        assertTrue(report.nothingToDo)
        assertEquals(0, provider.pushedBatches)
        assertEquals(SyncState.PENDING.name, dao.rows["e1"]?.syncState)
    }

    @Test
    fun `同步成功后标记 SYNCED`() = runTest {
        val dao = FakeEventDao()
        val provider = FakeProvider(outcome = SyncOutcome.SUCCESS)
        val repository = SyncRepository(dao, setOf(provider), FakeClock())
        repository.enqueue(event("e1"))
        repository.enqueue(event("e2"))

        val report = repository.syncNow()

        assertEquals(2, report.synced)
        assertEquals(2, provider.lastPushed.size)
        assertEquals(SyncState.SYNCED.name, dao.rows["e1"]?.syncState)
        assertEquals(SyncState.SYNCED.name, dao.rows["e2"]?.syncState)
    }

    @Test
    fun `同步失败会累加重试次数并可在下次重试`() = runTest {
        val dao = FakeEventDao()
        val provider = FakeProvider(outcome = SyncOutcome.FAILED)
        val repository = SyncRepository(dao, setOf(provider), FakeClock())
        repository.enqueue(event("e1"))

        repository.syncNow()

        assertEquals(SyncState.FAILED.name, dao.rows["e1"]?.syncState)
        assertEquals(1, dao.rows["e1"]?.retryCount)

        // 重试次数没到上限，下一次仍然会被取出来重试
        assertEquals(1, dao.pending(maxRetry = 5, limit = 10).size)
    }

    @Test
    fun `重试次数达到上限后不再重复提交`() = runTest {
        val dao = FakeEventDao()
        val provider = FakeProvider(outcome = SyncOutcome.FAILED)
        val repository = SyncRepository(dao, setOf(provider), FakeClock())
        repository.enqueue(event("e1"))

        repeat(5) { repository.syncNow() }

        assertEquals(5, dao.rows["e1"]?.retryCount)
        assertTrue(dao.pending(maxRetry = 5, limit = 10).isEmpty())
    }

    @Test
    fun `接口不支持上报时标记 SKIPPED`() = runTest {
        val dao = FakeEventDao()
        val provider = FakeProvider(outcome = SyncOutcome.UNSUPPORTED)
        val repository = SyncRepository(dao, setOf(provider), FakeClock())
        repository.enqueue(event("e1"))

        val report = repository.syncNow()

        assertEquals(1, report.skipped)
        assertEquals(SyncState.SKIPPED.name, dao.rows["e1"]?.syncState)
        assertTrue(dao.pending(maxRetry = 5, limit = 10).isEmpty())
    }

    @Test
    fun `按开始时间顺序批量提交`() = runTest {
        val dao = FakeEventDao()
        val provider = FakeProvider()
        val repository = SyncRepository(dao, setOf(provider), FakeClock())
        repository.enqueue(event("late", startAt = 300))
        repository.enqueue(event("early", startAt = 100))
        repository.enqueue(event("mid", startAt = 200))

        repository.syncNow()

        assertEquals(listOf("early", "mid", "late"), provider.lastPushed.map { it.eventId })
    }
}
