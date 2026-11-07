package com.example.mondialer

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService

class MyInCallService : InCallService() {

    companion object {
        var instance: MyInCallService? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onBringToForeground(showDialpad: Boolean) {
        super.onBringToForeground(showDialpad)
        startActivity(Intent(this, InCallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }

    override fun onCallAdded(call: Call) {
        OngoingCall.add(call)
        val i = Intent(this, InCallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }

    override fun onCallRemoved(call: Call) {
        OngoingCall.remove(call)
    }
}
