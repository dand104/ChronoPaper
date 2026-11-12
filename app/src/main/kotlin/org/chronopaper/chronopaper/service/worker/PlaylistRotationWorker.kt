package org.chronopaper.chronopaper.service.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import org.chronopaper.chronopaper.appContainer

class PlaylistRotationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer
        val settings = container.wallpaperRepository.settings.first()
        if (!settings.isPeriodicRotationActive) {
            return Result.success()
        }

        container.rotatePlaylistUseCase()
        container.notifyFrameRefreshRequested()
        return Result.success()
    }
}