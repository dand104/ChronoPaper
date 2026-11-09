package org.chronopaper.chronopaper.domain.usecase

import android.net.Uri
import org.chronopaper.chronopaper.domain.model.DynamicHeicPack
import org.chronopaper.chronopaper.domain.repository.WallpaperRepository

class ImportHeicUseCase(
    private val repository: WallpaperRepository
) {
    suspend operator fun invoke(
        sourceUri: Uri,
        title: String,
        targetWidth: Int,
        targetHeight: Int
    ): Result<DynamicHeicPack> = runCatching {
        repository.importDynamicHeic(
            sourceUri = sourceUri,
            title = title,
            targetWidth = targetWidth,
            targetHeight = targetHeight
        )
    }
}