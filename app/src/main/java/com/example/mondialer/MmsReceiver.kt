package com.example.mondialer

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import androidx.core.content.FileProvider
import java.io.File

class MmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val data = intent.getByteArrayExtra("data") ?: return simpleNotify(context)
        val location = MmsCodec.parseNotificationLocation(data)
            ?: return simpleNotify(context)

        try {
            val dir = File(context.cacheDir, "mms")
            dir.mkdirs()
            val file = File(dir, "in_${System.currentTimeMillis()}.pdu")
            file.createNewFile()
            val uri = FileProvider.getUriForFile(
                context, "com.example.mondialer.files", file)

            val done = Intent(context, MmsDownloadedReceiver::class.java)
                .putExtra("path", file.absolutePath)
            val pi = PendingIntent.getBroadcast(
                context, file.hashCode(), done,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)

            SmsManager.getDefault().downloadMultimediaMessage(
                context, location, uri, null, pi)
        } catch (e: Exception) {
            simpleNotify(context)
        }
    }

    private fun simpleNotify(context: Context) {
        try {
            val notif = Notification.Builder(context, "sms")
                .setSmallIcon(android.R.drawable.sym_action_chat)
                .setContentTitle(context.getString(R.string.mms_received))
                .setContentText(context.getString(R.string.mms_dl_fail))
                .setAutoCancel(true)
                .build()
            context.getSystemService(NotificationManager::class.java)
                .notify((System.currentTimeMillis() % 10000).toInt(), notif)
        } catch (_: Exception) {}
    }
}
