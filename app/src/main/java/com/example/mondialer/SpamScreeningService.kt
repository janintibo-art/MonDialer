package com.example.mondialer

import android.telecom.Call
import android.telecom.CallScreeningService

class SpamScreeningService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        BlockRulesStore.appCtx = applicationContext

        // On ne filtre que les appels entrants
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }

        val number = details.handle?.schemeSpecificPart
        val block = BlockRulesStore.shouldBlock(number)

        val response = CallResponse.Builder()
        if (block) {
            response
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipNotification(true)
        }
        respondToCall(details, response.build())
    }
}
