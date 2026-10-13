package com.abdullah.lockapp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.abdullah.lockapp.core.Difficulty
import com.abdullah.lockapp.core.FocusState
import com.abdullah.lockapp.core.GateSettings
import com.abdullah.lockapp.core.QuestionMix
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    private object Keys {
        val focusStartedAt = longPreferencesKey("focus_started_at")
        val focusEndsAt = longPreferencesKey("focus_ends_at")
        val questionsRequired = intPreferencesKey("questions_required")
        val unlockMinutes = intPreferencesKey("unlock_minutes")
        val wrongCooldownSeconds = intPreferencesKey("wrong_cooldown_seconds")
        val mixGk = intPreferencesKey("mix_gk")
        val mixSatMath = intPreferencesKey("mix_sat_math")
        val mixSatWords = intPreferencesKey("mix_sat_words")
        val difficulty = stringPreferencesKey("difficulty")
        val armed = booleanPreferencesKey("armed")
        val pipStepDone = booleanPreferencesKey("pip_step_done")
        val unlocks = stringPreferencesKey("unlocks")
        val packsVersion = intPreferencesKey("packs_version")
        val openTdbToken = stringPreferencesKey("opentdb_token")
        val openTdbRotation = intPreferencesKey("opentdb_rotation")
        val lastRefillAt = longPreferencesKey("last_refill_at")
        val lastRefillAdded = intPreferencesKey("last_refill_added")
        val testQuestionsOnly = booleanPreferencesKey("test_questions_only")
    }

    private val data: Flow<Preferences> = store.data.catch {
        if (it is IOException) emit(emptyPreferences()) else throw it
    }

    val gateSettings: Flow<GateSettings> = data.map { p ->
        val defaults = GateSettings()
        GateSettings(
            questionsRequired = p[Keys.questionsRequired] ?: defaults.questionsRequired,
            unlockMinutes = p[Keys.unlockMinutes] ?: defaults.unlockMinutes,
            wrongCooldownSeconds = p[Keys.wrongCooldownSeconds] ?: defaults.wrongCooldownSeconds,
            mix = QuestionMix(
                gk = p[Keys.mixGk] ?: defaults.mix.gk,
                satMath = p[Keys.mixSatMath] ?: defaults.mix.satMath,
                satWords = p[Keys.mixSatWords] ?: defaults.mix.satWords,
            ),
            difficulty = p[Keys.difficulty]?.let { runCatching { Difficulty.valueOf(it) }.getOrNull() }
                ?: defaults.difficulty,
        )
    }.distinctUntilChanged()

    val focus: Flow<FocusState?> = data.map { p ->
        val start = p[Keys.focusStartedAt]
        val end = p[Keys.focusEndsAt]
        if (start != null && end != null) FocusState(start, end) else null
    }.distinctUntilChanged()

    /** True once first-run setup is finished and the change guard is active. */
    val armed: Flow<Boolean> = data.map { it[Keys.armed] ?: false }.distinctUntilChanged()
    val pipStepDone: Flow<Boolean> = data.map { it[Keys.pipStepDone] ?: false }.distinctUntilChanged()
    val testQuestionsOnly: Flow<Boolean> = data.map { it[Keys.testQuestionsOnly] ?: false }.distinctUntilChanged()
    val lastRefill: Flow<Pair<Long, Int>?> = data.map { p ->
        p[Keys.lastRefillAt]?.let { it to (p[Keys.lastRefillAdded] ?: 0) }
    }.distinctUntilChanged()

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        store.edit { block(it) }
    }

    suspend fun setFocus(startedAt: Long, endsAt: Long) = edit {
        it[Keys.focusStartedAt] = startedAt
        it[Keys.focusEndsAt] = endsAt
    }

    suspend fun clearFocus() = edit {
        it.remove(Keys.focusStartedAt)
        it.remove(Keys.focusEndsAt)
    }

    suspend fun setQuestionsRequired(count: Int) = edit { it[Keys.questionsRequired] = count }
    suspend fun setUnlockMinutes(minutes: Int) = edit { it[Keys.unlockMinutes] = minutes }
    suspend fun setWrongCooldown(seconds: Int) = edit { it[Keys.wrongCooldownSeconds] = seconds }
    suspend fun setDifficulty(difficulty: Difficulty) = edit { it[Keys.difficulty] = difficulty.name }

    suspend fun setMix(mix: QuestionMix) = edit {
        it[Keys.mixGk] = mix.gk
        it[Keys.mixSatMath] = mix.satMath
        it[Keys.mixSatWords] = mix.satWords
    }

    suspend fun setArmed(armed: Boolean) = edit { it[Keys.armed] = armed }
    suspend fun setPipStepDone(done: Boolean) = edit { it[Keys.pipStepDone] = done }
    suspend fun setTestQuestionsOnly(enabled: Boolean) = edit { it[Keys.testQuestionsOnly] = enabled }

    suspend fun loadUnlocks(): Map<String, Long> = data.first()[Keys.unlocks]
        ?.let { runCatching { Json.decodeFromString<Map<String, Long>>(it) }.getOrNull() }
        .orEmpty()

    suspend fun saveUnlocks(unlocks: Map<String, Long>) = edit { it[Keys.unlocks] = Json.encodeToString(unlocks) }

    suspend fun packsVersion(): Int = data.first()[Keys.packsVersion] ?: 0
    suspend fun setPacksVersion(version: Int) = edit { it[Keys.packsVersion] = version }

    suspend fun openTdbToken(): String? = data.first()[Keys.openTdbToken]
    suspend fun setOpenTdbToken(token: String?) = edit {
        if (token == null) it.remove(Keys.openTdbToken) else it[Keys.openTdbToken] = token
    }

    /** Returns the next index in a round-robin over Open Trivia DB categories. */
    suspend fun nextOpenTdbRotation(): Int {
        var value = 0
        edit {
            value = it[Keys.openTdbRotation] ?: 0
            it[Keys.openTdbRotation] = value + 1
        }
        return value
    }

    suspend fun setLastRefill(at: Long, added: Int) = edit {
        it[Keys.lastRefillAt] = at
        it[Keys.lastRefillAdded] = added
    }
}
