package com.example.mondialer

import android.telecom.Call
import android.telecom.CallScreeningService

class SpamScreeningService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        BlockRulesStore.appCtx = applicationContext

        if (details.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }

        val number = details.handle?.schemeSpecificPart
        val reason = BlockRulesStore.blockReason(number)

        val response = CallResponse.Builder()
        if (reason != null) {
            BlockRulesStore.logBlocked(number ?: "(masqué)", reason)
            AnarchieWidget.refresh(applicationContext)
            if (BlockRulesStore.silentMode) {
                // Mode discret : sonnerie coupée, l'appel file en messagerie
                response.setSilenceCall(true)
            } else {
                response
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipNotification(true)
            }
        }
        respondToCall(details, response.build())
    }
}
