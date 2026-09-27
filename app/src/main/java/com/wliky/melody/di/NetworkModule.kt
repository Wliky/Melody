package com.wliky.melody.di

import android.content.Context
import com.wliky.melody.data.remote.CookieStore
import com.wliky.melody.data.remote.NeteaseClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideCookieStore(@ApplicationContext context: Context): CookieStore = CookieStore(context)

    @Provides
    @Singleton
    fun provideNeteaseClient(cookieStore: CookieStore): NeteaseClient =
        NeteaseClient(NeteaseClient.defaultOkHttp(cookieStore), cookieStore)
}
