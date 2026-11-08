package com.surveymate

import org.json.JSONObject

data class CallRecording(
    val id: Long,
    val phone: String,
    val name: String,
    val fileName: String,
    val startedAt: Long,
    val durationMs: Long
) {
    val displayName: String
        get() = name.ifBlank { phone.ifBlank { "Unknown" } }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("phone", phone)
        put("name", name)
        put("fileName", fileName)
        put("startedAt", startedAt)
        put("durationMs", durationMs)
    }

    companion object {
        fun fromJson(obj: JSONObject) = CallRecording(
            id = obj.optLong("id"),
            phone = obj.optString("phone"),
            name = obj.optString("name"),
            fileName = obj.optString("fileName"),
            startedAt = obj.optLong("startedAt"),
            durationMs = obj.optLong("durationMs")
        )
    }
}
