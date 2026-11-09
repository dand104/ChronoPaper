package org.chronopaper.chronopaper.domain.model

enum class WallpaperMode {
    STATIC_PAIR,
    PLAYLIST,
    APPLE_SOLAR,
    APPLE_APR;

    companion object {
        fun fromNameOrDefault(name: String?): WallpaperMode {
            return entries.firstOrNull { it.name == name } ?: STATIC_PAIR
        }
    }
}