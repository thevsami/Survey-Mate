package com.surveymate

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.concurrent.CopyOnWriteArrayList

object CallRecordingController {
    const val ACTION_START = "com.surveymate.RECORD_START"
    const val ACTION_STOP = "com.surveymate.RECORD_STOP"
    const val EXTRA_PHONE = "extra_phone"
    const val EXTRA_NAME = "extra_name"

    @Volatile
    var recording: Boolean = false
        private set

    @Volatile
    var startedAt: Long = 0L
        private set

    @Volatile
    private var sessionOpen: Boolean = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<(Boolean) -> Unit>()

    fun hasMicPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun addListener(listener: (Boolean) -> Unit) {
        listeners.add(listener)
        listener(recording)
    }

    fun removeListener(listener: (Boolean) -> Unit) {
        listeners.remove(listener)
    }

    fun toggle(context: Context) {
        if (recording) stop(context) else start(context)
    }

    fun start(context: Context, phone: String = "", name: String = "") {
        val app = context.applicationContext
        if (!hasMicPermission(app)) {
            val launch = Intent(app, MicPermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(launch)
            return
        }
        val identity = identity(app, phone, name)
        sessionOpen = true
        dispatch(app, ACTION_START, identity.first, identity.second)
    }

    fun stop(context: Context, phone: String = "", name: String = "") {
        if (!sessionOpen && !recording) return
        val app = context.applicationContext
        val identity = identity(app, phone, name)
        dispatch(app, ACTION_STOP, identity.first, identity.second)
    }

    private fun identity(app: Context, phone: String, name: String): Pair<String, String> {
        val resolved = CallNumberTracker.resolve(app)
        return CallNumberTracker.sanitizeNumber(phone).ifBlank { resolved.first } to
            CallNumberTracker.sanitizeName(name).ifBlank { resolved.second }
    }

    private fun dispatch(app: Context, action: String, phone: String, name: String) {
        val rec = Intent(app, CallRecordingService::class.java).apply {
            this.action = action
            putExtra(EXTRA_PHONE, phone)
            putExtra(EXTRA_NAME, name)
        }
        try {
            ContextCompat.startForegroundService(app, rec)
        } catch (_: Exception) {
            try {
                app.startService(rec)
            } catch (_: Exception) {
                if (action == ACTION_START) sessionOpen = false
            }
        }
    }

    internal fun publish(active: Boolean, started: Long = if (active) System.currentTimeMillis() else 0L) {
        recording = active
        startedAt = started
        if (!active) sessionOpen = false
        mainHandler.post {
            listeners.forEach { it(active) }
        }
    }

    fun elapsedMs(): Long {
        if (!recording || startedAt == 0L) return 0L
        return (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
    }

    fun formatElapsed(): String {
        val total = elapsedMs() / 1000
        val m = total / 60
        val s = total % 60
        return "%d:%02d".format(m, s)
    }
}
