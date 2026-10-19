package com.betcoin.testing

import android.net.Uri
import org.mockito.Mockito

/** A fresh opaque [Uri] for JVM tests (android.net.Uri can't be built off-device). */
fun testUri(): Uri = Mockito.mock(Uri::class.java)
