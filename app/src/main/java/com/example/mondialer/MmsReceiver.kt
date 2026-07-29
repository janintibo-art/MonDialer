package com.example.mondialer

import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Réception MMS non prise en charge en détail : notification simple
        try {
            val notif = Notification.Builder(context, "sms")
                .setSmallIcon(android.R.drawable.sym_action_chat)
                .setContentTitle(context.getString(R.string.mms_received))
                .setContentText(context.getString(R.string.mms_hint))
                .setAutoCancel(true)
                .build()
            context.getSystemService(NotificationManager::class.java)
                .notify(9999, notif)
        } catch (_: Exception) {}
    }
}
