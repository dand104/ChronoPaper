package org.chronopaper.chronopaper.domain.usecase

import org.chronopaper.chronopaper.domain.model.HeicScheduleType
import org.chronopaper.chronopaper.domain.model.WallpaperMode
import org.chronopaper.chronopaper.domain.repository.WallpaperRepository

class RotatePlaylistUseCase(
    private val repository: WallpaperRepository
) {
    suspend operator fun invoke(): String? {
        val currentSettings = repository.settings.value

        return when (currentSettings.mode) {
            WallpaperMode.STATIC_PAIR,
            WallpaperMode.PLAYLIST -> rotatePairedSets(currentSettings.activePairId)

            WallpaperMode.APPLE_SOLAR,
            WallpaperMode.APPLE_APR -> rotateHeicPacks(currentSettings.activeHeicPackId)
        }
    }

    private suspend fun rotatePairedSets(currentId: String?): String? {
        val pairs = repository.pairedSets.value
        if (pairs.isEmpty()) return null

        val currentIndex = pairs.indexOfFirst { it.id == currentId }
        val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % pairs.size
        val nextPairId = pairs[nextIndex].id

        repository.setActivePair(nextPairId)
        return nextPairId
    }

    private suspend fun rotateHeicPacks(currentPackId: String?): String? {
        val packs = repository.heicPacks.value
        if (packs.isEmpty()) return null

        val currentIndex = packs.indexOfFirst { it.id == currentPackId }
        val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % packs.size
        val nextPack = packs[nextIndex]
        val nextMode = when (nextPack.scheduleType) {
            HeicScheduleType.SOLAR -> WallpaperMode.APPLE_SOLAR
            HeicScheduleType.APPEARANCE -> WallpaperMode.APPLE_APR
        }

        repository.setActiveHeicPack(nextPack.id, nextMode)
        return nextPack.id
    }
}