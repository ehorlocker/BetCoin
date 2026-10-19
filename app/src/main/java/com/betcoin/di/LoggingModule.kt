package com.betcoin.di

import com.betcoin.util.AndroidErrorLogger
import com.betcoin.util.ErrorLogger
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt module that binds [ErrorLogger] to its Logcat implementation. */
@Module
@InstallIn(SingletonComponent::class)
abstract class LoggingModule {

    @Binds
    @Singleton
    abstract fun bindErrorLogger(impl: AndroidErrorLogger): ErrorLogger
}
