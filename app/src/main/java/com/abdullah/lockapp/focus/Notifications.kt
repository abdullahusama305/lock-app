package com.abdullah.lockapp.focus

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.abdullah.lockapp.R
import com.abdullah.lockapp.ui.MainActivity
import com.abdullah.lockapp.ui.formatClock

object Notifications {
    private const val CHANNEL_FOCUS = "focus"
    private const val CHANNEL_ALERTS = "alerts"
    private const val ID_FOCUS = 1
    private const val ID_PROTECTION = 2

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_FOCUS, "Focus session", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Countdown while a focus session is running"
                },
                NotificationChannel(CHANNEL_ALERTS, "Protection alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Tells you when the blocker has been switched off"
                },
            ),
        )
    }

    private fun openApp(context: Context) = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun showFocus(context: Context, endsAt: Long) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val remaining = endsAt - System.currentTimeMillis()
        if (remaining <= 0) return
        val notification = NotificationCompat.Builder(context, CHANNEL_FOCUS)
            .setSmallIcon(R.drawable.ic_stat_lock)
            .setContentTitle("Focus session")
            .setContentText("Blocked apps stay locked until ${formatClock(endsAt)}")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(endsAt)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setTimeoutAfter(remaining)
            .setContentIntent(openApp(context))
            .build()
        NotificationManagerCompat.from(context).notify(ID_FOCUS, notification)
    }

    fun showProtectionOff(context: Context, message: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val fix = PendingIntent.getActivity(
            context,
            1,
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_lock)
            .setContentTitle("LockApp protection is off")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setOnlyAlertOnce(true)
            .setContentIntent(fix)
            .build()
        NotificationManagerCompat.from(context).notify(ID_PROTECTION, notification)
    }

    fun cancelProtectionOff(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_PROTECTION)
    }
}
