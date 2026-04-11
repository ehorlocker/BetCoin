package com.example.betcoin.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.betcoin.data.database.converter.BetStatusConverter
import com.example.betcoin.data.database.dao.AppSettingsDao
import com.example.betcoin.data.database.dao.BetDao
import com.example.betcoin.data.database.dao.BetOutcomeDao
import com.example.betcoin.data.database.dao.BetParticipantDao
import com.example.betcoin.data.database.dao.UserDao
import com.example.betcoin.data.database.entity.AppSettings
import com.example.betcoin.data.database.entity.Bet
import com.example.betcoin.data.database.entity.BetOutcome
import com.example.betcoin.data.database.entity.BetParticipant
import com.example.betcoin.data.database.entity.User

/**
 * The Room database for the BetCoin application.
 *
 * Contains all five entities and provides abstract DAO accessors for each. The
 * [BetStatusConverter] handles serialization of [com.example.betcoin.data.model.BetStatus] enum
 * values to and from their string representation.
 */
@Database(
  entities = [
    User::class,
    Bet::class,
    BetOutcome::class,
    BetParticipant::class,
    AppSettings::class,
  ],
  version = 1,
  exportSchema = false,
)
@TypeConverters(BetStatusConverter::class)
abstract class BetCoinDatabase : RoomDatabase() {

  /** Returns the [UserDao] for user operations. */
  abstract fun userDao(): UserDao

  /** Returns the [BetDao] for bet operations. */
  abstract fun betDao(): BetDao

  /** Returns the [BetOutcomeDao] for bet outcome operations. */
  abstract fun betOutcomeDao(): BetOutcomeDao

  /** Returns the [BetParticipantDao] for bet participant operations. */
  abstract fun betParticipantDao(): BetParticipantDao

  /** Returns the [AppSettingsDao] for application settings operations. */
  abstract fun appSettingsDao(): AppSettingsDao
}
