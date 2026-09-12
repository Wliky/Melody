package com.wliky.melody.core.di

import android.content.Context
import androidx.room.Room
import com.wliky.melody.core.common.AndroidNetworkMonitor
import com.wliky.melody.core.common.Clock
import com.wliky.melody.core.common.DefaultDispatchersProvider
import com.wliky.melody.core.common.DispatchersProvider
import com.wliky.melody.core.common.NetworkMonitor
import com.wliky.melody.core.common.SystemClock
import com.wliky.melody.core.database.MelodyDatabase
import com.wliky.melody.core.database.PlaybackEventDao
import com.wliky.melody.core.database.PlaybackHistoryDao
import com.wliky.melody.core.database.SearchHistoryDao
import com.wliky.melody.core.network.MelodyJson
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    fun provideJson(): Json = MelodyJson

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MelodyDatabase =
        Room.databaseBuilder(context, MelodyDatabase::class.java, MelodyDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun providePlaybackHistoryDao(db: MelodyDatabase): PlaybackHistoryDao = db.playbackHistoryDao()

    @Provides
    fun providePlaybackEventDao(db: MelodyDatabase): PlaybackEventDao = db.playbackEventDao()

    @Provides
    fun provideSearchHistoryDao(db: MelodyDatabase): SearchHistoryDao = db.searchHistoryDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {

    @Binds
    abstract fun bindDispatchersProvider(impl: DefaultDispatchersProvider): DispatchersProvider

    @Binds
    abstract fun bindNetworkMonitor(impl: AndroidNetworkMonitor): NetworkMonitor

    @Binds
    abstract fun bindClock(impl: SystemClock): Clock
}
