package com.wliky.melody.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PlaybackHistoryEntity::class,
        PlaybackEventEntity::class,
        SearchHistoryEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class MelodyDatabase : RoomDatabase() {
    abstract fun playbackHistoryDao(): PlaybackHistoryDao
    abstract fun playbackEventDao(): PlaybackEventDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        const val NAME = "melody.db"
    }
}
