package org.chronopaper.chronopaper.ui.screens.pairs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.chronopaper.chronopaper.domain.model.PairedWallpaperSet
import org.chronopaper.chronopaper.ui.components.ChronoIcons
import org.chronopaper.chronopaper.ui.components.LocalWallpaperThumbnail
import org.chronopaper.chronopaper.ui.components.rememberUriImageBitmap

@Composable
fun PairedSetsScreen(
    viewModel: PairedSetsViewModel = viewModel(factory = PairedSetsViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showCreateSheet by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateSheet = true },
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                icon = {
                    Icon(
                        imageVector = ChronoIcons.Add,
                        contentDescription = null
                    )
                },
                text = { Text("New pair") }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Paired wallpaper",
                        style = MaterialTheme.typography.displaySmall
                    )
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
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
                                text = "Playlist mode",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Automatic switching of the active pair every ${state.rotationIntervalHours} hours.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = state.isPlaylistMode,
                            onCheckedChange = viewModel::togglePlaylistRotation
                        )
                    }
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

            if (state.sets.isEmpty()) {
                item {
                    EmptyPairsCard(onCreateClick = { showCreateSheet = true })
                }
            } else {
                items(
                    items = state.sets,
                    key = { it.id }
                ) { set ->
                    PairedSetCard(
                        set = set,
                        isSelected = set.id == state.activePairId,
                        onSelect = { viewModel.activatePair(set.id) },
                        onDelete = { viewModel.deletePair(set.id) }
                    )
                }
            }
        }
    }

    if (showCreateSheet) {
        val metrics = context.resources.displayMetrics
        CreatePairedSetBottomSheet(
            isSaving = state.isSaving,
            onDismiss = { showCreateSheet = false },
            onConfirm = { title, lightUri, darkUri ->
                viewModel.createPairedSet(
                    title = title,
                    lightUri = lightUri,
                    darkUri = darkUri,
                    targetWidth = metrics.widthPixels,
                    targetHeight = metrics.heightPixels,
                    onComplete = { showCreateSheet = false }
                )
            }
        )
    }
}

@Composable
private fun PairedSetCard(
    set: PairedWallpaperSet,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onSelect,
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
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(22.dp)),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    LocalWallpaperThumbnail(
                        filePath = set.lightPath,
                        contentDescription = "${set.title} (Day)",
                        isNightPlaceholder = false,
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.85f),
                        contentColor = Color.Black,
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
                                imageVector = ChronoIcons.Solar,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Text("Light", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    LocalWallpaperThumbnail(
                        filePath = set.darkPath,
                        contentDescription = "${set.title} (Night)",
                        isNightPlaceholder = true,
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.75f),
                        contentColor = Color.White,
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
                                imageVector = ChronoIcons.Moon,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Text("Dark", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
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
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = ChronoIcons.Check,
                                    contentDescription = "Active pair",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    Column {
                        Text(
                            text = set.title,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = if (isSelected) "Active" else "Select",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = ChronoIcons.Delete,
                        contentDescription = "Remove pair"
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyPairsCard(onCreateClick: () -> Unit) {
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
                text = "The collection of pairs is empty",
                style = MaterialTheme.typography.titleLarge
            )
            Button(
                onClick = onCreateClick,
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(imageVector = ChronoIcons.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create a pair")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePairedSetBottomSheet(
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (title: String, lightUri: Uri, darkUri: Uri) -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var lightUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var darkUriString by rememberSaveable { mutableStateOf<String?>(null) }

    val lightUri = lightUriString?.let(Uri::parse)
    val darkUri = darkUriString?.let(Uri::parse)

    val pickLightLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) lightUriString = uri.toString()
    }

    val pickDarkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) darkUriString = uri.toString()
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
            Text(
                text = "New Day / Night pair",
                style = MaterialTheme.typography.headlineMedium
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Pair name") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UriPickerSlot(
                    label = "Light theme",
                    uri = lightUri,
                    isDarkSlot = false,
                    onClick = {
                        pickLightLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.weight(1f)
                )

                UriPickerSlot(
                    label = "Dark theme",
                    uri = darkUri,
                    isDarkSlot = true,
                    onClick = {
                        pickDarkLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Button(
                onClick = {
                    if (lightUri != null && darkUri != null) {
                        onConfirm(title, lightUri, darkUri)
                    }
                },
                enabled = !isSaving && lightUri != null && darkUri != null,
                shape = RoundedCornerShape(22.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Optimization into WebP...")
                } else {
                    Text("Save and activate")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun UriPickerSlot(
    label: String,
    uri: Uri?,
    isDarkSlot: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bitmap by rememberUriImageBitmap(uri)
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (isDarkSlot) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            val currentBitmap = bitmap
            if (currentBitmap != null) {
                Image(
                    bitmap = currentBitmap,
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f))
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(12.dp)
            ) {
                Icon(
                    imageVector = if (isDarkSlot) ChronoIcons.Moon else ChronoIcons.Solar,
                    contentDescription = null,
                    tint = if (currentBitmap != null) Color.White else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = if (uri == null) "Choose: $label" else label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (currentBitmap != null) Color.White else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}