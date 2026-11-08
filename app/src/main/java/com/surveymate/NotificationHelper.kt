package com.surveymate

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationHelper {
    const val CHANNEL_REMINDERS = "survey_reminders"
    const val CHANNEL_CALLS = "survey_calls"
    const val CHANNEL_OVERLAY = "survey_overlay"
    const val CHANNEL_OVERLAY_MIN = "survey_overlay_min"
    const val CHANNEL_RECORDING = "survey_recording"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Survey reminders", NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_CALLS, "Call survey helper", NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_OVERLAY, "Call overlay", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_OVERLAY_MIN, "In-call helper", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RECORDING, "Call recording", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
        )
    }
}
