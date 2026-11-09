package org.chronopaper.chronopaper.domain.model

data class PairedWallpaperSet(
    val id: String,
    val title: String,
    val lightPath: String,
    val darkPath: String,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)