package com.medtrack.ui.setup

import com.medtrack.health.HealthStatus
import com.medtrack.ui.settings.SetupStatus

/** One first-run screen: a permission, what it's for, and why it's safe to allow. In the order they're shown. */
enum class SetupStep(val title: String, val body: String, val reassurance: String, val allowLabel: String) {
    NOTIFICATIONS(
        "Notifications",
        "So Pillhaven can remind you when it's time for a medicine, even when the app is closed.",
        "On the lock screen it only says a medicine is due. Names show after you unlock.",
        "Allow notifications",
    ),
    EXACT_ALARMS(
        "Alarms on time",
        "So each reminder rings at the exact minute, not several minutes late while the phone saves power.",
        "Pillhaven only sets alarms for your doses.",
        "Allow exact alarms",
    ),
    FULL_SCREEN(
        "Full-screen alarm",
        "So a reminder can fill the screen and ring like an alarm clock, even when the phone is locked.",
        "You can mark the dose taken, snooze it or skip it right from that screen.",
        "Allow full-screen alarm",
    ),
    BATTERY(
        "Battery",
        "So Android doesn't put Pillhaven to sleep and silence a reminder.",
        "Pillhaven wakes only at dose times, so it uses very little battery.",
        "Allow unrestricted battery",
    ),
    AUTO_START(
        "Auto-start",
        "This phone also stops apps in the background. In its settings, allow Pillhaven to auto-start and run in the background.",
        "After the phone restarts, reminders resume once you unlock it.",
        "Open phone settings",
    ),
    MEALS(
        "Meal reminders (optional)",
        "For medicines taken after a meal: log the meal in a fitness app, and the reminder comes a set time after it.",
        "Pillhaven only reads the meals you log, and nothing leaves your phone.",
        "Connect Health Connect",
    ),
}

/** Makers whose phones kill background apps unless auto-start is allowed (Realme and OnePlus report their own names). */
private val AUTO_START_MAKERS = setOf("realme", "oppo", "oneplus", "xiaomi", "redmi", "poco", "vivo", "iqoo", "samsung")

/** The screens to show: steps already allowed are left out; auto-start can't be checked, so it shows wherever it applies. */
fun setupSteps(status: SetupStatus, manufacturer: String): List<SetupStep> =
    SetupStep.entries.filter { step ->
        when (step) {
            SetupStep.AUTO_START -> manufacturer.lowercase() in AUTO_START_MAKERS
            else -> !step.isDone(status)
        }
    }

/** Whether this step's permission is allowed now; auto-start is never known to be. */
fun SetupStep.isDone(status: SetupStatus): Boolean = when (this) {
    SetupStep.NOTIFICATIONS -> status.notifications
    SetupStep.EXACT_ALARMS -> status.exactAlarms
    SetupStep.FULL_SCREEN -> status.fullScreen
    SetupStep.BATTERY -> status.batteryUnrestricted
    SetupStep.AUTO_START -> false
    SetupStep.MEALS -> status.health == HealthStatus.CONNECTED
}
