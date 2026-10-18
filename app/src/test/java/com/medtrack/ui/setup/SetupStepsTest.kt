package com.medtrack.ui.setup

import com.medtrack.health.HealthStatus
import com.medtrack.ui.settings.SetupStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupStepsTest {
    private val nothing = SetupStatus(notifications = false, exactAlarms = false, fullScreen = false, batteryUnrestricted = false, health = HealthStatus.NOT_CONNECTED)
    private val everything = SetupStatus(notifications = true, exactAlarms = true, fullScreen = true, batteryUnrestricted = true, health = HealthStatus.CONNECTED)

    @Test fun allStepsInOrder() {
        assertEquals(
            listOf(SetupStep.NOTIFICATIONS, SetupStep.EXACT_ALARMS, SetupStep.FULL_SCREEN, SetupStep.BATTERY, SetupStep.MEALS),
            setupSteps(nothing),
        )
    }

    @Test fun alreadyAllowedStepsAreLeftOut() {
        val exactGranted = nothing.copy(exactAlarms = true, health = HealthStatus.CONNECTED)
        assertEquals(listOf(SetupStep.NOTIFICATIONS, SetupStep.FULL_SCREEN, SetupStep.BATTERY), setupSteps(exactGranted))
        assertEquals(emptyList<SetupStep>(), setupSteps(everything))
    }

    @Test fun stepIsDoneWhenItsPermissionIsAllowed() {
        assertTrue(SetupStep.NOTIFICATIONS.isDone(everything))
        assertFalse(SetupStep.NOTIFICATIONS.isDone(nothing))
        assertTrue(SetupStep.MEALS.isDone(everything))
        assertFalse(SetupStep.MEALS.isDone(nothing.copy(health = HealthStatus.NOT_INSTALLED)))
    }

    @Test fun eachStepExplainsItself() {
        SetupStep.entries.forEach {
            assertTrue(it.title.isNotBlank())
            assertTrue(it.body.isNotBlank())
            assertTrue(it.reassurance.isNotBlank())
        }
        assertEquals("Allow notifications", SetupStep.NOTIFICATIONS.allowLabel)
    }
}
