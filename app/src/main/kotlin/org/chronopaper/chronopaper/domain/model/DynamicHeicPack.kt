package org.chronopaper.chronopaper.domain.model

enum class HeicScheduleType {
    SOLAR,
    APPEARANCE
}

data class DynamicHeicPack(
    val id: String,
    val title: String,
    val scheduleType: HeicScheduleType,
    val frames: List<SolarFrame>,
    val lightDefaultIndex: Int?,
    val darkDefaultIndex: Int?,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)