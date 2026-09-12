package com.wliky.melody.core.common

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

/**
 * 统一的结果 / 错误模型。
 * UI 层只消费 [AppError] 的语义分类，不需要（也不允许）到处 catch 底层异常。
 */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>

    fun getOrNull(): T? = (this as? Success)?.data
}

inline fun <T> AppResult<T>.onSuccess(block: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) block(data)
    return this
}

inline fun <T> AppResult<T>.onFailure(block: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) block(error)
    return this
}

/** 与 Kotlin 内置 Result 的 fold 语义一致，方便在两个分支里直接产出 UI 状态。 */
inline fun <T, R> AppResult<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (AppError) -> R,
): R = when (this) {
    is AppResult.Success -> onSuccess(data)
    is AppResult.Failure -> onFailure(error)
}

sealed class AppError(open val message: String, open val cause: Throwable? = null) {
    /** 网络不可用 / 超时 / DNS 失败，可重试。 */
    data class Network(override val message: String = "网络连接失败，请检查网络后重试", override val cause: Throwable? = null) : AppError(message, cause)

    /** 登录失效（cookie 过期或被踢下线），需要重新登录。 */
    data class Unauthorized(override val message: String = "登录状态已失效，请重新扫码登录", override val cause: Throwable? = null) : AppError(message, cause)

    /** 资源不可用：无版权 / 需要会员 / 已下架。 */
    data class Unavailable(override val message: String = "该内容当前不可播放", override val cause: Throwable? = null) : AppError(message, cause)

    data class NotFound(override val message: String = "没有找到相关内容", override val cause: Throwable? = null) : AppError(message, cause)

    /** 接口返回结构变化 / JSON 解析失败。 */
    data class Parse(override val message: String = "数据解析失败，接口可能已变更", override val cause: Throwable? = null) : AppError(message, cause)

    data class Server(override val message: String, val code: Int = -1, override val cause: Throwable? = null) : AppError(message, cause)

    data class Unknown(override val message: String = "出现了未知问题", override val cause: Throwable? = null) : AppError(message, cause)

    val recoverable: Boolean
        get() = this is Network || this is Server || this is Unknown
}

/** 把任意异常折叠成 [AppError]，业务层不再向上抛裸异常。 */
fun Throwable.toAppError(): AppError = when (this) {
    is AppError -> this
    is IOException -> AppError.Network(cause = this)
    is SerializationException -> AppError.Parse(cause = this)
    else -> AppError.Unknown(message = message ?: "出现了未知问题", cause = this)
}

/**
 * 执行可能失败的挂起块，把异常折叠为 [AppResult]。
 * 注意：CancellationException 必须原样抛出，否则会破坏协程取消语义。
 */
suspend inline fun <T> appRunCatching(crossinline block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (throwable: Throwable) {
    AppResult.Failure(throwable.toAppError())
}
