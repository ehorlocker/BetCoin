package com.example.betcoin.di

import com.example.betcoin.data.repository.BetRepository
import com.example.betcoin.data.repository.SettingsRepository
import com.example.betcoin.data.repository.UserRepository
import com.example.betcoin.data.repository.local.LocalBetRepository
import com.example.betcoin.data.repository.local.LocalSettingsRepository
import com.example.betcoin.data.repository.local.LocalUserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module that binds repository interfaces to their local (Room-backed)
 * implementations. Swapping to a remote implementation later only requires
 * changing the bindings here.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindUserRepository(impl: LocalUserRepository): UserRepository

    @Binds
    abstract fun bindBetRepository(impl: LocalBetRepository): BetRepository

    @Binds
    abstract fun bindSettingsRepository(impl: LocalSettingsRepository): SettingsRepository
}
