package com.freechat.companion

import com.google.gson.JsonObject

/** Non-secret provenance of a bound WeChat round; never inferred from user text. */
data class WechatBinding(val userId: String, val boundAt: Long) {
    companion object {
        const val CHANNEL_HEADER = "X-FreeChat-Channel"

        /** The authenticated App proxy builds fresh headers and never forwards this marker. */
        fun fromTrustedRequest(body: JsonObject, channelHeader: String?, loopbackPeer: Boolean): WechatBinding? {
            if (!loopbackPeer || channelHeader != "wechat") return null
            val metadata = body.get("wechatBinding")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
            val userId = metadata.get("userId")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString ?: return null
            if (userId.isEmpty() || userId.length > 64 || userId.any { !it.isLetterOrDigit() && it !in "-_" }) return null
            val boundAt = metadata.get("boundAt")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asString
                ?.toLongOrNull()?.takeIf { it > 0L } ?: return null
            return WechatBinding(userId, boundAt)
        }
    }
}
