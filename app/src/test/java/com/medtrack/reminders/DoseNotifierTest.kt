package com.medtrack.reminders

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DoseNotifierTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val nm get() = context.getSystemService(NotificationManager::class.java)
    private val notifier = DoseNotifier(context)

    @Before fun setUp() = notifier.ensureChannel()

    private fun doses() = nm.activeNotifications.filter { it.tag == DoseNotifier.TAG }.sortedBy { it.id }

    /** The full-screen alarm rings by itself; its doses must stop the system's looping sound but keep their content. */
    @Test fun hushMovesRingingDosesToTheQuietChannel() {
        notifier.ring(listOf(RingItem(1, "Dolo 650", "1 tablet", null), RingItem(2, "Pan 40", "1 tablet", null)))
        assertEquals(Notification.FLAG_INSISTENT, doses().first().notification.flags and Notification.FLAG_INSISTENT)

        notifier.hush()

        assertEquals(listOf(1, 2), doses().map { it.id })
        doses().forEach {
            assertEquals(DoseNotifier.QUIET_CHANNEL_ID, it.notification.channelId)
            assertEquals(0, it.notification.flags and Notification.FLAG_INSISTENT)
            assertEquals(3, it.notification.actions.size)
        }
        assertEquals("Dolo 650", doses().first().notification.extras.getString(Notification.EXTRA_TITLE))
    }

    @Test fun quietChannelMakesNoSound() {
        val quiet = nm.getNotificationChannel(DoseNotifier.QUIET_CHANNEL_ID)
        assertNull(quiet.sound)
        assertEquals(false, quiet.shouldVibrate())
    }
}
