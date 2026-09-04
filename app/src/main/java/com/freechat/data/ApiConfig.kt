package com.freechat.data

// 保留，供将来扩展使用
data class ApiConfig(
    val baseUrl: String = "https://api.deepseek.com",
    val apiKey: String = "",
    val model: String = "deepseek-v4-flash",
    val provider: String = "deepseek"
)
