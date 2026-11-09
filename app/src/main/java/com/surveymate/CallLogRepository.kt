package com.surveymate

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import java.util.Locale

object CallLogRepository {
    fun recent(context: Context, limit: Int = 40): List<RecentCall> {
        return (phoneRecent(context, limit) + WhatsAppCallStore.recent(context))
            .sortedByDescending { it.dateMillis }
            .take(limit)
    }

    fun phoneRecent(context: Context, limit: Int = 40): List<RecentCall> {
        val calls = mutableListOf<RecentCall>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
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
        }
        return calls
    }

    fun latestInWindow(context: Context, windowMs: Long = 120_000L): RecentCall? {
        val now = System.currentTimeMillis()
        return recent(context).firstOrNull {
            it.source != Survey.SOURCE_WHATSAPP && now - it.dateMillis <= windowMs
        }
    }

    fun currentCall(context: Context): RecentCall? {
        val now = System.currentTimeMillis()
        val latest = recent(context).firstOrNull { it.source != Survey.SOURCE_WHATSAPP } ?: return null
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

    fun resolveWhatsAppPhone(context: Context, rawNumber: String, contactName: String): String {
        val number = rawNumber.ifBlank { lookupPhoneByName(context, contactName) }
        if (number.isBlank()) return ""
        if (number.startsWith("+")) return "+" + number.drop(1).filter(Char::isDigit)

        val digits = number.filter(Char::isDigit)
        if (digits.isBlank()) return ""
        if (digits.startsWith("00")) return "+${digits.drop(2)}"

        val telephony = context.getSystemService(TelephonyManager::class.java)
        val region = telephony?.networkCountryIso?.takeIf(String::isNotBlank)
            ?: telephony?.simCountryIso?.takeIf(String::isNotBlank)
            ?: Locale.getDefault().country.takeIf(String::isNotBlank)
            ?: return digits
        return PhoneNumberUtils.formatNumberToE164(digits, region.uppercase(Locale.ROOT)) ?: digits
    }

    private fun lookupPhoneByName(context: Context, contactName: String): String {
        if (contactName.isBlank() ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED
        ) return ""
        return try {
            val numbers = mutableSetOf<String>()
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ? COLLATE NOCASE",
                arrayOf(contactName),
                null
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex).equals(contactName, ignoreCase = true)) {
                        cursor.getString(numberIndex)?.trim()?.takeIf(String::isNotBlank)?.let(numbers::add)
                    }
                }
            }
            numbers.singleOrNull().orEmpty()
        } catch (_: SecurityException) {
            ""
        }
    }
}
