package com.surveymate

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File

class CallRecordingService : Service() {
    private var capture: CallAudioCapture? = null
    private var outputFile: File? = null
    private var phone: String = ""
    private var name: String = ""
    private var startedAt: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        applyIdentity(intent)
        startSilentForeground()
        when (intent?.action) {
            CallRecordingController.ACTION_STOP -> {
                finishRecording()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                if (!CallRecordingController.hasMicPermission(this)) {
                    CallRecordingController.publish(false)
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (capture?.active == true) return START_STICKY
                if (!beginRecording()) {
                    CallRecordingController.publish(false)
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        return START_STICKY
    }

    private fun applyIdentity(intent: Intent?) {
        val extraPhone = CallNumberTracker.sanitizeNumber(
            intent?.getStringExtra(CallRecordingController.EXTRA_PHONE).orEmpty()
        )
        val extraName = CallNumberTracker.sanitizeName(
            intent?.getStringExtra(CallRecordingController.EXTRA_NAME).orEmpty()
        )
        if (extraPhone.isNotBlank()) phone = extraPhone
        if (extraName.isNotBlank()) name = extraName
        if (phone.isBlank() || name.isBlank()) {
            val resolved = CallNumberTracker.resolve(this)
            if (phone.isBlank()) phone = resolved.first
            if (name.isBlank()) name = resolved.second
        }
    }

    override fun onDestroy() {
        if (capture?.active == true) finishRecording()
        super.onDestroy()
    }

    @Synchronized
    private fun beginRecording(): Boolean {
        val store = CallRecordingStore(this)
        val id = System.currentTimeMillis()
        val safePhone = phone.filter { it.isDigit() || it == '+' }.ifBlank { "unknown" }
        val file = File(store.dir(), "rec_${safePhone}_$id.m4a")
        val next = CallAudioCapture(this)
        if (!next.start(file)) {
            file.delete()
            return false
        }
        capture = next
        outputFile = file
        startedAt = System.currentTimeMillis()
        CallRecordingController.publish(true, startedAt)
        return true
    }

    @Synchronized
    private fun finishRecording() {
        val savedPhone = phone
        val savedName = name
        val start = startedAt
        val result = try {
            capture?.stop()
        } catch (_: Exception) {
            null
        }
        capture = null
        CallRecordingController.publish(false)
        var file = result?.file ?: outputFile
        var duration = result?.durationMs ?: if (start > 0L) System.currentTimeMillis() - start else 0L
        outputFile = null
        startedAt = 0L
        if (file == null || !file.exists() || file.length() < 1024) {
            file?.delete()
            return
        }
        var number = savedPhone
        var contact = savedName
        if (number.isBlank()) {
            val resolved = CallNumberTracker.resolve(this)
            number = resolved.first
            if (contact.isBlank()) contact = resolved.second
        }
        if (number.isBlank()) {
            val latest = CallLogRepository.latestInWindow(this, 10 * 60 * 1000L)
            number = latest?.number.orEmpty()
            if (contact.isBlank()) contact = latest?.name.orEmpty()
        }
        if (contact.isBlank() && number.isNotBlank()) {
            contact = CallLogRepository.lookupName(this, number)
        }
        val safePhone = number.filter { it.isDigit() || it == '+' }.ifBlank { "unknown" }
        if (number.isNotBlank() && file.name.contains("unknown")) {
            val renamed = File(
                file.parentFile,
                "rec_${safePhone}_${start.takeIf { it > 0L } ?: System.currentTimeMillis()}.m4a"
            )
            if (file.renameTo(renamed)) file = renamed
        }
        if (duration <= 0L) duration = 1000L
        CallRecordingStore(this).add(
            CallRecording(
                id = start.takeIf { it > 0L } ?: System.currentTimeMillis(),
                phone = number,
                name = contact,
                fileName = file.absolutePath,
                startedAt = start.takeIf { it > 0L } ?: System.currentTimeMillis(),
                durationMs = duration
            )
        )
        notifySaved(number.ifBlank { getString(R.string.unknown_number) })
    }

    private fun startSilentForeground() {
        NotificationHelper.ensureChannels(this)
        val notification: Notification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_RECORDING)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(getString(R.string.recording_notification_title))
            .setContentText(
                getString(
                    R.string.recording_notification_text,
                    phone.ifBlank { getString(R.string.unknown_number) }
                )
            )
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                startForeground(2003, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(2003, notification)
            }
        } catch (_: Exception) {
        }
    }

    private fun notifySaved(number: String) {
        NotificationHelper.ensureChannels(this)
        val notification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_CALLS)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(getString(R.string.recording_saved_title))
            .setContentText(getString(R.string.recording_saved_text, number))
            .setAutoCancel(true)
            .build()
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.notify(2004, notification)
        } catch (_: Exception) {
        }
    }
}
