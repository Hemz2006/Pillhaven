package com.medtrack.health

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.medtrack.container
import com.medtrack.reminders.every15Minutes
import kotlinx.coroutines.CancellationException

class MealCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        try {
            applicationContext.container.mealChecker.check()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // e.g. permission revoked mid-read; the fallback alarm still covers the dose.
            Log.w("MealCheckWorker", "Meal check failed", e)
        }
        return Result.success()
    }
}

/** Runs [MealCheckWorker] every 15 minutes (Android's minimum) while any after-meal medication is active. */
class MealCheckScheduler(private val context: Context) {
    fun update(enabled: Boolean) {
        WorkManager.getInstance(context).every15Minutes(NAME, MealCheckWorker::class.java, enabled)
    }

    private companion object {
        const val NAME = "meal-check"
    }
}
