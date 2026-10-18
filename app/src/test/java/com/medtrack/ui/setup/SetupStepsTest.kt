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

    @Test fun allStepsInOrderOnAPhoneThatNeedsAutoStart() {
        assertEquals(
            listOf(SetupStep.NOTIFICATIONS, SetupStep.EXACT_ALARMS, SetupStep.FULL_SCREEN, SetupStep.BATTERY, SetupStep.AUTO_START, SetupStep.MEALS),
            setupSteps(nothing, manufacturer = "realme"),
        )
    }

    @Test fun autoStartOnlyOnPhonesThatNeedIt() {
        assertTrue(SetupStep.AUTO_START in setupSteps(nothing, "Xiaomi"))
        assertTrue(SetupStep.AUTO_START in setupSteps(nothing, "samsung"))
        assertTrue(SetupStep.AUTO_START in setupSteps(nothing, "OPPO"))
        assertTrue(SetupStep.AUTO_START in setupSteps(nothing, "vivo"))
        assertFalse(SetupStep.AUTO_START in setupSteps(nothing, "Google"))
    }

    @Test fun alreadyAllowedStepsAreLeftOut() {
        val exactGranted = nothing.copy(exactAlarms = true, health = HealthStatus.CONNECTED)
        assertEquals(
            listOf(SetupStep.NOTIFICATIONS, SetupStep.FULL_SCREEN, SetupStep.BATTERY),
            setupSteps(exactGranted, "Google"),
        )
        // Auto-start can't be checked, so it is always shown where it applies.
        assertEquals(listOf(SetupStep.AUTO_START), setupSteps(everything, "realme"))
    }

    @Test fun stepIsDoneWhenItsPermissionIsAllowed() {
        assertTrue(SetupStep.NOTIFICATIONS.isDone(everything))
        assertFalse(SetupStep.NOTIFICATIONS.isDone(nothing))
        assertTrue(SetupStep.MEALS.isDone(everything))
        assertFalse(SetupStep.MEALS.isDone(nothing.copy(health = HealthStatus.NOT_INSTALLED)))
        assertFalse(SetupStep.AUTO_START.isDone(everything))
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
