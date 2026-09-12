package com.wliky.melody.core.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * 全局 Json 配置。第三方接口字段经常变化，这里全部按「宽松解析」处理：
 *  - ignoreUnknownKeys：新增字段不会导致解析失败
 *  - isLenient / coerceInputValues：类型擦边的值也能读出来
 */
val MelodyJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
    allowStructuredMapKeys = true
}

/**
 * 宽松数字解析器：网易云接口同一个字段有时返回数字、有时返回字符串
 * （例如 `id` / `fee` / `playCount`），用它可以避免整条数据解析失败。
 */
object FlexibleLongSerializer : KSerializer<Long?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleLong", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Long?) {
        if (value == null) encoder.encodeNull() else encoder.encodeLong(value)
    }

    override fun deserialize(decoder: Decoder): Long? = decoder.readFlexiblePrimitive()?.asLong()
}

object FlexibleIntSerializer : KSerializer<Int?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleInt", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: Int?) {
        if (value == null) encoder.encodeNull() else encoder.encodeInt(value)
    }

    override fun deserialize(decoder: Decoder): Int? = decoder.readFlexiblePrimitive()?.asLong()?.toInt()
}

object FlexibleStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }

    override fun deserialize(decoder: Decoder): String? = decoder.readFlexiblePrimitive()?.contentOrNull
}

private fun Decoder.readFlexiblePrimitive(): JsonPrimitive? {
    val jsonDecoder = this as? JsonDecoder ?: return null
    val element = jsonDecoder.decodeJsonElement()
    return element as? JsonPrimitive
}

private fun JsonPrimitive.asLong(): Long? =
    longOrNull ?: doubleOrNull?.toLong() ?: contentOrNull?.trim()?.toLongOrNull()

// ---------------------------------------------------------------------------
// JsonObject 取值辅助：接口字段缺失 / 类型不符时返回 null，不抛异常。
// ---------------------------------------------------------------------------

fun JsonElement?.objOrNull(): JsonObject? = this as? JsonObject

fun JsonElement?.arrayOrNull(): JsonArray? = this as? JsonArray

fun JsonObject?.str(key: String): String? {
    val element = this?.get(key) ?: return null
    if (element is JsonNull) return null
    return (element as? JsonPrimitive)?.contentOrNull
}

fun JsonObject?.long(key: String): Long? {
    val element = this?.get(key) ?: return null
    if (element !is JsonPrimitive) return null
    return element.longOrNull ?: element.doubleOrNull?.toLong() ?: element.contentOrNull?.trim()?.toLongOrNull()
}

fun JsonObject?.int(key: String): Int? = this.long(key)?.toInt()

fun JsonObject?.boolean(key: String): Boolean? {
    val element = this?.get(key) ?: return null
    if (element !is JsonPrimitive) return null
    return element.booleanOrNull
}

fun JsonObject?.obj(key: String): JsonObject? = this?.get(key).objOrNull()

fun JsonObject?.arr(key: String): JsonArray? = this?.get(key).arrayOrNull()

fun JsonElement?.asObjectOrNull(): JsonObject? = when (this) {
    is JsonObject -> this
    is JsonArray -> firstOrNull()?.objOrNull()
    else -> null
}

fun JsonElement?.firstArrayOrEmpty(): JsonArray = when (this) {
    is JsonArray -> this
    is JsonObject -> (this["result"] ?: this["data"])?.arrayOrNull() ?: JsonArray(emptyList())
    else -> JsonArray(emptyList())
}

fun jsonObjectOf(vararg pairs: Pair<String, JsonElement>): JsonObject = JsonObject(pairs.toMap())

fun String?.toJsonPrimitiveOrNull(): JsonPrimitive? = this?.let { JsonPrimitive(it) }

fun Long.toJsonPrimitive(): JsonPrimitive = JsonPrimitive(this)

fun Boolean.toJsonPrimitive(): JsonPrimitive = JsonPrimitive(this)

fun Int.toJsonPrimitive(): JsonPrimitive = JsonPrimitive(this)

@Suppress("unused")
fun JsonArray?.toList(): List<JsonElement> = this ?: JsonArray(emptyList())

/** 便捷：把 "[{...},{...}]" 解析为 List<JsonObject>。 */
fun JsonElement?.objectList(): List<JsonObject> =
    this.arrayOrNull()?.mapNotNull { it.objOrNull() } ?: emptyList()

/** 便捷：读取 { "code": 200 } 风格的接口状态码。 */
fun JsonObject?.code(): Int = this.int("code") ?: -1

fun JsonArray?.objects(): List<JsonObject> = this?.mapNotNull { it.objOrNull() } ?: emptyList()
