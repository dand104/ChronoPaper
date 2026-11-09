package org.chronopaper.chronopaper.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.chronopaper.chronopaper.ChronoApplication
import org.chronopaper.chronopaper.di.AppContainer
import org.chronopaper.chronopaper.domain.astronomy.SolarPosition
import org.chronopaper.chronopaper.domain.astronomy.SunCalc
import org.chronopaper.chronopaper.domain.model.GeoCoordinate
import org.chronopaper.chronopaper.domain.model.UserSettings

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val solarPosition: SolarPosition = SolarPosition(180.0, 45.0)
)

class SettingsViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val repository = container.wallpaperRepository

    val uiState: StateFlow<SettingsUiState> = repository.settings
        .map { settings ->
            val sunPos = SunCalc.calculatePosition(
                epochMillis = System.currentTimeMillis(),
                latitudeDegrees = settings.location.latitude,
                longitudeDegrees = settings.location.longitude
            )
            SettingsUiState(
                settings = settings,
                solarPosition = sunPos
            )
        }
        .stateIn(
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

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ChronoApplication
                SettingsViewModel(app.container)
            }
        }
    }
}