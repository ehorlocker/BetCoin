package com.betcoin.di

import android.content.Context
import androidx.room.Room
import com.betcoin.BuildConfig
import com.betcoin.data.database.BetCoinDatabase
import com.betcoin.data.database.dao.AppSettingsDao
import com.betcoin.data.database.dao.BetDao
import com.betcoin.data.database.dao.BetOutcomeDao
import com.betcoin.data.database.dao.BetParticipantDao
import com.betcoin.data.database.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides the Room [BetCoinDatabase] instance and all 5 DAO
 * accessors as singleton-scoped dependencies.
 *
 * The database filename is determined by [BuildConfig.DB_NAME], which varies by
 * product flavor: "betcoin.db" for prod and "betcoin_dev.db" for dev.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BetCoinDatabase {
        return Room.databaseBuilder(
            context,
            BetCoinDatabase::class.java,
            BuildConfig.DB_NAME
        ).build()
    }

    @Provides
    fun provideUserDao(database: BetCoinDatabase): UserDao {
        return database.userDao()
    }

    @Provides
    fun provideBetDao(database: BetCoinDatabase): BetDao {
        return database.betDao()
    }

    @Provides
    fun provideBetOutcomeDao(database: BetCoinDatabase): BetOutcomeDao {
        return database.betOutcomeDao()
    }

    @Provides
    fun provideBetParticipantDao(database: BetCoinDatabase): BetParticipantDao {
        return database.betParticipantDao()
    }

    @Provides
    fun provideAppSettingsDao(database: BetCoinDatabase): AppSettingsDao {
        return database.appSettingsDao()
    }
}
