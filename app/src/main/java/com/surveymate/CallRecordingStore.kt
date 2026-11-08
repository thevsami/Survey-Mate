package com.surveymate

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import java.io.File

class CallRecordingStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("call_recordings", Context.MODE_PRIVATE)

    fun dir(): File = File(app.filesDir, "call_recordings").apply { mkdirs() }

    fun fileFor(recording: CallRecording): File = File(dir(), recording.fileName)

    fun all(): List<CallRecording> {
        return loadRaw().filter { fileFor(it).exists() }.sortedByDescending { it.startedAt }
    }

    fun forPhone(phone: String): List<CallRecording> {
        val key = normalizePhone(phone)
        if (key.isBlank()) return emptyList()
        return all().filter { normalizePhone(it.phone) == key || it.phone == phone }
    }

    @Synchronized
    fun add(recording: CallRecording) {
        val next = listOf(recording) + loadRaw().filter { it.id != recording.id }
        save(next)
    }

    @Synchronized
    fun delete(id: Long) {
        val current = loadRaw()
        current.find { it.id == id }?.let { fileFor(it).delete() }
        save(current.filterNot { it.id == id })
    }

    fun addListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun removeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun loadRaw(): List<CallRecording> {
        val raw = prefs.getString("items", "[]") ?: "[]"
        val array = JSONArray(raw)
        val result = mutableListOf<CallRecording>()
        for (i in 0 until array.length()) {
            result.add(CallRecording.fromJson(array.getJSONObject(i)))
        }
        return result
    }

    private fun save(items: List<CallRecording>) {
        val array = JSONArray()
        items.forEach { array.put(it.toJson()) }
        prefs.edit().putString("items", array.toString()).apply()
    }

    companion object {
        fun normalizePhone(raw: String): String {
            val digits = raw.filter { it.isDigit() }
            return if (digits.length > 9) digits.takeLast(9) else digits
        }
    }
}
