package com.surveymate

import android.app.Application

class SurveyMateApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
        CallMonitor.start(this)
    }
}
