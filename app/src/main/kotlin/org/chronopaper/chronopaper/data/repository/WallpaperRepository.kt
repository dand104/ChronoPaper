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

    override suspend fun createManualSet(
        title: String,
        imageUris: List<Uri>,
        mode: WallpaperMode,
        targetWidth: Int,
        targetHeight: Int,
        activateImmediately: Boolean
    ): PairedWallpaperSet {
        require(imageUris.isNotEmpty()) {
            "Select at least one image"
        }
        val pairId = UUID.randomUUID().toString()
        val files = storageManager.copyAndOptimizeImages(
            sourceUris = imageUris,
            pairId = pairId,
            targetWidth = targetWidth,
            targetHeight = targetHeight
        )
        val paths = files.map { it.absolutePath }
        val resolvedMode = when {
            paths.size == 1 -> WallpaperMode.THEME
            paths.size > 2 && mode == WallpaperMode.THEME -> WallpaperMode.SOLAR
            else -> mode
        }

        val pairedSet = PairedWallpaperSet(
            id = pairId,
            title = title,
            imagePaths = paths,
            mode = resolvedMode
        )
        dataStore.upsertPairedSet(pairedSet)

        if (activateImmediately) {
            dataStore.updateSettings { current ->
                current.copy(
                    mode = resolvedMode,
                    activePairId = pairId,
                    activeHeicPackId = null
                )
            }
        }
        return pairedSet
    }

    override suspend fun updateManualSet(
        pairId: String,
        title: String,
        sources: List<String>,
        mode: WallpaperMode,
        targetWidth: Int,
        targetHeight: Int
    ) {
        require(sources.isNotEmpty()) { "Select at least one image" }
        val existing = pairedSets.value.firstOrNull { it.id == pairId } ?: return
        val resolvedPaths = mutableListOf<String>()
        val newlyCreatedFiles = mutableListOf<String>()

        try {
            for (src in sources) {
                if (!src.startsWith("content://") && File(src).exists()) {
                    resolvedPaths.add(src)
                } else {
                    val saved = storageManager.copyAndOptimizeImages(
                        sourceUris = listOf(Uri.parse(src)),
                        pairId = "${pairId}_${UUID.randomUUID().toString().take(6)}",
                        targetWidth = targetWidth,
                        targetHeight = targetHeight
                    ).first().absolutePath
                    newlyCreatedFiles.add(saved)
                    resolvedPaths.add(saved)
                }
            }
        } catch (t: Throwable) {
            storageManager.deleteSetFiles(newlyCreatedFiles)
            throw t
        }

        val removedOldFiles = existing.imagePaths.filterNot { it in resolvedPaths }
        if (removedOldFiles.isNotEmpty()) {
            storageManager.deleteSetFiles(removedOldFiles)
        }

        val resolvedMode = when {
            resolvedPaths.size == 1 -> WallpaperMode.THEME
            resolvedPaths.size > 2 && mode == WallpaperMode.THEME -> WallpaperMode.SOLAR
            else -> mode
        }

        val updatedSet = existing.copy(
            title = title.trim().ifEmpty { existing.title },
            imagePaths = resolvedPaths,
            mode = resolvedMode
        )
        dataStore.upsertPairedSet(updatedSet)

        if (settings.value.activePairId == pairId) {
            dataStore.updateSettings { it.copy(mode = resolvedMode) }
        }
    }

    override suspend fun updateHeicPack(
        packId: String,
        title: String,
        mode: WallpaperMode,
        swapLightDark: Boolean
    ) {
        val existing = heicPacks.value.firstOrNull { it.id == packId } ?: return
        val resolvedMode = if (mode in existing.allowedModes) mode else existing.mode
        val currentLight = existing.lightDefaultIndex ?: existing.frames.firstOrNull { it.isLightDefault }?.frameIndex ?: 0
        val currentDark = existing.darkDefaultIndex ?: existing.frames.firstOrNull { it.isDarkDefault }?.frameIndex ?: existing.frames.lastIndex.coerceAtLeast(0)
        val newLight = if (swapLightDark) currentDark else currentLight
        val newDark = if (swapLightDark) currentLight else currentDark
        val updatedFrames = if (swapLightDark) {
            existing.frames.map { frame ->
                frame.copy(
                    isLightDefault = frame.frameIndex == newLight,
                    isDarkDefault = frame.frameIndex == newDark
                )
            }
        } else {
            existing.frames
        }

        val updatedPack = existing.copy(
            title = title.trim().ifEmpty { existing.title },
            mode = resolvedMode,
            frames = updatedFrames,
            lightDefaultIndex = newLight,
            darkDefaultIndex = newDark
        )
        dataStore.upsertHeicPack(updatedPack)

        if (settings.value.activeHeicPackId == packId) {
            dataStore.updateSettings { it.copy(mode = resolvedMode) }
        }
    }

    override suspend fun deletePairedSet(pairId: String) {
        val existing = pairedSets.value.firstOrNull { it.id == pairId } ?: return
        storageManager.deleteSetFiles(existing.imagePaths)
        dataStore.removePairedSet(pairId)

        if (settings.value.activePairId == pairId) {
            val nextPair = pairedSets.value.firstOrNull()
            val fallbackHeic = heicPacks.value.firstOrNull()
            dataStore.updateSettings { current ->
                when {
                    nextPair != null -> current.copy(
                        activePairId = nextPair.id,
                        activeHeicPackId = null,
                        mode = nextPair.mode
                    )
                    fallbackHeic != null -> current.copy(
                        activePairId = null,
                        activeHeicPackId = fallbackHeic.id,
                        mode = fallbackHeic.mode
                    )
                    else -> current.copy(
                        activePairId = null,
                        activeHeicPackId = null
                    )
                }
            }
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
        val layout = HeicFrameExtractor.inspectContainer(heicBytes)

        val metadata = HeicXmpExtractor.extractMetadata(
            heicBytes = heicBytes,
            fallbackFrameCount = layout.masterItemIds.size.coerceAtLeast(1)
        )

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
                        azimuth = if (index == metadata.darkIndex) 0.0 else 180.0,
                        altitude = if (index == metadata.darkIndex) -45.0 else 45.0,
                        filePath = file.absolutePath,
                        isLightDefault = index == metadata.lightIndex,
                        isDarkDefault = index == metadata.darkIndex
                    )
                }
            }

            val pack = DynamicHeicPack(
                id = packId,
                title = title,
                mode = metadata.mode,
                frames = frames,
                lightDefaultIndex = metadata.lightIndex,
                darkDefaultIndex = metadata.darkIndex
            )
            dataStore.upsertHeicPack(pack)

            if (activateImmediately) {
                dataStore.updateSettings { current ->
                    current.copy(
                        mode = metadata.mode,
                        activeHeicPackId = packId,
                        activePairId = null
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
            val nextHeic = heicPacks.value.firstOrNull()
            val fallbackPair = pairedSets.value.firstOrNull()
            dataStore.updateSettings { current ->
                when {
                    nextHeic != null -> current.copy(
                        activeHeicPackId = nextHeic.id,
                        activePairId = null,
                        mode = nextHeic.mode
                    )
                    fallbackPair != null -> current.copy(
                        activeHeicPackId = null,
                        activePairId = fallbackPair.id,
                        mode = fallbackPair.mode
                    )
                    else -> current.copy(
                        activeHeicPackId = null,
                        activePairId = null
                    )
                }
            }
        }
    }

    override suspend fun setActiveMode(mode: WallpaperMode) {
        val current = settings.value
        val activePair = pairedSets.value.firstOrNull { it.id == current.activePairId }
        val activeHeic = heicPacks.value.firstOrNull { it.id == current.activeHeicPackId }

        if (activePair != null && mode in activePair.allowedModes) {
            dataStore.upsertPairedSet(activePair.copy(mode = mode))
        } else if (activeHeic != null && mode in activeHeic.allowedModes) {
            dataStore.upsertHeicPack(activeHeic.copy(mode = mode))
        }

        dataStore.updateSettings { it.copy(mode = mode) }
    }

    override suspend fun setActivePair(pairId: String, mode: WallpaperMode?) {
        val existing = pairedSets.value.firstOrNull { it.id == pairId } ?: return
        val resolvedMode = if (mode != null && mode in existing.allowedModes) {
            mode
        } else {
            existing.mode
        }
        if (resolvedMode != existing.mode) {
            dataStore.upsertPairedSet(existing.copy(mode = resolvedMode))
        }
        dataStore.updateSettings { current ->
            current.copy(
                activePairId = pairId,
                activeHeicPackId = null,
                mode = resolvedMode
            )
        }
    }

    override suspend fun setActiveHeicPack(packId: String, mode: WallpaperMode?) {
        val existing = heicPacks.value.firstOrNull { it.id == packId } ?: return
        val resolvedMode = if (mode != null && mode in existing.allowedModes) {
            mode
        } else {
            existing.mode
        }
        if (resolvedMode != existing.mode) {
            dataStore.upsertHeicPack(existing.copy(mode = resolvedMode))
        }
        dataStore.updateSettings { current ->
            current.copy(
                activeHeicPackId = packId,
                activePairId = null,
                mode = resolvedMode
            )
        }
    }

    override suspend fun setAutoRotateEnabled(enabled: Boolean) {
        dataStore.updateSettings { it.copy(autoRotateEnabled = enabled) }
    }

    override suspend fun updateRotationIntervalHours(hours: Int) {
        dataStore.updateSettings { it.copy(rotationIntervalHours = hours.coerceIn(1, 168)) }
    }

    override suspend fun updateLocation(location: GeoCoordinate) {
        dataStore.updateSettings { it.copy(location = location) }
    }
}