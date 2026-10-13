package com.abdullah.lockapp.core

import com.abdullah.lockapp.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Per-app unlocks earned at the gate. Held in memory so the blocker sees a grant the instant the
 * gate closes; persisted so a process restart doesn't take time away. Null until restored.
 */
class UnlockSessions(private val settings: SettingsRepository, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<Map<String, Long>?>(null)
    val state: StateFlow<Map<String, Long>?> = _state.asStateFlow()

    suspend fun restore(now: Long) {
        val saved = settings.loadUnlocks().filterValues { it > now }
        val current = _state.value
        _state.value = if (current == null) saved else saved + current
    }

    fun grant(pkg: String, until: Long) {
        val next = (_state.value ?: emptyMap()) + (pkg to until)
        _state.value = next
        scope.launch { settings.saveUnlocks(next) }
    }

    fun clear() {
        _state.value = emptyMap()
        scope.launch { settings.saveUnlocks(emptyMap()) }
    }
}
