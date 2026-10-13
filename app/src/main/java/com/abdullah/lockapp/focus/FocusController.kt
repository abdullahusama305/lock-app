package com.abdullah.lockapp.focus

import android.content.Context
import com.abdullah.lockapp.core.Clock
import com.abdullah.lockapp.core.FocusState
import com.abdullah.lockapp.data.EventLog
import com.abdullah.lockapp.data.EventType
import com.abdullah.lockapp.data.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Focus sessions lock every blocklisted app until they end. They can be extended, never ended early. */
class FocusController(
    private val context: Context,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val events: EventLog,
) {
    val focus: Flow<FocusState?> = settings.focus

    suspend fun start(minutes: Int) {
        val now = clock.now()
        if (settings.focus.first()?.isActive(now) == true) return
        val endsAt = now + minutes * 60_000L
        settings.setFocus(now, endsAt)
        events.log(EventType.FOCUS_STARTED)
        Notifications.showFocus(context, endsAt)
    }

    suspend fun extend(minutes: Int) {
        val now = clock.now()
        val current = settings.focus.first()?.takeIf { it.isActive(now) } ?: return
        val endsAt = current.endsAt + minutes * 60_000L
        settings.setFocus(current.startedAt, endsAt)
        Notifications.showFocus(context, endsAt)
    }

    /** Notifications don't survive a reboot; put the countdown back if a session is still running. */
    suspend fun restoreNotification() {
        val current = settings.focus.first() ?: return
        if (current.isActive(clock.now())) Notifications.showFocus(context, current.endsAt)
    }
}
