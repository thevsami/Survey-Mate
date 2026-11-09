package com.surveymate

import android.content.Context
import android.provider.CallLog
import org.json.JSONArray
import org.json.JSONObject

object WhatsAppCallStore {
    private const val PREFS = "whatsapp_call_history"
    private const val ACTIVE_KEY = "active"
    private const val HISTORY_KEY = "history"
    private const val MAX_HISTORY = 200
    private val lock = Any()

    private data class ActiveCall(
        val number: String,
        val name: String,
        val dateMillis: Long,
        val type: Int,
        val connectedAt: Long
    ) {
        val connected: Boolean get() = connectedAt > 0L

        fun toJson(key: String) = JSONObject().apply {
            put("key", key)
            put("number", number)
            put("name", name)
            put("dateMillis", dateMillis)
            put("type", type)
            put("connectedAt", connectedAt)
        }
    }

    fun onRinging(context: Context, key: String, number: String, name: String, now: Long) {
        synchronized(lock) {
            val active = readActive(context)
            val previous = active[key]
            active[key] = ActiveCall(
                number = number.ifBlank { previous?.number.orEmpty() },
                name = name.ifBlank { previous?.name.orEmpty() },
                dateMillis = previous?.dateMillis?.takeIf { it > 0L } ?: now,
                type = CallLog.Calls.INCOMING_TYPE,
                connectedAt = previous?.connectedAt ?: 0L
            )
            writeActive(context, active)
        }
    }

    fun onConnected(
        context: Context,
        key: String,
        number: String,
        name: String,
        isIncoming: Boolean,
        now: Long
    ) {
        synchronized(lock) {
            val active = readActive(context)
            val previous = active[key]
            val type = if (previous?.type == CallLog.Calls.INCOMING_TYPE || isIncoming) {
                CallLog.Calls.INCOMING_TYPE
            } else {
                CallLog.Calls.OUTGOING_TYPE
            }
            val connectedAt = previous?.connectedAt?.takeIf { it > 0L } ?: now
            val call = ActiveCall(
                number = number.ifBlank { previous?.number.orEmpty() },
                name = name.ifBlank { previous?.name.orEmpty() },
                dateMillis = connectedAt,
                type = type,
                connectedAt = connectedAt
            )
            active[key] = call
            writeActive(context, active)
            upsertHistory(
                context,
                key,
                call,
                call.type,
                (now - call.connectedAt).coerceAtLeast(0L) / 1000L
            )
        }
    }

    fun onEnded(context: Context, key: String, now: Long): Boolean {
        synchronized(lock) {
            val active = readActive(context)
            val call = active.remove(key) ?: return false
            writeActive(context, active)
            upsertHistory(
                context,
                key,
                call,
                if (call.connected) call.type else CallLog.Calls.MISSED_TYPE,
                if (call.connected) (now - call.connectedAt).coerceAtLeast(0L) / 1000L else 0L
            )
            return true
        }
    }

    fun recordMissed(context: Context, key: String, number: String, name: String, now: Long): Boolean {
        synchronized(lock) {
            val active = readActive(context)
            val pending = active.remove(key)
            val call = pending ?: ActiveCall(
                number = number,
                name = name,
                dateMillis = now,
                type = CallLog.Calls.MISSED_TYPE,
                connectedAt = 0L
            )
            writeActive(context, active)
            upsertHistory(
                context,
                key,
                call.copy(
                    number = number.ifBlank { call.number },
                    name = name.ifBlank { call.name },
                    dateMillis = call.dateMillis.takeIf { it > 0L } ?: now
                ),
                CallLog.Calls.MISSED_TYPE,
                0L
            )
            return pending != null
        }
    }

    fun recent(context: Context): List<RecentCall> = synchronized(lock) {
        val history = readHistory(context)
        buildList {
            for (index in 0 until history.length()) {
                val item = history.optJSONObject(index) ?: continue
                add(
                    RecentCall(
                        number = item.optString("number"),
                        name = item.optString("name"),
                        dateMillis = item.optLong("dateMillis"),
                        type = item.optInt("type", CallLog.Calls.MISSED_TYPE),
                        durationSeconds = item.optLong("durationSeconds"),
                        source = Survey.SOURCE_WHATSAPP,
                        sourceId = item.optString("callKey")
                    )
                )
            }
        }
    }

    fun updateNumber(context: Context, callKey: String, number: String) {
        if (callKey.isBlank()) return
        synchronized(lock) {
            val history = readHistory(context)
            val index = (0 until history.length()).firstOrNull {
                history.optJSONObject(it)?.optString("callKey") == callKey
            } ?: return
            history.optJSONObject(index)?.put("number", number)
            writeHistory(context, history)
        }
    }

    private fun readActive(context: Context): MutableMap<String, ActiveCall> {
        val array = preferences(context).getString(ACTIVE_KEY, "[]")?.let(::JSONArray) ?: JSONArray()
        return buildMap {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val key = item.optString("key")
                if (key.isNotBlank()) {
                    put(
                        key,
                        ActiveCall(
                            number = item.optString("number"),
                            name = item.optString("name"),
                            dateMillis = item.optLong("dateMillis"),
                            type = item.optInt("type", CallLog.Calls.INCOMING_TYPE),
                            connectedAt = item.optLong("connectedAt")
                        )
                    )
                }
            }
        }.toMutableMap()
    }

    private fun writeActive(context: Context, active: Map<String, ActiveCall>) {
        val array = JSONArray()
        active.forEach { (key, call) -> array.put(call.toJson(key)) }
        preferences(context).edit().putString(ACTIVE_KEY, array.toString()).apply()
    }

    private fun upsertHistory(
        context: Context,
        key: String,
        call: ActiveCall,
        type: Int,
        durationSeconds: Long
    ) {
        val history = readHistory(context)
        val existingIndex = (0 until history.length()).firstOrNull { index ->
            history.optJSONObject(index)?.optString("callKey") == key
        }
        val entry = JSONObject().apply {
            put("callKey", key)
            put("number", call.number)
            put("name", call.name)
            put("dateMillis", call.dateMillis.takeIf { it > 0L } ?: System.currentTimeMillis())
            put("type", type)
            put("durationSeconds", durationSeconds)
        }
        if (existingIndex == null) {
            history.put(0, entry)
        } else {
            history.remove(existingIndex)
            history.put(0, entry)
        }
        while (history.length() > MAX_HISTORY) history.remove(history.length() - 1)
        writeHistory(context, history)
    }

    private fun writeHistory(context: Context, history: JSONArray) {
        preferences(context).edit().putString(HISTORY_KEY, history.toString()).apply()
    }

    private fun readHistory(context: Context): JSONArray =
        preferences(context).getString(HISTORY_KEY, "[]")?.let(::JSONArray) ?: JSONArray()

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
