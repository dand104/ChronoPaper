package org.chronopaper.chronopaper.ui.screens.pairs

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.chronopaper.chronopaper.domain.astronomy.SolarPosition
import org.chronopaper.chronopaper.domain.model.DynamicHeicPack
import org.chronopaper.chronopaper.domain.model.PairedWallpaperSet
import org.chronopaper.chronopaper.domain.model.SolarFrame
import org.chronopaper.chronopaper.domain.model.WallpaperMode
import org.chronopaper.chronopaper.ui.components.ChronoIcons
import org.chronopaper.chronopaper.ui.components.LocalWallpaperThumbnail
import org.chronopaper.chronopaper.ui.components.rememberUriImageBitmap
import org.chronopaper.chronopaper.ui.theme.ExpressiveActivationBar
import java.util.Locale

@Composable
fun PairedSetsScreen(
    viewModel: PairedSetsViewModel = viewModel(factory = PairedSetsViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showCreateSheet by rememberSaveable { mutableStateOf(false) }
    var editingManualSetId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingHeicPackId by rememberSaveable { mutableStateOf<String?>(null) }

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
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (!state.isImportingHeic) {
                            heicLauncher.launch(
                                arrayOf("image/heic", "image/heif", "application/octet-stream", "*/*")
                            )
                        }
                    },
                    shape = RoundedCornerShape(
                        topStart = 28.dp,
                        bottomStart = 28.dp,
                        topEnd = 8.dp,
                        bottomEnd = 8.dp
                    ),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    icon = {
                        if (state.isImportingHeic) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(imageVector = ChronoIcons.Solar, contentDescription = null)
                        }
                    },
                    text = { Text(if (state.isImportingHeic) "Unpacking..." else "Import .heic") }
                )

                ExtendedFloatingActionButton(
                    onClick = { showCreateSheet = true },
                    shape = RoundedCornerShape(
                        topStart = 8.dp,
                        bottomStart = 8.dp,
                        topEnd = 28.dp,
                        bottomEnd = 28.dp
                    ),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    icon = { Icon(imageVector = ChronoIcons.Add, contentDescription = null) },
                    text = { Text("New set") }
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 108.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Wallpapers",
                        style = MaterialTheme.typography.displaySmall
                    )
                }
            }

            if (state.activeTrajectoryFrames.size >= 2 && state.activeTrajectoryTitle != null) {
                item {
                    UnifiedSolarTrajectoryCard(
                        title = state.activeTrajectoryTitle!!,
                        frames = state.activeTrajectoryFrames,
                        sunPosition = state.currentSunPosition,
                        closestFrameIndex = state.closestFrameIndex
                    )
                }
            }

            state.errorMessage?.let { error ->
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
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
                                Text("OK")
                            }
                        }
                    }
                }
            }

            if (state.sets.isEmpty() && state.heicPacks.isEmpty()) {
                item {
                    EmptyCollectionsCard()
                }
            } else {
                items(
                    items = state.sets,
                    key = { "pair_${it.id}" }
                ) { set ->
                    val isSelected = set.id == state.activePairId
                    ManualSetCard(
                        set = set,
                        isSelected = isSelected,
                        activeMode = if (isSelected) state.activeMode else set.mode,
                        closestFrameIndex = if (isSelected) state.closestFrameIndex else null,
                        onSelect = { mode -> viewModel.activatePair(set.id, mode) },
                        onEdit = { editingManualSetId = set.id }
                    )
                }

                items(
                    items = state.heicPacks,
                    key = { "heic_${it.id}" }
                ) { pack ->
                    val isSelected = pack.id == state.activeHeicPackId
                    HeicContainerCard(
                        pack = pack,
                        isSelected = isSelected,
                        activeMode = if (isSelected) state.activeMode else pack.mode,
                        closeFrameIndex = if (isSelected) state.closestFrameIndex else null,
                        onSelect = { mode -> viewModel.activateHeicPack(pack.id, mode) },
                        onEdit = { editingHeicPackId = pack.id }
                    )
                }
            }
        }
    }

    if (showCreateSheet) {
        val metrics = context.resources.displayMetrics
        CreateManualSetBottomSheet(
            editingSet = null,
            isSaving = state.isSaving,
            onDismiss = { showCreateSheet = false },
            onConfirm = { title, sources, mode ->
                viewModel.createManualSet(
                    title = title,
                    imageUris = sources.map(Uri::parse),
                    mode = mode,
                    targetWidth = metrics.widthPixels,
                    targetHeight = metrics.heightPixels,
                    onComplete = { showCreateSheet = false }
                )
            },
            onDelete = null
        )
    }

    val editingSet = editingManualSetId?.let { id -> state.sets.firstOrNull { it.id == id } }
    if (editingSet != null) {
        val metrics = context.resources.displayMetrics
        CreateManualSetBottomSheet(
            editingSet = editingSet,
            isSaving = state.isSaving,
            onDismiss = { editingManualSetId = null },
            onConfirm = { title, sources, mode ->
                viewModel.updateManualSet(
                    pairId = editingSet.id,
                    title = title,
                    sources = sources,
                    mode = mode,
                    targetWidth = metrics.widthPixels,
                    targetHeight = metrics.heightPixels,
                    onComplete = { editingManualSetId = null }
                )
            },
            onDelete = {
                viewModel.deletePair(editingSet.id)
                editingManualSetId = null
            }
        )
    }

    val editingHeic = editingHeicPackId?.let { id -> state.heicPacks.firstOrNull { it.id == id } }
    if (editingHeic != null) {
        EditHeicPackBottomSheet(
            pack = editingHeic,
            onDismiss = { editingHeicPackId = null },
            onSave = { title, mode, swapped ->
                viewModel.updateHeicPack(
                    packId = editingHeic.id,
                    title = title,
                    mode = mode,
                    swapLightDark = swapped,
                    onComplete = { editingHeicPackId = null }
                )
            },
            onDelete = {
                viewModel.deleteHeicPack(editingHeic.id)
                editingHeicPackId = null
            }
        )
    }
}

