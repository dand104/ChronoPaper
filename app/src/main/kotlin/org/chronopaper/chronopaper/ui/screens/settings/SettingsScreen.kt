package org.chronopaper.chronopaper.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.chronopaper.chronopaper.domain.util.OemBatteryAdvisor
import org.chronopaper.chronopaper.ui.components.ChronoIcons
import org.chronopaper.chronopaper.ui.theme.ExpressiveGroupShapes
import java.util.Locale
import androidx.core.net.toUri

private val SettingsCardItemModifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val motionScheme = MaterialTheme.motionScheme

    var showBatteryWarning by remember {
        mutableStateOf(OemBatteryAdvisor.shouldShowBatteryOptimizationWarning(context))
    }
    var showLocationDialog by rememberSaveable { mutableStateOf(false) }

    val animatedLat by animateFloatAsState(
        targetValue = state.settings.location.latitude.toFloat(),
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "AnimatedLat"
    )
    val animatedLon by animateFloatAsState(
        targetValue = state.settings.location.longitude.toFloat(),
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "AnimatedLon"
    )
    val animatedAlt by animateFloatAsState(
        targetValue = state.solarPosition.altitudeDegrees.toFloat(),
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "AnimatedAlt"
    )
    val animatedAz by animateFloatAsState(
        targetValue = state.solarPosition.azimuthDegrees.toFloat(),
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "AnimatedAz"
    )

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.detectCoarseLocation(context)
        }
    }

    val requestCoarseLocation: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.detectCoarseLocation(context)
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

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
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 36.dp),
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
                        .clip(ExpressiveGroupShapes.forIndex(0, 2))
                        .clickable { showLocationDialog = true }
                ) {
                    ListItem(
                        modifier = SettingsCardItemModifier,
                        supportingContent = {
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "Current sun angle: altitude %.1f°, azimuth %.1f°",
                                    animatedAlt,
                                    animatedAz
                                )
                            )
                        },
                        leadingContent = {
                            Icon(imageVector = ChronoIcons.Location, contentDescription = null)
                        },
                        trailingContent = {
                            Text(
                                text = "Modify",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    ) {
                        Text(
                            text = String.format(
                                Locale.US,
                                "Latitude %.4f°, Longitude %.4f°",
                                animatedLat,
                                animatedLon
                            )
                        )
                    }
                }

                Surface(
                    shape = ExpressiveGroupShapes.forIndex(1, 2),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ExpressiveGroupShapes.forIndex(1, 2))
                        .clickable(
                            enabled = !state.isDetectingLocation,
                            onClick = requestCoarseLocation
                        )
                ) {
                    ListItem(
                        modifier = SettingsCardItemModifier,
                        supportingContent = {
                            AnimatedContent(
                                targetState = state.locationMessage
                                    ?: "Coarse network & Wi-Fi position without GPS battery drain",
                                transitionSpec = {
                                    fadeIn(motionScheme.defaultEffectsSpec())
                                        .togetherWith(fadeOut(motionScheme.fastEffectsSpec()))
                                },
                                label = "LocationSupportingText"
                            ) { message ->
                                Text(text = message)
                            }
                        },
                        leadingContent = {
                            AnimatedContent(
                                targetState = state.isDetectingLocation,
                                transitionSpec = {
                                    (fadeIn(motionScheme.defaultEffectsSpec()) +
                                            scaleIn(initialScale = 0.7f, animationSpec = motionScheme.defaultSpatialSpec()))
                                        .togetherWith(
                                            fadeOut(motionScheme.fastEffectsSpec()) +
                                                    scaleOut(targetScale = 0.7f, animationSpec = motionScheme.fastSpatialSpec())
                                        )
                                },
                                contentAlignment = Alignment.Center,
                                label = "LocationIconTransition"
                            ) { isDetecting ->
                                Box(
                                    modifier = Modifier.size(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isDetecting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = ChronoIcons.Solar,
                                            contentDescription = null
                                        )
                                    }
                                }
                            }
                        },
                        trailingContent = {
                            AnimatedContent(
                                targetState = state.isDetectingLocation,
                                transitionSpec = {
                                    fadeIn(motionScheme.defaultEffectsSpec())
                                        .togetherWith(fadeOut(motionScheme.fastEffectsSpec()))
                                },
                                label = "LocationButtonText"
                            ) { isDetecting ->
                                Text(
                                    text = if (isDetecting) "Locating..." else "Detect",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isDetecting) {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    ) {
                        Text("Detect location automatically")
                    }
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
                    shape = ExpressiveGroupShapes.forIndex(0, 3),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ExpressiveGroupShapes.forIndex(0, 3))
                        .clickable { viewModel.setAutoRotate(!state.settings.autoRotateEnabled) }
                ) {
                    ListItem(
                        modifier = SettingsCardItemModifier,
                        supportingContent = {
                            Text("Automatically switch the active pair or .heic package")
                        },
                        leadingContent = {
                            Icon(imageVector = ChronoIcons.Refresh, contentDescription = null)
                        },
                        trailingContent = {
                            Switch(
                                checked = state.settings.autoRotateEnabled,
                                onCheckedChange = null
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    ) {
                        Text("Periodic change of sets")
                    }
                }

                Surface(
                    shape = ExpressiveGroupShapes.forIndex(1, 3),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ExpressiveGroupShapes.forIndex(1, 3))
                        .clickable { viewModel.setShuffleEnabled(!state.settings.shuffleEnabled) }
                ) {
                    ListItem(
                        modifier = SettingsCardItemModifier,
                        leadingContent = {
                            Icon(imageVector = ChronoIcons.Shuffle, contentDescription = null)
                        },
                        trailingContent = {
                            Switch(
                                checked = state.settings.shuffleEnabled,
                                onCheckedChange = null
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    ) {
                        Text("Shuffle wallpaper pairs")
                    }
                }

                Surface(
                    shape = ExpressiveGroupShapes.forIndex(2, 3),
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

        item {
            Text(
                text = "About project",
                style = MaterialTheme.typography.titleLarge
            )
        }

        item {
            AboutProjectCard(context = context)
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
private fun AboutProjectCard(context: Context) {
    Column(
        modifier = Modifier.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Surface(
            shape = ExpressiveGroupShapes.forIndex(0, 2),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            ListItem(
                modifier = SettingsCardItemModifier,
                supportingContent = {
                    Text(
                        text = "Dynamic wallpaper changer with auto rotation."
                    )
                },
                leadingContent = {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = ChronoIcons.Wallpaper,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                trailingContent = {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Text(
                            text = "v1.0",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            ) {
                Text(
                    text = "ChronoPaper",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        Surface(
            shape = ExpressiveGroupShapes.forIndex(1, 2),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .clip(ExpressiveGroupShapes.forIndex(1, 2))
                .clickable { openGitHubRepository(context) }
        ) {
            ListItem(
                modifier = SettingsCardItemModifier,
                supportingContent = {
                    Text("Licensed under Apache 2.0")
                },
                leadingContent = {
                    Icon(
                        imageVector = ChronoIcons.GitHub,
                        contentDescription = "GitHub",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            ) {
                Text("Source code")
            }
        }
    }
}

private fun openGitHubRepository(context: Context) {
    val intent = Intent(
        Intent.ACTION_VIEW,
        "https://github.com/dand104/ChronoPaper".toUri()
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
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