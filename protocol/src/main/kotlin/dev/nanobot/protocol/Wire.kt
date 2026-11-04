package dev.nanobot.protocol

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal fun JsonObject.textOrNull(name: String): String? =
    (get(name) as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonObject.longOrNull(name: String): Long? =
    (get(name) as? JsonPrimitive)?.content?.toLongOrNull()

internal fun JsonObject.intOrNull(name: String): Int? = longOrNull(name)?.toInt()

internal fun JsonObject.booleanOrNull(name: String): Boolean? =
    (get(name) as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
internal fun JsonObject.objectOrNull(name: String): JsonObject? = get(name) as? JsonObject

internal fun JsonObject.arrayOrNull(name: String): JsonArray? = get(name) as? JsonArray

internal fun JsonArray.strings(): List<String> =
    mapNotNull { (it as? JsonPrimitive)?.takeIf { primitive -> primitive.isString }?.content }
