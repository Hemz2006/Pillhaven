package com.medtrack.reminders

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Revoking exact alarms cancels the app's alarm with no broadcast, so a periodic check re-arms reminders while any
 * medicine is active.
 */
@RunWith(AndroidJUnit4::class)
class ReminderCheckTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before fun setUp() {
        if (!WorkManager.isInitialized()) WorkManager.initialize(context, Configuration.Builder().build())
    }

    private fun state(): WorkInfo.State? =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(ReminderCheckScheduler.NAME).get().lastOrNull()?.state

    @Test fun runsWhileMedicinesAreActiveAndStopsWithout() {
        val checks = ReminderCheckScheduler(context)
        checks.update(enabled = true).result.get()
        assertEquals(WorkInfo.State.ENQUEUED, state())
        checks.update(enabled = false).result.get()
        assertEquals(WorkInfo.State.CANCELLED, state())
    }
}
