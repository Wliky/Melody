package com.wliky.melody.core.network

import com.wliky.melody.core.common.AppError
import com.wliky.melody.core.common.DispatchersProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

data class HttpResponseData(
    val code: Int,
    val body: String,
    val headers: Map<String, List<String>>,
) {
    fun header(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()

    /** 提取 Set-Cookie 中的键值对，用于登录后维护会话。 */
    fun setCookies(): List<Pair<String, String>> =
        headers.entries
            .filter { it.key.equals("Set-Cookie", ignoreCase = true) }
            .flatMap { it.value }
            .mapNotNull { raw ->
                val pair = raw.substringBefore(';')
                val name = pair.substringBefore('=', "").trim()
                val value = pair.substringAfter('=', "").trim()
                if (name.isEmpty()) null else name to value
            }

    fun requireSuccess() {
        if (code !in 200..299) throw toHttpError()
    }

    fun parseBody(url: String): JsonElement {
        if (body.isBlank()) return JsonObject(emptyMap())
        return runCatching { MelodyJson.parseToJsonElement(body) }
            .getOrElse { throw AppError.Parse("接口返回内容无法解析：${body.take(120)}（$url）", it) }
    }

    private fun toHttpError(): AppError = when {
        code == 401 || code == 403 -> AppError.Unauthorized()
        code == 404 -> AppError.NotFound()
        code in 400..499 -> AppError.Server("请求被拒绝（HTTP $code）", code)
        code >= 500 -> AppError.Server("服务端暂时不可用（HTTP $code）", code)
        else -> AppError.Server("请求失败（HTTP $code）", code)
    }
}

/**
 * 所有 HTTP 请求的唯一出口。
 *
 * 业务层（ViewModel / Repository 上层）永远不直接碰 OkHttp；
 * 网易云特有字段只允许出现在 data 层，见文档 §7。
 */
@Singleton
class ApiClient @Inject constructor(
    private val client: OkHttpClient,
    private val dispatchers: DispatchersProvider,
) {

    suspend fun execute(request: Request): HttpResponseData = withContext(dispatchers.io) {
        client.newCall(request).execute().use { it.toData() }
    }

    suspend fun get(
        url: String,
        query: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
    ): HttpResponseData {
        val parsed = url.toHttpUrlOrNull() ?: throw AppError.Server("接口地址不合法：$url")
        val builder = parsed.newBuilder()
        query.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return execute(buildRequest(builder.build(), headers, form = null))
    }

    suspend fun postForm(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
    ): HttpResponseData {
        val parsed = url.toHttpUrlOrNull() ?: throw AppError.Server("接口地址不合法：$url")
        return execute(buildRequest(parsed, headers, form))
    }

    suspend fun postJson(
        url: String,
        json: String,
        headers: Map<String, String> = emptyMap(),
        contentType: String = JSON_TYPE,
    ): HttpResponseData {
        val parsed = url.toHttpUrlOrNull() ?: throw AppError.Server("接口地址不合法：$url")
        val request = Request.Builder()
            .url(parsed)
            .apply { headers.forEach { (key, value) -> if (value.isNotBlank()) header(key, value) } }
            .post(json.toRequestBody(contentType.toMediaType()))
            .build()
        return execute(request)
    }

    private fun buildRequest(
        url: HttpUrl,
        headers: Map<String, String>,
        form: Map<String, String>?,
    ): Request {
        val builder = Request.Builder().url(url)
        headers.forEach { (key, value) -> if (value.isNotBlank()) builder.header(key, value) }
        if (form != null) {
            val body = FormBody.Builder().apply {
                form.forEach { (key, value) -> add(key, value) }
            }.build()
            builder.post(body)
        } else {
            builder.get()
        }
        return builder.build()
    }

    private fun Response.toData(): HttpResponseData = HttpResponseData(
        code = code,
        body = body?.string().orEmpty(),
        headers = headers.toMultimap(),
    )

    companion object {
        const val JSON_TYPE = "application/json;charset=UTF-8"
    }
}
