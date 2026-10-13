package com.abdullah.lockapp

import android.app.Application
import android.content.Context
import com.abdullah.lockapp.core.Clock
import com.abdullah.lockapp.core.PolicyEngine
import com.abdullah.lockapp.core.UnlockSessions
import com.abdullah.lockapp.core.WallClock
import com.abdullah.lockapp.data.AppsRepository
import com.abdullah.lockapp.data.EventLog
import com.abdullah.lockapp.data.RulesRepository
import com.abdullah.lockapp.data.SettingsRepository
import com.abdullah.lockapp.data.StatsRepository
import com.abdullah.lockapp.data.UsageRepository
import com.abdullah.lockapp.data.db.AppDatabase
import com.abdullah.lockapp.focus.FocusController
import com.abdullah.lockapp.focus.Notifications
import com.abdullah.lockapp.questions.OpenTdbSource
import com.abdullah.lockapp.questions.QuestionPacks
import com.abdullah.lockapp.questions.QuestionRefillWorker
import com.abdullah.lockapp.questions.QuestionRepository
import com.abdullah.lockapp.questions.TriviaApiSource
import com.abdullah.lockapp.service.ProtectionWatchdog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Manual dependency wiring; one instance per process, shared by the UI, the gate and the service. */
class AppContainer(private val app: Application) {
    val clock: Clock = WallClock
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val db: AppDatabase by lazy { AppDatabase.build(app) }
    val settings = SettingsRepository(app)
    val unlocks = UnlockSessions(settings, appScope)
    val usage = UsageRepository(app, clock)
    val apps = AppsRepository(app)
    val events by lazy { EventLog(db.events(), clock, appScope) }
    val rules by lazy { RulesRepository(db, settings, clock) }
    val policy by lazy { PolicyEngine(rules, settings, unlocks, usage, clock, appScope) }
    val focus by lazy { FocusController(app, settings, clock, events) }
    val stats by lazy { StatsRepository(db, usage, clock) }

    private val http by lazy { OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build() }
    val openTdb by lazy { OpenTdbSource(http, settings) }
    val triviaApi by lazy { TriviaApiSource(http) }
    val questions by lazy {
        QuestionRepository(db.questions(), settings, clock) { QuestionRefillWorker.refillNow(app, force = false) }
    }
    private val packs by lazy { QuestionPacks(app, db.questions(), settings) }

    fun start() {
        Notifications.createChannels(app)
        appScope.launch {
            unlocks.restore(clock.now())
            runCatching { packs.seedIfNeeded() }
            rules.applyDuePending()
            focus.restoreNotification()
        }
        QuestionRefillWorker.schedulePeriodic(app)
        ProtectionWatchdog.schedule(app)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as LockApplication).container
