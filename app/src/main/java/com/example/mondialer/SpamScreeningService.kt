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
            if (BlockRulesStore.notifyBlocked) notifyBlocked(number, reason)
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

    /**
     * Signale l'appel écarté, sans bruit ni vibration : l'important est de
     * pouvoir constater qu'un correspondant légitime a été filtré par erreur.
     */
    private fun notifyBlocked(number: String?, reason: String) {
        try {
            val shown = number ?: getString(R.string.hidden_number)
            val open = android.content.Intent(this, BlockedLogActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            val pi = android.app.PendingIntent.getActivity(
                this, shown.hashCode(), open,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE)

            val notif = android.app.Notification.Builder(this, "blocked")
                .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
                .setContentTitle(getString(R.string.notif_blocked_title, shown))
                .setContentText(reason)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build()
            getSystemService(android.app.NotificationManager::class.java)
                .notify(shown.hashCode(), notif)
        } catch (_: Exception) {}
    }
}
