package org.chronopaper.chronopaper.ui.components

import android.graphics.ImageDecoder
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.chronopaper.chronopaper.domain.model.PhotoCrop
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

private object ThumbnailCache {
    private val cache = object : LruCache<String, ImageBitmap>(16 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = (value.width * value.height * 4) / 1024
    }
    fun get(key: String): ImageBitmap? = synchronized(cache) { cache.get(key) }
    fun put(key: String, bitmap: ImageBitmap) = synchronized(cache) { cache.put(key, bitmap) }
}

@Composable
fun rememberLocalImageBitmap(filePath: String?, maxDimensionPx: Int = 800): State<ImageBitmap?> {
    return produceState(initialValue = filePath?.let { ThumbnailCache.get("$it:$maxDimensionPx") }, filePath, maxDimensionPx) {
        if (filePath.isNullOrBlank()) {
            value = null
            return@produceState
        }
        val cacheKey = "$filePath:$maxDimensionPx"
        ThumbnailCache.get(cacheKey)?.let {
            value = it
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = File(filePath)
                if (!file.exists()) return@runCatching null
                val source = ImageDecoder.createSource(file)
                val bmp = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val maxSrc = max(info.size.width, info.size.height)
                    if (maxSrc > maxDimensionPx && maxSrc > 0) {
                        val ratio = maxDimensionPx.toFloat() / maxSrc.toFloat()
                        decoder.setTargetSize(
                            (info.size.width * ratio).roundToInt().coerceAtLeast(1),
                            (info.size.height * ratio).roundToInt().coerceAtLeast(1)
                        )
                    }
                }.asImageBitmap()
                ThumbnailCache.put(cacheKey, bmp)
                bmp
            }.getOrNull()
        }
    }
}

@Composable
fun rememberUriImageBitmap(uri: Uri?, maxDimensionPx: Int = 600): State<ImageBitmap?> {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(initialValue = null, uri, maxDimensionPx) {
        if (uri == null) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            runCatching {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val maxSrc = max(info.size.width, info.size.height)
                    if (maxSrc > maxDimensionPx && maxSrc > 0) {
                        val ratio = maxDimensionPx.toFloat() / maxSrc.toFloat()
                        decoder.setTargetSize(
                            (info.size.width * ratio).roundToInt().coerceAtLeast(1),
                            (info.size.height * ratio).roundToInt().coerceAtLeast(1)
                        )
                    }
                }.asImageBitmap()
            }.getOrNull()
        }
    }
}

@Composable
fun CroppedBitmapCanvas(
    bitmap: ImageBitmap,
    crop: PhotoCrop,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.clipToBounds()) {
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()
        val sw = size.width
        val sh = size.height
        if (bw > 0f && bh > 0f && sw > 0f && sh > 0f) {
            val baseScale = max(sw / bw, sh / bh)
            val scale = baseScale * crop.sanitizedZoom
            val scaledW = bw * scale
            val scaledH = bh * scale
            val overflowX = max(0f, scaledW - sw)
            val overflowY = max(0f, scaledH - sh)
            val dx = (sw - scaledW) * 0.5f - (overflowX * 0.5f * crop.sanitizedPanX)
            val dy = (sh - scaledH) * 0.5f - (overflowY * 0.5f * crop.sanitizedPanY)

            withTransform({
                translate(left = dx, top = dy)
                scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
            }) {
                drawImage(
                    image = bitmap,
                    filterQuality = FilterQuality.Medium
                )
            }
        }
    }
}

@Composable
fun LocalWallpaperThumbnail(
    filePath: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    isNightPlaceholder: Boolean = false,
    crop: PhotoCrop = PhotoCrop.DEFAULT,
    maxDimensionPx: Int = 800
) {
    val bitmap by rememberLocalImageBitmap(filePath = filePath, maxDimensionPx = maxDimensionPx)
    val currentBitmap = bitmap
    val gradient = if (isNightPlaceholder) {
        listOf(Color(0xFF141824), Color(0xFF232B3E))
    } else {
        listOf(Color(0xFFDDE7FF), Color(0xFFF6E6D8))
    }

    if (currentBitmap != null) {
        CroppedBitmapCanvas(
            bitmap = currentBitmap,
            crop = crop,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(Brush.linearGradient(gradient)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isNightPlaceholder) ChronoIcons.Moon else ChronoIcons.Solar,
                contentDescription = null,
                tint = if (isNightPlaceholder) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.25f),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}