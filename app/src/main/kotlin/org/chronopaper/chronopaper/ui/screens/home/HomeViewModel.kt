package org.chronopaper.chronopaper.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.chronopaper.chronopaper.ChronoApplication
import org.chronopaper.chronopaper.di.AppContainer
import org.chronopaper.chronopaper.domain.astronomy.SolarPosition
import org.chronopaper.chronopaper.domain.astronomy.SunCalc
import org.chronopaper.chronopaper.domain.model.ActiveWallpaperState
import org.chronopaper.chronopaper.domain.model.UserSettings
import org.chronopaper.chronopaper.domain.model.WallpaperMode

data class HomeUiState(
    val settings: UserSettings = UserSettings(),
    val activeTitle: String = "No collection selected",
    val activeLightState: ActiveWallpaperState = ActiveWallpaperState.Fallback(isNightMode = false),
    val activeDarkState: ActiveWallpaperState = ActiveWallpaperState.Fallback(isNightMode = true),
    val solarPosition: SolarPosition = SolarPosition(180.0, 45.0),
    val pairsCount: Int = 0,
    val heicPacksCount: Int = 0,
    val previewNightOverride: Boolean? = null
)

class HomeViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val repository = container.wallpaperRepository
    private val resolveCurrentFrame = container.resolveCurrentFrameUseCase
    private val rotatePlaylist = container.rotatePlaylistUseCase

    private val previewNightOverride = MutableStateFlow<Boolean?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        repository.settings,
        repository.pairedSets,
        repository.heicPacks,
        previewNightOverride
    ) { settings, pairs, packs, nightOverride ->
        val now = System.currentTimeMillis()
        val sunPos = SunCalc.calculatePosition(
            epochMillis = now,
            latitudeDegrees = settings.location.latitude,
            longitudeDegrees = settings.location.longitude
        )

        val lightState = resolveCurrentFrame(
            settings = settings,
            pairedSets = pairs,
            heicPacks = packs,
            isNightMode = false,
            epochMillis = now
        )
        val darkState = resolveCurrentFrame(
            settings = settings,
            pairedSets = pairs,
            heicPacks = packs,
            isNightMode = true,
            epochMillis = now
        )

        val title = when (settings.mode) {
            WallpaperMode.STATIC_PAIR,
            WallpaperMode.PLAYLIST -> {
                val pair = pairs.firstOrNull { it.id == settings.activePairId } ?: pairs.firstOrNull()
                pair?.title ?: "No matching wallpaper"
            }

            WallpaperMode.APPLE_SOLAR,
            WallpaperMode.APPLE_APR -> {
                val pack = packs.firstOrNull { it.id == settings.activeHeicPackId } ?: packs.firstOrNull()
                pack?.title ?: "No .heic packages"
            }
        }

        HomeUiState(
            settings = settings,
            activeTitle = title,
            activeLightState = lightState,
            activeDarkState = darkState,
            solarPosition = sunPos,
            pairsCount = pairs.size,
            heicPacksCount = packs.size,
            previewNightOverride = nightOverride
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    fun selectMode(mode: WallpaperMode) {
        viewModelScope.launch {
            repository.setActiveMode(mode)
            container.notifyFrameRefreshRequested()
        }
    }

    fun togglePreviewTheme(currentSystemDark: Boolean) {
        val current = previewNightOverride.value ?: currentSystemDark
        previewNightOverride.value = !current
    }

    fun rotateNow() {
        viewModelScope.launch {
            rotatePlaylist()
            container.notifyFrameRefreshRequested()
        }
    }

    fun requestImmediateEngineRefresh() {
        container.notifyFrameRefreshRequested()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ChronoApplication
                HomeViewModel(app.container)
            }
        }
    }
}