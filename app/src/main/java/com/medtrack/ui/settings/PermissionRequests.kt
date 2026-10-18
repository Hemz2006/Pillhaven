package com.medtrack.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.PermissionController
import com.medtrack.health.HealthConnectGateway
import com.medtrack.health.HealthStatus
import com.medtrack.reminders.DoseNotifier

/** Opens the system dialog or settings page for each permission. Shared by Settings and the first-run screens. */
class PermissionRequests internal constructor(
    private val context: Context,
    private val askNotifications: () -> Unit,
    private val askHealth: () -> Unit,
) {
    fun notifications() {
        if (channelBlocked(context)) {
            context.open(
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, DoseNotifier.CHANNEL_ID),
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askNotifications()
        } else {
            context.openNotificationSettings()
        }
    }

    fun exactAlarms() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, context.packageUri()))
    }

    fun fullScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.open(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, context.packageUri()))
        }
    }

    fun battery() = context.open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, context.packageUri()))

    /** Connects Health Connect, or opens its store page when it is missing or too old. */
    fun health(status: HealthStatus) {
        if (status == HealthStatus.NOT_CONNECTED) askHealth() else context.openHealthConnectStore()
    }
}

/** [onResult] runs when a system dialog closes; settings pages are noticed when the screen resumes. */
@Composable
fun rememberPermissionRequests(health: HealthConnectGateway, onResult: () -> Unit): PermissionRequests {
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Permanently denied: the system shows no dialog, so send the user to the settings page.
        if (!granted) context.openNotificationSettings()
        onResult()
    }
    val healthPermissions = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { onResult() }
    return PermissionRequests(
        context,
        askNotifications = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) },
        askHealth = { healthPermissions.launch(health.permissions) },
    )
}

private fun Context.packageUri(): Uri = Uri.parse("package:$packageName")

private fun Context.openNotificationSettings() =
    open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))

private fun Context.open(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: android.content.ActivityNotFoundException) {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri()))
    }
}

private fun Context.openHealthConnectStore() =
    open(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata")))
