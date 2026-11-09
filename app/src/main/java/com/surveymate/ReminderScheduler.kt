package com.surveymate

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object ReminderScheduler {
    private const val REQUEST_CODE = 7001

    fun refresh(context: Context) {
        scheduleDaily(context)
    }

    fun rescheduleAll(context: Context) {
        scheduleDaily(context)
    }

    fun scheduleNext(context: Context) {
        scheduleDaily(context)
    }

    private fun cancelDaily(context: Context) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = dailyPendingIntent(app, PendingIntent.FLAG_NO_CREATE)
        if (pending != null) {
            alarmManager.cancel(pending)
            pending.cancel()
        }
    }

    private fun scheduleDaily(context: Context) {
        val app = context.applicationContext
        if (SurveyStore(app).all().none { !it.completed }) {
            cancelDaily(app)
            return
        }

        val nextReminder = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 19)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = dailyPendingIntent(app, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextReminder, pending)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextReminder, pending)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextReminder, pending)
        }
    }

    private fun dailyPendingIntent(context: Context, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_DAILY),
            flags or PendingIntent.FLAG_IMMUTABLE
        )
}
