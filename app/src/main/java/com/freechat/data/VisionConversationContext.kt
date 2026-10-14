package com.freechat.data

/** Add visual input to the current turn without discarding the normal conversation context. */
object VisionConversationContext {
    fun attach(context: List<Map<String, Any?>>, images: List<Pair<String, String>>, prompt: String): List<Map<String, Any?>> {
        val result = context.toMutableList()
        val latestUser = result.indexOfLast { it["role"] == "user" }
        val current = result.getOrNull(latestUser)?.get("content") as? String
        val text = if (current.isNullOrBlank() || current == prompt) prompt else "$current\n\n$prompt"
        val parts = images.map { (base64, mime) ->
            mapOf<String, Any?>("type" to "image_url", "image_url" to mapOf("url" to "data:$mime;base64,$base64"))
        } + mapOf("type" to "text", "text" to text)
        // Tool analysis may follow an assistant/tool round. Preserve its order and append a user turn.
        if (latestUser == result.lastIndex && latestUser >= 0) {
            result[latestUser] = result[latestUser] + ("content" to parts)
        } else result.add(mapOf("role" to "user", "content" to parts))
        return result
    }
}
