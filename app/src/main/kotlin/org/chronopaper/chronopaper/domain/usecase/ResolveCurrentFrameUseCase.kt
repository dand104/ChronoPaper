package org.chronopaper.chronopaper.domain.usecase

import org.chronopaper.chronopaper.domain.astronomy.SunCalc
import org.chronopaper.chronopaper.domain.model.ActiveWallpaperState
import org.chronopaper.chronopaper.domain.model.DynamicHeicPack
import org.chronopaper.chronopaper.domain.model.PairedWallpaperSet
import org.chronopaper.chronopaper.domain.model.SolarFrame
import org.chronopaper.chronopaper.domain.model.UserSettings
import org.chronopaper.chronopaper.domain.model.WallpaperMode

class ResolveCurrentFrameUseCase {

    operator fun invoke(
        settings: UserSettings,
        pairedSets: List<PairedWallpaperSet>,
        heicPacks: List<DynamicHeicPack>,
        isNightMode: Boolean,
        epochMillis: Long = System.currentTimeMillis()
    ): ActiveWallpaperState {
        return when (settings.mode) {
            WallpaperMode.STATIC_PAIR,
            WallpaperMode.PLAYLIST -> resolvePairedSet(
                settings = settings,
                pairedSets = pairedSets,
                isNightMode = isNightMode
            )

            WallpaperMode.APPLE_APR -> resolveHeicAppearance(
                settings = settings,
                heicPacks = heicPacks,
                isNightMode = isNightMode
            )

            WallpaperMode.APPLE_SOLAR -> resolveHeicSolar(
                settings = settings,
                heicPacks = heicPacks,
                isNightMode = isNightMode,
                epochMillis = epochMillis
            )
        }
    }

    private fun resolvePairedSet(
        settings: UserSettings,
        pairedSets: List<PairedWallpaperSet>,
        isNightMode: Boolean
    ): ActiveWallpaperState {
        val targetSet = pairedSets.firstOrNull { it.id == settings.activePairId }
            ?: pairedSets.firstOrNull()
            ?: return ActiveWallpaperState.Fallback(isNightMode)

        val path = if (isNightMode) targetSet.darkPath else targetSet.lightPath
        return ActiveWallpaperState.StaticBitmap(
            filePath = path,
            isNightMode = isNightMode,
            mode = settings.mode,
            sourceId = targetSet.id
        )
    }

    private fun resolveHeicAppearance(
        settings: UserSettings,
        heicPacks: List<DynamicHeicPack>,
        isNightMode: Boolean
    ): ActiveWallpaperState {
        val pack = heicPacks.firstOrNull { it.id == settings.activeHeicPackId }
            ?: heicPacks.firstOrNull()
            ?: return ActiveWallpaperState.Fallback(isNightMode)

        val frame = selectAppearanceFrame(pack, isNightMode)
            ?: return ActiveWallpaperState.Fallback(isNightMode)

        return ActiveWallpaperState.StaticBitmap(
            filePath = frame.filePath,
            isNightMode = isNightMode,
            mode = WallpaperMode.APPLE_APR,
            sourceId = pack.id
        )
    }

    private fun resolveHeicSolar(
        settings: UserSettings,
        heicPacks: List<DynamicHeicPack>,
        isNightMode: Boolean,
        epochMillis: Long
    ): ActiveWallpaperState {
        val pack = heicPacks.firstOrNull { it.id == settings.activeHeicPackId }
            ?: heicPacks.firstOrNull()
            ?: return ActiveWallpaperState.Fallback(isNightMode)

        val sunPos = SunCalc.calculatePosition(
            epochMillis = epochMillis,
            latitudeDegrees = settings.location.latitude,
            longitudeDegrees = settings.location.longitude
        )

        val bestFrame = pack.frames.minByOrNull { frame ->
            SunCalc.angularDistanceDegrees(
                alt1Deg = sunPos.altitudeDegrees,
                az1Deg = sunPos.azimuthDegrees,
                alt2Deg = frame.altitude,
                az2Deg = frame.azimuth
            )
        } ?: selectAppearanceFrame(pack, isNightMode)
        ?: return ActiveWallpaperState.Fallback(isNightMode)

        return ActiveWallpaperState.StaticBitmap(
            filePath = bestFrame.filePath,
            isNightMode = isNightMode,
            mode = WallpaperMode.APPLE_SOLAR,
            sourceId = pack.id
        )
    }

    private fun selectAppearanceFrame(
        pack: DynamicHeicPack,
        isNightMode: Boolean
    ): SolarFrame? {
        if (pack.frames.isEmpty()) return null
        val preferredIndex = if (isNightMode) pack.darkDefaultIndex else pack.lightDefaultIndex
        if (preferredIndex != null) {
            pack.frames.firstOrNull { it.frameIndex == preferredIndex }?.let { return it }
        }
        return if (isNightMode) {
            pack.frames.firstOrNull { it.isDarkDefault } ?: pack.frames.last()
        } else {
            pack.frames.firstOrNull { it.isLightDefault } ?: pack.frames.first()
        }
    }
}