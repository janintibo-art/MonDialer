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

    override fun onCallAdded(call: Call) {
        OngoingCall.call = call
        val i = Intent(this, InCallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }

    override fun onCallRemoved(call: Call) {
        if (OngoingCall.call == call) OngoingCall.call = null
    }
}
