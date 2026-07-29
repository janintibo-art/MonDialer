package com.example.mondialer

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.SmsManager

class HeadlessSmsSendService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
            val dest = intent?.data?.schemeSpecificPart
            if (!text.isNullOrBlank() && !dest.isNullOrBlank()) {
                val sm = SmsManager.getDefault()
                val parts = sm.divideMessage(text)
                sm.sendMultipartTextMessage(dest, null, parts, null, null)
            }
        } catch (_: Exception) {}
        stopSelf()
        return START_NOT_STICKY
    }
}
