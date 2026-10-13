package com.abdullah.lockapp.service

import android.view.accessibility.AccessibilityWindowInfo

/**
 * Which apps are on screen right now. Only application windows count, so the keyboard, the
 * notification shade and other System UI never look like "the user left the app". Split screen
 * yields both halves; a picture-in-picture window counts too.
 */
class ForegroundTracker {
    fun visibleApps(windows: List<AccessibilityWindowInfo>?, fallbackPackage: String?): List<String> {
        val packages = LinkedHashSet<String>()
        windows.orEmpty()
            .sortedByDescending { it.isFocused || it.isActive }
            .forEach { window ->
                if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                    window.root?.packageName?.toString()?.let(packages::add)
                }
            }
        if (packages.isEmpty() && fallbackPackage != null) packages += fallbackPackage
        return packages.toList()
    }
}
