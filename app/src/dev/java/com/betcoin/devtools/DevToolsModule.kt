package com.betcoin.devtools

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DevToolsModule {
    @Binds
    abstract fun bindDevToolsEntry(impl: DevDevToolsEntry): DevToolsEntry
}
