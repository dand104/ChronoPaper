package org.chronopaper.chronopaper.ui.screens.home

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.chronopaper.chronopaper.domain.model.ActiveWallpaperState
import org.chronopaper.chronopaper.domain.model.WallpaperMode
import org.chronopaper.chronopaper.service.ChronoWallpaperService
import org.chronopaper.chronopaper.ui.components.ChronoIcons
import org.chronopaper.chronopaper.ui.components.LocalWallpaperThumbnail
import java.util.Locale

@Composable
fun HomeScreen(
    onNavigateToPairs: () -> Unit,
    onNavigateToHeic: () -> Unit,
    isFromWallpaperSettings: Boolean = false,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val isPreviewNight = state.previewNightOverride ?: systemDark

    var isLiveWallpaperActive by remember {
        mutableStateOf(isChronoServiceActive(context))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isLiveWallpaperActive = isChronoServiceActive(context)
                viewModel.requestImmediateEngineRefresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val previewState = if (isPreviewNight) state.activeDarkState else state.activeLightState
    val previewFilePath = (previewState as? ActiveWallpaperState.StaticBitmap)?.filePath

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ChronoPaper",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                EngineStatusBadge(isActive = isLiveWallpaperActive)
            }
        }

        item {
            HeroPreviewCard(
                title = state.activeTitle,
                mode = state.settings.mode,
                filePath = previewFilePath,
                isPreviewNight = isPreviewNight,
                solarAltitude = state.solarPosition.altitudeDegrees,
                solarAzimuth = state.solarPosition.azimuthDegrees,
                isServiceActive = isLiveWallpaperActive,
                isFromWallpaperSettings = isFromWallpaperSettings,
                onTogglePreviewTheme = { viewModel.togglePreviewTheme(systemDark) },
                onRotateNow = viewModel::rotateNow,
                onActivateService = { launchLiveWallpaperPicker(context) }
            )
        }

        item {
            Text(
                text = "Variants",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        item {
            ModeSelectorGrid(
                selectedMode = state.settings.mode,
                pairsCount = state.pairsCount,
                heicCount = state.heicPacksCount,
                rotationHours = state.settings.rotationIntervalHours,
                onSelectMode = viewModel::selectMode
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = onNavigateToPairs,
                    shape = RoundedCornerShape(22.dp),
                    contentPadding = PaddingValues(vertical = 16.dp, horizontal = 16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = ChronoIcons.Pairs,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pairs (${state.pairsCount})")
                }

                FilledTonalButton(
                    onClick = onNavigateToHeic,
                    shape = RoundedCornerShape(22.dp),
                    contentPadding = PaddingValues(vertical = 16.dp, horizontal = 16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = ChronoIcons.Solar,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("HEIC (${state.heicPacksCount})")
                }
            }
        }
    }
}

@Composable
private fun EngineStatusBadge(isActive: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
            )
            Text(
                text = if (isActive) "Active" else "Disabled",
                style = MaterialTheme.typography.labelLarge,
                color = if (isActive) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
            )
        }
    }
}

@Composable
private fun HeroPreviewCard(
    title: String,
    mode: WallpaperMode,
    filePath: String?,
    isPreviewNight: Boolean,
    solarAltitude: Double,
    solarAzimuth: Double,
    isServiceActive: Boolean,
    isFromWallpaperSettings: Boolean = false,
    onTogglePreviewTheme: () -> Unit,
    onRotateNow: () -> Unit,
    onActivateService: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(36.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
            ) {
                LocalWallpaperThumbnail(
                    filePath = filePath,
                    contentDescription = title,
                    isNightPlaceholder = isPreviewNight,
                    maxDimensionPx = 1280,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.28f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.72f)
                                )
                            )
                        )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.45f),
                        contentColor = Color.White
                    ) {
                        Text(
                            text = String.format(
                                Locale.US,
                                "Sun: %.1f° alt. · %.0f° az.",
                                solarAltitude,
                                solarAzimuth
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalIconButton(onClick = onTogglePreviewTheme) {
                            Icon(
                                imageVector = if (isPreviewNight) ChronoIcons.Moon else ChronoIcons.Solar,
                                contentDescription = "Toggle Day/Night preview"
                            )
                        }
                        FilledTonalIconButton(onClick = onRotateNow) {
                            Icon(
                                imageVector = ChronoIcons.Refresh,
                                contentDescription = "Next clip"
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(20.dp)
                ) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    Text(
                        text = modeTitle(mode),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
            if (!isFromWallpaperSettings) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onActivateService,
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(vertical = 16.dp, horizontal = 20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isServiceActive) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            contentColor = if (isServiceActive) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimary
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = ChronoIcons.Wallpaper,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isServiceActive) {
                                "Open the live wallpaper system screen"
                            } else {
                                "Set ChronoPaper as wallpaper"
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeSelectorGrid(
    selectedMode: WallpaperMode,
    pairsCount: Int,
    heicCount: Int,
    rotationHours: Int,
    onSelectMode: (WallpaperMode) -> Unit
) {
    val items = listOf(
        Triple(
            WallpaperMode.STATIC_PAIR,
            "Paired Day / Night",
            "Response on the system theme ($pairsCount pcs.)"
        ),
        Triple(
            WallpaperMode.PLAYLIST,
            "Pairs playlist",
            "Automatic change every $rotationHours hours."
        ),
        Triple(
            WallpaperMode.APPLE_SOLAR,
            "Solar HEIC",
            "Along the path of the sun ($heicCount pcs.)"
        ),
        Triple(
            WallpaperMode.APPLE_APR,
            "Day/Night from .heic",
            "Daytime and nighttime shot in .heic"
        )
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (rowItems in items.chunked(2)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for ((mode, title, subtitle) in rowItems) {
                    val isSelected = selectedMode == mode
                    Card(
                        onClick = { onSelectMode(mode) },
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = when (mode) {
                                        WallpaperMode.STATIC_PAIR,
                                        WallpaperMode.PLAYLIST -> ChronoIcons.Pairs

                                        WallpaperMode.APPLE_SOLAR,
                                        WallpaperMode.APPLE_APR -> ChronoIcons.Solar
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = ChronoIcons.Check,
                                        contentDescription = "Selected",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun modeTitle(mode: WallpaperMode): String = when (mode) {
    WallpaperMode.STATIC_PAIR -> "Synchronization with the system theme"
    WallpaperMode.PLAYLIST -> "Paired wallpapers playlist by schedule"
    WallpaperMode.APPLE_SOLAR -> "Astronomical calculation of the sun's height"
    WallpaperMode.APPLE_APR -> "Day and night mode from HEIC"
}

private fun isChronoServiceActive(context: Context): Boolean {
    return runCatching {
        val wm = WallpaperManager.getInstance(context)
        val info = wm.wallpaperInfo
        info?.component == ComponentName(context, ChronoWallpaperService::class.java)
    }.getOrDefault(false)
}

private fun launchLiveWallpaperPicker(context: Context) {
    val component = ComponentName(context, ChronoWallpaperService::class.java)
    val directIntent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
        putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(directIntent)
    }.onFailure {
        val fallbackIntent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(fallbackIntent) }
    }
}