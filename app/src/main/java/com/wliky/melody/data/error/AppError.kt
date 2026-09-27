package com.wliky.melody.data.error

/**
 * 统一错误模型：全 App 唯一的错误语义来源。
 * UI 层据此映射文案与重试按钮，不再散落 try/catch。
 */
sealed class AppError(
    val message: String,
    val retryable: Boolean = true,
) {

    /** 无网络 / 连接超时 */
    class Network(cause: String? = null) : AppError("网络连接失败，请检查网络", retryable = true) {
        val cause: String? = cause
    }

    /** 接口返回非 200 或业务码失败（含 code 供排查） */
    class Api(val code: Int, val apiMessage: String?) :
        AppError(apiMessage ?: "服务返回错误（$code）", retryable = code !in nonRetryableCodes) {
        companion object {
            private val nonRetryableCodes = setOf(301, 400, 401, 403, 405, 406)
        }
    }

    /** 响应体解析失败 */
    class Parse(cause: String? = null) : AppError("数据解析失败", retryable = false) {
        val cause: String? = cause
    }

    /** 登录态失效（cookie 过期 / 未登录） */
    object Unauthorized : AppError("登录已过期，请重新登录", retryable = false)

    /** 未知异常兜底 */
    class Unknown(cause: String? = null) : AppError("出了点问题，请稍后重试", retryable = true) {
        val cause: String? = cause
    }
}

/**
 * 轻量 Result 型：Success / Failure(AppError)。
 * Repository 与 ViewModel 的统一返回值。
 */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>

    companion object {
        fun <T> success(data: T): AppResult<T> = Success(data)
        fun failure(error: AppError): AppResult<Nothing> = Failure(error)
    }
}

/** 成功时取数据，失败时抛出错误（仅限内部使用）。 */
fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.data
