package com.betcoin.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a registered player in the BetCoin app.
 *
 * All currency values are stored as [Long] (integer BetCoin) to avoid floating-point rounding.
 * [username] uses `COLLATE NOCASE`, so the unique index rejects names that differ only in case
 * ("Jay" and "jay") and username lookups ignore case.
 */
@Entity(
  tableName = "users",
  indices = [Index(value = ["username"], unique = true)],
)
data class User(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0L,

  @ColumnInfo(name = "username", collate = ColumnInfo.NOCASE)
  val username: String,

  @ColumnInfo(name = "pin_hash")
  val pinHash: String,

  @ColumnInfo(name = "balance", defaultValue = "1000")
  val balance: Long = 1000L,

  @ColumnInfo(name = "total_wins", defaultValue = "0")
  val totalWins: Int = 0,

  @ColumnInfo(name = "total_losses", defaultValue = "0")
  val totalLosses: Int = 0,

  @ColumnInfo(name = "total_earnings", defaultValue = "0")
  val totalEarnings: Long = 0L,

  @ColumnInfo(name = "total_lost", defaultValue = "0")
  val totalLost: Long = 0L,

  @ColumnInfo(name = "bailout_count", defaultValue = "0")
  val bailoutCount: Int = 0,

  @ColumnInfo(name = "total_debt", defaultValue = "0")
  val totalDebt: Long = 0L,

  /** Opaque key returned by the avatar store, or null until an avatar is set. */
  @ColumnInfo(name = "avatar_ref")
  val avatarRef: String? = null,

  @ColumnInfo(name = "created_at")
  val createdAt: Long,
)
