package com.freechat.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/** Tool metadata shared by companion replies and standard streaming/fallback replies. */
internal object ChatToolCalls {
    fun read(message: JsonObject?): JsonArray? =
        message?.get("tool_calls")?.takeIf { it.isJsonArray }?.asJsonArray
}
