package com.betcoin.data.database.converter

import com.betcoin.data.model.ThemePreference
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [ThemePreferenceConverter] Room TypeConverter.
 */
class ThemePreferenceConverterTest {

  private val converter = ThemePreferenceConverter()

  @Test
  fun fromThemePreference_storesEnumName() {
    assertThat(converter.fromThemePreference(ThemePreference.SYSTEM)).isEqualTo("SYSTEM")
    assertThat(converter.fromThemePreference(ThemePreference.LIGHT)).isEqualTo("LIGHT")
    assertThat(converter.fromThemePreference(ThemePreference.DARK)).isEqualTo("DARK")
  }

  @Test
  fun roundTrip_allValuesConvertCorrectly() {
    for (preference in ThemePreference.values()) {
      val asString = converter.fromThemePreference(preference)
      assertThat(converter.toThemePreference(asString)).isEqualTo(preference)
    }
  }
}
