package org.chronopaper.chronopaper.service

import android.app.WallpaperColors
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.chronopaper.chronopaper.appContainer
import org.chronopaper.chronopaper.domain.model.WallpaperMode
import org.chronopaper.chronopaper.service.renderer.StaticImageRenderer
import org.chronopaper.chronopaper.service.renderer.WallpaperRenderer
import kotlin.time.Duration.Companion.minutes

class ChronoWallpaperService : WallpaperService() {

    private val activeEngines = mutableSetOf<ChronoEngine>()

    override fun onCreateEngine(): Engine = ChronoEngine()

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        activeEngines.forEach { it.onConfigurationChanged(newConfig) }
    }

    inner class ChronoEngine : Engine() {

        private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val container by lazy { applicationContext.appContainer }
        private val renderer: WallpaperRenderer = StaticImageRenderer()

        private val isNightModeFlow = MutableStateFlow(false)
        private var isTimeReceiverRegistered = false
        private var visibleSolarTickerJob: Job? = null
        private var hasDrawnInitialFrame = false

        private val timeChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                container.notifyFrameRefreshRequested()
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            activeEngines.add(this)
            isNightModeFlow.value = isSystemInNightMode(resources.configuration)
            setTouchEventsEnabled(false)
            registerTimeReceiver()
            observeWallpaperState()
        }

        fun onConfigurationChanged(newConfig: Configuration) {
            isNightModeFlow.value = isSystemInNightMode(newConfig)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                isNightModeFlow.value = isSystemInNightMode(resources.configuration)
                container.notifyFrameRefreshRequested()
                startVisibleSolarTicker()
            } else {
                stopVisibleSolarTicker()
            }
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            renderer.onSurfaceChanged(width, height)
            renderer.draw(holder)
            hasDrawnInitialFrame = true
        }

        override fun onComputeColors(): WallpaperColors? {
            return renderer.computeColors()
        }

        private fun observeWallpaperState() {
            val repository = container.wallpaperRepository
            val resolveUseCase = container.resolveCurrentFrameUseCase

            engineScope.launch {
                combine(
                    repository.settings,
                    repository.pairedSets,
                    repository.heicPacks,
                    isNightModeFlow,
                    container.refreshTriggers.onStart { emit(Unit) }
                ) { settings, pairs, packs, isNight, _ ->
                    resolveUseCase(
                        settings = settings,
                        pairedSets = pairs,
                        heicPacks = packs,
                        isNightMode = isNight,
                        epochMillis = System.currentTimeMillis()
                    )
                }.collect { activeState ->
                    val contentChanged = renderer.updateState(activeState)
                    if (contentChanged || !hasDrawnInitialFrame) {
                        renderer.draw(surfaceHolder)
                        hasDrawnInitialFrame = true
                    }
                    if (contentChanged) {
                        notifyColorsChanged()
                    }
                }
            }
        }

        private fun startVisibleSolarTicker() {
            visibleSolarTickerJob?.cancel()
            visibleSolarTickerJob = engineScope.launch {
                while (isActive) {
                    delay(15.minutes)
                    if (container.wallpaperRepository.settings.value.mode == WallpaperMode.APPLE_SOLAR) {
                        container.notifyFrameRefreshRequested()
                    }
                }
            }
        }

        private fun stopVisibleSolarTicker() {
            visibleSolarTickerJob?.cancel()
            visibleSolarTickerJob = null
        }

        private fun registerTimeReceiver() {
            if (isTimeReceiverRegistered) return
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            registerReceiver(timeChangeReceiver, filter)
            isTimeReceiverRegistered = true
        }

        private fun unregisterTimeReceiver() {
            if (!isTimeReceiverRegistered) return
            runCatching { unregisterReceiver(timeChangeReceiver) }
            isTimeReceiverRegistered = false
        }

        private fun isSystemInNightMode(config: Configuration): Boolean {
            return (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }

        override fun onDestroy() {
            activeEngines.remove(this)
            stopVisibleSolarTicker()
            unregisterTimeReceiver()
            engineScope.cancel()
            renderer.release()
            super.onDestroy()
        }
    }
}