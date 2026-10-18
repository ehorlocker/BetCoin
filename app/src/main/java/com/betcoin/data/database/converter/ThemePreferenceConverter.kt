package com.betcoin.data.database.converter

import androidx.room.TypeConverter
import com.betcoin.data.model.ThemePreference

/** Converts [ThemePreference] to and from its [String] representation for Room storage. */
class ThemePreferenceConverter {

  @TypeConverter
  fun fromThemePreference(preference: ThemePreference): String = preference.name

  @TypeConverter
  fun toThemePreference(value: String): ThemePreference = ThemePreference.valueOf(value)
}
