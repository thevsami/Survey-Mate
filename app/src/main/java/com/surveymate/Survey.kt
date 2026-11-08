package com.surveymate

import org.json.JSONObject

data class Survey(
    val id: Long,
    val name: String,
    val phone: String,
    val date: String,
    val time: String,
    val location: String,
    val notes: String,
    val completed: Boolean = false,
    val source: String = SOURCE_MANUAL
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("phone", phone)
        put("date", date)
        put("time", time)
        put("location", location)
        put("notes", notes)
        put("completed", completed)
        put("source", source)
    }

    companion object {
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_CALL = "call"
        const val SOURCE_WHATSAPP = "whatsapp"
        const val SOURCE_RECENT = "recent"

        fun fromJson(o: JSONObject): Survey = Survey(
            id = o.getLong("id"),
            name = o.optString("name"),
            phone = o.optString("phone"),
            date = o.optString("date"),
            time = o.optString("time"),
            location = o.optString("location"),
            notes = o.optString("notes"),
            completed = o.optBoolean("completed", false),
            source = o.optString("source", SOURCE_MANUAL)
        )
    }
}
