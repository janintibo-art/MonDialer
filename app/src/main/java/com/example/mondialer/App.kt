package com.example.mondialer

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        BlockRulesStore.appCtx = applicationContext
    }
}
