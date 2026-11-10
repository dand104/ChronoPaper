package org.chronopaper.chronopaper.domain.usecase

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.chronopaper.chronopaper.domain.model.DynamicHeicPack
import org.chronopaper.chronopaper.domain.model.GeoCoordinate
import org.chronopaper.chronopaper.domain.model.PairedWallpaperSet
import org.chronopaper.chronopaper.domain.model.UserSettings
import org.chronopaper.chronopaper.domain.model.WallpaperMode
import org.chronopaper.chronopaper.domain.repository.WallpaperRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class RotatePlaylistUseCaseTest {

    private lateinit var fakeRepository: FakeWallpaperRepository
    private lateinit var rotateUseCase: RotatePlaylistUseCase

    @Before
    fun setUp() {
        fakeRepository = FakeWallpaperRepository()
        rotateUseCase = RotatePlaylistUseCase(fakeRepository)
    }

    @Test
    fun `rotate returns null when playlist is empty`() = runBlocking {
        val result = rotateUseCase()
        assertNull(result)
    }

    @Test
    fun `rotate cycles through manual sets and HEIC packs and wraps around`() = runBlocking {
        fakeRepository.setPairedSets(
            listOf(
                PairedWallpaperSet("p1", "One", "/l1", "/d1"),
                PairedWallpaperSet("p2", "Two", "/l2", "/d2")
            )
        )
        fakeRepository.setHeicPacks(
            listOf(
                DynamicHeicPack("h1", "Solar", WallpaperMode.SOLAR, emptyList())
            )
        )
        fakeRepository.setActivePair("p1", WallpaperMode.THEME)

        assertEquals("p2", rotateUseCase())
        assertEquals("h1", rotateUseCase())
        assertEquals(WallpaperMode.SOLAR, fakeRepository.settings.value.mode)
        assertEquals("p1", rotateUseCase())
        assertEquals(WallpaperMode.THEME, fakeRepository.settings.value.mode)
    }

    private class FakeWallpaperRepository : WallpaperRepository {
        private val _settings = MutableStateFlow(UserSettings())
        override val settings: StateFlow<UserSettings> = _settings.asStateFlow()

        private val _pairedSets = MutableStateFlow<List<PairedWallpaperSet>>(emptyList())
        override val pairedSets: StateFlow<List<PairedWallpaperSet>> = _pairedSets.asStateFlow()

        private val _heicPacks = MutableStateFlow<List<DynamicHeicPack>>(emptyList())
        override val heicPacks: StateFlow<List<DynamicHeicPack>> = _heicPacks.asStateFlow()

        fun setPairedSets(list: List<PairedWallpaperSet>) {
            _pairedSets.value = list
        }

        fun setHeicPacks(list: List<DynamicHeicPack>) {
            _heicPacks.value = list
        }

        override suspend fun setActivePair(pairId: String, mode: WallpaperMode?) {
            _settings.value = _settings.value.copy(
                activePairId = pairId,
                activeHeicPackId = null,
                mode = mode ?: _settings.value.mode
            )
        }

        override suspend fun setActiveHeicPack(packId: String, mode: WallpaperMode?) {
            _settings.value = _settings.value.copy(
                activeHeicPackId = packId,
                activePairId = null,
                mode = mode ?: _settings.value.mode
            )
        }

        override suspend fun setActiveMode(mode: WallpaperMode) {
            _settings.value = _settings.value.copy(mode = mode)
        }

        override suspend fun createManualSet(title: String, imageUris: List<Uri>, mode: WallpaperMode, targetWidth: Int, targetHeight: Int, activateImmediately: Boolean) = error("Not needed")
        override suspend fun deletePairedSet(pairId: String) {}
        override suspend fun importDynamicHeic(sourceUri: Uri, title: String, targetWidth: Int, targetHeight: Int, activateImmediately: Boolean) = error("Not needed")
        override suspend fun deleteHeicPack(packId: String) {}
        override suspend fun setAutoRotateEnabled(enabled: Boolean) {}
        override suspend fun updateLocation(location: GeoCoordinate) {}
        override suspend fun updateRotationIntervalHours(hours: Int) {}
    }
}