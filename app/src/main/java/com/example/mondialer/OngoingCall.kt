package com.example.mondialer

import android.telecom.Call

object OngoingCall {
    private val calls = mutableListOf<Call>()
    @Synchronized fun add(value: Call) { if (!calls.contains(value)) calls.add(value) }
    @Synchronized fun remove(value: Call) { calls.remove(value) }
    @get:Synchronized val call: Call?
        get() = calls.lastOrNull { it.state != Call.STATE_DISCONNECTED }
}
