package com.freechat.sync

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * 账号头像的**本地缓存**。
 *
 * 为什么不让界面直接去服务器要：抽屉左下角那个圆头像在每次拉开侧栏时都在，
 * 每次都要走一趟网络的话，没网的时候它会空一下、有网的时候又白费一次往返。
 * 所以这里落一份盘，界面只读这份；真正跟服务器对账是 [AccountManager.refreshAvatar] 的事。
 *
 * 缓存跟 userId 绑定，并且**记着它对应的服务端版本号**。改了头像或换了账号，
 * 这里的版本号对不上，下一次 refresh 就会把它换掉 —— 不需要专门去清理旧账号的图。
 *
 * 未登录时也要能用（[current] 返回 null），因为抽屉是常驻的，不能因为没登录就崩。
 */
object AvatarStore {

    private var appContext: Context? = null

    /** 当前该显示的头像字节。null = 没有（未登录、或登录了但没设过头像） */
    private val _current = MutableStateFlow<ByteArray?>(null)
    val current: StateFlow<ByteArray?> = _current.asStateFlow()

    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    private fun dir(): File? = appContext?.filesDir?.let { File(it, "account_avatar").apply { mkdirs() } }

    private fun imageFile(userId: String) = dir()?.let { File(it, "$userId.jpg") }
    private fun revFile(userId: String) = dir()?.let { File(it, "$userId.rev") }

    /** 没裁过的原图，文件名带后缀区分，免得跟上面那张成品撞上 */
    private fun originalFile(userId: String) = dir()?.let { File(it, "${userId}_original.jpg") }

    fun localRev(userId: String): Long =
        revFile(userId)?.takeIf { it.exists() }?.let { runCatching { it.readText().trim().toLong() }.getOrNull() } ?: -1L

    /**
     * 把一份头像落到本地。
     *
     * [bytes] 传 null 表示「这个账号就是没有头像」—— 也要把 rev 写下来，
     * 否则下次 refresh 看到 -1 又去问一遍服务器，而服务器每次都会告诉你 404。
     */
    fun cacheLocally(userId: String, bytes: ByteArray?, rev: Long) {
        val img = imageFile(userId) ?: return
        val revF = revFile(userId) ?: return
        runCatching {
            if (bytes == null || bytes.isEmpty()) img.delete() else img.writeBytes(bytes)
            revF.writeText(rev.toString())
        }
        // 只有当前登录的这个账号值得进内存 —— 换账号时自然会被下一次 refresh 覆盖
        val active = Session.peekAuth()?.userId
        if (active == null || active == userId) _current.value = bytes?.takeIf { it.isNotEmpty() }
    }

    /** 冷启动 / 刚登录时：把盘上那份读进内存，界面立刻就有东西可画 */
    fun loadFromDisk(userId: String) {
        val img = imageFile(userId)
        _current.value = img?.takeIf { it.exists() }?.let { runCatching { it.readBytes() }.getOrNull() }
    }

    // ===== 原图（「编辑头像」用）=====
    //
    // 服务端只存裁好的成品，所以想「重选范围」就必须本机留一份没裁过的。
    // 它是**设备本地**的东西：换台机器同步下来的只有成品，那边点了「编辑头像」
    // 会退回拿成品本身去裁（[originalBytes] 返回 null，由调用方决定怎么办）。
    // 也正因为只在本机，它不参与云端同步，不占服务端配额。

    /** 上传头像时顺手把原图留在本机 */
    fun saveOriginal(userId: String, bytes: ByteArray) {
        val f = originalFile(userId) ?: return
        runCatching { f.writeBytes(bytes) }
    }

    /** 没有就返回 null（另一台设备设的头像、或用户清过数据），调用方自己找退路 */
    fun originalBytes(userId: String): ByteArray? =
        originalFile(userId)?.takeIf { it.exists() }?.let { runCatching { it.readBytes() }.getOrNull() }
            ?.takeIf { it.isNotEmpty() }

    /** 头像撤掉之后原图就没有意义了，留着只会占地方 */
    fun removeOriginal(userId: String) {
        runCatching { originalFile(userId)?.delete() }
    }

    /** 登出 / 注销：屏幕上不该再挂着上一个人的头像 */
    fun clear() {
        _current.value = null
    }
}
