package com.abdullah.lockapp.gate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abdullah.lockapp.AppContainer
import com.abdullah.lockapp.core.BlockReason
import com.abdullah.lockapp.core.Decision
import com.abdullah.lockapp.data.EventType
import com.abdullah.lockapp.questions.GateQuestion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class GateViewModel(private val c: AppContainer) : ViewModel() {

    data class AppInfo(val packageName: String, val label: String)

    enum class AnswerState { WAITING, CORRECT, WRONG }

    sealed interface UiState {
        data object Loading : UiState

        data class Blocked(
            val app: AppInfo,
            val reason: BlockReason,
            val until: Long,
            val limitMinutes: Int?,
        ) : UiState

        data class Quiz(
            val app: AppInfo,
            val question: GateQuestion,
            val correctSoFar: Int,
            val required: Int,
            val selected: Int?,
            val answer: AnswerState,
            /** Choices ignore taps until then, so reflex-tapping doesn't work. */
            val choicesEnabledAt: Long,
            /** After a wrong answer, "Next" unlocks at this time. */
            val nextEnabledAt: Long,
            val minutesLeftToday: Int?,
            val unlockMinutes: Int,
            val cooldownSeconds: Int,
            /** True when the app was unlocked a moment ago and that time just ran out. */
            val timeUp: Boolean,
        ) : UiState

        /** Unlocked; the activity should close and reveal the app. */
        data class Passed(val app: AppInfo, val minutes: Int) : UiState

        /** Nothing to gate any more (unlocked meanwhile, rule removed, …). */
        data object NotNeeded : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    var target: String? = null
        private set
    private val shownIds = HashSet<String>()
    private var answered = 0
    private var correct = 0
    private var finished = false
    private var timeUp = false

    init {
        // Rules, focus or settings changed while the gate is up (a focus session ended, a limit was
        // edited, …): decide again so the screen never shows a stale reason.
        viewModelScope.launch {
            c.policy.snapshot.drop(1).collect { if (!finished && target != null) reevaluate() }
        }
    }

    fun setTarget(pkg: String) {
        if (pkg == target && _state.value !is UiState.NotNeeded) {
            reevaluate()
            return
        }
        target = pkg
        shownIds.clear()
        answered = 0
        correct = 0
        finished = false
        val lastUnlock = c.policy.snapshot.value.unlocks[pkg]
        timeUp = lastUnlock != null && c.clock.now() - lastUnlock in 0..TIME_UP_WINDOW_MS
        _state.value = UiState.Loading
        reevaluate(firstShow = true)
    }

    fun reevaluate(firstShow: Boolean = false) {
        val pkg = target ?: return
        viewModelScope.launch {
            val app = AppInfo(pkg, c.apps.label(pkg))
            when (val decision = c.policy.decide(pkg)) {
                is Decision.Allow -> _state.value = UiState.NotNeeded
                is Decision.Blocked -> {
                    val limit = c.policy.snapshot.value.rules[pkg]?.dailyLimitMinutes
                    _state.value = UiState.Blocked(app, decision.reason, decision.until, limit)
                    if (firstShow) {
                        val type = if (decision.reason == BlockReason.FOCUS) EventType.BLOCKED_FOCUS else EventType.BLOCKED_LIMIT
                        c.events.log(type, pkg)
                    }
                }
                is Decision.Gate -> {
                    val current = _state.value
                    if (current is UiState.Quiz && current.app.packageName == pkg) {
                        _state.value = current.copy(minutesLeftToday = decision.minutesLeftToday)
                    } else {
                        loadQuestion(app, decision.minutesLeftToday)
                        if (firstShow) c.events.log(EventType.GATE_SHOWN, pkg)
                    }
                }
            }
        }
    }

    private suspend fun loadQuestion(app: AppInfo, minutesLeftToday: Int?) {
        val settings = c.settings.gateSettings.first()
        val question = c.questions.next(shownIds)
        shownIds += question.id
        val now = c.clock.now()
        _state.value = UiState.Quiz(
            app = app,
            question = question,
            correctSoFar = correct,
            required = settings.questionsRequired,
            selected = null,
            answer = AnswerState.WAITING,
            choicesEnabledAt = now + READ_DELAY_MS,
            nextEnabledAt = now,
            minutesLeftToday = minutesLeftToday,
            unlockMinutes = minOf(settings.unlockMinutes, minutesLeftToday ?: Int.MAX_VALUE),
            cooldownSeconds = settings.wrongCooldownSeconds,
            timeUp = timeUp,
        )
    }

    fun answer(index: Int) {
        val quiz = _state.value as? UiState.Quiz ?: return
        val now = c.clock.now()
        if (quiz.answer != AnswerState.WAITING || now < quiz.choicesEnabledAt) return
        val isCorrect = index == quiz.question.correctIndex
        answered++
        if (isCorrect) correct++
        viewModelScope.launch { c.questions.recordAnswer(quiz.question, isCorrect) }
        if (isCorrect) {
            _state.value = quiz.copy(selected = index, answer = AnswerState.CORRECT, correctSoFar = correct)
            viewModelScope.launch {
                delay(CORRECT_PAUSE_MS)
                if (correct >= quiz.required) pass(quiz.app) else loadQuestion(quiz.app, quiz.minutesLeftToday)
            }
        } else {
            _state.value = quiz.copy(
                selected = index,
                answer = AnswerState.WRONG,
                nextEnabledAt = now + quiz.cooldownSeconds * 1000L,
            )
        }
    }

    fun next() {
        val quiz = _state.value as? UiState.Quiz ?: return
        if (quiz.answer != AnswerState.WRONG || c.clock.now() < quiz.nextEnabledAt) return
        viewModelScope.launch { loadQuestion(quiz.app, quiz.minutesLeftToday) }
    }

    /** "Bad question": hide it for good and swap in another. Earns nothing. */
    fun flagQuestion() {
        val quiz = _state.value as? UiState.Quiz ?: return
        if (quiz.answer == AnswerState.CORRECT) return
        if (quiz.answer == AnswerState.WRONG && c.clock.now() < quiz.nextEnabledAt) return // no skipping the wait
        viewModelScope.launch {
            if (!quiz.question.generated) c.questions.flag(quiz.question.id)
            loadQuestion(quiz.app, quiz.minutesLeftToday)
        }
    }

    private suspend fun pass(app: AppInfo) {
        val settings = c.settings.gateSettings.first()
        val decision = c.policy.decide(app.packageName)
        val minutesLeft = (decision as? Decision.Gate)?.minutesLeftToday
        val minutes = minOf(settings.unlockMinutes, minutesLeft ?: Int.MAX_VALUE)
        c.unlocks.grant(app.packageName, c.clock.now() + minutes * 60_000L)
        c.events.log(EventType.UNLOCKED, app.packageName, answered, correct)
        finished = true
        _state.value = UiState.Passed(app, minutes)
    }

    /** The gate closed without an unlock: Go home, Back, Recents, or the screen switched away. */
    fun onLeftWithoutUnlock() {
        if (finished) return
        finished = true
        val pkg = target ?: return
        if (_state.value is UiState.Quiz || _state.value is UiState.Loading) {
            c.events.log(EventType.TURNED_BACK, pkg, answered, correct)
        }
    }

    private companion object {
        const val READ_DELAY_MS = 1_500L
        const val CORRECT_PAUSE_MS = 700L
        const val TIME_UP_WINDOW_MS = 10 * 60 * 1000L
    }
}
