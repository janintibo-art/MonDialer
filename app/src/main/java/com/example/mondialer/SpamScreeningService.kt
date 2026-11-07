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
        // Android peut transmettre le nom présenté par le réseau/opérateur.
        // Cette information est parfois absente et n'est pas une identité certifiée.
        BlockRulesStore.rememberNetworkCallerName(number, details.callerDisplayName)
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
        // En mode avertissement, l'appel passe normalement : notification seulement.
        if (reason == null && CommunityLists.mode(applicationContext) == "warn" &&
            !BlockRulesStore.isInContacts(number)) {
            CommunityLists.match(applicationContext, number)?.let { hit ->
                warnCommunity(number, hit)
            }
        }
        respondToCall(details, response.build())
    }

    private fun warnCommunity(number: String?, hit: CommunityLists.Hit) {
        try {
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(android.app.NotificationChannel(
                "community_warn", "Avertissements communautaires",
                android.app.NotificationManager.IMPORTANCE_DEFAULT))
            val title = "Numéro signalé (non vérifié)"
            val body = "${number ?: "Numéro inconnu"} · ${hit.source} · ${hit.reports} signalements"
            val notification = android.app.Notification.Builder(this, "community_warn")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title).setContentText(body)
                .setAutoCancel(true).build()
            manager.notify((number ?: "").hashCode(), notification)
        } catch (_: Exception) { }
    }

    /**
     * Signale l'appel écarté, sans bruit ni vibration : l'important est de
     * pouvoir constater qu'un correspondant légitime a été filtré par erreur.
     */
    private fun notifyBlocked(number: String?, reason: String) {
        try {
            // Un correspondant connu bloqué par erreur doit être identifiable
            val shown = BlockRulesStore.callerAlias(number)
                ?: BlockRulesStore.networkCallerName(number)
                ?: ContactLookup.displayName(this, number)
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
