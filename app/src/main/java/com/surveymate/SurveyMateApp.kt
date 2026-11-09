package com.surveymate

import android.app.Application

class SurveyMateApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
        ReminderScheduler.rescheduleAll(this)
        CallMonitor.start(this)
    }
}
