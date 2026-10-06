package com.freechat.data

import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Cause-chain classification, without exposing raw provider payloads, URLs or credentials. */
object SceneImageFailure {
    class ReferenceFailure : IOException("Invalid scene reference")
    enum class Reason { AUTH, ADDRESS, TLS, CONFIGURATION, ENDPOINT, MODEL, QUOTA, RATE_LIMIT,
        SAFETY, PARAMETERS, SERVER, EMPTY, TIMEOUT, NETWORK, REFERENCES, UNKNOWN }

    fun provider(error: Throwable): ApiFailure? = causes(error).filterIsInstance<ApiFailure>().firstOrNull()
    private fun causes(error: Throwable): List<Throwable> = generateSequence(error) { it.cause }.take(12).toList()

    fun reason(error: Throwable): Reason {
        val chain = causes(error)
        provider(error)?.let {
            if (it.status == 0 && it.errorCode == "invalid_api_url") return Reason.CONFIGURATION
            return when (it.kind) {
                ApiFailure.Kind.AUTH -> Reason.AUTH
                ApiFailure.Kind.ENDPOINT -> Reason.ENDPOINT
                ApiFailure.Kind.MODEL -> Reason.MODEL
                ApiFailure.Kind.QUOTA -> Reason.QUOTA
                ApiFailure.Kind.RATE_LIMIT -> Reason.RATE_LIMIT
                ApiFailure.Kind.SAFETY -> Reason.SAFETY
                ApiFailure.Kind.PARAMETERS -> Reason.PARAMETERS
                ApiFailure.Kind.SERVER -> Reason.SERVER
                ApiFailure.Kind.EMPTY -> Reason.EMPTY
                ApiFailure.Kind.UNKNOWN -> Reason.UNKNOWN
            }
        }
        return when {
            chain.any { it is ReferenceFailure } -> Reason.REFERENCES
            chain.any { it is UnknownHostException } -> Reason.ADDRESS
            chain.any { it is SSLException } -> Reason.TLS
            chain.any { it is SocketTimeoutException || it is InterruptedIOException } -> Reason.TIMEOUT
            chain.any { it is com.google.gson.JsonParseException } -> Reason.EMPTY
            chain.any { it is IllegalArgumentException } -> Reason.CONFIGURATION
            chain.any { it is IOException } -> Reason.NETWORK
            else -> Reason.UNKNOWN
        }
    }
}
