package com.example.mondialer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        BlockRulesStore.appCtx = applicationContext
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("sms", "Messages", NotificationManager.IMPORTANCE_HIGH)
        )
    }
}
