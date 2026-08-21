package eu.kanade.tachiyomi.source.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

private val json = Json { ignoreUnknownKeys = true }
private val emptyMemo = JsonObject(emptyMap())

fun JsonObject.encodeMemo(): String =
    if (isEmpty()) "{}" else json.encodeToString(JsonObject.serializer(), this)

fun String.decodeMemo(): JsonObject {
    if (isBlank() || this == "{}") return emptyMemo
    return runCatching { json.parseToJsonElement(this).jsonObject }.getOrDefault(emptyMemo)
}

fun ByteArray.decodeMemoBytes(): JsonObject =
    if (isEmpty()) emptyMemo else decodeToString().decodeMemo()

fun JsonObject.encodeMemoBytes(): ByteArray =
    if (isEmpty()) ByteArray(0) else encodeMemo().encodeToByteArray()
