package com.surveymate

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import org.json.JSONArray
import java.io.File

class CallRecordingStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("call_recordings", Context.MODE_PRIVATE)

    fun dir(): File = File(app.filesDir, "call_recordings").apply { mkdirs() }

    fun fileFor(recording: CallRecording): File {
        val absolute = File(recording.fileName)
        return if (absolute.isAbsolute && absolute.exists()) {
            absolute
        } else {
            File(dir(), recording.fileName)
        }
    }

    fun all(): List<CallRecording> {
        autoImportExternalRecordings()
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

    @Synchronized
    private fun autoImportExternalRecordings() {
        val current = loadRaw()
        val seen = current.map { it.fileName }.toHashSet()
        val additions = mutableListOf<CallRecording>()
        for (file in discoverOppoFiles()) {
            if (file.absolutePath in seen) continue
            additions += CallRecording(
                id = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis(),
                phone = "",
                name = "OPPO call recording",
                fileName = file.absolutePath,
                startedAt = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis(),
                durationMs = (file.length() / 160L).coerceAtLeast(1_000L)
            )
        }
        if (additions.isNotEmpty()) {
            save(current + additions)
        }
    }

    private fun discoverOppoFiles(): List<File> {
        val roots = listOf(
            File(Environment.getExternalStorageDirectory(), "CallRecordings"),
            File(Environment.getExternalStorageDirectory(), "recordings"),
            File(Environment.getExternalStorageDirectory(), "Recordings"),
            File(Environment.getExternalStorageDirectory(), "Android/data/com.coloros.calling/files"),
            File(Environment.getExternalStorageDirectory(), "Android/data/com.oppo.dialer/files"),
            File(Environment.getExternalStorageDirectory(), "Android/data/com.android.dialer/files"),
            File("/sdcard/CallRecordings"),
            File("/sdcard/recordings"),
            File("/sdcard/Recordings"),
            File("/sdcard/Android/data/com.coloros.calling/files"),
            File("/sdcard/Android/data/com.oppo.dialer/files"),
            File("/sdcard/Android/data/com.android.dialer/files")
        )
        val seen = linkedSetOf<File>()
        for (root in roots.filter { it.exists() }) {
            walk(root, seen)
        }
        return seen.filter { isAudioFile(it) }.sortedByDescending { it.lastModified() }
    }

    private fun walk(root: File, seen: MutableSet<File>) {
        if (!root.exists()) return
        val files = root.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                walk(file, seen)
            } else if (file.isFile && isAudioFile(file)) {
                seen.add(file)
            }
        }
    }

    private fun isAudioFile(file: File): Boolean {
        val ext = file.extension.lowercase()
        return file.isFile && (ext in setOf("aac", "m4a", "mp4", "3gp", "amr", "wav", "mp3"))
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
