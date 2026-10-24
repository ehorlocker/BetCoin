package com.betcoin.di

import android.content.Context
import androidx.room.Room
import com.betcoin.data.database.BetCoinDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Replaces [DatabaseModule] in instrumented tests with an in-memory Room database, so every
 * test starts empty and never touches the app's real database file. The DAO providers come
 * along from [DatabaseModule]'s replacement here.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
object TestDatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BetCoinDatabase =
        Room.inMemoryDatabaseBuilder(context, BetCoinDatabase::class.java).build()

    @Provides
    fun provideUserDao(database: BetCoinDatabase) = database.userDao()

    @Provides
    fun provideBetDao(database: BetCoinDatabase) = database.betDao()

    @Provides
    fun provideBetOutcomeDao(database: BetCoinDatabase) = database.betOutcomeDao()

    @Provides
    fun provideBetParticipantDao(database: BetCoinDatabase) = database.betParticipantDao()

    @Provides
    fun provideAppSettingsDao(database: BetCoinDatabase) = database.appSettingsDao()
}
