package com.freechat.data

import okhttp3.Interceptor
import okhttp3.Response

/**
 * 内置模型代理（2026-10-18）：内置模型的真 Key 只住服务器，客户端只连
 * freechater.com 的 /api/ai/v1/ 内置接口 —— 包里没有任何 Key（反编译也拿不走），
 * 每次调用进服务器用量账本（谁/哪台/哪个模型/几点/tokens），换模型·下架·封禁全云端生效。
 *
 * 身份头（服务器按它记账/封禁）：
 *   X-FC-Install  设备号 install_id —— 未登录按设备记账；
 *   Authorization 登录令牌 —— 登录了按账号记账（服务器优先认账号）。
 * 设备号读取经 installIdProvider 由调用方注入。
 */
object BuiltInProxy {
    const val BASE = "https://freechater.com/api/ai/v1"
    const val CATALOG_URL = "https://freechater.com/api/ai/catalog"
    const val REMOTE_NOTICE_URL = "https://freechater.com/api/remote-notice"

    /** 调用方注入的设备号读取；未注入=匿名调用照走（服务器记「匿名」）。 */
    @Volatile
    var installIdProvider: (() -> String?)? = null

    /** 代理/目录/弹窗接口都带身份头（设备号进账本与弹窗留痕）。 */
    fun isProxyUrl(url: String): Boolean =
        url.startsWith("https://freechater.com/api/ai") || url.startsWith(REMOTE_NOTICE_URL)

    /**
     * 挂到所有模型出口的 OkHttpClient 上：/api/ai/ 的请求换身份头、清掉调用点
     * 旧的 Bearer <Key>（Key 已经不存在，别把空串发出去）；其它请求原样放行。
     */
    fun interceptor(): Interceptor = Interceptor { chain ->
        val req = chain.request()
        if (!isProxyUrl(req.url.toString())) return@Interceptor chain.proceed(req)
        val b = req.newBuilder().removeHeader("Authorization")
        com.freechat.sync.Session.loadAuth()?.token?.takeIf { it.isNotBlank() }?.let {
            b.header("Authorization", "Bearer $it")
        }
        installIdProvider?.invoke()?.takeIf { it.isNotBlank() }?.let {
            b.header("X-FC-Install", it)
        }
        chain.proceed(b.build())
    }
}
