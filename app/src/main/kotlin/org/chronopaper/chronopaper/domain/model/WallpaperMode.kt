package org.chronopaper.chronopaper.domain.model

enum class WallpaperMode {
    THEME,
    SOLAR,
    TIME;

    companion object {
        fun fromNameOrDefault(name: String?): WallpaperMode =
            entries.firstOrNull { it.name == name } ?: THEME
    }
}