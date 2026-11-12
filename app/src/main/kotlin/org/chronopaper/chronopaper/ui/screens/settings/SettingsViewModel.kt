package org.chronopaper.chronopaper.ui.screens.settings

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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
import org.chronopaper.chronopaper.domain.model.GeoCoordinate
import org.chronopaper.chronopaper.domain.model.UserSettings
import org.chronopaper.chronopaper.domain.util.CoarseLocationProvider

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val solarPosition: SolarPosition = SolarPosition(180.0, 45.0),
    val isDetectingLocation: Boolean = false,
    val locationMessage: String? = null
)

class SettingsViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val repository = container.wallpaperRepository
    private val isDetectingLocation = MutableStateFlow(false)
    private val locationMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        repository.settings,
        isDetectingLocation,
        locationMessage
    ) { settings, detecting, message ->
        val sunPos = SunCalc.calculatePosition(
            epochMillis = System.currentTimeMillis(),
            latitudeDegrees = settings.location.latitude,
            longitudeDegrees = settings.location.longitude
        )

        SettingsUiState(
            settings = settings,
            solarPosition = sunPos,
            isDetectingLocation = detecting,
            locationMessage = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    fun setAutoRotate(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoRotateEnabled(enabled)
            container.notifyFrameRefreshRequested()
        }
    }

    fun setShuffleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setShuffleEnabled(enabled)
        }
    }

    fun setRotationIntervalHours(hours: Int) {
        viewModelScope.launch {
            repository.updateRotationIntervalHours(hours)
        }
    }

    fun updateCoordinates(latitude: Double, longitude: Double) {
        val validLat = latitude.coerceIn(-90.0, 90.0)
        val validLon = longitude.coerceIn(-180.0, 180.0)
        viewModelScope.launch {
            repository.updateLocation(GeoCoordinate(validLat, validLon))
            container.notifyFrameRefreshRequested()
        }
    }

    fun detectCoarseLocation(context: Context) {
        if (isDetectingLocation.value) return
        viewModelScope.launch {
            val startMark = SystemClock.elapsedRealtime()
            isDetectingLocation.value = true
            locationMessage.value = null
            try {
                val coord = CoarseLocationProvider.fetchCoarseLocation(context.applicationContext)
                val elapsed = SystemClock.elapsedRealtime() - startMark
                if (elapsed < MIN_DETECT_ANIMATION_MS) {
                    delay(MIN_DETECT_ANIMATION_MS - elapsed)
                }

                if (coord != null) {
                    repository.updateLocation(coord)
                    container.notifyFrameRefreshRequested()
                    locationMessage.value = "Location updated (${String.format("%.2f°, %.2f°", coord.latitude, coord.longitude)})"
                } else {
                    locationMessage.value = "Unable to determine location. Check device settings."
                }
            } catch (t: Throwable) {
                if (t is CancellationException) {
                    ensureActive()
                }
                locationMessage.value = t.localizedMessage ?: "Failed to get location"
            } finally {
                isDetectingLocation.value = false
            }
        }
    }
    fun dismissLocationMessage() {
        locationMessage.value = null
    }

    companion object {
        private const val MIN_DETECT_ANIMATION_MS = 380L
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ChronoApplication
                SettingsViewModel(app.container)
            }
        }
    }
}