package com.betcoin.util

import android.util.Log
import javax.inject.Inject

/** Logs handled errors. Abstracted so JVM unit tests do not need the Android logger. */
fun interface ErrorLogger {
    fun logError(tag: String, message: String, throwable: Throwable)
}

/** [ErrorLogger] that writes to Logcat. */
class AndroidErrorLogger @Inject constructor() : ErrorLogger {
    override fun logError(tag: String, message: String, throwable: Throwable) {
        Log.e(tag, message, throwable)
    }
}
