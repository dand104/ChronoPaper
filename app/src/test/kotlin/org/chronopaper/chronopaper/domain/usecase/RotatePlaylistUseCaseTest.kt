package org.chronopaper.chronopaper.domain.usecase

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.chronopaper.chronopaper.domain.model.DynamicHeicPack
import org.chronopaper.chronopaper.domain.model.GeoCoordinate
import org.chronopaper.chronopaper.domain.model.HeicScheduleType
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
        fakeRepository.setMode(WallpaperMode.PLAYLIST)
        val result = rotateUseCase()
        assertNull(result)
    }

    @Test
    fun `rotate cycles through paired sets sequentially and wraps around`() = runBlocking {
        fakeRepository.setPairedSets(
            listOf(
                PairedWallpaperSet("p1", "One", "/l1", "/d1"),
                PairedWallpaperSet("p2", "Two", "/l2", "/d2"),
                PairedWallpaperSet("p3", "Three", "/l3", "/d3")
            )
        )
        fakeRepository.setMode(WallpaperMode.PLAYLIST)
        fakeRepository.setActivePair("p1")

        val nextId1 = rotateUseCase()
        assertEquals("p2", nextId1)
        assertEquals("p2", fakeRepository.settings.value.activePairId)

        val nextId2 = rotateUseCase()
        assertEquals("p3", nextId2)

        val wrapAroundId = rotateUseCase()
        assertEquals("p1", wrapAroundId)
        assertEquals("p1", fakeRepository.settings.value.activePairId)
    }

    @Test
    fun `rotate cycles through HEIC packs and switches mode accordingly`() = runBlocking {
        val solarPack = DynamicHeicPack("h1", "Solar", HeicScheduleType.SOLAR, emptyList(), null, null)
        val aprPack = DynamicHeicPack("h2", "Appearance", HeicScheduleType.APPEARANCE, emptyList(), null, null)

        fakeRepository.setHeicPacks(listOf(solarPack, aprPack))
        fakeRepository.setActiveHeicPack("h1", WallpaperMode.APPLE_SOLAR)

        val nextId = rotateUseCase()
        assertEquals("h2", nextId)
        assertEquals(WallpaperMode.APPLE_APR, fakeRepository.settings.value.mode)

        val wrapId = rotateUseCase()
        assertEquals("h1", wrapId)
        assertEquals(WallpaperMode.APPLE_SOLAR, fakeRepository.settings.value.mode)
    }

    private class FakeWallpaperRepository : WallpaperRepository {
        private val _settings = MutableStateFlow(UserSettings())
        override val settings: StateFlow<UserSettings> = _settings.asStateFlow()

        private val _pairedSets = MutableStateFlow<List<PairedWallpaperSet>>(emptyList())
        override val pairedSets: StateFlow<List<PairedWallpaperSet>> = _pairedSets.asStateFlow()

        private val _heicPacks = MutableStateFlow<List<DynamicHeicPack>>(emptyList())
        override val heicPacks: StateFlow<List<DynamicHeicPack>> = _heicPacks.asStateFlow()

        fun setMode(mode: WallpaperMode) {
            _settings.value = _settings.value.copy(mode = mode)
        }

        fun setPairedSets(list: List<PairedWallpaperSet>) {
            _pairedSets.value = list
        }

        fun setHeicPacks(list: List<DynamicHeicPack>) {
            _heicPacks.value = list
        }

        override suspend fun setActivePair(pairId: String) {
            _settings.value = _settings.value.copy(activePairId = pairId)
        }

        override suspend fun setActiveHeicPack(packId: String, mode: WallpaperMode) {
            _settings.value = _settings.value.copy(activeHeicPackId = packId, mode = mode)
        }

        override suspend fun setActiveMode(mode: WallpaperMode) {
            _settings.value = _settings.value.copy(mode = mode)
        }

        override suspend fun createPairedSet(title: String, lightUri: Uri, darkUri: Uri, targetWidth: Int, targetHeight: Int, activateImmediately: Boolean) = error("Not needed")
        override suspend fun deletePairedSet(pairId: String) {}
        override suspend fun importDynamicHeic(sourceUri: Uri, title: String, targetWidth: Int, targetHeight: Int, activateImmediately: Boolean) = error("Not needed")
        override suspend fun deleteHeicPack(packId: String) {}
        override suspend fun setAutoRotateEnabled(enabled: Boolean) {}
        override suspend fun updateLocation(location: GeoCoordinate) {}
        override suspend fun updateRotationIntervalHours(hours: Int) {}
    }
}