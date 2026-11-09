package com.surveymate

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

object WhatsAppCallAccess {
    fun isEnabled(context: Context): Boolean {
        val component = ComponentName(context, WhatsAppNotificationListener::class.java)
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return enabledListeners.split(":").any { ComponentName.unflattenFromString(it) == component }
    }

    fun request(context: Context) {
        AlertDialog.Builder(context)
            .setTitle(R.string.whatsapp_history_access_title)
            .setMessage(R.string.whatsapp_history_access_disclosure)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.continue_label) { _, _ ->
                try {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(
                        context,
                        R.string.notification_access_settings_unavailable,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .show()
    }
}
