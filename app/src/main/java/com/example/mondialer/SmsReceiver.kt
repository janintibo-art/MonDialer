package com.example.mondialer

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (msgs.isEmpty()) return

        val address = msgs[0].originatingAddress ?: ""
        val body = msgs.joinToString("") { it.messageBody ?: "" }
        val date = System.currentTimeMillis()

        // En tant qu'app SMS par défaut, c'est à nous d'enregistrer le message
        try {
            val values = ContentValues().apply {
                put("address", address)
                put("body", body)
                put("date", date)
                put("read", 0)
                put("seen", 0)
            }
            context.contentResolver.insert(Uri.parse("content://sms/inbox"), values)
        } catch (_: Exception) {}

        // Notification
        try {
            val open = Intent(context, ThreadActivity::class.java)
                .putExtra("address", address)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pi = PendingIntent.getActivity(
                context, address.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notif = Notification.Builder(context, "sms")
                .setSmallIcon(android.R.drawable.sym_action_chat)
                .setContentTitle(address)
                .setContentText(body.take(120))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            context.getSystemService(NotificationManager::class.java)
                .notify(address.hashCode(), notif)
        } catch (_: Exception) {}
    }
}
