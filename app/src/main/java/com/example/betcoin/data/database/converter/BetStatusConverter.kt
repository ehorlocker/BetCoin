package com.example.betcoin.data.database.converter

import androidx.room.TypeConverter
import com.example.betcoin.data.model.BetStatus

/** Converts [BetStatus] to and from its [String] representation for Room storage. */
class BetStatusConverter {

  @TypeConverter
  fun fromBetStatus(status: BetStatus): String = status.name

  @TypeConverter
  fun toBetStatus(value: String): BetStatus = BetStatus.valueOf(value)
}
