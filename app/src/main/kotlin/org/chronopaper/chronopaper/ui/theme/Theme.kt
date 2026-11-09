package org.chronopaper.chronopaper.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ChronoTypography,
        shapes = ChronoShapes,
        content = content
    )
}