package org.chronopaper.chronopaper.domain.model

data class SolarFrame(
    val frameIndex: Int,
    val azimuth: Double,
    val altitude: Double,
    val filePath: String,
    val isLightDefault: Boolean = false,
    val isDarkDefault: Boolean = false
)
