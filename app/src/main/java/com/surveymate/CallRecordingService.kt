package com.surveymate

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File

class CallRecordingService : Service() {
    private var recorder: MediaRecorder? = null
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
                if (recorder != null) return START_STICKY
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
        if (recorder != null) finishRecording()
        super.onDestroy()
    }

    @Synchronized
    private fun beginRecording(): Boolean {
        val store = CallRecordingStore(this)
        val id = System.currentTimeMillis()
        val safePhone = phone.filter { it.isDigit() || it == '+' }.ifBlank { "unknown" }
        val file = File(store.dir(), "rec_${safePhone}_$id.m4a")
        val sources = buildList {
            add(MediaRecorder.AudioSource.MIC)
            add(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            add(MediaRecorder.AudioSource.CAMCORDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                add(MediaRecorder.AudioSource.UNPROCESSED)
            }
            if (Build.VERSION.SDK_INT < 29) {
                @Suppress("DEPRECATION")
                add(MediaRecorder.AudioSource.VOICE_CALL)
            }
            add(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
        }
        for (source in sources) {
            try {
                val next = createRecorder()
                next.setAudioSource(source)
                next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                next.setAudioEncodingBitRate(128_000)
                next.setAudioSamplingRate(44_100)
                next.setAudioChannels(1)
                next.setOutputFile(file.absolutePath)
                next.prepare()
                next.start()
                recorder = next
                outputFile = file
                startedAt = System.currentTimeMillis()
                CallRecordingController.publish(true, startedAt)
                return true
            } catch (_: Exception) {
                releaseRecorder()
                if (file.exists() && file.length() == 0L) file.delete()
            }
        }
        return false
    }

    @Synchronized
    private fun finishRecording() {
        var file = outputFile
        val start = startedAt
        val savedPhone = phone
        val savedName = name
        var duration = if (start > 0L) System.currentTimeMillis() - start else 0L
        try {
            recorder?.apply {
                stop()
                reset()
            }
        } catch (_: Exception) {
        }
        releaseRecorder()
        CallRecordingController.publish(false)
        if (file == null || !file.exists() || file.length() < 256) {
            file?.delete()
            outputFile = null
            startedAt = 0L
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
            val renamed = File(file.parentFile, "rec_${safePhone}_${start.takeIf { it > 0L } ?: System.currentTimeMillis()}.m4a")
            if (file.renameTo(renamed)) file = renamed
        }
        duration = measuredDuration(file, duration)
        if (duration <= 0L) duration = 1000L
        CallRecordingStore(this).add(
            CallRecording(
                id = start.takeIf { it > 0L } ?: System.currentTimeMillis(),
                phone = number,
                name = contact,
                fileName = file.name,
                startedAt = start.takeIf { it > 0L } ?: System.currentTimeMillis(),
                durationMs = duration
            )
        )
        notifySaved(number.ifBlank { getString(R.string.unknown_number) })
        outputFile = null
        startedAt = 0L
    }

    private fun measuredDuration(file: File, fallback: Long): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
                ?: fallback
        } catch (_: Exception) {
            fallback
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun createRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }

    private fun releaseRecorder() {
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
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
