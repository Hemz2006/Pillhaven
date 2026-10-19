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

    @Test fun batteryStepSendsUserToTheList() {
        assertEquals("Open battery settings", SetupStep.BATTERY.allowLabel)
        assertTrue(SetupStep.BATTERY.reassurance.contains("Pillhaven"))
    }

    /** Pillhaven's own battery page opens on most phones; older ones open the list filtered to optimised apps. */
    @Test fun batteryStepCoversEveryScreenItCanOpen() {
        assertTrue(SetupStep.BATTERY.reassurance.startsWith("Choose Unrestricted"))
        assertTrue(SetupStep.BATTERY.reassurance.contains("All apps"))
    }

    /** Stock Android 14+ shows Allow background usage on with Optimized below it; ColorOS 15 shows only that switch, and it exempts. */
    @Test fun batteryStepNamesBothBackgroundSwitches() {
        assertTrue(SetupStep.BATTERY.reassurance.contains("alone leaves it Optimized"))
        assertTrue(SetupStep.BATTERY.reassurance.contains("turn on Allow background usage"))
    }

    /** On Realme (ColorOS) "Don't optimise" alone leaves the app optimised; only Allow background activity exempts it. */
    @Test fun batteryStepNamesTheRealmeSwitch() {
        assertTrue(SetupStep.BATTERY.reassurance.contains("Realme"))
        assertTrue(SetupStep.BATTERY.reassurance.contains("Allow background activity"))
    }
}
