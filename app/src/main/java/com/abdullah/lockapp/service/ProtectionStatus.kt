package com.abdullah.lockapp.service

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.security.advancedprotection.AdvancedProtectionManager
import androidx.core.content.ContextCompat
import com.abdullah.lockapp.appContainer

data class ProtectionState(
    /** The service is switched on in Accessibility settings. */
    val serviceEnabled: Boolean,
    /** The system has actually bound the service in this process. */
    val serviceRunning: Boolean,
    val usageAccess: Boolean,
    val notifications: Boolean,
    val advancedProtection: Boolean,
) {
    val ok get() = serviceEnabled && serviceRunning
}

object ProtectionStatus {
    fun component(context: Context) = ComponentName(context, BlockerService::class.java)

    fun isServiceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val mine = component(context)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == mine }
    }

    /** Advanced Protection (Android 16+) revokes accessibility access from non-accessibility apps. */
    fun isAdvancedProtectionOn(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        return runCatching {
            context.getSystemService(AdvancedProtectionManager::class.java)?.isAdvancedProtectionEnabled == true
        }.getOrDefault(false)
    }

    fun read(context: Context): ProtectionState {
        val c = context.applicationContext
        return ProtectionState(
            serviceEnabled = isServiceEnabled(c),
            serviceRunning = BlockerService.isConnected,
            usageAccess = c.appContainer.usage.hasAccess(),
            notifications = ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
            advancedProtection = isAdvancedProtectionOn(c),
        )
    }
}
