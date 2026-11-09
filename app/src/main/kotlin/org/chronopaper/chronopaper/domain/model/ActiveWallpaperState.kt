package org.chronopaper.chronopaper.domain.model

sealed interface ActiveWallpaperState {
    val isNightMode: Boolean

    data class Fallback(
        override val isNightMode: Boolean
    ) : ActiveWallpaperState

    data class StaticBitmap(
        val filePath: String,
        override val isNightMode: Boolean,
        val mode: WallpaperMode,
        val sourceId: String
    ) : ActiveWallpaperState
}