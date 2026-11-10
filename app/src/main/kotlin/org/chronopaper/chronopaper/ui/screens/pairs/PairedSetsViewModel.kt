package org.chronopaper.chronopaper.ui.screens.pairs

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
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
import org.chronopaper.chronopaper.domain.model.PairedWallpaperSet
import org.chronopaper.chronopaper.domain.model.SolarFrame
import org.chronopaper.chronopaper.domain.model.WallpaperMode

data class PairedSetsUiState(
    val sets: List<PairedWallpaperSet> = emptyList(),
    val heicPacks: List<DynamicHeicPack> = emptyList(),
    val activePairId: String? = null,
    val activeHeicPackId: String? = null,
    val activeMode: WallpaperMode = WallpaperMode.THEME,
    val currentSunPosition: SolarPosition = SolarPosition(180.0, 45.0),
    val activeTrajectoryTitle: String? = null,
    val activeTrajectoryFrames: List<SolarFrame> = emptyList(),
    val closestFrameIndex: Int? = null,
    val isPlaylistMode: Boolean = false,
    val rotationIntervalHours: Int = 24,
    val isSaving: Boolean = false,
    val isImportingHeic: Boolean = false,
    val errorMessage: String? = null
)

class PairedSetsViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val repository = container.wallpaperRepository
    private val importHeicUseCase = container.importHeicUseCase
    private val isSaving = MutableStateFlow(false)
    private val isImportingHeic = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PairedSetsUiState> = combine(
        repository.pairedSets,
        repository.heicPacks,
        repository.settings,
        isSaving,
        combine(isImportingHeic, errorMessage) { imp, err -> imp to err }
    ) { sets, packs, settings, saving, (importing, error) ->
        val sunPos = SunCalc.calculatePosition(
            epochMillis = System.currentTimeMillis(),
            latitudeDegrees = settings.location.latitude,
            longitudeDegrees = settings.location.longitude
        )

        val activeHeic = settings.activeHeicPackId?.let { id -> packs.firstOrNull { it.id == id } }
        val activePair = settings.activePairId?.let { id -> sets.firstOrNull { it.id == id } }
        val resolvedPair = activePair ?: if (activeHeic == null) sets.firstOrNull() else null
        val resolvedHeic = activeHeic ?: if (resolvedPair == null) packs.firstOrNull() else null

        val (trajectoryTitle, trajectoryFrames) = when {
            resolvedHeic != null -> resolvedHeic.title to resolvedHeic.frames
            resolvedPair != null -> resolvedPair.title to resolvedPair.toSolarFrames()
            else -> null to emptyList()
        }

        val closestIdx = trajectoryFrames.minByOrNull { frame ->
            SunCalc.angularDistanceDegrees(
                alt1Deg = sunPos.altitudeDegrees,
                az1Deg = sunPos.azimuthDegrees,
                alt2Deg = frame.altitude,
                az2Deg = frame.azimuth
            )
        }?.frameIndex

        PairedSetsUiState(
            sets = sets,
            heicPacks = packs,
            activePairId = resolvedPair?.id,
            activeHeicPackId = resolvedHeic?.id,
            activeMode = settings.mode,
            currentSunPosition = sunPos,
            activeTrajectoryTitle = trajectoryTitle,
            activeTrajectoryFrames = trajectoryFrames,
            closestFrameIndex = closestIdx,
            isPlaylistMode = settings.isPeriodicRotationActive,
            rotationIntervalHours = settings.rotationIntervalHours,
            isSaving = saving,
            isImportingHeic = importing,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PairedSetsUiState()
    )

    fun activatePair(pairId: String, mode: WallpaperMode? = null) {
        viewModelScope.launch {
            repository.setActivePair(pairId, mode)
            container.notifyFrameRefreshRequested()
        }
    }

    fun activateHeicPack(packId: String, mode: WallpaperMode? = null) {
        viewModelScope.launch {
            repository.setActiveHeicPack(packId, mode)
            container.notifyFrameRefreshRequested()
        }
    }

    fun togglePlaylistRotation(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoRotateEnabled(enabled)
            container.notifyFrameRefreshRequested()
        }
    }

    fun createManualSet(
        title: String,
        imageUris: List<Uri>,
        mode: WallpaperMode,
        targetWidth: Int,
        targetHeight: Int,
        onComplete: () -> Unit
    ) {
        if (isSaving.value || imageUris.isEmpty()) return
        viewModelScope.launch {
            isSaving.value = true
            errorMessage.value = null
            val cleanTitle = title.trim().ifEmpty {
                "Set #${repository.pairedSets.value.size + 1}"
            }
            runCatching {
                repository.createManualSet(
                    title = cleanTitle,
                    imageUris = imageUris,
                    mode = mode,
                    targetWidth = targetWidth,
                    targetHeight = targetHeight,
                    activateImmediately = true
                )
                container.notifyFrameRefreshRequested()
            }.onFailure {
                if (it is CancellationException) {
                    ensureActive()
                }
            }.onSuccess {
                onComplete()
            }.onFailure { err ->
                errorMessage.value = err.localizedMessage ?: "Failed to optimize images"
            }
            isSaving.value = false
        }
    }

    fun updateManualSet(
        pairId: String,
        title: String,
        sources: List<String>,
        mode: WallpaperMode,
        targetWidth: Int,
        targetHeight: Int,
        onComplete: () -> Unit
    ) {
        if (isSaving.value || sources.isEmpty()) return
        viewModelScope.launch {
            isSaving.value = true
            errorMessage.value = null
            runCatching {
                repository.updateManualSet(
                    pairId = pairId,
                    title = title,
                    sources = sources,
                    mode = mode,
                    targetWidth = targetWidth,
                    targetHeight = targetHeight
                )
                container.notifyFrameRefreshRequested()
            }.onFailure {
                if (it is CancellationException) {
                    ensureActive()
                }
            }.onSuccess {
                onComplete()
            }.onFailure { err ->
                errorMessage.value = err.localizedMessage ?: "Failed to update set"
            }
            isSaving.value = false
        }
    }

    fun updateHeicPack(
        packId: String,
        title: String,
        mode: WallpaperMode,
        swapLightDark: Boolean,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            repository.updateHeicPack(
                packId = packId,
                title = title,
                mode = mode,
                swapLightDark = swapLightDark
            )
            container.notifyFrameRefreshRequested()
            onComplete()
        }
    }

    fun importHeicUri(
        uri: Uri,
        suggestedTitle: String,
        targetWidth: Int,
        targetHeight: Int
    ) {
        if (isImportingHeic.value) return

        viewModelScope.launch {
            isImportingHeic.value = true
            errorMessage.value = null
            val cleanTitle = suggestedTitle
                .removeSuffix(".heic")
                .removeSuffix(".HEIC")
                .trim()
                .ifEmpty { "Dynamic #${repository.heicPacks.value.size + 1}" }

            importHeicUseCase(
                sourceUri = uri,
                title = cleanTitle,
                targetWidth = targetWidth,
                targetHeight = targetHeight
            ).onSuccess {
                container.notifyFrameRefreshRequested()
            }.onFailure { err ->
                errorMessage.value = err.localizedMessage ?: "Failed to unpack .heic container"
            }
            isImportingHeic.value = false
        }
    }

    fun deletePair(pairId: String) {
        viewModelScope.launch {
            repository.deletePairedSet(pairId)
            container.notifyFrameRefreshRequested()
        }
    }

    fun deleteHeicPack(packId: String) {
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
                PairedSetsViewModel(app.container)
            }
        }
    }
}