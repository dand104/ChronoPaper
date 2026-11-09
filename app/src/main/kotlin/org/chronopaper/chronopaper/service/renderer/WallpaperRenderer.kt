package org.chronopaper.chronopaper.service.renderer

import android.app.WallpaperColors
import android.view.SurfaceHolder
import org.chronopaper.chronopaper.domain.model.ActiveWallpaperState

interface WallpaperRenderer {
    suspend fun updateState(state: ActiveWallpaperState): Boolean

    fun onSurfaceChanged(width: Int, height: Int)

    fun draw(holder: SurfaceHolder)

    fun computeColors(): WallpaperColors?

    fun release()
}