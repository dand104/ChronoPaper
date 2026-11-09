package org.chronopaper.chronopaper.ui.screens.heic

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.chronopaper.chronopaper.domain.astronomy.SolarPosition
import org.chronopaper.chronopaper.domain.model.DynamicHeicPack
import org.chronopaper.chronopaper.domain.model.HeicScheduleType
import org.chronopaper.chronopaper.domain.model.SolarFrame
import org.chronopaper.chronopaper.domain.model.WallpaperMode
import org.chronopaper.chronopaper.ui.components.ChronoIcons
import org.chronopaper.chronopaper.ui.components.LocalWallpaperThumbnail
import java.util.Locale

@Composable
fun HeicImportScreen(
    viewModel: HeicImportViewModel = viewModel(factory = HeicImportViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val heicLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val metrics = context.resources.displayMetrics
            val fileName = resolveDisplayName(context, uri) ?: "Dynamic"
            viewModel.importHeicUri(
                uri = uri,
                suggestedTitle = fileName,
                targetWidth = metrics.widthPixels,
                targetHeight = metrics.heightPixels
            )
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (!state.isImporting) {
                        heicLauncher.launch(
                            arrayOf("image/heic", "image/heif", "application/octet-stream", "*/*")
                        )
                    }
                },
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                icon = {
                    if (state.isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(imageVector = ChronoIcons.Add, contentDescription = null)
                    }
                },
                text = {
                    Text(if (state.isImporting) "Unpacking frames..." else "Import .heic")
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Solar HEIC",
                        style = MaterialTheme.typography.displaySmall
                    )
                }
            }

            state.errorMessage?.let { error ->
                item {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = viewModel::dismissError) {
                                Text("Close")
                            }
                        }
                    }
                }
            }

            val activePack = state.activePack
            if (activePack != null) {
                item {
                    SolarTrajectoryCard(
                        pack = activePack,
                        sunPosition = state.currentSunPosition,
                        closestFrameIndex = state.closestFrameIndex
                    )
                }
            }

            if (state.packs.isEmpty()) {
                item {
                    EmptyHeicCard(
                        isImporting = state.isImporting,
                        onImportClick = {
                            heicLauncher.launch(
                                arrayOf("image/heic", "image/heif", "application/octet-stream", "*/*")
                            )
                        }
                    )
                }
            } else {
                items(
                    items = state.packs,
                    key = { it.id }
                ) { pack ->
                    val isSelected = pack.id == state.activePack?.id
                    HeicPackCard(
                        pack = pack,
                        isSelected = isSelected,
                        activeMode = state.activeMode,
                        closestFrameIndex = if (isSelected) state.closestFrameIndex else null,
                        onSelectPack = { mode -> viewModel.selectPack(pack, mode) },
                        onDeletePack = { viewModel.deletePack(pack.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SolarTrajectoryCard(
    pack: DynamicHeicPack,
    sunPosition: SolarPosition,
    closestFrameIndex: Int?
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Solar map: ${pack.title}",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = String.format(
                            Locale.US,
                            "Now: azimuth %.1f°, attitude %.1f° · frame #%s",
                            sunPosition.azimuthDegrees,
                            sunPosition.altitudeDegrees,
                            closestFrameIndex?.toString() ?: "—"
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = onSurfaceVariant
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        text = "${pack.frames.size} frames",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(20.dp))
            ) {
                val width = size.width
                val height = size.height

                fun azToX(az: Double): Float =
                    ((az.coerceIn(0.0, 360.0) / 360.0) * width).toFloat()

                fun altToY(alt: Double): Float {
                    val normalized = (90.0 - alt.coerceIn(-90.0, 90.0)) / 180.0
                    return (normalized * height).toFloat()
                }

                val horizonY = altToY(0.0)
                drawLine(
                    color = outlineColor,
                    start = Offset(0f, horizonY),
                    end = Offset(width, horizonY),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                )

                val sortedFrames = pack.frames.sortedBy { it.azimuth }
                if (sortedFrames.size >= 2) {
                    val trajectoryPath = Path().apply {
                        sortedFrames.forEachIndexed { idx, frame ->
                            val x = azToX(frame.azimuth)
                            val y = altToY(frame.altitude)
                            if (idx == 0) moveTo(x, y) else lineTo(x, y)
                        }
                    }
                    drawPath(
                        path = trajectoryPath,
                        color = primaryColor.copy(alpha = 0.45f),
                        style = Stroke(width = 3.dp.toPx())
                    )
                }

                sortedFrames.forEach { frame ->
                    val center = Offset(azToX(frame.azimuth), altToY(frame.altitude))
                    val isActiveFrame = frame.frameIndex == closestFrameIndex
                    drawCircle(
                        color = if (isActiveFrame) primaryColor else primaryColor.copy(alpha = 0.55f),
                        radius = if (isActiveFrame) 8.dp.toPx() else 4.5.dp.toPx(),
                        center = center
                    )
                }

                val sunOffset = Offset(
                    x = azToX(sunPosition.azimuthDegrees),
                    y = altToY(sunPosition.altitudeDegrees)
                )
                drawCircle(
                    color = tertiaryColor.copy(alpha = 0.28f),
                    radius = 14.dp.toPx(),
                    center = sunOffset
                )
                drawCircle(
                    color = tertiaryColor,
                    radius = 6.dp.toPx(),
                    center = sunOffset
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("0° N", style = MaterialTheme.typography.labelMedium, color = onSurfaceVariant)
                Text("90° E", style = MaterialTheme.typography.labelMedium, color = onSurfaceVariant)
                Text("180° S (noon)", style = MaterialTheme.typography.labelMedium, color = onSurfaceVariant)
                Text("270° W", style = MaterialTheme.typography.labelMedium, color = onSurfaceVariant)
                Text("360°", style = MaterialTheme.typography.labelMedium, color = onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HeicPackCard(
    pack: DynamicHeicPack,
    isSelected: Boolean,
    activeMode: WallpaperMode,
    closestFrameIndex: Int?,
    onSelectPack: (WallpaperMode) -> Unit,
    onDeletePack: () -> Unit
) {
    Card(
        onClick = {
            val defaultMode = if (pack.scheduleType == HeicScheduleType.SOLAR) {
                WallpaperMode.APPLE_SOLAR
            } else {
                WallpaperMode.APPLE_APR
            }
            onSelectPack(defaultMode)
        },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pack.title,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "${pack.frames.size} frames · Format ${pack.scheduleType.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                IconButton(onClick = onDeletePack) {
                    Icon(
                        imageVector = ChronoIcons.Delete,
                        contentDescription = "Delete .heic package"
                    )
                }
            }

            if (pack.scheduleType == HeicScheduleType.SOLAR) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isSelected && activeMode == WallpaperMode.APPLE_SOLAR,
                        onClick = { onSelectPack(WallpaperMode.APPLE_SOLAR) },
                        label = { Text("By the sun (SunCalc)") }
                    )
                    FilterChip(
                        selected = isSelected && activeMode == WallpaperMode.APPLE_APR,
                        onClick = { onSelectPack(WallpaperMode.APPLE_APR) },
                        label = { Text("Only Day / Night") }
                    )
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(
                    items = pack.frames,
                    key = { it.frameIndex }
                ) { frame ->
                    SolarFramePreviewTile(
                        frame = frame,
                        isCurrentSolarMatch = frame.frameIndex == closestFrameIndex
                    )
                }
            }
        }
    }
}

@Composable
private fun SolarFramePreviewTile(
    frame: SolarFrame,
    isCurrentSolarMatch: Boolean
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isCurrentSolarMatch) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        modifier = Modifier.width(120.dp)
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .clip(RoundedCornerShape(14.dp))
            ) {
                LocalWallpaperThumbnail(
                    filePath = frame.filePath,
                    contentDescription = "Frame #${frame.frameIndex}",
                    isNightPlaceholder = frame.altitude < 0,
                    maxDimensionPx = 400,
                    modifier = Modifier.fillMaxSize()
                )

                if (frame.isLightDefault || frame.isDarkDefault) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.65f),
                        contentColor = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = if (frame.isLightDefault) "Day" else "Night",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = String.format(Locale.US, "#%d · %.0f°", frame.frameIndex, frame.altitude),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun EmptyHeicCard(
    isImporting: Boolean,
    onImportClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = ChronoIcons.Solar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = "Import .heic wallpapers",
                style = MaterialTheme.typography.titleLarge
            )
            Button(
                onClick = onImportClick,
                enabled = !isImporting,
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(imageVector = ChronoIcons.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isImporting) "Unpacking..." else "Choose .heic file")
            }
        }
    }
}

private fun resolveDisplayName(context: Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else {
                    null
                }
            }
    }.getOrNull()
}