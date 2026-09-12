package com.wliky.melody.data.di

import com.wliky.melody.core.player.SongUrlProvider
import com.wliky.melody.data.repository.NeteaseSongUrlProvider
import com.wliky.melody.data.repository.NeteaseSyncProvider
import com.wliky.melody.data.repository.SyncProvider
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoSet
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * data 层绑定：core 定义的端口 → 具体适配实现。
 *
 * 同步 Provider 用 @IntoSet 注册：以后要接入新的同步目标
 * （比如其它合法音乐服务的 Provider），新增一个 @Binds @IntoSet 即可，
 * SyncRepository 不用改一行代码。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindSongUrlProvider(impl: NeteaseSongUrlProvider): SongUrlProvider

    @Binds
    @IntoSet
    abstract fun bindNeteaseSyncProvider(impl: NeteaseSyncProvider): SyncProvider
}
