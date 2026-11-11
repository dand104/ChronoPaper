package org.chronopaper.chronopaper.domain.repository

import android.net.Uri
import kotlinx.coroutines.flow.StateFlow
import org.chronopaper.chronopaper.domain.model.DynamicHeicPack
import org.chronopaper.chronopaper.domain.model.GeoCoordinate
import org.chronopaper.chronopaper.domain.model.PairedWallpaperSet
import org.chronopaper.chronopaper.domain.model.PhotoCrop
import org.chronopaper.chronopaper.domain.model.UserSettings
import org.chronopaper.chronopaper.domain.model.WallpaperMode

interface WallpaperRepository {
    val settings: StateFlow<UserSettings>
    val pairedSets: StateFlow<List<PairedWallpaperSet>>
    val heicPacks: StateFlow<List<DynamicHeicPack>>

    suspend fun createManualSet(
        title: String,
        imageUris: List<Uri>,
        imageCrops: List<PhotoCrop> = emptyList(),
        targetWidth: Int,
        targetHeight: Int,
        activateImmediately: Boolean = true
    ): PairedWallpaperSet

    suspend fun updateManualSet(
        pairId: String,
        title: String,
        sources: List<String>,
        imageCrops: List<PhotoCrop> = emptyList(),
        targetWidth: Int,
        targetHeight: Int
    ) {}

    suspend fun updateHeicPack(
        packId: String,
        title: String,
        swapLightDark: Boolean = false
    ) {}

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

    suspend fun setActiveHeicPack(packId: String)

    suspend fun setAutoRotateEnabled(enabled: Boolean)

    suspend fun updateLocation(location: GeoCoordinate)

    suspend fun updateRotationIntervalHours(hours: Int)
}