package com.medtrack.ui.edit

import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.medtrack.AppContainer
import com.medtrack.data.MedForm
import com.medtrack.schedule.DropSite
import com.medtrack.schedule.ScheduleRule
import com.medtrack.schedule.Side
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class EditViewModelTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val container = AppContainer(context)

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // Saving refreshes reminders, which schedules work; WorkManager stays initialised across tests in one process.
        if (!WorkManager.isInitialized()) WorkManager.initialize(context, Configuration.Builder().build())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
        container.db.close()
    }

    @Test fun doubleSaveInsertsOnce() = runBlocking {
        val vm = EditViewModel(container, 0L)
        vm.update { it.copy(genericName = "metformin") }
        vm.save()
        vm.save()
        withTimeout(10_000) { vm.state.first { it.saved } }
        vm.save() // after completion: still a no-op
        assertEquals(1, container.db.medicationDao().active().size)
        assertTrue(vm.state.value.error == null)
    }

    private fun dropsVm(site: DropSite?, vararg rules: ScheduleRule) = EditViewModel(container, 0L).apply {
        update { m -> m.copy(genericName = "M", form = MedForm.DROPS, dropSite = site, steps = listOf(m.steps.single().copy(rules = rules.toList()))) }
    }

    @Test fun dropsWithoutSiteCantSave() = runBlocking {
        val vm = dropsVm(null, ScheduleRule.DailyTimes(listOf(LocalTime.of(8, 0))))
        vm.save()
        assertEquals("Choose what the drops are for", vm.state.value.error)
        assertEquals(0, container.db.medicationDao().active().size)
    }

    @Test fun eyeDropsWithoutSideCantSave() = runBlocking {
        val vm = dropsVm(DropSite.EYE, ScheduleRule.DailyTimes(listOf(LocalTime.of(8, 0))))
        vm.save()
        assertEquals("Choose which eye", vm.state.value.error)
        assertEquals(0, container.db.medicationDao().active().size)
    }

    @Test fun eyeDropsWithSidesSave() = runBlocking {
        val vm = dropsVm(
            DropSite.EYE,
            ScheduleRule.DailyTimes(listOf(LocalTime.of(8, 0)), Side.LEFT),
            ScheduleRule.DailyTimes(listOf(LocalTime.of(21, 0)), Side.RIGHT),
        )
        vm.save()
        withTimeout(10_000) { vm.state.first { it.saved } }
        val saved = container.db.medicationDao().active().single()
        assertEquals(DropSite.EYE, saved.dropSite)
        assertEquals(listOf(Side.LEFT, Side.RIGHT), saved.steps.single().rules.map { it.side })
    }
}
