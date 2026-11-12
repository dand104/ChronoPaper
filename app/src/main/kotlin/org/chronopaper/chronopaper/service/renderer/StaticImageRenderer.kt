package org.chronopaper.chronopaper.service.renderer

import android.app.WallpaperColors
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.util.LruCache
import android.view.SurfaceHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.chronopaper.chronopaper.data.parser.HeicFrameExtractor
import org.chronopaper.chronopaper.domain.model.ActiveWallpaperState
import org.chronopaper.chronopaper.domain.model.PhotoCrop
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

class StaticImageRenderer : WallpaperRenderer {

    private val renderLock = Any()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val drawMatrix = Matrix()
    private val colorsCache = LruCache<String, WallpaperColors>(MAX_COLOR_CACHE_ENTRIES)

    private var currentBitmap: Bitmap? = null
    private var currentColors: WallpaperColors? = null

    private var loadedFilePath: String? = null
    private var currentCrop: PhotoCrop = PhotoCrop.DEFAULT
    private var decodedZoom: Float = 1f
    private var decodedSurfaceWidth: Int = 0
    private var decodedSurfaceHeight: Int = 0
    private var isNightFallback: Boolean = false
    private var surfaceWidth: Int = 0
    private var surfaceHeight: Int = 0

    override suspend fun updateState(state: ActiveWallpaperState): Boolean {
        return when (state) {
            is ActiveWallpaperState.Fallback -> synchronized(renderLock) {
                val prevNight = isNightFallback
                isNightFallback = state.isNightMode
                val hadBitmap = currentBitmap != null || loadedFilePath != null
                clearBitmapLocked()
                val fallbackColors = createFallbackColors(state.isNightMode)
                val colorsChanged = currentColors != fallbackColors
                currentColors = fallbackColors
                hadBitmap || colorsChanged || (prevNight != state.isNightMode)
            }

            is ActiveWallpaperState.StaticBitmap -> {
                val targetW: Int
                val targetH: Int
                val zoomFactor = state.crop.sanitizedZoom
                synchronized(renderLock) {
                    isNightFallback = state.isNightMode
                    if (surfaceWidth <= 0 || surfaceHeight <= 0) {
                        currentCrop = state.crop
                        return false
                    }
                    val bmp = currentBitmap
                    val surfaceSizeChanged =
                        decodedSurfaceWidth != surfaceWidth || decodedSurfaceHeight != surfaceHeight
                    val zoomIncreasedSignificantly =
                        zoomFactor > decodedZoom * ZOOM_REDECODE_THRESHOLD

                    if (
                        state.filePath == loadedFilePath &&
                        bmp != null &&
                        !bmp.isRecycled &&
                        !surfaceSizeChanged &&
                        !zoomIncreasedSignificantly
                    ) {
                        if (state.crop != currentCrop) {
                            currentCrop = state.crop
                            recalculateMatrixLocked(bmp.width, bmp.height)
                            return true
                        }
                        return false
                    }
                    currentCrop = state.crop
                    targetW = (surfaceWidth * zoomFactor).roundToInt()
                    targetH = (surfaceHeight * zoomFactor).roundToInt()
                }

                val decoded = decodeHardwareBitmapAndColors(state.filePath, targetW, targetH)
                synchronized(renderLock) {
                    if (decoded == null) {
                        clearBitmapLocked()
                        currentColors = createFallbackColors(state.isNightMode)
                        return true
                    }

                    val (newBitmap, newColors) = decoded
                    val oldBitmap = currentBitmap
                    currentBitmap = newBitmap
                    currentColors = newColors
                    loadedFilePath = state.filePath
                    currentCrop = state.crop
                    decodedZoom = zoomFactor
                    decodedSurfaceWidth = surfaceWidth
                    decodedSurfaceHeight = surfaceHeight
                    recalculateMatrixLocked(newBitmap.width, newBitmap.height)
                    oldBitmap?.recycle()
                    true
                }
            }
        }
    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        synchronized(renderLock) {
            surfaceWidth = width
            surfaceHeight = height
            val bmp = currentBitmap
            if (bmp != null && !bmp.isRecycled) {
                recalculateMatrixLocked(bmp.width, bmp.height)
            }
        }
    }

