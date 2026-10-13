package com.abdullah.lockapp.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.abdullah.lockapp.appContainer
import com.abdullah.lockapp.focus.Notifications
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Every 15 minutes: nag if protection is off, or if Settings shows the service on while Android
 * isn't actually running it (what happens after a crash). Also applies queued midnight changes.
 */
class ProtectionWatchdog(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.appContainer
        c.rules.applyDuePending()
        if (!c.settings.armed.first()) return Result.success()

        // If this process was just started for the worker, give Android a moment to bind the service.
        if (!BlockerService.isConnected) delay(5_000)
        val enabled = ProtectionStatus.isServiceEnabled(applicationContext)
        when {
            enabled && BlockerService.isConnected -> Notifications.cancelProtectionOff(applicationContext)
            enabled -> Notifications.showProtectionOff(
                applicationContext,
                "Android shows LockApp as on, but it isn't running. Turn it off and on again in Accessibility settings.",
            )
            else -> Notifications.showProtectionOff(
                applicationContext,
                "Instagram, YouTube and your other apps aren't being blocked. Turn LockApp back on in Accessibility settings.",
            )
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "protection-watchdog",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ProtectionWatchdog>(15, TimeUnit.MINUTES).build(),
            )
        }
    }
}
