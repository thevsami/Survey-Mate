package com.surveymate

import android.provider.CallLog

data class RecentCall(
    val number: String,
    val name: String,
    val dateMillis: Long,
    val type: Int,
    val durationSeconds: Long
) {
    val displayName: String
        get() = name.ifBlank { number.ifBlank { "Unknown" } }

    val isIncoming: Boolean get() = type == CallLog.Calls.INCOMING_TYPE
    val isOutgoing: Boolean get() = type == CallLog.Calls.OUTGOING_TYPE
    val isMissed: Boolean get() = type == CallLog.Calls.MISSED_TYPE || type == CallLog.Calls.REJECTED_TYPE
}
