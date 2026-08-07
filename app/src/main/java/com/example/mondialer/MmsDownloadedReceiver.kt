package com.example.mondialer

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.io.File

class MmsDownloadedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val path = intent.getStringExtra("path") ?: return
        val file = File(path)
        try {
            if (!file.exists() || file.length() == 0L) return
            val bytes = file.readBytes()
            val msg = MmsCodec.parseRetrieve(bytes) ?: return
            val from = msg.from ?: "inconnu"

            MmsStore.insert(context, from, msg.parts, outgoing = false)

            // Notification
            val hasImage = msg.parts.any { it.mime.startsWith("image/") }
            val text = msg.parts.firstOrNull { it.mime.startsWith("text/") }
                ?.let { String(it.data).take(80) }
                ?: if (hasImage) "📷 Photo" else "📎 Pièce jointe"

            val open = Intent(context, ThreadActivity::class.java)
                .putExtra("address", from)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pi = PendingIntent.getActivity(
                context, from.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val who = ContactLookup.displayName(context, from)
            val builder = Notification.Builder(context, "sms")
                .setSmallIcon(android.R.drawable.sym_action_chat)
                .setContentTitle(who)
                .setContentText(text)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
            ContactLookup.photo(context, from)?.let { builder.setLargeIcon(it) }
            val notif = builder.build()
            context.getSystemService(NotificationManager::class.java)
                .notify(from.hashCode(), notif)
        } catch (_: Exception) {
        } finally {
            try { file.delete() } catch (_: Exception) {}
        }
    }
}
