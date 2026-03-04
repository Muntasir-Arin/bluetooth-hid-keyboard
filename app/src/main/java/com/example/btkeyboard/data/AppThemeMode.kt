package com.example.btkeyboard.data

enum class AppThemeMode {
    DARK,
    LIGHT,
    ;

    companion object {
        fun fromStoredValue(value: String?): AppThemeMode {
            return entries.firstOrNull { it.name == value } ?: DARK
        }
    }
}
