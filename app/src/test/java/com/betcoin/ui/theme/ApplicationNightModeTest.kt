package com.betcoin.ui.theme

import android.app.UiModeManager
import com.betcoin.data.model.ThemePreference
import org.junit.Assert.assertEquals
import org.junit.Test

class ApplicationNightModeTest {

    @Test
    fun system_mapsToAuto() {
        assertEquals(UiModeManager.MODE_NIGHT_AUTO, ThemePreference.SYSTEM.toApplicationNightMode())
    }

    @Test
    fun light_mapsToNo() {
        assertEquals(UiModeManager.MODE_NIGHT_NO, ThemePreference.LIGHT.toApplicationNightMode())
    }

    @Test
    fun dark_mapsToYes() {
        assertEquals(UiModeManager.MODE_NIGHT_YES, ThemePreference.DARK.toApplicationNightMode())
    }
}
