package org.chronopaper.chronopaper.data.repository

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.chronopaper.chronopaper.data.local.PreferencesDataStore
import org.chronopaper.chronopaper.data.local.StorageManager
import org.chronopaper.chronopaper.data.parser.HeicFrameExtractor
import org.chronopaper.chronopaper.data.parser.HeicXmpExtractor
import org.chronopaper.chronopaper.domain.model.DynamicHeicPack
import org.chronopaper.chronopaper.domain.model.GeoCoordinate
import org.chronopaper.chronopaper.domain.model.HeicScheduleType
import org.chronopaper.chronopaper.domain.model.PairedWallpaperSet
import org.chronopaper.chronopaper.domain.model.SolarFrame
import org.chronopaper.chronopaper.domain.model.UserSettings
import org.chronopaper.chronopaper.domain.model.WallpaperMode
import org.chronopaper.chronopaper.domain.repository.WallpaperRepository
import java.io.File
import java.util.UUID

class DefaultWallpaperRepository(
    private val dataStore: PreferencesDataStore,
    private val storageManager: StorageManager
) : WallpaperRepository {

    override val settings: StateFlow<UserSettings> = dataStore.settings
    override val pairedSets: StateFlow<List<PairedWallpaperSet>> = dataStore.pairedSets
    override val heicPacks: StateFlow<List<DynamicHeicPack>> = dataStore.heicPacks

    override suspend fun createPairedSet(
        title: String,
        lightUri: Uri,
        darkUri: Uri,
        targetWidth: Int,
        targetHeight: Int,
        activateImmediately: Boolean
    ): PairedWallpaperSet {
        val pairId = UUID.randomUUID().toString()
        val lightFile = storageManager.copyAndOptimizeImage(
            sourceUri = lightUri,
            pairId = pairId,
            isDark = false,
            targetWidth = targetWidth,
            targetHeight = targetHeight
        )
        val darkFile = try {
            storageManager.copyAndOptimizeImage(
                sourceUri = darkUri,
                pairId = pairId,
                isDark = true,
                targetWidth = targetWidth,
                targetHeight = targetHeight
            )
        } catch (t: Throwable) {
            lightFile.delete()
            throw t
        }

        val pairedSet = PairedWallpaperSet(
            id = pairId,
            title = title,
            lightPath = lightFile.absolutePath,
            darkPath = darkFile.absolutePath
        )
        dataStore.upsertPairedSet(pairedSet)

        if (activateImmediately) {
            dataStore.updateSettings { current ->
                current.copy(
                    mode = if (current.mode == WallpaperMode.PLAYLIST) {
                        WallpaperMode.PLAYLIST
                    } else {
                        WallpaperMode.STATIC_PAIR
                    },
                    activePairId = pairId
                )
            }
        }
        return pairedSet
    }

    override suspend fun deletePairedSet(pairId: String) {
        val existing = pairedSets.value.firstOrNull { it.id == pairId } ?: return
        storageManager.deletePairFiles(existing.lightPath, existing.darkPath)
        dataStore.removePairedSet(pairId)

        if (settings.value.activePairId == pairId) {
            val nextFallbackId = pairedSets.value.firstOrNull()?.id
            dataStore.updateSettings { it.copy(activePairId = nextFallbackId) }
        }
    }

    override suspend fun importDynamicHeic(
        sourceUri: Uri,
        title: String,
        targetWidth: Int,
        targetHeight: Int,
        activateImmediately: Boolean
    ): DynamicHeicPack = withContext(Dispatchers.IO) {
        val heicBytes = storageManager.readUriBytes(sourceUri)
        val metadata = HeicXmpExtractor.extractMetadata(heicBytes)
        val layout = HeicFrameExtractor.inspectContainer(heicBytes)

        val packId = UUID.randomUUID().toString()
        val packDir = storageManager.getHeicPackDirectory(packId)

        try {
            val requiredIndices = buildSet {
                metadata.solarEntries.forEach { add(it.frameIndex) }
                metadata.lightIndex?.let { add(it) }
                metadata.darkIndex?.let { add(it) }
            }
            require(requiredIndices.isNotEmpty()) {
                "HEIC metadata contains no frame indices to extract"
            }

            val extractedFiles = HashMap<Int, File>(requiredIndices.size)
            for (frameIndex in requiredIndices) {
                val outFile = File(packDir, "frame_$frameIndex.webp")
                HeicFrameExtractor.extractFrameToWebp(
                    heicBytes = heicBytes,
                    layout = layout,
                    frameIndex = frameIndex,
                    targetWidth = targetWidth,
                    targetHeight = targetHeight,
                    outputFile = outFile
                )
                extractedFiles[frameIndex] = outFile
            }

            val frames = if (metadata.solarEntries.isNotEmpty()) {
                metadata.solarEntries.mapNotNull { entry ->
                    val file = extractedFiles[entry.frameIndex] ?: return@mapNotNull null
                    SolarFrame(
                        frameIndex = entry.frameIndex,
                        azimuth = entry.azimuth,
                        altitude = entry.altitude,
                        filePath = file.absolutePath,
                        isLightDefault = entry.frameIndex == metadata.lightIndex,
                        isDarkDefault = entry.frameIndex == metadata.darkIndex
                    )
                }
            } else {
                requiredIndices.mapNotNull { index ->
                    val file = extractedFiles[index] ?: return@mapNotNull null
                    SolarFrame(
                        frameIndex = index,
                        azimuth = 180.0,
                        altitude = if (index == metadata.darkIndex) -30.0 else 45.0,
                        filePath = file.absolutePath,
                        isLightDefault = index == metadata.lightIndex,
                        isDarkDefault = index == metadata.darkIndex
                    )
                }
            }

            val pack = DynamicHeicPack(
                id = packId,
                title = title,
                scheduleType = metadata.scheduleType,
                frames = frames,
                lightDefaultIndex = metadata.lightIndex,
                darkDefaultIndex = metadata.darkIndex
            )
            dataStore.upsertHeicPack(pack)

            if (activateImmediately) {
                val targetMode = when (metadata.scheduleType) {
                    HeicScheduleType.SOLAR -> WallpaperMode.APPLE_SOLAR
                    HeicScheduleType.APPEARANCE -> WallpaperMode.APPLE_APR
                }
                dataStore.updateSettings { current ->
                    current.copy(
                        mode = targetMode,
                        activeHeicPackId = packId
                    )
                }
            }
            pack
        } catch (t: Throwable) {
            storageManager.deleteHeicPackFiles(packId)
            throw t
        }
    }

    override suspend fun deleteHeicPack(packId: String) {
        storageManager.deleteHeicPackFiles(packId)
        dataStore.removeHeicPack(packId)

        if (settings.value.activeHeicPackId == packId) {
            val nextFallbackId = heicPacks.value.firstOrNull()?.id
            dataStore.updateSettings { it.copy(activeHeicPackId = nextFallbackId) }
        }
    }

    override suspend fun setActiveMode(mode: WallpaperMode) {
        dataStore.updateSettings { current ->
            current.copy(
                mode = mode,
                autoRotateEnabled = if (mode == WallpaperMode.PLAYLIST) true else current.autoRotateEnabled
            )
        }
    }

    override suspend fun setActivePair(pairId: String) {
        dataStore.updateSettings { it.copy(activePairId = pairId) }
    }

    override suspend fun setActiveHeicPack(packId: String, mode: WallpaperMode) {
        dataStore.updateSettings { it.copy(activeHeicPackId = packId, mode = mode) }
    }

    override suspend fun setAutoRotateEnabled(enabled: Boolean) {
        dataStore.updateSettings { current ->
            val adjustedMode = when {
                enabled && current.mode == WallpaperMode.STATIC_PAIR -> WallpaperMode.PLAYLIST
                !enabled && current.mode == WallpaperMode.PLAYLIST -> WallpaperMode.STATIC_PAIR
                else -> current.mode
            }
            current.copy(
                mode = adjustedMode,
                autoRotateEnabled = enabled
            )
        }
    }

    override suspend fun updateRotationIntervalHours(hours: Int) {
        dataStore.updateSettings { it.copy(rotationIntervalHours = hours.coerceIn(1, 168)) }
    }

    override suspend fun updateLocation(location: GeoCoordinate) {
        dataStore.updateSettings { it.copy(location = location) }
    }
}