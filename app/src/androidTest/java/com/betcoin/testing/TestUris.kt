package com.betcoin.testing

import android.net.Uri
import java.util.UUID

/** A fresh [Uri] for instrumented tests. The JVM counterpart in `src/test` returns a mock instead. */
fun testUri(): Uri = Uri.parse("content://fake/${UUID.randomUUID()}.jpg")
