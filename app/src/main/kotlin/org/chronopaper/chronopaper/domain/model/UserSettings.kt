package org.chronopaper.chronopaper.domain.model

data class GeoCoordinate(
    val latitude: Double,
    val longitude: Double
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) {
            "Latitude must be in -90..90, got $latitude"
        }
        require(longitude.isFinite() && longitude in -180.0..180.0) {
            "Longitude must be in -180..180, got $longitude"
        }
    }
}

data class UserSettings(
    val mode: WallpaperMode = WallpaperMode.THEME,
    val activePairId: String? = null,
    val activeHeicPackId: String? = null,
    val autoRotateEnabled: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val rotationIntervalHours: Int = 24,
    val location: GeoCoordinate = DEFAULT_LOCATION
) {
    val isPeriodicRotationActive: Boolean
        get() = autoRotateEnabled

    companion object {
        val DEFAULT_LOCATION = GeoCoordinate(latitude = 55.7558, longitude = 37.6173)
    }
}