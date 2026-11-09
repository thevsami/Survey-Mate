package com.surveymate

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class WhatsAppNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        activeNotifications?.forEach(::handleNotification)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        handleNotification(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (!isWhatsApp(sbn.packageName)) return
        val isCallNotification = looksLikeCall(
            sbn.notification.category,
            notificationTitle(sbn),
            notificationText(sbn),
            sbn
        )
        val wasTracked = WhatsAppCallStore.onEnded(this, sbn.key, System.currentTimeMillis())
        if (isCallNotification || wasTracked) CallSession.onEnded(this, Survey.SOURCE_WHATSAPP)
    }

    private fun handleNotification(sbn: StatusBarNotification) {
        if (!isWhatsApp(sbn.packageName)) return
        val title = notificationTitle(sbn)
        val text = notificationText(sbn)
        if (!looksLikeCall(sbn.notification.category, title, text, sbn)) return

        val notificationNumber = extractNumber("$title $text")
        val name = extractName(title, notificationNumber)
        val phone = CallLogRepository.resolveWhatsAppPhone(this, notificationNumber, name)
        val now = System.currentTimeMillis()
        CallNumberTracker.remember(phone, name, Survey.SOURCE_WHATSAPP)
        if (isMissedCall(title, text)) {
            if (WhatsAppCallStore.recordMissed(this, sbn.key, phone, name, now)) {
                CallSession.onEnded(this, Survey.SOURCE_WHATSAPP)
            }
            return
        }
        if (isIncomingRinging(title, text, sbn)) {
            WhatsAppCallStore.onRinging(this, sbn.key, phone, name, now)
            return
        }

        WhatsAppCallStore.onConnected(
            this,
            sbn.key,
            phone,
            name,
            isIncoming(title, text, sbn),
            now
        )
        CallNumberTracker.markInCall(true, Survey.SOURCE_WHATSAPP)
        OverlayController.show(this, Survey.SOURCE_WHATSAPP)
    }

    private fun notificationTitle(sbn: StatusBarNotification): String =
        sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()

    private fun notificationText(sbn: StatusBarNotification): String {
        val extras = sbn.notification.extras
        return listOf(
            Notification.EXTRA_TEXT,
            Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_SUB_TEXT,
            Notification.EXTRA_SUMMARY_TEXT
        ).mapNotNull { extras.getCharSequence(it)?.toString() }
            .filter(String::isNotBlank)
            .distinct()
            .joinToString(" ")
    }

    private fun isWhatsApp(packageName: String): Boolean =
        packageName == "com.whatsapp" || packageName == "com.whatsapp.w4b"

    private fun looksLikeCall(
        category: String?,
        title: String,
        text: String,
        sbn: StatusBarNotification
    ): Boolean {
        val blob = "$title $text".lowercase()
        val actions = sbn.notification.actions?.joinToString(" ") { it.title?.toString().orEmpty() }.orEmpty().lowercase()
        return category == Notification.CATEGORY_CALL ||
            blob.contains("incoming") ||
            blob.contains("ongoing") ||
            blob.contains("ringing") ||
            blob.contains("calling") ||
            blob.contains("voice call") ||
            blob.contains("video call") ||
            blob.contains("missed call") ||
            actions.contains("decline") ||
            actions.contains("hang up") ||
            actions.contains("reject")
    }

    private fun isIncomingRinging(title: String, text: String, sbn: StatusBarNotification): Boolean {
        val blob = "$title $text".lowercase()
        val actions = sbn.notification.actions?.joinToString(" ") { it.title?.toString().orEmpty() }.orEmpty().lowercase()
        val connected = blob.contains("ongoing") ||
            blob.contains("calling") ||
            actions.contains("hang up") ||
            actions.contains("end call")
        val ringing = blob.contains("incoming") ||
            blob.contains("ringing") ||
            actions.contains("answer") ||
            actions.contains("accept")
        return ringing && !connected
    }

    private fun isIncoming(title: String, text: String, sbn: StatusBarNotification): Boolean {
        val blob = "$title $text".lowercase()
        val actions = sbn.notification.actions?.joinToString(" ") { it.title?.toString().orEmpty() }.orEmpty().lowercase()
        return blob.contains("incoming") ||
            blob.contains("ringing") ||
            actions.contains("answer") ||
            actions.contains("decline") ||
            actions.contains("reject")
    }

    private fun isMissedCall(title: String, text: String): Boolean =
        Regex("\\bmissed\\s+(voice\\s+|video\\s+)?call\\b", RegexOption.IGNORE_CASE)
            .containsMatchIn("$title $text")

    private fun extractName(title: String, number: String): String {
        val candidate = CallNumberTracker.sanitizeName(title)
        if (candidate.isBlank() || candidate == number || candidate.any(Char::isDigit)) return ""
        val words = listOf("incoming", "outgoing", "ongoing", "ringing", "calling", "voice call", "video call")
        return if (words.any { candidate.contains(it, ignoreCase = true) }) "" else candidate
    }

    private fun extractNumber(text: String): String {
        return Regex("(\\+?\\d[\\d\\s().-]{6,}\\d)").find(text)?.value
            ?.replace(Regex("[\\s().-]"), "")
            .orEmpty()
    }
}
