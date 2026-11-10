package org.chronopaper.chronopaper.domain.usecase

import org.chronopaper.chronopaper.domain.astronomy.SunCalc
import org.chronopaper.chronopaper.domain.model.ActiveWallpaperState
import org.chronopaper.chronopaper.domain.model.DynamicHeicPack
import org.chronopaper.chronopaper.domain.model.PairedWallpaperSet
import org.chronopaper.chronopaper.domain.model.SolarFrame
import org.chronopaper.chronopaper.domain.model.UserSettings
import org.chronopaper.chronopaper.domain.model.WallpaperMode
import java.util.Calendar

class ResolveCurrentFrameUseCase {

    operator fun invoke(
        settings: UserSettings,
        pairedSets: List<PairedWallpaperSet>,
        heicPacks: List<DynamicHeicPack>,
        isNightMode: Boolean,
        epochMillis: Long = System.currentTimeMillis()
    ): ActiveWallpaperState {
        val activeHeic = settings.activeHeicPackId?.let { id ->
            heicPacks.firstOrNull { it.id == id }
        }
        val activePair = settings.activePairId?.let { id ->
            pairedSets.firstOrNull { it.id == id }
        }

        return when {
            activeHeic != null -> resolvePack(activeHeic, settings, isNightMode, epochMillis)
            activePair != null -> resolvePair(activePair, settings, isNightMode, epochMillis)
            pairedSets.isNotEmpty() -> resolvePair(pairedSets.first(), settings, isNightMode, epochMillis)
            heicPacks.isNotEmpty() -> resolvePack(heicPacks.first(), settings, isNightMode, epochMillis)
            else -> ActiveWallpaperState.Fallback(isNightMode)
        }
    }

    private fun resolvePair(
        pair: PairedWallpaperSet,
        settings: UserSettings,
        isNightMode: Boolean,
        epochMillis: Long
    ): ActiveWallpaperState = resolveFrames(
        sourceId = pair.id,
        frames = pair.toSolarFrames(),
        allowedModes = pair.allowedModes,
        defaultMode = pair.mode,
        lightDefaultIndex = 0,
        darkDefaultIndex = (pair.frameCount - 1).coerceAtLeast(0),
        settings = settings,
        isNightMode = isNightMode,
        epochMillis = epochMillis
    )

    private fun resolvePack(
        pack: DynamicHeicPack,
        settings: UserSettings,
        isNightMode: Boolean,
        epochMillis: Long
    ): ActiveWallpaperState = resolveFrames(
        sourceId = pack.id,
        frames = pack.frames,
        allowedModes = pack.allowedModes,
        defaultMode = pack.mode,
        lightDefaultIndex = pack.lightDefaultIndex,
        darkDefaultIndex = pack.darkDefaultIndex,
        settings = settings,
        isNightMode = isNightMode,
        epochMillis = epochMillis
    )

    private fun resolveFrames(
        sourceId: String,
        frames: List<SolarFrame>,
        allowedModes: List<WallpaperMode>,
        defaultMode: WallpaperMode,
        lightDefaultIndex: Int?,
        darkDefaultIndex: Int?,
        settings: UserSettings,
        isNightMode: Boolean,
        epochMillis: Long
    ): ActiveWallpaperState {
        if (frames.isEmpty()) return ActiveWallpaperState.Fallback(isNightMode)

        val effectiveMode = if (settings.mode in allowedModes) settings.mode else defaultMode
        val selectedFrame = when (effectiveMode) {
            WallpaperMode.THEME -> selectThemeFrame(frames, lightDefaultIndex, darkDefaultIndex, isNightMode)
            WallpaperMode.SOLAR -> selectSolarFrame(frames, lightDefaultIndex, darkDefaultIndex, settings, isNightMode, epochMillis)
            WallpaperMode.TIME -> selectTimeFrame(frames, epochMillis)
        }

        return ActiveWallpaperState.StaticBitmap(
            filePath = selectedFrame.filePath,
            isNightMode = isNightMode,
            mode = effectiveMode,
            sourceId = sourceId
        )
    }

    private fun selectThemeFrame(
        frames: List<SolarFrame>,
        lightDefaultIndex: Int?,
        darkDefaultIndex: Int?,
        isNightMode: Boolean
    ): SolarFrame {
        val preferredIndex = if (isNightMode) darkDefaultIndex else lightDefaultIndex
        if (preferredIndex != null) {
            frames.firstOrNull { it.frameIndex == preferredIndex }?.let { return it }
        }
        return if (isNightMode) {
            frames.firstOrNull { it.isDarkDefault } ?: frames.last()
        } else {
            frames.firstOrNull { it.isLightDefault } ?: frames.first()
        }
    }

    private fun selectSolarFrame(
        frames: List<SolarFrame>,
        lightDefaultIndex: Int?,
        darkDefaultIndex: Int?,
        settings: UserSettings,
        isNightMode: Boolean,
        epochMillis: Long
    ): SolarFrame {
        if (frames.size == 1) return frames.first()
        val sunPos = SunCalc.calculatePosition(
            epochMillis = epochMillis,
            latitudeDegrees = settings.location.latitude,
            longitudeDegrees = settings.location.longitude
        )
        if (frames.size == 2) {
            return selectThemeFrame(
                frames = frames,
                lightDefaultIndex = lightDefaultIndex,
                darkDefaultIndex = darkDefaultIndex,
                isNightMode = sunPos.altitudeDegrees < 0.0
            )
        }
        return frames.minByOrNull { frame ->
            SunCalc.angularDistanceDegrees(
                alt1Deg = sunPos.altitudeDegrees,
                az1Deg = sunPos.azimuthDegrees,
                alt2Deg = frame.altitude,
                az2Deg = frame.azimuth
            )
        } ?: selectThemeFrame(frames, lightDefaultIndex, darkDefaultIndex, isNightMode)
    }

    private fun selectTimeFrame(
        frames: List<SolarFrame>,
        epochMillis: Long
    ): SolarFrame {
        if (frames.size == 1) return frames.first()
        val calendar = Calendar.getInstance().apply { timeInMillis = epochMillis }
        val minutesOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        return if (frames.size == 2) {
            if (minutesOfDay in 360..<1080) frames.first() else frames.last()
        } else {
            val index = ((minutesOfDay / 1440.0) * frames.size).toInt().coerceIn(0, frames.lastIndex)
            frames[index]
        }
    }
}