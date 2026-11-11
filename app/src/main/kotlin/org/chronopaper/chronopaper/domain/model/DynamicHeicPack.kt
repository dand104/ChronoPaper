package org.chronopaper.chronopaper.domain.model

data class DynamicHeicPack(
    val id: String,
    val title: String,
    val frames: List<SolarFrame>,
    val lightDefaultIndex: Int? = null,
    val darkDefaultIndex: Int? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
) {
    val frameCount: Int
        get() = frames.size
}