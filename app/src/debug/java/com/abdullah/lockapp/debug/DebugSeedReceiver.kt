package com.abdullah.lockapp.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.abdullah.lockapp.appContainer
import com.abdullah.lockapp.core.Change
import com.abdullah.lockapp.core.QuestionCategory
import com.abdullah.lockapp.data.db.QuestionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Test hooks for driving the app from adb (debug builds only), e.g.
 *
 *   adb shell am broadcast -n com.abdullah.lockapp/.debug.DebugSeedReceiver --es cmd seed \
 *       --es pkg com.google.android.youtube --ez gate true --ei limit 2 --ei required 1 --ei unlock 1
 *
 * Commands: seed, focus (--ei minutes), endfocus, clearunlocks, applypending, testquestions (--ez on).
 * Everything bypasses the change guard.
 */
class DebugSeedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        val c = context.appContainer
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (val cmd = intent.getStringExtra("cmd")) {
                    "seed" -> {
                        val pkg = intent.getStringExtra("pkg") ?: error("pkg required")
                        c.rules.applyNow(Change.AddApp(pkg, c.apps.label(pkg)))
                        c.rules.applyNow(Change.SetGate(pkg, intent.getBooleanExtra("gate", true)))
                        val limit = intent.getIntExtra("limit", -1)
                        c.rules.applyNow(Change.SetLimit(pkg, limit.takeIf { it > 0 }))
                        c.rules.applyNow(Change.SetQuestionsRequired(intent.getIntExtra("required", 3)))
                        c.rules.applyNow(Change.SetUnlockMinutes(intent.getIntExtra("unlock", 10)))
                        c.rules.applyNow(Change.SetWrongCooldown(intent.getIntExtra("cooldown", 10)))
                        c.settings.setArmed(true)
                    }
                    "focus" -> c.focus.start(intent.getIntExtra("minutes", 1))
                    "endfocus" -> c.settings.clearFocus()
                    "clearunlocks" -> c.unlocks.clear()
                    "applypending" -> c.rules.applyDuePending(Long.MAX_VALUE)
                    "testquestions" -> {
                        val on = intent.getBooleanExtra("on", true)
                        if (on) {
                            c.db.questions().insertAll(
                                listOf(
                                    QuestionEntity(
                                        id = "test:alpha",
                                        source = "debug",
                                        category = QuestionCategory.TEST.name,
                                        difficulty = 1,
                                        passage = null,
                                        prompt = "Debug question: tap ALPHA",
                                        correct = "ALPHA",
                                        wrong = listOf("BRAVO", "CHARLIE", "DELTA"),
                                        explanation = "The debug answer is always ALPHA.",
                                    ),
                                ),
                            )
                        } else {
                            c.db.questions().deleteTestQuestions()
                        }
                        c.settings.setTestQuestionsOnly(on)
                    }
                    else -> error("unknown cmd $cmd")
                }
                Log.i(TAG, "debug command ok: ${intent.getStringExtra("cmd")}")
                result.resultData = "ok"
            } catch (e: Throwable) {
                Log.e(TAG, "debug command failed", e)
                result.resultData = "error: ${e.message}"
            } finally {
                result.finish()
            }
        }
    }

    private companion object {
        const val TAG = "LockApp"
    }
}
