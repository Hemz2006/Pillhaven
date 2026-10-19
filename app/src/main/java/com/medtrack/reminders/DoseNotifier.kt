package com.medtrack.reminders

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.medtrack.R
import com.medtrack.data.PhotoStore

data class RingItem(val logId: Long, val title: String, val text: String, val photoPath: String?)

/** One alarm-style notification per due dose, each with Taken / Skip / Snooze. */
open class DoseNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Medication reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alarms for scheduled doses"
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            // Medicine names are health information: on the lock screen show only that something is due.
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
        // While the full-screen alarm is open it plays the sound itself (see AlarmSound), so its doses move here.
        manager.createNotificationChannel(
            NotificationChannel(QUIET_CHANNEL_ID, "Alarm on screen", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Doses showing on the full-screen alarm, which rings by itself"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
        // The first channel showed names on the lock screen; a channel's lock-screen setting can't be changed, so replace it.
        manager.deleteNotificationChannel(OLD_CHANNEL_ID)
    }

    open fun ring(items: List<RingItem>) {
        if (items.isEmpty() || !manager.areNotificationsEnabled()) return
        items.forEachIndexed { index, item ->
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_pill)
                .setContentTitle(item.title)
                .setContentText(item.text.ifBlank { "Time to take your medication" })
                .setStyle(NotificationCompat.BigTextStyle().bigText(item.text.ifBlank { "Time to take your medication" }))
                .setLargeIcon(item.photoPath?.let { runCatching { PhotoStore.decodeSampled(it, 256) }.getOrNull() })
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(lockScreenVersion())
                .setGroup(GROUP)
                .setSilent(index > 0) // one sound for the batch
                .setContentIntent(alarmScreen())
                .setFullScreenIntent(alarmScreen(), true)
                .addAction(0, "Taken", action(item.logId, DoseActionReceiver.ACTION_TAKEN, 1))
                .addAction(0, "Skip", action(item.logId, DoseActionReceiver.ACTION_SKIP, 2))
                .addAction(0, "Snooze 10 min", action(item.logId, DoseActionReceiver.ACTION_SNOOZE, 3))
                .build()
            if (index == 0) notification.flags = notification.flags or Notification.FLAG_INSISTENT // repeat sound until acted on
            manager.notify(TAG, item.logId.toInt(), notification)
        }
        if (items.size > 1) {
            val summary = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_pill)
                .setContentTitle("${items.size} medications due")
                .setGroup(GROUP)
                .setGroupSummary(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(lockScreenVersion())
                .setSilent(true)
                .setContentIntent(alarmScreen())
                .build()
            manager.notify(SUMMARY_TAG, 0, summary)
        }
    }

    /** What the locked screen shows instead: no medicine names, notes or photos. */
    private fun lockScreenVersion() = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_pill)
        .setContentTitle("Time to take your medicine")
        .setContentText("Unlock to see which")
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .build()

    /**
     * Re-posts the ringing dose notifications on the quiet channel. Android stops a notification's looping sound
     * whenever it likes (some phones do as the full-screen alarm opens), so the alarm screen rings by itself instead,
     * and this stops the system's copy of the sound where it is still playing.
     */
    fun hush() {
        manager.activeNotifications.filter { it.tag == TAG && it.notification.channelId == CHANNEL_ID }.forEach { sbn ->
            val quiet = Notification.Builder.recoverBuilder(context, sbn.notification)
                .setChannelId(QUIET_CHANNEL_ID)
                .setOnlyAlertOnce(true)
                .build()
            quiet.flags = quiet.flags and Notification.FLAG_INSISTENT.inv()
            manager.notify(TAG, sbn.id, quiet)
        }
    }

    fun cancel(logId: Long) = manager.cancel(TAG, logId.toInt())

    /** Removes dose notifications whose log is no longer pending (taken elsewhere, missed, or deleted). */
    fun cancelAllExcept(keep: Set<Long>) {
        val active = manager.activeNotifications.filter { it.tag == TAG }
        active.filter { it.id.toLong() !in keep }.forEach { manager.cancel(TAG, it.id) }
        if (active.none { it.id.toLong() in keep }) manager.cancel(SUMMARY_TAG, 0)
    }

    private fun alarmScreen(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun action(logId: Long, action: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        context, (logId * 4 + code).toInt(),
        Intent(context, DoseActionReceiver::class.java).setAction(action).putExtra(DoseActionReceiver.EXTRA_LOG_ID, logId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CHANNEL_ID = "dose_alarms_v2"
        const val QUIET_CHANNEL_ID = "dose_alarms_on_screen"
        private const val OLD_CHANNEL_ID = "dose_alarms_v1"
        const val TAG = "dose"
        private const val SUMMARY_TAG = "dose_summary"
        private const val GROUP = "doses"
    }
}
