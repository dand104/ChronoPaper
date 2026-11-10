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
    val activeTitle: String = "No collection added yet",
    val activeFormatLabel: String = "Add a manual set or import .heic",
    val allowedModes: List<WallpaperMode> = WallpaperMode.entries,
    val activeLightState: ActiveWallpaperState = ActiveWallpaperState.Fallback(isNightMode = false),
    val activeDarkState: ActiveWallpaperState = ActiveWallpaperState.Fallback(isNightMode = true),
    val solarPosition: SolarPosition = SolarPosition(180.0, 45.0),
    val pairsCount: Int = 0,
    val heicPacksCount: Int = 0,
    val previewNightOverride: Boolean? = null
) {
    val totalCollectionsCount: Int
        get() = pairsCount + heicPacksCount

    val hasAnyWallpaper: Boolean
        get() = totalCollectionsCount > 0
}

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
        val activeHeic = settings.activeHeicPackId?.let { id -> packs.firstOrNull { it.id == id } }
        val activePair = settings.activePairId?.let { id -> pairs.firstOrNull { it.id == id } }
        val fallbackPair = pairs.firstOrNull()
        val fallbackHeic = packs.firstOrNull()

        val (title, formatLabel, allowed) = when {
            activeHeic != null -> Triple(
                activeHeic.title,
                "HEIC container · ${activeHeic.frameCount} frames",
                activeHeic.allowedModes
            )
            activePair != null -> Triple(
                activePair.title,
                "Manual set · ${activePair.frameCount} photo(s)",
                activePair.allowedModes
            )
            fallbackPair != null -> Triple(
                fallbackPair.title,
                "Manual set · ${fallbackPair.frameCount} photo(s)",
                fallbackPair.allowedModes
            )
            fallbackHeic != null -> Triple(
                fallbackHeic.title,
                "HEIC container · ${fallbackHeic.frameCount} frames",
                fallbackHeic.allowedModes
            )
            else -> Triple(
                "No collection added yet",
                "Add a manual set or import .heic",
                WallpaperMode.entries
            )
        }

        HomeUiState(
            settings = settings,
            activeTitle = title,
            activeFormatLabel = formatLabel,
            allowedModes = allowed,
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

    fun toggleAutoRotate(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoRotateEnabled(enabled)
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