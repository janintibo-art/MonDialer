package com.example.mondialer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Déclenche l'appel fictif au moment programmé. */
class FakeCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val i = Intent(context, FakeCallActivity::class.java)
            .putExtra("name", intent.getStringExtra("name"))
            .putExtra("number", intent.getStringExtra("number"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    }
}
