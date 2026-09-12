package com.wliky.melody.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackHistoryDao {

    @Query("SELECT * FROM playback_history ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history WHERE songId = :songId LIMIT 1")
    suspend fun find(songId: String): PlaybackHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlaybackHistoryEntity)

    @Query("DELETE FROM playback_history")
    suspend fun clear()

    @Query("DELETE FROM playback_history WHERE songId = :songId")
    suspend fun delete(songId: String)
}

@Dao
interface PlaybackEventDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: PlaybackEventEntity): Long

    @Query(
        """
        SELECT * FROM playback_events
        WHERE syncState = 'PENDING'
           OR (syncState = 'FAILED' AND retryCount < :maxRetry)
        ORDER BY startAt ASC
        LIMIT :limit
        """,
    )
    suspend fun pending(maxRetry: Int, limit: Int): List<PlaybackEventEntity>

    @Query("SELECT COUNT(*) FROM playback_events WHERE syncState = :state")
    fun observeCount(state: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM playback_events")
    fun observeTotal(): Flow<Int>

    @Query("UPDATE playback_events SET syncState = :state, retryCount = retryCount + :retryDelta WHERE eventId IN (:eventIds)")
    suspend fun updateState(eventIds: List<String>, state: String, retryDelta: Int)

    @Query("DELETE FROM playback_events WHERE syncState = 'SYNCED' AND startAt < :before")
    suspend fun pruneSyncedBefore(before: Long)

    @Query("DELETE FROM playback_events")
    suspend fun clear()

    @Query("SELECT * FROM playback_events WHERE eventId = :eventId LIMIT 1")
    suspend fun find(eventId: String): PlaybackEventEntity?
}

@Dao
interface SearchHistoryDao {

    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SearchHistoryEntity)

    @Query("DELETE FROM search_history")
    suspend fun clear()
}
