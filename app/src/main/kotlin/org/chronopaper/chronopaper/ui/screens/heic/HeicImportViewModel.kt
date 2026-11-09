package org.chronopaper.chronopaper.ui.screens.heic

import android.net.Uri
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
import org.chronopaper.chronopaper.domain.model.DynamicHeicPack
import org.chronopaper.chronopaper.domain.model.HeicScheduleType
import org.chronopaper.chronopaper.domain.model.WallpaperMode

data class HeicImportUiState(
    val packs: List<DynamicHeicPack> = emptyList(),
    val activePack: DynamicHeicPack? = null,
    val activeMode: WallpaperMode = WallpaperMode.APPLE_SOLAR,
    val currentSunPosition: SolarPosition = SolarPosition(180.0, 45.0),
    val closestFrameIndex: Int? = null,
    val isImporting: Boolean = false,
    val errorMessage: String? = null
)

class HeicImportViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val repository = container.wallpaperRepository
    private val importHeicUseCase = container.importHeicUseCase

    private val isImporting = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HeicImportUiState> = combine(
        repository.heicPacks,
        repository.settings,
        isImporting,
        errorMessage
    ) { packs, settings, importing, error ->
        val activePack = packs.firstOrNull { it.id == settings.activeHeicPackId } ?: packs.firstOrNull()
        val sunPos = SunCalc.calculatePosition(
            epochMillis = System.currentTimeMillis(),
            latitudeDegrees = settings.location.latitude,
            longitudeDegrees = settings.location.longitude
        )

        val closestIdx = activePack?.frames?.minByOrNull { frame ->
            SunCalc.angularDistanceDegrees(
                alt1Deg = sunPos.altitudeDegrees,
                az1Deg = sunPos.azimuthDegrees,
                alt2Deg = frame.altitude,
                az2Deg = frame.azimuth
            )
        }?.frameIndex

        HeicImportUiState(
            packs = packs,
            activePack = activePack,
            activeMode = settings.mode,
            currentSunPosition = sunPos,
            closestFrameIndex = closestIdx,
            isImporting = importing,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HeicImportUiState()
    )

    fun importHeicUri(
        uri: Uri,
        suggestedTitle: String,
        targetWidth: Int,
        targetHeight: Int
    ) {
        if (isImporting.value) return
        viewModelScope.launch {
            isImporting.value = true
            errorMessage.value = null
            val cleanTitle = suggestedTitle
                .removeSuffix(".heic")
                .removeSuffix(".HEIC")
                .trim()
                .ifEmpty { "Apple Dynamic #${repository.heicPacks.value.size + 1}" }

            importHeicUseCase(
                sourceUri = uri,
                title = cleanTitle,
                targetWidth = targetWidth,
                targetHeight = targetHeight
            ).onSuccess {
                container.notifyFrameRefreshRequested()
            }.onFailure { err ->
                errorMessage.value = err.localizedMessage
                    ?: "The file does not contain apple_desktop:solar or apr metadata"
            }
            isImporting.value = false
        }
    }

    fun selectPack(pack: DynamicHeicPack, preferredMode: WallpaperMode? = null) {
        viewModelScope.launch {
            val mode = preferredMode ?: when (pack.scheduleType) {
                HeicScheduleType.SOLAR -> WallpaperMode.APPLE_SOLAR
                HeicScheduleType.APPEARANCE -> WallpaperMode.APPLE_APR
            }
            repository.setActiveHeicPack(pack.id, mode)
            container.notifyFrameRefreshRequested()
        }
    }

    fun deletePack(packId: String) {
        viewModelScope.launch {
            repository.deleteHeicPack(packId)
            container.notifyFrameRefreshRequested()
        }
    }

    fun dismissError() {
        errorMessage.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ChronoApplication
                HeicImportViewModel(app.container)
            }
        }
    }
}