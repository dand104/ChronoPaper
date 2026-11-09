package org.chronopaper.chronopaper.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object ChronoIcons {

    val Home: ImageVector by lazy {
        vectorIcon("Home") {
            moveTo(12f, 3.2f)
            lineTo(3f, 10.5f)
            verticalLineTo(20f)
            horizontalLineTo(9.5f)
            verticalLineTo(14f)
            horizontalLineTo(14.5f)
            verticalLineTo(20f)
            horizontalLineTo(21f)
            verticalLineTo(10.5f)
            close()
        }
    }

    val Pairs: ImageVector by lazy {
        vectorIcon("Pairs") {
            moveTo(4f, 6f)
            horizontalLineTo(14f)
            verticalLineTo(18f)
            horizontalLineTo(4f)
            close()
            moveTo(16f, 8f)
            horizontalLineTo(20f)
            verticalLineTo(20f)
            horizontalLineTo(9f)
            verticalLineTo(18f)
            horizontalLineTo(16f)
            close()
        }
    }

    val Solar: ImageVector by lazy {
        vectorIcon("Solar") {
            moveTo(12f, 7f)
            curveTo(9.24f, 7f, 7f, 9.24f, 7f, 12f)
            curveTo(7f, 14.76f, 9.24f, 17f, 12f, 17f)
            curveTo(14.76f, 17f, 17f, 14.76f, 17f, 12f)
            curveTo(17f, 9.24f, 14.76f, 7f, 12f, 7f)
            close()
            moveTo(11f, 1f)
            horizontalLineTo(13f)
            verticalLineTo(4f)
            horizontalLineTo(11f)
            close()
            moveTo(11f, 20f)
            horizontalLineTo(13f)
            verticalLineTo(23f)
            horizontalLineTo(11f)
            close()
            moveTo(1f, 11f)
            horizontalLineTo(4f)
            verticalLineTo(13f)
            horizontalLineTo(1f)
            close()
            moveTo(20f, 11f)
            horizontalLineTo(23f)
            verticalLineTo(13f)
            horizontalLineTo(20f)
            close()
        }
    }

    val Settings: ImageVector by lazy {
        vectorIcon("Settings") {
            moveTo(19.14f, 12.94f)
            curveTo(19.18f, 12.64f, 19.2f, 12.33f, 19.2f, 12f)
            curveTo(19.2f, 11.68f, 19.18f, 11.36f, 19.13f, 11.06f)
            lineTo(21.16f, 9.48f)
            lineTo(19.24f, 6.16f)
            lineTo(16.85f, 7.12f)
            curveTo(16.35f, 6.74f, 15.81f, 6.41f, 15.22f, 6.17f)
            lineTo(14.86f, 3.6f)
            horizontalLineTo(11.14f)
            lineTo(10.78f, 6.17f)
            curveTo(10.19f, 6.41f, 9.65f, 6.73f, 9.15f, 7.12f)
            lineTo(6.76f, 6.16f)
            lineTo(4.84f, 9.48f)
            lineTo(6.87f, 11.06f)
            curveTo(6.82f, 11.36f, 6.8f, 11.69f, 6.8f, 12f)
            curveTo(6.8f, 12.32f, 6.82f, 12.64f, 6.87f, 12.94f)
            lineTo(4.84f, 14.52f)
            lineTo(6.76f, 17.84f)
            lineTo(9.15f, 16.88f)
            curveTo(9.65f, 17.26f, 10.19f, 17.59f, 10.78f, 17.83f)
            lineTo(11.14f, 20.4f)
            horizontalLineTo(14.86f)
            lineTo(15.22f, 17.83f)
            curveTo(15.81f, 17.59f, 16.35f, 17.27f, 16.85f, 16.88f)
            lineTo(19.24f, 17.84f)
            lineTo(21.16f, 14.52f)
            close()
            moveTo(13f, 15.5f)
            curveTo(11.07f, 15.5f, 9.5f, 13.93f, 9.5f, 12f)
            curveTo(9.5f, 10.07f, 11.07f, 8.5f, 13f, 8.5f)
            curveTo(14.93f, 8.5f, 16.5f, 10.07f, 16.5f, 12f)
            curveTo(16.5f, 13.93f, 14.93f, 15.5f, 13f, 15.5f)
            close()
        }
    }

    val Moon: ImageVector by lazy {
        vectorIcon("Moon") {
            moveTo(12.3f, 3f)
            curveTo(7.3f, 3f, 3.3f, 7f, 3.3f, 12f)
            curveTo(3.3f, 17f, 7.3f, 21f, 12.3f, 21f)
            curveTo(16.2f, 21f, 19.5f, 18.5f, 20.7f, 15f)
            curveTo(15.8f, 16.1f, 11.2f, 12.4f, 11.2f, 7.4f)
            curveTo(11.2f, 5.8f, 11.6f, 4.3f, 12.3f, 3f)
            close()
        }
    }

    val Add: ImageVector by lazy {
        vectorIcon("Add") {
            moveTo(19f, 13f)
            horizontalLineTo(13f)
            verticalLineTo(19f)
            horizontalLineTo(11f)
            verticalLineTo(13f)
            horizontalLineTo(5f)
            verticalLineTo(11f)
            horizontalLineTo(11f)
            verticalLineTo(5f)
            horizontalLineTo(13f)
            verticalLineTo(11f)
            horizontalLineTo(19f)
            close()
        }
    }

    val Delete: ImageVector by lazy {
        vectorIcon("Delete") {
            moveTo(6f, 19f)
            curveTo(6f, 20.1f, 6.9f, 21f, 8f, 21f)
            horizontalLineTo(16f)
            curveTo(17.1f, 21f, 18f, 20.1f, 18f, 19f)
            verticalLineTo(7f)
            horizontalLineTo(6f)
            verticalLineTo(19f)
            close()
            moveTo(19f, 4f)
            horizontalLineTo(15.5f)
            lineTo(14.5f, 3f)
            horizontalLineTo(9.5f)
            lineTo(8.5f, 4f)
            horizontalLineTo(5f)
            verticalLineTo(6f)
            horizontalLineTo(19f)
            verticalLineTo(4f)
            close()
        }
    }

    val Check: ImageVector by lazy {
        vectorIcon("Check") {
            moveTo(9f, 16.17f)
            lineTo(4.83f, 12f)
            lineTo(3.41f, 13.41f)
            lineTo(9f, 19f)
            lineTo(21f, 7f)
            lineTo(19.59f, 5.59f)
            close()
        }
    }

    val Refresh: ImageVector by lazy {
        vectorIcon("Refresh") {
            moveTo(17.65f, 6.35f)
            curveTo(16.2f, 4.9f, 14.21f, 4f, 12f, 4f)
            curveTo(7.58f, 4f, 4.01f, 7.58f, 4.01f, 12f)
            curveTo(4.01f, 16.42f, 7.58f, 20f, 12f, 20f)
            curveTo(15.73f, 20f, 18.84f, 17.45f, 19.73f, 14f)
            horizontalLineTo(17.65f)
            curveTo(16.83f, 16.33f, 14.61f, 18f, 12f, 18f)
            curveTo(8.69f, 18f, 6f, 15.31f, 6f, 12f)
            curveTo(6f, 8.69f, 8.69f, 6f, 12f, 6f)
            curveTo(13.66f, 6f, 15.14f, 6.69f, 16.22f, 7.78f)
            lineTo(13f, 11f)
            horizontalLineTo(20f)
            verticalLineTo(4f)
            close()
        }
    }

    val Wallpaper: ImageVector by lazy {
        vectorIcon("Wallpaper") {
            moveTo(4f, 4f)
            horizontalLineTo(11f)
            verticalLineTo(2f)
            horizontalLineTo(4f)
            curveTo(2.9f, 2f, 2f, 2.9f, 2f, 4f)
            verticalLineTo(11f)
            horizontalLineTo(4f)
            verticalLineTo(4f)
            close()
            moveTo(10f, 13f)
            lineTo(6f, 18f)
            horizontalLineTo(18f)
            lineTo(15f, 14f)
            lineTo(12.97f, 16.71f)
            close()
            moveTo(20f, 2f)
            horizontalLineTo(13f)
            verticalLineTo(4f)
            horizontalLineTo(20f)
            verticalLineTo(11f)
            horizontalLineTo(22f)
            verticalLineTo(4f)
            curveTo(22f, 2.9f, 21.1f, 2f, 20f, 2f)
            close()
            moveTo(20f, 20f)
            horizontalLineTo(13f)
            verticalLineTo(22f)
            horizontalLineTo(20f)
            curveTo(21.1f, 22f, 22f, 21.1f, 22f, 20f)
            verticalLineTo(13f)
            horizontalLineTo(20f)
            verticalLineTo(20f)
            close()
            moveTo(4f, 13f)
            horizontalLineTo(2f)
            verticalLineTo(20f)
            curveTo(2f, 21.1f, 2.9f, 22f, 4f, 22f)
            horizontalLineTo(11f)
            verticalLineTo(20f)
            horizontalLineTo(4f)
            verticalLineTo(13f)
            close()
        }
    }

    val Warning: ImageVector by lazy {
        vectorIcon("Warning") {
            moveTo(1f, 21f)
            horizontalLineTo(23f)
            lineTo(12f, 2f)
            lineTo(1f, 21f)
            close()
            moveTo(13f, 18f)
            horizontalLineTo(11f)
            verticalLineTo(16f)
            horizontalLineTo(13f)
            verticalLineTo(18f)
            close()
            moveTo(13f, 14f)
            horizontalLineTo(11f)
            verticalLineTo(10f)
            horizontalLineTo(13f)
            verticalLineTo(14f)
            close()
        }
    }

    val Location: ImageVector by lazy {
        vectorIcon("Location") {
            moveTo(12f, 2f)
            curveTo(8.13f, 2f, 5f, 5.13f, 5f, 9f)
            curveTo(5f, 14.25f, 12f, 22f, 12f, 22f)
            curveTo(12f, 22f, 19f, 14.25f, 19f, 9f)
            curveTo(19f, 5.13f, 15.87f, 2f, 12f, 2f)
            close()
            moveTo(12f, 11.5f)
            curveTo(10.62f, 11.5f, 9.5f, 10.38f, 9.5f, 9f)
            curveTo(9.5f, 7.62f, 10.62f, 6.5f, 12f, 6.5f)
            curveTo(13.38f, 6.5f, 14.5f, 7.62f, 14.5f, 9f)
            curveTo(14.5f, 10.38f, 13.38f, 11.5f, 12f, 11.5f)
            close()
        }
    }

    private fun vectorIcon(
        name: String,
        block: PathBuilder.() -> Unit
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = SolidColor(Color.Black),
        stroke = null,
        strokeLineWidth = 1f,
        strokeLineCap = StrokeCap.Butt,
        strokeLineJoin = StrokeJoin.Bevel,
        strokeLineMiter = 1f,
        pathFillType = PathFillType.NonZero,
        pathBuilder = block
    ).build()
}