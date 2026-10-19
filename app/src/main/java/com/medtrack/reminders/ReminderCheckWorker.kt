package com.medtrack.reminders

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.Operation
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.medtrack.container
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Re-arms the next reminder. Revoking exact alarms (Settings > Alarms & reminders) cancels the app's alarm without any
 * broadcast, so without this check no reminder would ring again until the app was opened.
 */
class ReminderCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        try {
            applicationContext.container.engine.sync()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("ReminderCheckWorker", "Reminder check failed", e)
        }
        return Result.success()
    }
}

/** Runs [ReminderCheckWorker] every 15 minutes while any medication is active. */
class ReminderCheckScheduler(private val context: Context) {
    fun update(enabled: Boolean): Operation = WorkManager.getInstance(context).every15Minutes(NAME, ReminderCheckWorker::class.java, enabled)

    companion object {
        const val NAME = "reminder-check"
    }
}

/** Keeps one periodic job [name] running every 15 minutes (Android's minimum) while [enabled], and cancels it otherwise. */
fun WorkManager.every15Minutes(name: String, worker: Class<out ListenableWorker>, enabled: Boolean): Operation =
    if (enabled) {
        enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequest.Builder(worker, 15, TimeUnit.MINUTES).build())
    } else {
        cancelUniqueWork(name)
    }
