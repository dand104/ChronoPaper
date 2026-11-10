package org.chronopaper.chronopaper.di

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.chronopaper.chronopaper.data.local.PreferencesDataStore
import org.chronopaper.chronopaper.data.local.StorageManager
import org.chronopaper.chronopaper.data.repository.DefaultWallpaperRepository
import org.chronopaper.chronopaper.domain.repository.WallpaperRepository
import org.chronopaper.chronopaper.domain.usecase.ImportHeicUseCase
import org.chronopaper.chronopaper.domain.usecase.ResolveCurrentFrameUseCase
import org.chronopaper.chronopaper.domain.usecase.RotatePlaylistUseCase
import org.chronopaper.chronopaper.service.worker.WallpaperWorkScheduler

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val dataStore: PreferencesDataStore by lazy {
        PreferencesDataStore(appContext)
    }

    val storageManager: StorageManager by lazy {
        StorageManager(appContext)
    }

    val wallpaperRepository: WallpaperRepository by lazy {
        DefaultWallpaperRepository(
            dataStore = dataStore,
            storageManager = storageManager
        )
    }

    val resolveCurrentFrameUseCase: ResolveCurrentFrameUseCase by lazy {
        ResolveCurrentFrameUseCase()
    }

    val rotatePlaylistUseCase: RotatePlaylistUseCase by lazy {
        RotatePlaylistUseCase(wallpaperRepository)
    }

    val importHeicUseCase: ImportHeicUseCase by lazy {
        ImportHeicUseCase(wallpaperRepository)
    }

    val workScheduler: WallpaperWorkScheduler by lazy {
        WallpaperWorkScheduler(appContext) {
            wallpaperRepository
        }
    }

    fun notifyFrameRefreshRequested() {
        _refreshTriggers.tryEmit(Unit)
    }

    private val _refreshTriggers = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val refreshTriggers: SharedFlow<Unit> = _refreshTriggers.asSharedFlow()
}