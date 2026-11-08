package com.surveymate

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class WhatsAppNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!isWhatsApp(sbn.packageName)) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val category = sbn.notification.category
        if (!looksLikeCall(category, title, text, sbn)) return
        val phone = extractNumber("$title $text")
        CallNumberTracker.remember(phone, title, Survey.SOURCE_WHATSAPP)
        if (isIncomingRinging(title, text, sbn)) return
        CallNumberTracker.markInCall(true, Survey.SOURCE_WHATSAPP)
        OverlayController.show(this, Survey.SOURCE_WHATSAPP)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (!isWhatsApp(sbn.packageName)) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (looksLikeCall(sbn.notification.category, title, text, sbn)) {
            CallSession.onEnded(this, Survey.SOURCE_WHATSAPP)
        }
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
        val ringing = blob.contains("incoming") || blob.contains("ringing")
        return ringing && !connected
    }

    private fun extractNumber(text: String): String {
        val match = Regex("(\\+?\\d[\\d\\s\\-]{6,}\\d)").find(text)?.value?.replace(" ", "")?.replace("-", "")
        return match.orEmpty()
    }
}
