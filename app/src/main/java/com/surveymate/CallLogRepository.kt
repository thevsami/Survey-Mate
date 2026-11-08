package com.surveymate

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

object CallLogRepository {
    fun recent(context: Context, limit: Int = 40): List<RecentCall> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        val calls = mutableListOf<RecentCall>()
        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.DATE,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DURATION
                ),
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
                val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val durationIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)
                while (cursor.moveToNext() && calls.size < limit) {
                    val number = cursor.getString(numberIdx)?.trim().orEmpty()
                    if (number.isBlank()) continue
                    calls.add(
                        RecentCall(
                            number = number,
                            name = cursor.getString(nameIdx).orEmpty(),
                            dateMillis = cursor.getLong(dateIdx),
                            type = cursor.getInt(typeIdx),
                            durationSeconds = cursor.getLong(durationIdx)
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
        }
        return calls
    }

    fun latestInWindow(context: Context, windowMs: Long = 120_000L): RecentCall? {
        val now = System.currentTimeMillis()
        return recent(context, 8).firstOrNull { now - it.dateMillis <= windowMs }
    }

    fun currentCall(context: Context): RecentCall? {
        val now = System.currentTimeMillis()
        val latest = recent(context, 1).firstOrNull() ?: return null
        val age = now - latest.dateMillis
        if (age !in 0..4 * 60 * 60 * 1000L) return null
        if (age <= 45_000L) return latest
        if (latest.isOutgoing && latest.durationSeconds == 0L) return latest
        return null
    }

    fun lookupName(context: Context, number: String): String {
        if (number.isBlank()) return ""
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return ""
        }
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
            }.orEmpty()
        } catch (_: Exception) {
            ""
        }
    }
}
