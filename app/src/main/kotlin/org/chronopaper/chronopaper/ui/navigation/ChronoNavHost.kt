package org.chronopaper.chronopaper.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.chronopaper.chronopaper.ui.components.ChronoIcons
import org.chronopaper.chronopaper.ui.screens.home.HomeScreen
import org.chronopaper.chronopaper.ui.screens.pairs.PairedSetsScreen
import org.chronopaper.chronopaper.ui.screens.settings.SettingsScreen

enum class ChronoDestination(
    val label: String,
    val icon: ImageVector
) {
    HOME("Home", ChronoIcons.Home),
    WALLPAPERS("Wallpapers", ChronoIcons.Pairs),
    SETTINGS("Settings", ChronoIcons.Settings)
}

@Composable
fun ChronoNavHost(
    modifier: Modifier = Modifier,
    isFromWallpaperSettings: Boolean = false
) {
    var currentDestination by rememberSaveable { mutableStateOf(ChronoDestination.HOME) }
    val stateHolder = rememberSaveableStateHolder()

    val motionScheme = MaterialTheme.motionScheme

    BackHandler(enabled = currentDestination != ChronoDestination.HOME) {
        currentDestination = ChronoDestination.HOME
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp
            ) {
                ChronoDestination.entries.forEach { destination ->
                    val isSelected = currentDestination == destination

                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.15f else 1f,
                        animationSpec = motionScheme.defaultSpatialSpec(),
                        label = "NavIconScale"
                    )
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentDestination = destination },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.label,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                            )
                        },
                        label = {
                            Text(
                                text = destination.label,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentDestination,
                transitionSpec = {
                    (fadeIn(motionScheme.defaultEffectsSpec()) +
                            scaleIn(initialScale = 0.94f, animationSpec = motionScheme.defaultSpatialSpec()))
                        .togetherWith(
                            fadeOut(motionScheme.fastEffectsSpec()) +
                                    scaleOut(targetScale = 0.96f, animationSpec = motionScheme.fastSpatialSpec())
                        )
                },
                label = "ChronoScreenTransition"
            ) { destination ->
                stateHolder.SaveableStateProvider(key = destination.name) {
                    when (destination) {
                        ChronoDestination.HOME -> HomeScreen(
                            isFromWallpaperSettings = isFromWallpaperSettings,
                            onNavigateToWallpapers = { currentDestination = ChronoDestination.WALLPAPERS }
                        )

                        ChronoDestination.WALLPAPERS -> PairedSetsScreen()
                        ChronoDestination.SETTINGS -> SettingsScreen()
                    }
                }
            }
        }
    }
}