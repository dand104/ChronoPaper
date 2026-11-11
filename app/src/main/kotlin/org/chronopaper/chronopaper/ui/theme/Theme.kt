package org.chronopaper.chronopaper.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.chronopaper.chronopaper.domain.model.WallpaperMode
import org.chronopaper.chronopaper.ui.components.ChronoIcons

object ExpressiveMotion {
    fun <T> spatialBouncy(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    fun <T> spatialStandard(): SpringSpec<T> = spring(
        dampingRatio = 0.8f,
        stiffness = 380f
    )

    fun <T> effectsStandard(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
}

object ExpressiveGroupShapes {
    val Single = RoundedCornerShape(28.dp)
    val Top = RoundedCornerShape(
        topStart = 28.dp,
        topEnd = 28.dp,
        bottomStart = 6.dp,
        bottomEnd = 6.dp
    )
    val Middle = RoundedCornerShape(6.dp)
    val Bottom = RoundedCornerShape(
        topStart = 6.dp,
        topEnd = 6.dp,
        bottomStart = 28.dp,
        bottomEnd = 28.dp
    )

    fun forIndex(index: Int, totalCount: Int): RoundedCornerShape = when {
        totalCount <= 1 -> Single
        index == 0 -> Top
        index == totalCount - 1 -> Bottom
        else -> Middle
    }
}

@Composable
fun ExpressiveActivationBar(
    selectedMode: WallpaperMode,
    onSelectMode: (WallpaperMode) -> Unit,
    modifier: Modifier = Modifier,
    tall: Boolean = false
) {
    val items = listOf(
        Triple(WallpaperMode.THEME, "Theme", ChronoIcons.Moon),
        Triple(WallpaperMode.SOLAR, "Solar", ChronoIcons.Solar),
        Triple(WallpaperMode.TIME, "24h Time", ChronoIcons.Clock)
    )

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, (mode, label, icon) ->
                val isSelected = selectedMode == mode

                val weight by animateFloatAsState(
                    targetValue = if (isSelected) 1.32f else 1f,
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    label = "ExpressiveSegmentWeight"
                )
                val startCorner by animateDpAsState(
                    targetValue = if (isSelected || index == 0) 24.dp else 8.dp,
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    label = "ExpressiveStartCorner"
                )
                val endCorner by animateDpAsState(
                    targetValue = if (isSelected || index == items.lastIndex) 24.dp else 8.dp,
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    label = "ExpressiveEndCorner"
                )
                val shape = RoundedCornerShape(
                    topStart = startCorner,
                    bottomStart = startCorner,
                    topEnd = endCorner,
                    bottomEnd = endCorner
                )

                Surface(
                    shape = shape,
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    contentColor = when {
                        isSelected -> MaterialTheme.colorScheme.onPrimary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .weight(weight)
                        .height(if (tall) 64.dp else 44.dp)
                        .clip(shape)
                        .clickable { onSelectMode(mode) }
                ) {
                    if (tall) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

private val ChronoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun ChronoPaperTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = if (darkTheme) {
        dynamicDarkColorScheme(context)
    } else {
        dynamicLightColorScheme(context)
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = ChronoTypography,
        shapes = ChronoShapes,
        content = content
    )
}