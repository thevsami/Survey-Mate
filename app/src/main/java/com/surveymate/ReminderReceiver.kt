package com.surveymate

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DAILY) return

        val pendingSurveys = SurveyStore(context).all().filterNot { it.completed }
        if (pendingSurveys.isEmpty()) {
            ReminderScheduler.refresh(context)
            return
        }

        NotificationHelper.ensureChannels(context)
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            9001,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_nav_surveys)
            .setContentTitle(context.getString(R.string.daily_reminder_title))
            .setContentText(context.getString(R.string.daily_reminder_count, pendingSurveys.size))
            .setStyle(
                NotificationCompat.InboxStyle().setBigContentTitle(
                    context.getString(R.string.daily_reminder_title)
                ).also { style ->
                    pendingSurveys.forEach { survey ->
                        style.addLine(
                            context.getString(
                                R.string.daily_reminder_item,
                                survey.name,
                                survey.date,
                                survey.time
                            )
                        )
                    }
                }
            )
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        context.getSystemService(android.app.NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
        ReminderScheduler.scheduleNext(context)
    }

    companion object {
        const val ACTION_DAILY = "com.surveymate.action.DAILY_TODO_REMINDER"
        private const val NOTIFICATION_ID = 7001
    }
}
