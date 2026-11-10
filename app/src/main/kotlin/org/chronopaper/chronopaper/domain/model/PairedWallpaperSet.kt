package org.chronopaper.chronopaper.domain.model

import kotlin.math.PI
import kotlin.math.cos

data class PairedWallpaperSet(
    val id: String,
    val title: String,
    val imagePaths: List<String>,
    val mode: WallpaperMode = if (imagePaths.size > 2) WallpaperMode.SOLAR else WallpaperMode.THEME,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
) {
    constructor(
        id: String,
        title: String,
        lightPath: String,
        darkPath: String,
        createdAtEpochMillis: Long = System.currentTimeMillis()
    ) : this(
        id = id,
        title = title,
        imagePaths = listOfNotNull(
            lightPath.takeIf { it.isNotBlank() },
            darkPath.takeIf { it.isNotBlank() }
        ).distinct().ifEmpty { listOf(lightPath) },
        mode = WallpaperMode.THEME,
        createdAtEpochMillis = createdAtEpochMillis
    )

    val frameCount: Int
        get() = imagePaths.size

    val allowedModes: List<WallpaperMode>
        get() = buildList {
            if (frameCount in 1..2) add(WallpaperMode.THEME)
            if (frameCount >= 2) {
                add(WallpaperMode.SOLAR)
                add(WallpaperMode.TIME)
            }
        }.ifEmpty { listOf(WallpaperMode.THEME) }

    val solarFrames: List<SolarFrame> by lazy {
        when (imagePaths.size) {
            0 -> emptyList()
            1 -> listOf(
                SolarFrame(
                    frameIndex = 0,
                    azimuth = 180.0,
                    altitude = 45.0,
                    filePath = imagePaths[0],
                    isLightDefault = true,
                    isDarkDefault = true
                )
            )

            2 -> listOf(
                SolarFrame(
                    frameIndex = 0,
                    azimuth = 180.0,
                    altitude = 45.0,
                    filePath = imagePaths[0],
                    isLightDefault = true,
                    isDarkDefault = false
                ),
                SolarFrame(
                    frameIndex = 1,
                    azimuth = 0.0,
                    altitude = -45.0,
                    filePath = imagePaths[1],
                    isLightDefault = false,
                    isDarkDefault = true
                )
            )

            else -> imagePaths.mapIndexed { index, path ->
                val progress = index.toDouble() / imagePaths.size.toDouble()
                SolarFrame(
                    frameIndex = index,
                    azimuth = (progress * 360.0).mod(360.0),
                    altitude = -cos(progress * 2.0 * PI) * 60.0,
                    filePath = path,
                    isLightDefault = index == imagePaths.size / 2,
                    isDarkDefault = index == 0
                )
            }
        }
    }
    fun toSolarFrames(): List<SolarFrame> = solarFrames
}