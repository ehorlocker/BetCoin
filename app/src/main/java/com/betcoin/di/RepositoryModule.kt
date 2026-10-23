package com.betcoin.di

import com.betcoin.data.avatar.AvatarStore
import com.betcoin.data.avatar.CameraCaptureStore
import com.betcoin.data.avatar.LocalAvatarStore
import com.betcoin.data.avatar.LocalCameraCaptureStore
import com.betcoin.data.repository.BetRepository
import com.betcoin.data.repository.SettingsRepository
import com.betcoin.data.repository.UserRepository
import com.betcoin.data.repository.local.LocalBetRepository
import com.betcoin.data.repository.local.LocalSettingsRepository
import com.betcoin.data.repository.local.LocalUserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that binds repository interfaces to their local (Room-backed)
 * implementations. Swapping to a remote implementation later only requires
 * changing the bindings here.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: LocalUserRepository): UserRepository

    @Binds
    @Singleton
    abstract fun bindBetRepository(impl: LocalBetRepository): BetRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: LocalSettingsRepository): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindAvatarStore(impl: LocalAvatarStore): AvatarStore

    @Binds
    @Singleton
    abstract fun bindCameraCaptureStore(impl: LocalCameraCaptureStore): CameraCaptureStore
}