    override fun draw(holder: SurfaceHolder): Boolean {
        if (!holder.surface.isValid) return false

        val canvas = runCatching { holder.lockHardwareCanvas() }.getOrNull()
            ?: runCatching { holder.lockCanvas() }.getOrNull()
            ?: return false
        try {
            synchronized(renderLock) {
                val bgColor = if (isNightFallback) DARK_FALLBACK_COLOR else LIGHT_FALLBACK_COLOR
                canvas.drawColor(bgColor)
                var bitmap = currentBitmap

                if (bitmap != null && !bitmap.isRecycled) {
                    if (!canvas.isHardwareAccelerated && bitmap.config == Bitmap.Config.HARDWARE) {
                        val softwareCopy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                        if (softwareCopy != null) {
                            currentBitmap = softwareCopy
                            bitmap.recycle()
                            bitmap = softwareCopy
                        }
                    }
                    canvas.drawBitmap(bitmap, drawMatrix, bitmapPaint)
                }
            }
            return true
        } finally {
            runCatching { holder.unlockCanvasAndPost(canvas) }
        }
    }

    override fun release() {
        synchronized(renderLock) {
            clearBitmapLocked()
            currentColors = null
            colorsCache.evictAll()
        }
    }

    override fun computeColors(): WallpaperColors? = synchronized(renderLock) {
        currentColors
    }

    private fun clearBitmapLocked() {
        val old = currentBitmap
        currentBitmap = null
        loadedFilePath = null
        decodedZoom = 1f
        decodedSurfaceWidth = 0
        decodedSurfaceHeight = 0
        old?.recycle()
    }

    private fun recalculateMatrixLocked(bitmapWidth: Int, bitmapHeight: Int) {
        drawMatrix.reset()
        if (bitmapWidth <= 0 || bitmapHeight <= 0 || surfaceWidth <= 0 || surfaceHeight <= 0) {
            return
        }

        val bw = bitmapWidth.toFloat()
        val bh = bitmapHeight.toFloat()
        val sw = surfaceWidth.toFloat()
        val sh = surfaceHeight.toFloat()

        val baseScale = max(sw / bw, sh / bh)
        val scale = baseScale * currentCrop.sanitizedZoom

        val scaledW = bw * scale
        val scaledH = bh * scale

        val overflowX = max(0f, scaledW - sw)
        val overflowY = max(0f, scaledH - sh)

        val dx = (sw - scaledW) * 0.5f - (overflowX * 0.5f * currentCrop.sanitizedPanX)
        val dy = (sh - scaledH) * 0.5f - (overflowY * 0.5f * currentCrop.sanitizedPanY)

        drawMatrix.setScale(scale, scale)
        drawMatrix.postTranslate(dx, dy)
    }

    private suspend fun decodeHardwareBitmapAndColors(
        filePath: String,
        targetWidth: Int,
        targetHeight: Int
    ): Pair<Bitmap, WallpaperColors>? = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) return@withContext null

        runCatching {
            val source = ImageDecoder.createSource(file)
            val hardwareBitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_HARDWARE
                val srcW = info.size.width
                val srcH = info.size.height
                val scale = HeicFrameExtractor.computeOptimalScale(
                    srcWidth = srcW,
                    srcHeight = srcH,
                    targetWidth = targetWidth,
                    targetHeight = targetHeight
                )
                if (scale < 1f) {
                    decoder.setTargetSize(
                        (srcW * scale).roundToInt().coerceAtLeast(1),
                        (srcH * scale).roundToInt().coerceAtLeast(1)
                    )
                }
            }
            val cachedColors = synchronized(colorsCache) { colorsCache.get(filePath) }
            val colors = cachedColors ?: run {
                val thumbBitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.setTargetSize(COLOR_SAMPLE_DIMENSION, COLOR_SAMPLE_DIMENSION)
                }
                try {
                    WallpaperColors.fromBitmap(thumbBitmap).also { computed ->
                        synchronized(colorsCache) { colorsCache.put(filePath, computed) }
                    }
                } finally {
                    thumbBitmap.recycle()
                }
            }
            hardwareBitmap to colors
        }.getOrNull()
    }

    private fun createFallbackColors(isNight: Boolean): WallpaperColors {
        val primary = Color.valueOf(if (isNight) DARK_FALLBACK_COLOR else LIGHT_FALLBACK_COLOR)
        return WallpaperColors(primary, null, null)
    }

    companion object {
        private const val MAX_COLOR_CACHE_ENTRIES = 32
        private const val COLOR_SAMPLE_DIMENSION = 112
        private const val ZOOM_REDECODE_THRESHOLD = 1.15f
        private const val DARK_FALLBACK_COLOR = 0xFF121212.toInt()
        private const val LIGHT_FALLBACK_COLOR = 0xFFF5F5F5.toInt()
    }
}