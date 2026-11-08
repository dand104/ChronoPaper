package org.chronopaper.chronopaper.service

import android.app.WallpaperColors
import android.content.res.Configuration
import android.graphics.Bitmap
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class ChronoWallpaperService : WallpaperService() {

    private val activeEngines = mutableSetOf<ChronoEngine>()

    override fun onCreateEngine(): Engine = ChronoEngine()

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        activeEngines.forEach { it.onConfigurationChanged(newConfig) }
    }

    inner class ChronoEngine : Engine() {

        private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var currentBitmap: Bitmap? = null
        private var currentWallpaperColors: WallpaperColors? = null
        private var isNightMode: Boolean = false

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            activeEngines.add(this)
            isNightMode = isSystemInNightMode(resources.configuration)
            setTouchEventsEnabled(false)
        }

        fun onConfigurationChanged(newConfig: Configuration) {
            val newNightMode = isSystemInNightMode(newConfig)
            if (newNightMode != isNightMode) {
                isNightMode = newNightMode
                updateWallpaperForCurrentState()
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                val currentNight = isSystemInNightMode(resources.configuration)
                if (currentNight != isNightMode) {
                    isNightMode = currentNight
                    updateWallpaperForCurrentState()
                } else {
                    drawFrame()
                }
            }
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            drawFrame()
        }

        override fun onComputeColors(): WallpaperColors? {
            return currentWallpaperColors
        }

        private fun updateWallpaperForCurrentState() {
            // TODO
            drawFrame()
        }

        private fun drawFrame() {
            val holder = surfaceHolder ?: return
            if (!holder.surface.isValid) return

            val canvas = holder.lockHardwareCanvas() ?: return
            try {
                val bitmap = currentBitmap
                if (bitmap != null) {
                    canvas.drawBitmap(bitmap, 0f, 0f, null)
                } else {
                    canvas.drawColor(if (isNightMode) 0xFF121212.toInt() else 0xFFF5F5F5.toInt())
                }
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }

        private fun isSystemInNightMode(config: Configuration): Boolean {
            return (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }

        override fun onDestroy() {
            activeEngines.remove(this)
            engineScope.cancel()
            super.onDestroy()
        }
    }
}