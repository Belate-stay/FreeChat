package com.freechat.data

import com.google.gson.JsonParser
import java.io.IOException

/** Keep provider diagnostics, never raw response text (which may contain prompts or credentials). */
class ApiFailure(val status: Int, val errorCode: String = "", val errorType: String = "",
    val requestId: String = "", val parameter: String = "", private val diagnosedKind: Kind? = null) : IOException(
        "HTTP $status" + errorCode.takeIf { it.isNotEmpty() }?.let { " ($it)" }.orEmpty()) {
    enum class Kind { AUTH, ENDPOINT, MODEL, QUOTA, RATE_LIMIT, SAFETY, PARAMETERS, SERVER, EMPTY, UNKNOWN }
    val kind: Kind get() {
        return codeKind(errorCode + " " + errorType) ?: diagnosedKind ?: when {
            status == 402 -> Kind.QUOTA
            status == 401 || status == 403 -> Kind.AUTH
            status == 404 || status == 405 -> Kind.ENDPOINT
            status == 429 -> Kind.RATE_LIMIT
            status >= 500 -> Kind.SERVER
            status == 400 || status == 413 || status == 422 -> Kind.PARAMETERS
            else -> Kind.UNKNOWN
        }
    }
    companion object {
        private fun safeToken(value: String) = value.take(128).takeIf { it.matches(Regex("[A-Za-z0-9_.:/-]{1,128}")) }.orEmpty()
        private fun codeKind(value: String): Kind? {
            val code = value.lowercase()
            return when {
                listOf("moderation", "content_policy", "safety", "contentfilter", "content_filter", "contentrisk", "sensitive", "responsibleai").any { it in code } -> Kind.SAFETY
                listOf("quota", "balance", "credit", "arrears", "payment_required").any { it in code } -> Kind.QUOTA
                listOf("invalid_api_key", "authentication", "unauthorized", "permission_denied", "invalid_token").any { it in code } -> Kind.AUTH
                listOf("model_not_found", "model_not_exist", "unsupported_model", "invalid_model").any { it in code } -> Kind.MODEL
                listOf("endpoint_not_found", "invalid_endpoint", "invalid_api_url", "wrong_endpoint").any { it in code } -> Kind.ENDPOINT
                "rate_limit" in code || "ratelimit" in code -> Kind.RATE_LIMIT
                "empty_image_result" in code || "invalid_image_result" in code -> Kind.EMPTY
                else -> null
            }
        }
        /** Recognize provider explanations locally, then discard the raw text (never store/log it). */
        private fun messageKind(value: String): Kind? {
            val m = value.take(8192).lowercase().replace(Regex("\\s+"), " ")
            fun mentions(vararg words: String) = words.any { it in m }
            return when {
                mentions("insufficient quota", "quota exceeded", "quota exhausted", "insufficient balance", "balance is insufficient", "not enough balance",
                    "insufficient credit", "credits exhausted", "out of credits", "billing hard limit", "payment required", "余额不足", "餘額不足", "额度不足", "額度不足", "配额不足", "配額不足", "欠费", "欠費", "余额已用完", "quota has been exceeded", "exceeded your current quota") -> Kind.QUOTA
                mentions("content policy", "content-policy", "safety system", "safety policy", "moderation blocked", "content filter", "content_filter", "responsible ai",
                    "unsafe content", "policy violation", "敏感内容", "敏感內容", "内容违规", "內容違規", "内容安全", "內容安全", "审核不通过", "審核不通過", "违反政策", "違反政策") -> Kind.SAFETY
                mentions("invalid api key", "incorrect api key", "api key is invalid", "api key expired", "invalid token", "token is invalid", "authentication failed",
                    "unauthorized", "access token expired", "无效的令牌", "無效的令牌", "令牌无效", "令牌無效", "密钥无效", "密鑰無效", "密钥错误", "api key错误", "鉴权失败", "鑒權失敗", "没有权限", "沒有權限") -> Kind.AUTH
                mentions("model not found", "model does not exist", "unknown model", "unsupported model", "模型不存在", "不支持该模型", "不支援該模型", "无可用渠道", "無可用渠道", "no available channel") -> Kind.MODEL
                mentions("invalid endpoint", "endpoint not found", "wrong endpoint", "invalid api url", "接口不存在", "路由不存在", "地址错误", "位址錯誤") -> Kind.ENDPOINT
                mentions("rate limit", "too many requests", "请求过于频繁", "請求過於頻繁", "请求限流", "請求限流", "并发限制", "併發限制") -> Kind.RATE_LIMIT
                else -> null
            }
        }
        fun fromResponse(status: Int, body: String, headerRequestId: String = ""): ApiFailure {
            val root = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
            val errorValue = root?.get("error")
            val error = errorValue?.takeIf { it.isJsonObject }?.asJsonObject
            fun field(name: String) = (error?.get(name) ?: root?.get(name))?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            val requestId = headerRequestId.ifBlank {
                root?.get("request_id")?.takeIf { it.isJsonPrimitive }?.asString ?: field("request_id")
            }
            val explanation = listOf(field("message"), field("detail"), field("msg"),
                errorValue?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()).joinToString(" ")
            // Gateway plain-text errors are also common; do not classify an echoed HTML page/prompt.
            val plain = if (root == null && !body.trimStart().startsWith("<")) body else ""
            return ApiFailure(status, safeToken(field("code")), safeToken(field("type")), safeToken(requestId), safeToken(field("param")),
                messageKind(explanation.ifBlank { plain }))
        }
    }
}
