package com.medtrack.reminders

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings

/**
 * The full-screen alarm's own looping alarm tone and vibration, on the alarm stream. It doesn't rely on the
 * notification's sound, which Android may stop after one ring.
 */
class AlarmSound(context: Context) {
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    // The phone's alarm tone, else its ringtone if no alarm tone is set.
    private val ringtone: Ringtone? = (
        RingtoneManager.getRingtone(context, Settings.System.DEFAULT_ALARM_ALERT_URI)
            ?: RingtoneManager.getRingtone(context, Settings.System.DEFAULT_RINGTONE_URI)
        )?.apply {
            audioAttributes = attributes
            isLooping = true
        }
    private val vibrator = context.getSystemService(Vibrator::class.java)

    fun start() {
        if (ringtone?.isPlaying == false) ringtone.play()
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 800), 0), attributes)
    }

    fun stop() {
        ringtone?.stop()
        vibrator?.cancel()
    }
}
