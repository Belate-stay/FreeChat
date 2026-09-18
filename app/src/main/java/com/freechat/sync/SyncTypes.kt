package com.freechat.sync

import com.google.gson.JsonElement

/**
 * 服务端契约的传输对象 —— 逐字段对齐 `FreeChatServer/src/sync.js` 与 `auth.js`，
 * 和网页端 `src/core/sync/api.ts` 里的类型是一套东西（两端必须同构，
 * 否则同一份数据在网页和安卓之间会互相看不懂）。
 *
 * 服务端是个**带版本的对象存储**，不解析业务数据：它只认 `kind + id + 版本号 + 一坨 JSON`。
 */

/** 同步对象一共五种，按「一条对话一个箱子」切 */
object SyncKind {
    const val CONV = "conv"
    const val MSGS = "msgs"
    const val MEMS = "mems"
    const val SETTINGS = "settings"

    /**
     * 每对话「新规则」里**设备无关的那半**（联网/思考过程/记忆总结/温度/长度），id 是对话 id。
     *
     * 只同步一半：另外一半存的是**本机自定义模型的 id**，另一台设备上没有那个模型，
     * 传过去只会选中一个点不通的项。切分写在 `PerConvBridge`，两端同一份。
     */
    const val PCSET = "pcset"

    /** 设置只有一条，id 写死 —— 两端约定 */
    const val SETTINGS_ID = "global"
}

data class SyncUser(
    val id: String = "",
    val username: String = "",
    val createdAt: Long = 0,
    /** 头像版本号，0 = 没有头像。客户端拿它跟本地缓存那份比，一样就不必再下一遍图 */
    val avatarRev: Int = 0
)

data class UsageInfo(
    val bytesUsed: Long = 0,
    val bytesLimit: Long = 0
)

/** 对象的元信息（`changes` / `list` 返回的就是它，**不带 data**） */
data class ObjectMeta(
    val kind: String = "",
    val id: String = "",
    val rev: Long = 0,
    val updatedAt: Long = 0,
    val seq: Long = 0,
    val deleted: Boolean = false
)

/** 对象的元信息 + 内容。**墓碑（deleted）没有 data** */
data class SyncObject(
    val kind: String = "",
    val id: String = "",
    val rev: Long = 0,
    val updatedAt: Long = 0,
    val seq: Long = 0,
    val deleted: Boolean = false,
    val data: JsonElement? = null
)

data class ChangesResult(
    val changes: List<ObjectMeta> = emptyList(),
    val nextSeq: Long = 0,
    val hasMore: Boolean = false
)

data class PutResult(
    val kind: String = "",
    val id: String = "",
    val rev: Long = 0,
    val seq: Long = 0,
    val updatedAt: Long = 0,
    val deleted: Boolean = false
)

data class AuthResult(
    val token: String = "",
    val user: SyncUser = SyncUser(),
    /** 恢复码 —— 只在注册/找回时返回，且**只此一次**，丢了只能靠它找回账号 */
    val recoveryCode: String? = null
)

data class TokenInfo(
    val id: String = "",
    val label: String = "",
    val createdAt: Long = 0,
    val lastUsed: Long = 0,
    val expiresAt: Long = 0
)

/**
 * 服务端的错误。
 *
 * **网络不通（[status] == 0）必须和「服务器说你不对」分开**：
 * 前者等会儿自己会好，后者要清令牌/提示用户。
 */
class ApiError(
    val status: Int,
    val code: String,
    message: String,
    /** 409 时带上云端当前那一条的元信息（服务端在 `current` 字段里给），够判断「云端是不是已经删了」*/
    val current: ObjectMeta? = null
) : Exception(message) {

    val isNetworkError: Boolean get() = status == 0
    val isConflict: Boolean get() = status == 409 && code == "conflict"
    val isUnauthorized: Boolean get() = status == 401
}
