package com.abdullah.lockapp.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.os.UserManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.abdullah.lockapp.AppContainer
import com.abdullah.lockapp.appContainer
import com.abdullah.lockapp.core.Decision
import com.abdullah.lockapp.data.EventType
import com.abdullah.lockapp.gate.GateActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Watches which app is on screen and covers blocked apps with [GateActivity].
 *
 * Event-driven: window changes trigger an evaluation (debounced); while an allowed app stays on
 * screen, one timer re-evaluates when its unlock or daily allowance runs out.
 */
class BlockerService : AccessibilityService() {
    private val scope = MainScope()
    private val tracker = ForegroundTracker()
    private var container: AppContainer? = null
    private var evaluateJob: Job? = null
    private var recheckJob: Job? = null
    private val lastLaunch = HashMap<String, Long>()
    private var lastPendingCheck = 0L
    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = safely {
            when (intent.action) {
                Intent.ACTION_USER_UNLOCKED -> init()
                Intent.ACTION_USER_PRESENT -> scheduleEvaluate(null, 300)
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                    container?.events?.log(EventType.TIME_CHANGED)
                    scheduleEvaluate(null, 0)
                }
            }
        }
    }

    override fun onServiceConnected() = safely {
        isConnected = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_UNLOCKED)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
        // After a reboot, storage isn't readable until the user unlocks; touching it early crashes us.
        if (getSystemService(UserManager::class.java).isUserUnlocked) init()
    }

    private fun init() {
        if (container != null) return
        val c = appContainer
        container = c
        c.events.log(EventType.SERVICE_ON)
        scope.launch { c.policy.snapshot.drop(1).collect { scheduleEvaluate(null, 0) } }
        scheduleEvaluate(null, 0)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = safely {
        if (container == null || event == null) return@safely
        val type = event.eventType
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            scheduleEvaluate(event.packageName?.toString(), EVENT_DEBOUNCE_MS)
        }
    }

    private fun scheduleEvaluate(fallbackPackage: String?, delayMs: Long) {
        evaluateJob?.cancel()
        evaluateJob = scope.launch {
            delay(delayMs)
            try {
                evaluate(fallbackPackage)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "evaluate failed", e)
            }
        }
    }

    private suspend fun evaluate(fallbackPackage: String?) {
        val c = container ?: return
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) return

        val nowElapsed = SystemClock.elapsedRealtime()
        if (nowElapsed - lastPendingCheck > 60_000) {
            lastPendingCheck = nowElapsed
            c.rules.applyDuePending()
        }

        val visible = tracker.visibleApps(windows, fallbackPackage)
        recheckJob?.cancel()
        var recheckAt: Long? = null
        for (pkg in visible) {
            when (val decision = c.policy.decide(pkg)) {
                is Decision.Allow -> decision.recheckAt?.let { recheckAt = minOf(recheckAt ?: it, it) }
                is Decision.Gate, is Decision.Blocked -> {
                    launchGate(pkg)
                    return
                }
            }
        }
        recheckAt?.let { at ->
            val wait = (at - c.clock.now()).coerceAtLeast(0) + 500
            recheckJob = scope.launch {
                delay(wait)
                scheduleEvaluate(null, 0)
            }
        }
    }

    private fun launchGate(pkg: String) {
        if (GateActivity.visibleFor == pkg) return
        val now = SystemClock.elapsedRealtime()
        if (now - (lastLaunch[pkg] ?: 0L) < LAUNCH_DEBOUNCE_MS) return
        lastLaunch[pkg] = now
        // Started directly: accessibility services may launch activities from the background.
        startActivity(
            Intent(this, GateActivity::class.java)
                .putExtra(GateActivity.EXTRA_PACKAGE, pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
        )
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        safely {
            isConnected = false
            container?.events?.log(EventType.SERVICE_OFF)
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isConnected = false
        scope.cancel()
        if (receiverRegistered) runCatching { unregisterReceiver(receiver) }
        super.onDestroy()
    }

    /** A crash here leaves Android showing the service as on while it stays unbound, so never throw. */
    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            Log.e(TAG, "BlockerService error", e)
        }
    }

    companion object {
        private const val TAG = "LockApp"
        private const val EVENT_DEBOUNCE_MS = 150L
        private const val LAUNCH_DEBOUNCE_MS = 500L

        @Volatile
        var isConnected = false
            private set
    }
}
