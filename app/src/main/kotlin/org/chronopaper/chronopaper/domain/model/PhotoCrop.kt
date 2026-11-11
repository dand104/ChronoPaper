package org.chronopaper.chronopaper.domain.model

import kotlin.math.abs

data class PhotoCrop(
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f
) {
    val sanitizedZoom: Float
        get() = if (zoom.isFinite()) zoom.coerceIn(MIN_ZOOM, MAX_ZOOM) else 1f

    val sanitizedPanX: Float
        get() = if (panX.isFinite()) panX.coerceIn(-1f, 1f) else 0f

    val sanitizedPanY: Float
        get() = if (panY.isFinite()) panY.coerceIn(-1f, 1f) else 0f

    val isDefault: Boolean
        get() = abs(sanitizedZoom - 1f) < 0.01f &&
                abs(sanitizedPanX) < 0.01f &&
                abs(sanitizedPanY) < 0.01f

    fun serialize(): String = "$sanitizedZoom,$sanitizedPanX,$sanitizedPanY"

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 3f
        val DEFAULT = PhotoCrop()

        fun deserialize(raw: String?): PhotoCrop {
            if (raw.isNullOrBlank()) return DEFAULT
            val parts = raw.split(',')
            if (parts.size != 3) return DEFAULT
            val z = parts[0].toFloatOrNull() ?: 1f
            val px = parts[1].toFloatOrNull() ?: 0f
            val py = parts[2].toFloatOrNull() ?: 0f
            return PhotoCrop(zoom = z, panX = px, panY = py)
        }
    }
}