package com.example.betcoin

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * The [Application] subclass for BetCoin, annotated with [HiltAndroidApp]
 * to trigger Hilt's code generation and serve as the application-level
 * dependency container.
 */
@HiltAndroidApp
class BetCoinApp : Application()
