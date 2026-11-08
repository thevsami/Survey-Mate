package com.surveymate

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class QuickAddOverlayService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startSilentForeground()
        OverlayController.attachFromService(this)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        OverlayController.detachFromService()
        super.onDestroy()
    }

    private fun startSilentForeground() {
        NotificationHelper.ensureChannels(this)
        val notification: Notification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_OVERLAY_MIN)
            .setSmallIcon(R.drawable.ic_nav_calls)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.overlay_keepalive_text))
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(2002, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(2002, notification)
            }
        } catch (_: Exception) {
        }
    }
}
