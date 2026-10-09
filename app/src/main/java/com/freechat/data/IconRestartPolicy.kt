package com.freechat.data

/** This check intentionally includes background conversations and their buffered delivery. */
object IconRestartPolicy {
    @JvmStatic
    fun requiresConfirmation(pending: Boolean, loading: Collection<Boolean>, typing: Collection<Boolean>): Boolean =
        pending || loading.any { it } || typing.any { it }
}
