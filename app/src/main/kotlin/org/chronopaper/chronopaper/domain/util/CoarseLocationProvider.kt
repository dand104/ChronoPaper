package org.chronopaper.chronopaper.domain.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.chronopaper.chronopaper.domain.model.GeoCoordinate
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

object CoarseLocationProvider {
    suspend fun fetchCoarseLocation(context: Context): GeoCoordinate? = withContext(Dispatchers.IO) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return@withContext null
        val locationManager = context.getSystemService(LocationManager::class.java) ?: return@withContext null

        val providers = listOf(
            LocationManager.FUSED_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        val cachedLocation = providers
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }

        if (cachedLocation != null) {
            return@withContext cachedLocation.toGeoCoordinateOrNull()
        }

        val activeProvider = listOf(
            LocationManager.FUSED_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        ).firstOrNull { provider ->
            runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
        } ?: return@withContext null

        val freshLocation = withTimeoutOrNull(5_000L.milliseconds) {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }

                runCatching {
                    locationManager.getCurrentLocation(
                        activeProvider,
                        signal,
                        Dispatchers.IO.asExecutor()
                    ) { location ->
                        if (continuation.isActive) {
                            continuation.resume(location)
                        }
                    }
                }.onFailure {
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }
        }

        return@withContext freshLocation?.toGeoCoordinateOrNull()
    }

    private fun Location.toGeoCoordinateOrNull(): GeoCoordinate? = runCatching {
        GeoCoordinate(latitude = latitude, longitude = longitude)
    }.getOrNull()
}