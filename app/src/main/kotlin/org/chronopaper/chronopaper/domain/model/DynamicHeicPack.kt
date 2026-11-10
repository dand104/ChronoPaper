package org.chronopaper.chronopaper.domain.model

data class DynamicHeicPack(
    val id: String,
    val title: String,
    val mode: WallpaperMode,
    val frames: List<SolarFrame>,
    val lightDefaultIndex: Int? = null,
    val darkDefaultIndex: Int? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
) {
    val frameCount: Int
        get() = frames.size

    val allowedModes: List<WallpaperMode>
        get() = buildList {
            if (frameCount in 1..2 || lightDefaultIndex != null || darkDefaultIndex != null) {
                add(WallpaperMode.THEME)
            }
            if (frameCount >= 2) {
                add(WallpaperMode.SOLAR)
                add(WallpaperMode.TIME)
            }
        }.ifEmpty { listOf(WallpaperMode.THEME) }
}