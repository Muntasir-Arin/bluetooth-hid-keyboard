package com.example.btkeyboard.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppThemeModeTest {

    @Test
    fun fromStoredValueReturnsModeForKnownValue() {
        assertEquals(AppThemeMode.LIGHT, AppThemeMode.fromStoredValue("LIGHT"))
        assertEquals(AppThemeMode.DARK, AppThemeMode.fromStoredValue("DARK"))
    }

    @Test
    fun fromStoredValueFallsBackToDarkForUnknownValue() {
        assertEquals(AppThemeMode.DARK, AppThemeMode.fromStoredValue("AUTO"))
        assertEquals(AppThemeMode.DARK, AppThemeMode.fromStoredValue(null))
    }

    @Test
    fun appSettingsDefaultsToDarkTheme() {
        assertEquals(AppThemeMode.DARK, AppSettings().themeMode)
    }
}
