package com.example.mondialer

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony

object MmsStore {

    /** Insère un MMS (reçu ou envoyé) dans le fournisseur système avec ses parties. */
    fun insert(ctx: Context, address: String, parts: List<MmsCodec.Part>, outgoing: Boolean): Boolean {
        return try {
            val threadId = Telephony.Threads.getOrCreateThreadId(ctx, address)
            val values = ContentValues().apply {
                put("thread_id", threadId)
                put("date", System.currentTimeMillis() / 1000)
                put("msg_box", if (outgoing) 2 else 1)
                put("read", if (outgoing) 1 else 0)
                put("seen", if (outgoing) 1 else 0)
                put("ct_t", "application/vnd.wap.multipart.mixed")
                put("m_type", if (outgoing) 128 else 132)
                put("mms_version", 18)
            }
            val msgUri = ctx.contentResolver.insert(Uri.parse("content://mms"), values)
                ?: return false
            val msgId = msgUri.lastPathSegment ?: return false

            // Adresses
            val addr = ContentValues().apply {
                put("address", address)
                put("charset", 106)
                put("type", if (outgoing) 151 else 137)  // 151 = destinataire, 137 = expéditeur
            }
            ctx.contentResolver.insert(Uri.parse("content://mms/$msgId/addr"), addr)

            // Parties
            for ((idx, part) in parts.withIndex()) {
                val pv = ContentValues().apply {
                    put("mid", msgId)
                    put("ct", part.mime)
                    put("name", part.name ?: "part$idx")
                    put("cl", part.name ?: "part$idx")
                }
                if (part.mime.startsWith("text/")) {
                    pv.put("text", String(part.data))
                    pv.put("chset", 106)
                    ctx.contentResolver.insert(Uri.parse("content://mms/$msgId/part"), pv)
                } else {
                    val partUri = ctx.contentResolver.insert(
                        Uri.parse("content://mms/$msgId/part"), pv) ?: continue
                    ctx.contentResolver.openOutputStream(partUri)?.use {
                        it.write(part.data)
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
