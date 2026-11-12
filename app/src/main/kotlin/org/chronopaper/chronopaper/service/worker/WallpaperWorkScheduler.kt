package org.chronopaper.chronopaper.service.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.chronopaper.chronopaper.domain.repository.WallpaperRepository
import java.util.concurrent.TimeUnit

class WallpaperWorkScheduler(
    private val context: Context,
    private val repositoryProvider: () -> WallpaperRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    @Volatile
    private var lastScheduledIntervalHours: Int? = null

    private data class ScheduleConfig(
        val isPeriodicRotationActive: Boolean,
        val rotationIntervalHours: Int
    )

    fun startObservingSettings() {
        scope.launch {
            repositoryProvider().settings
                .map {
                    ScheduleConfig(
                        isPeriodicRotationActive = it.isPeriodicRotationActive,
                        rotationIntervalHours = it.rotationIntervalHours
                    )
                }
                .distinctUntilChanged()
                .collect { config ->
                    applySchedule(config)
                }
        }
    }

    private fun applySchedule(config: ScheduleConfig) {
        val userManager = context.getSystemService(UserManager::class.java)
        if (userManager != null && !userManager.isUserUnlocked) {
            val unlockReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    runCatching { context.unregisterReceiver(this) }
                    enqueueOrCancelWork(config)
                }
            }
            context.registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_UNLOCKED))
            return
        }
        enqueueOrCancelWork(config)
    }

    private fun enqueueOrCancelWork(config: ScheduleConfig) = runCatching {
        val previousInterval = lastScheduledIntervalHours
        if (config.isPeriodicRotationActive) {
            val interval = config.rotationIntervalHours.toLong().coerceAtLeast(1L)
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<PlaylistRotationWorker>(
                interval,
                TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            val policy = if (previousInterval == null || previousInterval == config.rotationIntervalHours) {
                ExistingPeriodicWorkPolicy.KEEP
            } else {
                ExistingPeriodicWorkPolicy.UPDATE
            }

            workManager.enqueueUniquePeriodicWork(
                WORK_PLAYLIST_ROTATION,
                policy,
                request
            )
            lastScheduledIntervalHours = config.rotationIntervalHours
        } else {
            workManager.cancelUniqueWork(WORK_PLAYLIST_ROTATION)
            lastScheduledIntervalHours = null
        }
    }

    companion object {
        private const val WORK_PLAYLIST_ROTATION = "chronopaper_playlist_rotation"
        private const val SCHEDULER_PREFS_NAME = "chronopaper_scheduler_prefs"
        private const val KEY_LAST_SCHEDULED_INTERVAL_HOURS = "last_scheduled_interval_hours"
    }
}