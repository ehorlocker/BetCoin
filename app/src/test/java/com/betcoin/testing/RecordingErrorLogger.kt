package com.betcoin.testing

import com.betcoin.util.ErrorLogger

/** [ErrorLogger] fake that records every call for assertions. */
class RecordingErrorLogger : ErrorLogger {
    data class Call(val tag: String, val message: String, val throwable: Throwable)

    val calls = mutableListOf<Call>()

    override fun logError(tag: String, message: String, throwable: Throwable) {
        calls += Call(tag, message, throwable)
    }
}
