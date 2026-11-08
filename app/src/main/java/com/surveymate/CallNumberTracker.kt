package com.surveymate

import android.content.Context

object CallNumberTracker {
    @Volatile
    var inCall: Boolean = false
        private set

    @Volatile
    var source: String = Survey.SOURCE_CALL
        private set

    @Volatile
    private var liveNumber: String = ""

    @Volatile
    private var liveName: String = ""

    fun remember(number: String, name: String = "", callSource: String = Survey.SOURCE_CALL) {
        val cleaned = sanitizeNumber(number)
        val cleanedName = sanitizeName(name)
        if (cleaned.isNotBlank()) liveNumber = cleaned
        if (cleanedName.isNotBlank()) liveName = cleanedName
        if (cleaned.isNotBlank() || cleanedName.isNotBlank()) source = callSource
    }

    fun markInCall(active: Boolean, callSource: String = source) {
        inCall = active
        source = callSource
        if (!active) {
            liveNumber = ""
            liveName = ""
        }
    }

    fun resolve(context: Context): Pair<String, String> {
        var number = liveNumber
        var name = liveName
        if (number.isBlank()) {
            val recent = CallLogRepository.currentCall(context)
            number = sanitizeNumber(recent?.number.orEmpty())
            if (name.isBlank()) name = sanitizeName(recent?.name.orEmpty())
        }
        if (name.isBlank() && number.isNotBlank()) {
            name = CallLogRepository.lookupName(context, number)
        }
        return number to name
    }

    fun sanitizeNumber(raw: String): String {
        val n = raw.trim()
        if (n.isEmpty()) return ""
        if (n == "-1" || n == "-2") return ""
        val unknown = listOf("unknown", "unknown number", "private", "anonymous", "restricted")
        if (unknown.any { n.equals(it, ignoreCase = true) }) return ""
        return n
    }

    fun sanitizeName(raw: String): String {
        val n = raw.trim()
        if (n.isEmpty()) return ""
        val unknown = listOf("unknown", "unknown number", "private number", "whatsapp")
        if (unknown.any { n.equals(it, ignoreCase = true) }) return ""
        return n
    }
}
