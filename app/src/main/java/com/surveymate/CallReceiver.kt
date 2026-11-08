package com.surveymate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telephony.TelephonyManager

class CallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) return
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val incoming = CallNumberTracker.sanitizeNumber(
            intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                ?: intent.getStringExtra("incoming_number").orEmpty()
        )

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                CallNumberTracker.remember(incoming, callSource = Survey.SOURCE_CALL)
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                CallNumberTracker.remember(incoming, callSource = Survey.SOURCE_CALL)
                CallNumberTracker.markInCall(true, Survey.SOURCE_CALL)
                OverlayController.show(context, Survey.SOURCE_CALL)
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                CallSession.onEnded(context, Survey.SOURCE_CALL)
            }
        }
    }
}
