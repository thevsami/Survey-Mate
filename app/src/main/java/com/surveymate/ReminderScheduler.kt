package com.surveymate

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Locale

object ReminderScheduler {
    private val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    fun schedule(context: Context, survey: Survey) {
        cancel(context, survey.id)
        if (survey.completed) return
        val whenMillis = try {
            format.parse("${survey.date} ${survey.time}")?.time ?: return
        } catch (_: Exception) {
            return
        }
        val app = context.applicationContext
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val reminders = listOf(
            Triple(0, whenMillis - 2L * 24 * 60 * 60 * 1000, app.getString(R.string.reminder_2d)),
            Triple(1, whenMillis - 1L * 24 * 60 * 60 * 1000, app.getString(R.string.reminder_1d)),
            Triple(2, whenMillis - 4L * 60 * 60 * 1000, app.getString(R.string.reminder_4h))
        )
        reminders.forEach { (index, trigger, title) ->
            if (trigger <= System.currentTimeMillis()) return@forEach
            val intent = Intent(app, ReminderReceiver::class.java).apply {
                putExtra("title", title)
                putExtra(
                    "text",
                    app.getString(R.string.reminder_body, survey.name, survey.time, survey.location.ifBlank { survey.phone })
                )
                putExtra("survey_id", survey.id)
            }
            val pending = PendingIntent.getBroadcast(
                app,
                requestCode(survey.id, index),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
                }
            } catch (_: SecurityException) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            }
        }
    }

    fun cancel(context: Context, surveyId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        repeat(3) { index ->
            val pending = PendingIntent.getBroadcast(
                context,
                requestCode(surveyId, index),
                Intent(context, ReminderReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pending != null) alarmManager.cancel(pending)
        }
    }

    fun rescheduleAll(context: Context) {
        SurveyStore(context).all().filterNot { it.completed }.forEach { schedule(context, it) }
    }

    private fun requestCode(surveyId: Long, index: Int): Int =
        ((surveyId % 1_000_000L).toInt() * 10) + index
}
