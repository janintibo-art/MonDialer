package com.example.mondialer

import android.app.Activity
import android.os.Bundle
import android.telecom.Call
import android.telecom.VideoProfile
import android.widget.Button
import android.widget.TextView

class InCallActivity : Activity() {

    private var call: Call? = null

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            updateUi(state)
            if (state == Call.STATE_DISCONNECTED) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incall)

        call = OngoingCall.call
        val c = call
        if (c == null) { finish(); return }

        val number = c.details?.handle?.schemeSpecificPart ?: getString(R.string.hidden_number)
        findViewById<TextView>(R.id.txtNumber).text = number

        findViewById<Button>(R.id.btnAnswer).setOnClickListener {
            c.answer(VideoProfile.STATE_AUDIO_ONLY)
        }
        findViewById<Button>(R.id.btnHangup).setOnClickListener {
            if (c.state == Call.STATE_RINGING) c.reject(false, null) else c.disconnect()
        }

        c.registerCallback(callback)
        updateUi(c.state)
    }

    private fun updateUi(state: Int) {
        val txt = findViewById<TextView>(R.id.txtState)
        val answer = findViewById<Button>(R.id.btnAnswer)
        when (state) {
            Call.STATE_RINGING -> { txt.text = getString(R.string.state_ringing); answer.isEnabled = true }
            Call.STATE_DIALING -> { txt.text = getString(R.string.state_dialing); answer.isEnabled = false }
            Call.STATE_ACTIVE -> { txt.text = getString(R.string.state_active); answer.isEnabled = false }
            Call.STATE_DISCONNECTED -> txt.text = getString(R.string.state_ended)
            else -> txt.text = ""
        }
    }

    override fun onDestroy() {
        call?.unregisterCallback(callback)
        super.onDestroy()
    }
}
