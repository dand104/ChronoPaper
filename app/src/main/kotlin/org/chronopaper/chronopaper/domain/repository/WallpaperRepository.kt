package org.chronopaper.chronopaper.domain.repository

import android.net.Uri
import kotlinx.coroutines.flow.StateFlow
import org.chronopaper.chronopaper.domain.model.DynamicHeicPack
import org.chronopaper.chronopaper.domain.model.GeoCoordinate
import org.chronopaper.chronopaper.domain.model.PairedWallpaperSet
import org.chronopaper.chronopaper.domain.model.UserSettings
import org.chronopaper.chronopaper.domain.model.WallpaperMode

interface WallpaperRepository {
    val settings: StateFlow<UserSettings>
    val pairedSets: StateFlow<List<PairedWallpaperSet>>
    val heicPacks: StateFlow<List<DynamicHeicPack>>

    suspend fun createPairedSet(
        title: String,
        lightUri: Uri,
        darkUri: Uri,
        targetWidth: Int,
        targetHeight: Int,
        activateImmediately: Boolean = true
    ): PairedWallpaperSet

    suspend fun deletePairedSet(pairId: String)

    suspend fun importDynamicHeic(
        sourceUri: Uri,
        title: String,
        targetWidth: Int,
        targetHeight: Int,
        activateImmediately: Boolean = true
    ): DynamicHeicPack

    suspend fun deleteHeicPack(packId: String)

    suspend fun setActiveMode(mode: WallpaperMode)

    suspend fun setActivePair(pairId: String)

    suspend fun setActiveHeicPack(packId: String, mode: WallpaperMode = WallpaperMode.APPLE_SOLAR)

    suspend fun setAutoRotateEnabled(enabled: Boolean)

    suspend fun updateLocation(location: GeoCoordinate)

    suspend fun updateRotationIntervalHours(hours: Int)
}