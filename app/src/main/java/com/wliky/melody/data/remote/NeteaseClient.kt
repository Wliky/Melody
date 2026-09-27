package com.wliky.melody.data.remote

import com.wliky.melody.data.error.AppError
import com.wliky.melody.data.error.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 网易云 weapi 客户端：统一加密、请求头、错误映射。
 *
 * 端点约定：传 `/weapi/xxx`，最终请求 `https://music.163.com/weapi/xxx`。
 */
class NeteaseClient(
    private val okHttpClient: OkHttpClient,
    private val cookieStore: CookieStore? = null,
) {

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * 写接口（歌单增删等）需要的 csrf token：必须与 cookie 里的 `__csrf` 一致，
     * 否则服务端返回 524「当前环境异常」。读接口传空字符串即可。
     */
    fun csrfToken(): String = cookieStore?.csrfToken().orEmpty()

    /**
     * 发起 weapi 调用并解析为 JsonObject，业务码 200 才算成功（常规接口用这个）。
     */
    suspend fun callWeapi(
        endpoint: String,
        payloadJson: String,
        extraHeaders: Map<String, String> = emptyMap(),
    ): AppResult<JsonObject> = when (val raw = post(endpoint, payloadJson, extraHeaders)) {
        is AppResult.Failure -> raw
        is AppResult.Success -> {
            val code = (raw.data["code"]?.toString())?.toIntOrNull() ?: 200
            if (code == 200) {
                raw
            } else {
                val msg = raw.data["message"]?.toString()?.trim('"')
                    ?: raw.data["msg"]?.toString()?.trim('"')
                val error = if (code == 301) AppError.Unauthorized else AppError.Api(code, msg)
                AppResult.failure(error)
            }
        }
    }

    /**
     * 发起 weapi 调用并解析为 JsonObject，不做业务码校验。
     * 供「业务码本身是状态」的接口使用（如二维码轮询 800/801/802/803）。
     */
    suspend fun post(
        endpoint: String,
        payloadJson: String,
        extraHeaders: Map<String, String> = emptyMap(),
    ): AppResult<JsonObject> = withContext(Dispatchers.IO) {
        val form = WeapiCrypto.encrypt(payloadJson).let { cipher ->
            FormBody.Builder()
                .add("params", cipher.getValue("params"))
                .add("encSecKey", cipher.getValue("encSecKey"))
                .build()
        }
        val request = Request.Builder()
            .url("$BASE_HOST$endpoint")
            .post(form)
            .header("User-Agent", USER_AGENT)
            .apply { extraHeaders.forEach { (k, v) -> header(k, v) } }
            .build()

        val body: String = try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext AppResult.failure(AppError.Api(response.code, "服务响应异常"))
                }
                response.body.string()
            }
        } catch (e: IOException) {
            return@withContext AppResult.failure(AppError.Network(e.message))
        }

        val root = try {
            json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            return@withContext AppResult.failure(AppError.Parse(e.message))
        }

        AppResult.success(root)
    }

    companion object {
        const val BASE_HOST = "https://music.163.com"
        private const val APP_VERSION = "8.9.70"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

        fun defaultOkHttp(cookieJar: CookieStore): OkHttpClient =
            OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
    }
}
