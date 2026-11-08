package com.surveymate

import android.content.Context

object CallSession {
    fun onEnded(context: Context, source: String, clearNumber: Boolean = true) {
        val (phone, name) = CallNumberTracker.resolve(context)
        CallRecordingController.stop(context, phone, name)
        OverlayController.hideIfSource(context, source)
        if (clearNumber) CallNumberTracker.markInCall(false, source)
    }
}
