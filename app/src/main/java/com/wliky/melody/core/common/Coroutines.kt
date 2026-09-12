package com.wliky.melody.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * 调度器提供者。抽成接口是为了单元测试里换成 TestDispatcher，
 * 避免测试里出现真实的 IO 线程与 sleep。
 */
interface DispatchersProvider {
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val main: CoroutineDispatcher
}

class DefaultDispatchersProvider @javax.inject.Inject constructor() : DispatchersProvider {
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val main: CoroutineDispatcher = Dispatchers.Main
}

/** 全局时钟抽象，方便测试播放事件队列的时间计算。 */
interface Clock {
    fun now(): Long
}

class SystemClock @javax.inject.Inject constructor() : Clock {
    override fun now(): Long = System.currentTimeMillis()
}
