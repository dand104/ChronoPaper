package org.chronopaper.chronopaper.ui.screens.home

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import org.chronopaper.chronopaper.domain.model.PhotoCrop
import org.chronopaper.chronopaper.domain.model.WallpaperMode
import org.chronopaper.chronopaper.service.ChronoWallpaperService
import org.chronopaper.chronopaper.ui.components.ChronoIcons
import org.chronopaper.chronopaper.ui.components.LocalWallpaperThumbnail
import org.chronopaper.chronopaper.ui.theme.ExpressiveActivationBar

@Composable
fun HomeScreen(
    onNavigateToWallpapers: () -> Unit,
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
    val staticPreview = previewState as? ActiveWallpaperState.StaticBitmap
    val previewFilePath = staticPreview?.filePath
    val previewCrop = staticPreview?.crop ?: PhotoCrop.DEFAULT

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
                Text(
                    text = "ChronoPaper",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                EngineStatusBadge(isActive = isLiveWallpaperActive)
            }
        }

        item {
            HeroPreviewCard(
                title = state.activeTitle,
                formatLabel = state.activeFormatLabel,
                mode = state.settings.mode,
                filePath = previewFilePath,
                crop = previewCrop,
                isPreviewNight = isPreviewNight,
                isServiceActive = isLiveWallpaperActive,
                hasAnyWallpaper = state.hasAnyWallpaper,
                isFromWallpaperSettings = isFromWallpaperSettings,
                onTogglePreviewTheme = { viewModel.togglePreviewTheme(systemDark) },
                onRotateNow = viewModel::rotateNow,
                onActivateService = { launchLiveWallpaperPicker(context) }
            )
        }

        item {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Activation mode",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = modeSubtitle(state.settings.mode),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    ExpressiveActivationBar(
                        selectedMode = state.settings.mode,
                        onSelectMode = viewModel::selectMode,
                        tall = true
                    )
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,

                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .clickable { viewModel.toggleAutoRotate(!state.settings.autoRotateEnabled) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Playlist auto-rotation",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Cycle sets every ${state.settings.rotationIntervalHours}h (${state.totalCollectionsCount} total)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = state.settings.autoRotateEnabled,
                        onCheckedChange = null
                    )
                }
            }
        }

        item {
            FilledTonalButton(
                onClick = onNavigateToWallpapers,
                shape = RoundedCornerShape(28.dp),
                contentPadding = PaddingValues(vertical = 18.dp, horizontal = 24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = ChronoIcons.Pairs,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Manage sets (${state.totalCollectionsCount})",
                    style = MaterialTheme.typography.labelLarge
                )
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
    formatLabel: String,
    mode: WallpaperMode,
    filePath: String?,
    crop: PhotoCrop,
    isPreviewNight: Boolean,
    isServiceActive: Boolean,
    hasAnyWallpaper: Boolean,
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
                    crop = crop,
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
                                    Color.Black.copy(alpha = 0.75f)
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalIconButton(onClick = onTogglePreviewTheme) {
                            Icon(
                                imageVector = if (isPreviewNight) ChronoIcons.Moon else ChronoIcons.Solar,
                                contentDescription = "Toggle Day/Night preview"
                            )
                        }
                        if (hasAnyWallpaper) {
                            FilledTonalIconButton(onClick = onRotateNow) {
                                Icon(
                                    imageVector = ChronoIcons.Refresh,
                                    contentDescription = "Next set"
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(20.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    Text(
                        text = "$formatLabel · ${modeTitle(mode)}",
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
                        enabled = hasAnyWallpaper,
                        shape = RoundedCornerShape(28.dp),
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
                            text = when {
                                !hasAnyWallpaper -> "Add a wallpaper set first"
                                isServiceActive -> "Open system live wallpaper screen"
                                else -> "Set ChronoPaper as wallpaper"
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

private fun modeTitle(mode: WallpaperMode): String = when (mode) {
    WallpaperMode.THEME -> "System theme"
    WallpaperMode.SOLAR -> "By sun position"
    WallpaperMode.TIME -> "By time schedule"
}

private fun modeSubtitle(mode: WallpaperMode): String = when (mode) {
    WallpaperMode.THEME -> "Switches frames with system theme"
    WallpaperMode.SOLAR -> "Follows sun altitude & azimuth"
    WallpaperMode.TIME -> "Distributes frames across the clock"
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