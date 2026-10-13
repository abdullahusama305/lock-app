package com.abdullah.lockapp.questions

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.abdullah.lockapp.appContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/** Tops up general-knowledge questions from the trivia APIs. The gate itself never waits on this. */
class QuestionRefillWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.appContainer
        val force = inputData.getBoolean(KEY_FORCE, false)
        if (!force && c.questions.unseenGeneralKnowledge() >= ENOUGH_UNSEEN) return Result.success()

        var added = 0
        try {
            added += c.questions.insert(c.triviaApi.fetch())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "The Trivia API fetch failed", e)
        }
        try {
            val categories = OpenTdbSource.CATEGORIES
            val category = categories[c.settings.nextOpenTdbRotation() % categories.size]
            delay(OpenTdbSource.RATE_LIMIT_MS)
            added += c.questions.insert(c.openTdb.fetch(category))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Open Trivia DB fetch failed", e)
        }
        c.settings.setLastRefill(System.currentTimeMillis(), added)
        Log.i(TAG, "Question refill added $added")
        return Result.success()
    }

    companion object {
        private const val TAG = "LockApp"
        private const val KEY_FORCE = "force"
        private const val ENOUGH_UNSEEN = 300

        private val networkConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedulePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "question-refill",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<QuestionRefillWorker>(12, TimeUnit.HOURS)
                    .setConstraints(networkConstraint)
                    .build(),
            )
        }

        fun refillNow(context: Context, force: Boolean) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "question-refill-now",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<QuestionRefillWorker>()
                    .setConstraints(networkConstraint)
                    .setInputData(workDataOf(KEY_FORCE to force))
                    .build(),
            )
        }
    }
}
