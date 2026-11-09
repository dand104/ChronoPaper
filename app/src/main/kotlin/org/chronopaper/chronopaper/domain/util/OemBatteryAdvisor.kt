package org.chronopaper.chronopaper.domain.util

import android.content.Context
import android.os.Build
import android.os.PowerManager

object OemBatteryAdvisor {

    private val ADVISE_OEM_BRANDS = setOf(
        "xiaomi",
        "redmi",
        "poco",
        "oppo",
        "realme",
        "oneplus",
        "vivo",
        "iqoo",
        "huawei",
        "honor"
    )

    fun isAggressiveOemDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return ADVISE_OEM_BRANDS.any { oem ->
            manufacturer.contains(oem) || brand.contains(oem)
        }
    }

    fun shouldShowBatteryOptimizationWarning(context: Context): Boolean {
        if (!isAggressiveOemDevice()) return false
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return false
        return !powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
}