package com.example.mondialer

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService

class MyInCallService : InCallService() {

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
