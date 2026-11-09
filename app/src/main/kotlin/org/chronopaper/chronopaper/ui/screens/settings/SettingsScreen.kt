package org.chronopaper.chronopaper.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.chronopaper.chronopaper.domain.util.OemBatteryAdvisor
import org.chronopaper.chronopaper.ui.components.ChronoIcons
import org.chronopaper.chronopaper.ui.theme.ExpressiveGroupShapes
import java.util.Locale
import kotlin.math.abs

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showBatteryWarning by remember {
        mutableStateOf(OemBatteryAdvisor.shouldShowBatteryOptimizationWarning(context))
    }
    var showLocationDialog by rememberSaveable { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                showBatteryWarning = OemBatteryAdvisor.shouldShowBatteryOptimizationWarning(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.displaySmall
                )
            }
        }

        if (showBatteryWarning) {
            item {
                OemBatteryWarningCard(
                    onDisableOptimization = { requestIgnoreBatteryOptimizations(context) }
                )
            }
        }

        item {
            Text(
                text = "Astronomical coordinates",
                style = MaterialTheme.typography.titleLarge
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Surface(
                    shape = ExpressiveGroupShapes.forIndex(0, 2),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLocationDialog = true }
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "Latitude %.4f°, Longitude %.4f°",
                                    state.settings.location.latitude,
                                    state.settings.location.longitude
                                )
                            )
                        },
                        supportingContent = {
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "Current sun angle: altitude %.1f°, azimuth %.1f°",
                                    state.solarPosition.altitudeDegrees,
                                    state.solarPosition.azimuthDegrees
                                )
                            )
                        },
                        leadingContent = {
                            Icon(imageVector = ChronoIcons.Location, contentDescription = null)
                        },
                        trailingContent = {
                            TextButton(onClick = { showLocationDialog = true }) {
                                Text("Modify")
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }

        item {
            Text(
                text = "Playlist rotation",
                style = MaterialTheme.typography.titleLarge
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Surface(
                    shape = ExpressiveGroupShapes.forIndex(0, 2),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setAutoRotate(!state.settings.autoRotateEnabled) }
                ) {
                    ListItem(
                        headlineContent = { Text("Periodic change of sets") },
                        supportingContent = {
                            Text("Automatically switch the active pair or .heic package")
                        },
                        leadingContent = {
                            Icon(imageVector = ChronoIcons.Refresh, contentDescription = null)
                        },
                        trailingContent = {
                            Switch(
                                checked = state.settings.autoRotateEnabled,
                                onCheckedChange = viewModel::setAutoRotate
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }

                Surface(
                    shape = ExpressiveGroupShapes.forIndex(1, 2),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Change interval: every ${state.settings.rotationIntervalHours} h.",
                            style = MaterialTheme.typography.titleMedium
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(1, 3, 6, 12, 24, 48, 168).forEach { hours ->
                                FilterChip(
                                    selected = state.settings.rotationIntervalHours == hours,
                                    onClick = { viewModel.setRotationIntervalHours(hours) },
                                    label = {
                                        Text(if (hours == 168) "7 days" else "$hours h.")
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLocationDialog) {
        CoordinateEditorDialog(
            initialLat = state.settings.location.latitude,
            initialLon = state.settings.location.longitude,
            onDismiss = { showLocationDialog = false },
            onSave = { lat, lon ->
                viewModel.updateCoordinates(lat, lon)
                showLocationDialog = false
            }
        )
    }
}

@Composable
private fun OemBatteryWarningCard(
    onDisableOptimization: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = ChronoIcons.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Aggressive shell energy saving",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Text(
                text = "On Xiaomi/HyperOS, OPPO/OnePlus/Realme, Vivo, and Honor devices, the system may freeze background wallpaper change triggers. Disable battery restriction.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
            )
            Button(
                onClick = onDisableOptimization,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                )
            ) {
                Text("Remove background restrictions")
            }
        }
    }
}

@Composable
private fun CoordinateEditorDialog(
    initialLat: Double,
    initialLon: Double,
    onDismiss: () -> Unit,
    onSave: (Double, Double) -> Unit
) {
    var latText by rememberSaveable { mutableStateOf(initialLat.toString()) }
    var lonText by rememberSaveable { mutableStateOf(initialLon.toString()) }

    val parsedLat = latText.replace(',', '.').toDoubleOrNull()
    val parsedLon = lonText.replace(',', '.').toDoubleOrNull()
    val isValid = parsedLat != null && parsedLat in -90.0..90.0 &&
            parsedLon != null && parsedLon in -180.0..180.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Coordinates") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Used for calculating the altitude and azimuth of the sun.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = latText,
                    onValueChange = { latText = it },
                    label = { Text("Latitude (-90..90)") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
                OutlinedTextField(
                    value = lonText,
                    onValueChange = { lonText = it },
                    label = { Text("Longitude (-180..180)") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (parsedLat != null && parsedLon != null) {
                        onSave(parsedLat, parsedLon)
                    }
                },
                enabled = isValid
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun requestIgnoreBatteryOptimizations(context: Context) {
    val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(fallbackIntent)
    }.onFailure {
        runCatching { context.startActivity(appDetailsIntent) }
    }
}