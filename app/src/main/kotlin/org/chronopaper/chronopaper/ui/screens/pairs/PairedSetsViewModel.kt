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
import org.chronopaper.chronopaper.domain.model.PairedWallpaperSet
import org.chronopaper.chronopaper.domain.model.WallpaperMode

data class PairedSetsUiState(
    val sets: List<PairedWallpaperSet> = emptyList(),
    val activePairId: String? = null,
    val isPlaylistMode: Boolean = false,
    val rotationIntervalHours: Int = 24,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

class PairedSetsViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val repository = container.wallpaperRepository
    private val isSaving = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PairedSetsUiState> = combine(
        repository.pairedSets,
        repository.settings,
        isSaving,
        errorMessage
    ) { sets, settings, saving, error ->
        PairedSetsUiState(
            sets = sets,
            activePairId = settings.activePairId ?: sets.firstOrNull()?.id,
            isPlaylistMode = settings.isPeriodicRotationActive,
            rotationIntervalHours = settings.rotationIntervalHours,
            isSaving = saving,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PairedSetsUiState()
    )

    fun activatePair(pairId: String) {
        viewModelScope.launch {
            repository.setActivePair(pairId)
            val currentMode = repository.settings.value.mode
            if (currentMode != WallpaperMode.STATIC_PAIR && currentMode != WallpaperMode.PLAYLIST) {
                repository.setActiveMode(WallpaperMode.STATIC_PAIR)
            }
            container.notifyFrameRefreshRequested()
        }
    }

    fun togglePlaylistRotation(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoRotateEnabled(enabled)
            container.notifyFrameRefreshRequested()
        }
    }

    fun createPairedSet(
        title: String,
        lightUri: Uri,
        darkUri: Uri,
        targetWidth: Int,
        targetHeight: Int,
        onComplete: () -> Unit
    ) {
        if (isSaving.value) return
        viewModelScope.launch {
            isSaving.value = true
            errorMessage.value = null
            val cleanTitle = title.trim().ifEmpty {
                "Pair #${repository.pairedSets.value.size + 1}"
            }
            runCatching {
                repository.createPairedSet(
                    title = cleanTitle,
                    lightUri = lightUri,
                    darkUri = darkUri,
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

    fun deletePair(pairId: String) {
        viewModelScope.launch {
            repository.deletePairedSet(pairId)
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