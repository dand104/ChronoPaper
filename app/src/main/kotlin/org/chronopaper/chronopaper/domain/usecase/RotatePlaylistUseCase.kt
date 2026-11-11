package org.chronopaper.chronopaper.domain.usecase

import org.chronopaper.chronopaper.domain.repository.WallpaperRepository

class RotatePlaylistUseCase(
    private val repository: WallpaperRepository
) {
    suspend operator fun invoke(): String? {
        val pairs = repository.pairedSets.value.map { PlaylistEntry.Manual(it.id) }
        val packs = repository.heicPacks.value.map { PlaylistEntry.Heic(it.id) }
        val allEntries = pairs + packs
        if (allEntries.isEmpty()) return null

        val settings = repository.settings.value
        val activeId = settings.activeHeicPackId ?: settings.activePairId
        val currentIndex = allEntries.indexOfFirst { it.id == activeId }
        val nextEntry = allEntries[if (currentIndex == -1) 0 else (currentIndex + 1) % allEntries.size]

        when (nextEntry) {
            is PlaylistEntry.Manual -> repository.setActivePair(nextEntry.id)
            is PlaylistEntry.Heic -> repository.setActiveHeicPack(nextEntry.id)
        }
        return nextEntry.id
    }
    private sealed interface PlaylistEntry {
        val id: String
        data class Manual(override val id: String) : PlaylistEntry
        data class Heic(override val id: String) : PlaylistEntry
    }
}