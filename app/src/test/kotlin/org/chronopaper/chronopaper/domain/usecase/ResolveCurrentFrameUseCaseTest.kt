package org.chronopaper.chronopaper.domain.usecase

import org.chronopaper.chronopaper.domain.model.ActiveWallpaperState
import org.chronopaper.chronopaper.domain.model.DynamicHeicPack
import org.chronopaper.chronopaper.domain.model.GeoCoordinate
import org.chronopaper.chronopaper.domain.model.PairedWallpaperSet
import org.chronopaper.chronopaper.domain.model.SolarFrame
import org.chronopaper.chronopaper.domain.model.UserSettings
import org.chronopaper.chronopaper.domain.model.WallpaperMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class ResolveCurrentFrameUseCaseTest {

    private lateinit var useCase: ResolveCurrentFrameUseCase

    @Before
    fun setUp() {
        useCase = ResolveCurrentFrameUseCase()
    }

    @Test
    fun `THEME mode returns Fallback when collections are empty`() {
        val settings = UserSettings(mode = WallpaperMode.THEME)

        val result = useCase(
            settings = settings,
            pairedSets = emptyList(),
            heicPacks = emptyList(),
            isNightMode = false
        )

        assertTrue(result is ActiveWallpaperState.Fallback)
        assertEquals(false, result.isNightMode)
    }

    @Test
    fun `THEME mode switches light and dark paths based on isNightMode`() {
        val pair = PairedWallpaperSet(
            id = "pair_1",
            title = "Mojave",
            lightPath = "/cache/light.webp",
            darkPath = "/cache/dark.webp"
        )
        val settings = UserSettings(
            mode = WallpaperMode.THEME,
            activePairId = "pair_1"
        )

        val lightResult = useCase(
            settings = settings,
            pairedSets = listOf(pair),
            heicPacks = emptyList(),
            isNightMode = false
        )
        val darkResult = useCase(
            settings = settings,
            pairedSets = listOf(pair),
            heicPacks = emptyList(),
            isNightMode = true
        )

        assertEquals("/cache/light.webp", (lightResult as ActiveWallpaperState.StaticBitmap).filePath)
        assertEquals("/cache/dark.webp", (darkResult as ActiveWallpaperState.StaticBitmap).filePath)
    }

    @Test
    fun `HEIC pack in THEME mode selects lightDefaultIndex and darkDefaultIndex`() {
        val frames = listOf(
            SolarFrame(frameIndex = 0, azimuth = 180.0, altitude = 40.0, filePath = "/f0.webp"),
            SolarFrame(frameIndex = 1, azimuth = 0.0, altitude = -30.0, filePath = "/f1.webp")
        )
        val pack = DynamicHeicPack(
            id = "heic_1",
            title = "Catalina",
            mode = WallpaperMode.THEME,
            frames = frames,
            lightDefaultIndex = 0,
            darkDefaultIndex = 1
        )
        val settings = UserSettings(
            mode = WallpaperMode.THEME,
            activeHeicPackId = "heic_1"
        )

        val dayResult = useCase(settings, emptyList(), listOf(pack), isNightMode = false)
        val nightResult = useCase(settings, emptyList(), listOf(pack), isNightMode = true)

        assertEquals("/f0.webp", (dayResult as ActiveWallpaperState.StaticBitmap).filePath)
        assertEquals("/f1.webp", (nightResult as ActiveWallpaperState.StaticBitmap).filePath)
    }

    @Test
    fun `SOLAR mode chooses the frame with closest angular distance to the sun`() {
        val epochMillis = Instant.parse("2026-03-20T12:00:00Z").toEpochMilli()
        val loc = GeoCoordinate(0.0, 0.0)

        val noonFrame = SolarFrame(frameIndex = 1, azimuth = 180.0, altitude = 89.0, filePath = "/noon.webp")
        val sunsetFrame = SolarFrame(frameIndex = 2, azimuth = 270.0, altitude = 5.0, filePath = "/sunset.webp")
        val nightFrame = SolarFrame(frameIndex = 3, azimuth = 0.0, altitude = -60.0, filePath = "/night.webp")

        val pack = DynamicHeicPack(
            id = "solar_pack",
            title = "Solar Earth",
            mode = WallpaperMode.SOLAR,
            frames = listOf(sunsetFrame, noonFrame, nightFrame),
            lightDefaultIndex = 1,
            darkDefaultIndex = 3
        )
        val settings = UserSettings(
            mode = WallpaperMode.SOLAR,
            activeHeicPackId = "solar_pack",
            location = loc
        )

        val result = useCase(
            settings = settings,
            pairedSets = emptyList(),
            heicPacks = listOf(pack),
            isNightMode = false,
            epochMillis = epochMillis
        )

        assertEquals("/noon.webp", (result as ActiveWallpaperState.StaticBitmap).filePath)
    }
}