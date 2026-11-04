package dev.nanobot.protocol

import kotlinx.serialization.json.Json

/** Wire JSON shared by the client and its tests. Unknown fields are forward-compatible. */
val NanobotJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    encodeDefaults = true
}
