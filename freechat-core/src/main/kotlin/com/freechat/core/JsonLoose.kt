package com.freechat.core

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** 从模型输出里提取 JSON 对象（容错：兼容首尾多余文字 / markdown 代码块） */
object JsonLoose {
    fun extractObject(raw: String): JsonObject? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            JsonParser.parseString(raw.substring(start, end + 1)).asJsonObject
        } catch (_: Exception) { null }
    }
}
