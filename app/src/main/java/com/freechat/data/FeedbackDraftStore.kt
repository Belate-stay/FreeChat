package com.freechat.data

import android.content.Context
import java.util.UUID

/**
 * 反馈草稿 + 设备端反馈身份（1.0.95 接真后端）。
 * clientId = 本机随机 UUID：匿名提交的「我的反馈」认领凭据——不进账号体系、不同步，
 * 只随反馈内容发给服务器（登录后 user_id 与它一起认领，两种身份都看得见自己的反馈）。
 */
class FeedbackDraftStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("freechat_feedback_draft", Context.MODE_PRIVATE)
    fun load(): String = prefs.getString("draft", "").orEmpty()
    fun save(text: String) { prefs.edit().putString("draft", text).apply() }

    /** 设备本地随机 id，首次生成后固定（换了 id 就认不回以前的匿名反馈了） */
    fun clientId(): String {
        val cur = prefs.getString("client_id", "").orEmpty()
        if (cur.isNotBlank()) return cur
        val fresh = UUID.randomUUID().toString()
        prefs.edit().putString("client_id", fresh).apply()
        return fresh
    }
}