@Composable
private fun ManualSetCard(
    set: PairedWallpaperSet,
    isSelected: Boolean,
    activeMode: WallpaperMode,
    closestFrameIndex: Int?,
    onSelect: (WallpaperMode?) -> Unit,
    onEdit: () -> Unit
) {
    val cornerRadius by animateDpAsState(
        targetValue = if (isSelected) 32.dp else 22.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "ManualCardCorner"
    )

    Surface(
        shape = RoundedCornerShape(cornerRadius),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .clickable { onSelect(null) }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CollectionCardHeader(
                title = set.title,
                subtitle = "Set · ${set.frameCount} photo(s)",
                isSelected = isSelected,
                onEdit = onEdit
            )

            ExpressiveActivationBar(
                selectedMode = activeMode,
                allowedModes = set.allowedModes,
                onSelectMode = { mode -> onSelect(mode) }
            )

            if (set.imagePaths.size <= 2) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(164.dp)
                        .clip(RoundedCornerShape(22.dp)),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    set.imagePaths.forEachIndexed { idx, path ->
                        val isNightTile = set.imagePaths.size == 2 && idx == 1
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            LocalWallpaperThumbnail(
                                filePath = path,
                                contentDescription = "${set.title} #${idx + 1}",
                                isNightPlaceholder = isNightTile,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                shape = CircleShape,
                                color = if (isNightTile) Color.Black.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.85f),
                                contentColor = if (isNightTile) Color.White else Color.Black,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isNightTile) ChronoIcons.Moon else ChronoIcons.Solar,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = when {
                                            set.imagePaths.size == 1 -> "Single"
                                            isNightTile -> "Night / #2"
                                            else -> "Day / #1"
                                        },
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                val solarFrames = set.toSolarFrames()
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(
                        items = solarFrames,
                        key = { it.frameIndex }
                    ) { frame ->
                        SolarFramePreviewTile(
                            frame = frame,
                            totalFrames = solarFrames.size,
                            isCurrentMatch = frame.frameIndex == closestFrameIndex
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeicContainerCard(
    pack: DynamicHeicPack,
    isSelected: Boolean,
    activeMode: WallpaperMode,
    closeFrameIndex: Int?,
    onSelect: (WallpaperMode?) -> Unit,
    onEdit: () -> Unit
) {
    val cornerRadius by animateDpAsState(
        targetValue = if (isSelected) 32.dp else 22.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "HeicCardCorner"
    )

    Surface(
        shape = RoundedCornerShape(cornerRadius),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .clickable { onSelect(null) }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CollectionCardHeader(
                title = pack.title,
                subtitle = "Set · ${pack.frameCount} frames",
                isSelected = isSelected,
                onEdit = onEdit
            )
            ExpressiveActivationBar(
                selectedMode = activeMode,
                allowedModes = pack.allowedModes,
                onSelectMode = { mode -> onSelect(mode) }
            )
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
                        totalFrames = pack.frames.size,
                        isCurrentMatch = frame.frameIndex == closeFrameIndex
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionCardHeader(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (isSelected) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = ChronoIcons.Check,
                            contentDescription = "Active",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge
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

        IconButton(onClick = onEdit) {
            Icon(
                imageVector = ChronoIcons.Edit,
                contentDescription = "Edit collection"
            )
        }
    }
}

@Composable
private fun SolarFramePreviewTile(
    frame: SolarFrame,
    totalFrames: Int,
    isCurrentMatch: Boolean
) {
    val hourLabel = if (totalFrames >= 2) {
        val startHour = ((frame.frameIndex.toDouble() / totalFrames.toDouble()) * 24.0).toInt() % 24
        String.format(Locale.US, "%02d:00", startHour)
    } else {
        "All day"
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isCurrentMatch) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        modifier = Modifier.width(124.dp)
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
                text = String.format(Locale.US, "#%d · %.0f° · %s", frame.frameIndex + 1, frame.altitude, hourLabel),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun UnifiedSolarTrajectoryCard(
    title: String,
    frames: List<SolarFrame>,
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Time map: $title",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = String.format(
                            Locale.US,
                            "Sun: %.1f° az, %.1f° alt · active frame #%s",
                            sunPosition.azimuthDegrees,
                            sunPosition.altitudeDegrees,
                            closestFrameIndex?.plus(1)?.toString() ?: "—"
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
                        text = "${frames.size} frames",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
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

                val sortedFrames = frames.sortedBy { it.azimuth }
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
        }
    }
}

@Composable
private fun EmptyCollectionsCard() {
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
                imageVector = ChronoIcons.Pairs,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = "No wallpaper collections yet",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Create a set or import an .heic container.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CreateManualSetBottomSheet(
    editingSet: PairedWallpaperSet?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (title: String, sources: List<String>, mode: WallpaperMode) -> Unit,
    onDelete: (() -> Unit)?
) {
    var title by rememberSaveable(editingSet?.id) {
        mutableStateOf(editingSet?.title.orEmpty())
    }
    var uriStrings by rememberSaveable(editingSet?.id) {
        mutableStateOf(editingSet?.imagePaths ?: emptyList())
    }
    var selectedModeName by rememberSaveable(editingSet?.id) {
        mutableStateOf((editingSet?.mode ?: WallpaperMode.THEME).name)
    }

    val photoCount = uriStrings.size

    val allowedModes = rememberAllowedModesForCount(photoCount)
    val effectiveMode = WallpaperMode.fromNameOrDefault(selectedModeName)
        .let { if (it in allowedModes) it else allowedModes.first() }

    val pickSingleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            uriStrings = uriStrings + uri.toString()
        }
    }

    val pickMultipleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 24)
    ) { uris ->
        if (uris.isNotEmpty()) {
            uriStrings = uriStrings + uris.map { it.toString() }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isSaving) onDismiss() },
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (editingSet != null) "Edit Set" else "New Set",
                    style = MaterialTheme.typography.headlineMedium
                )
                if (onDelete != null) {
                    FilledTonalButton(
                        onClick = onDelete,
                        enabled = !isSaving,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(
                            imageVector = ChronoIcons.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete")
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Collection name") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        pickMultipleLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = ChronoIcons.Pairs, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pick multiple")
                }

                FilledTonalButton(
                    onClick = {
                        pickSingleLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = ChronoIcons.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add 1 photo")
                }
            }

            if (uriStrings.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    itemsIndexed(uriStrings) { idx, source ->
                        SourcePreviewChip(
                            index = idx,
                            total = photoCount,
                            source = source,
                            mode = effectiveMode,
                            onRemove = {
                                uriStrings = uriStrings.toMutableList().also { it.removeAt(idx) }
                            }
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable {
                            pickMultipleLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Tap to pick photos",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (photoCount >= 1) {
                ExpressiveActivationBar(
                    selectedMode = effectiveMode,
                    allowedModes = allowedModes,
                    onSelectMode = { mode -> selectedModeName = mode.name }
                )

                if (effectiveMode == WallpaperMode.THEME && photoCount == 2) {
                    FilledTonalButton(
                        onClick = { uriStrings = uriStrings.reversed() },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = ChronoIcons.Swap,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Swap photos")
                    }
                }
            }

            Button(
                onClick = {
                    if (uriStrings.isNotEmpty()) {
                        onConfirm(title, uriStrings, effectiveMode)
                    }
                },
                enabled = !isSaving && uriStrings.isNotEmpty(),
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Optimizing to WebP...")
                } else {
                    Text(
                        if (editingSet != null) {
                            "Save changes ($photoCount photo${if (photoCount == 1) "" else "s"})"
                        } else {
                            "Save & add ($photoCount photo${if (photoCount == 1) "" else "s"})"
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EditHeicPackBottomSheet(
    pack: DynamicHeicPack,
    onDismiss: () -> Unit,
    onSave: (title: String, mode: WallpaperMode, swapLightDark: Boolean) -> Unit,
    onDelete: () -> Unit
) {
    var title by rememberSaveable(pack.id) { mutableStateOf(pack.title) }
    var selectedModeName by rememberSaveable(pack.id) { mutableStateOf(pack.mode.name) }
    var swappedLightDark by rememberSaveable(pack.id) { mutableStateOf(false) }

    val effectiveMode = WallpaperMode.fromNameOrDefault(selectedModeName)
        .let { if (it in pack.allowedModes) it else pack.allowedModes.first() }

    val baseLightIdx = pack.lightDefaultIndex
        ?: pack.frames.firstOrNull { it.isLightDefault }?.frameIndex
        ?: 0
    val baseDarkIdx = pack.darkDefaultIndex
        ?: pack.frames.firstOrNull { it.isDarkDefault }?.frameIndex
        ?: pack.frames.lastIndex.coerceAtLeast(0)

    val currentLightIdx = if (swappedLightDark) baseDarkIdx else baseLightIdx
    val currentDarkIdx = if (swappedLightDark) baseLightIdx else baseDarkIdx

    val lightFrame = pack.frames.firstOrNull { it.frameIndex == currentLightIdx } ?: pack.frames.firstOrNull()
    val darkFrame = pack.frames.firstOrNull { it.frameIndex == currentDarkIdx } ?: pack.frames.lastOrNull()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Edit Set",
                    style = MaterialTheme.typography.headlineMedium
                )
                FilledTonalButton(
                    onClick = onDelete,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(
                        imageVector = ChronoIcons.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete")
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Collection name") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            )

            ExpressiveActivationBar(
                selectedMode = effectiveMode,
                allowedModes = pack.allowedModes,
                onSelectMode = { mode -> selectedModeName = mode.name }
            )

            if (effectiveMode == WallpaperMode.THEME && lightFrame != null && darkFrame != null && pack.frameCount >= 2) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(20.dp)),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        LocalWallpaperThumbnail(
                            filePath = lightFrame.filePath,
                            contentDescription = "Light",
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.85f),
                            contentColor = Color.Black,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "Light",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        LocalWallpaperThumbnail(
                            filePath = darkFrame.filePath,
                            contentDescription = "Dark",
                            isNightPlaceholder = true,
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.75f),
                            contentColor = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "Dark",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                FilledTonalButton(
                    onClick = { swappedLightDark = !swappedLightDark },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = ChronoIcons.Swap,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Swap Light & Dark frames")
                }
            }

            Button(
                onClick = { onSave(title, effectiveMode, swappedLightDark) },
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save changes")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun rememberAllowedModesForCount(count: Int): List<WallpaperMode> = buildList {
    if (count <= 2) add(WallpaperMode.THEME)
    if (count >= 2) {
        add(WallpaperMode.SOLAR)
        add(WallpaperMode.TIME)
    }
}.ifEmpty { listOf(WallpaperMode.THEME) }

@Composable
private fun SourcePreviewChip(
    index: Int,
    total: Int,
    source: String,
    mode: WallpaperMode,
    onRemove: () -> Unit
) {
    val isLocalFile = !source.startsWith("content://")
    val badgeText = when {
        total == 1 -> "Light & Dark"
        mode == WallpaperMode.THEME -> if (index == 0) "Light" else "Dark"
        mode == WallpaperMode.TIME -> {
            val hour = ((index.toDouble() / total.toDouble()) * 24.0).toInt() % 24
            String.format(Locale.US, "%02d:00", hour)
        }
        else -> "Sun #${index + 1}"
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .width(128.dp)
            .height(148.dp)
    ) {
        Box {
            if (isLocalFile) {
                LocalWallpaperThumbnail(
                    filePath = source,
                    contentDescription = badgeText,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val bitmap by rememberUriImageBitmap(Uri.parse(source))
                val currentBitmap = bitmap
                if (currentBitmap != null) {
                    Image(
                        bitmap = currentBitmap,
                        contentDescription = badgeText,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.65f),
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f),
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRemove)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = ChronoIcons.Delete,
                        contentDescription = "Remove frame",
                        modifier = Modifier.size(14.dp)
                    )
                }
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