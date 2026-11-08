package com.surveymate

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

object CallMonitor {
    @Volatile
    private var registered: Boolean = false

    @Volatile
    private var ignoreNextIdle: Boolean = true

    fun start(context: Context) {
        if (registered) return
        val app = context.applicationContext
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val tm = app.getSystemService(TelephonyManager::class.java) ?: return
        try {
            ignoreNextIdle = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                tm.registerTelephonyCallback(
                    app.mainExecutor,
                    object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                        override fun onCallStateChanged(state: Int) = handleState(app, tm, state, "")
                    }
                )
            } else {
                @Suppress("DEPRECATION")
                tm.listen(object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        handleState(app, tm, state, phoneNumber.orEmpty())
                    }
                }, PhoneStateListener.LISTEN_CALL_STATE)
            }
            registered = true
        } catch (_: Exception) {
        }
    }

    private fun handleState(context: Context, tm: TelephonyManager, state: Int, phoneNumber: String) {
        if (phoneNumber.isNotBlank()) {
            CallNumberTracker.remember(phoneNumber, callSource = Survey.SOURCE_CALL)
        }
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                ignoreNextIdle = false
                CallNumberTracker.markInCall(true, Survey.SOURCE_CALL)
                OverlayController.show(context, Survey.SOURCE_CALL)
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (ignoreNextIdle) {
                    ignoreNextIdle = false
                    return
                }
                @Suppress("DEPRECATION")
                val actual = tm.callState
                if (actual == TelephonyManager.CALL_STATE_OFFHOOK ||
                    actual == TelephonyManager.CALL_STATE_RINGING
                ) return
                OverlayController.hideIfSource(context, Survey.SOURCE_CALL)
                if (CallNumberTracker.source == Survey.SOURCE_CALL) {
                    CallNumberTracker.markInCall(false, Survey.SOURCE_CALL)
                }
            }
        }
    }
}
