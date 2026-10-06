package com.freechat.sync

/** A successful revoke is a local tombstone, including while an older reload is in flight. */
class ActiveShareList {
    private val revokedIds = mutableSetOf<String>()
    fun revoked(id: String) { revokedIds.add(id) }
    fun visible(rows: List<ShareInfo>) = rows.filter { !it.revoked && it.id !in revokedIds }
}
