package org.chronopaper.chronopaper.domain.model

import kotlin.math.PI
import kotlin.math.cos

data class PairedWallpaperSet(
    val id: String,
    val title: String,
    val imagePaths: List<String>,
    val imageCrops: List<PhotoCrop> = emptyList(),
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
        createdAtEpochMillis = createdAtEpochMillis
    )

    val frameCount: Int
        get() = imagePaths.size

    fun cropForIndex(index: Int): PhotoCrop =
        imageCrops.getOrNull(index) ?: PhotoCrop.DEFAULT

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
                    isDarkDefault = true,
                    crop = cropForIndex(0)
                )
            )

            2 -> listOf(
                SolarFrame(
                    frameIndex = 0,
                    azimuth = 180.0,
                    altitude = 45.0,
                    filePath = imagePaths[0],
                    isLightDefault = true,
                    isDarkDefault = false,
                    crop = cropForIndex(0)
                ),
                SolarFrame(
                    frameIndex = 1,
                    azimuth = 0.0,
                    altitude = -45.0,
                    filePath = imagePaths[1],
                    isLightDefault = false,
                    isDarkDefault = true,
                    crop = cropForIndex(1)
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
                    isDarkDefault = index == 0,
                    crop = cropForIndex(index)
                )
            }
        }
    }

    fun toSolarFrames(): List<SolarFrame> = solarFrames
}