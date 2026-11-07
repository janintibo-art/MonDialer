package com.example.mondialer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.Call
import android.telecom.InCallService

/** Conserve l'accès à l'écran d'appel même après fermeture de l'interface. */
class MyInCallService : InCallService() {
    companion object {
        var instance: MyInCallService? = null
        private const val CHANNEL_ID = "ongoing_phone_call"
        private const val NOTIFICATION_ID = 8821
    }

    private val observers = mutableMapOf<Call, Call.Callback>()

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        observers.forEach { (call, callback) ->
            try { call.unregisterCallback(callback) } catch (_: Exception) { }
        }
        observers.clear()
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onBringToForeground(showDialpad: Boolean) {
        super.onBringToForeground(showDialpad)
        openCallScreen()
    }

    private fun openCallScreen() {
        if (OngoingCall.call == null) return
        startActivity(Intent(this, InCallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        OngoingCall.add(call)
        if (!observers.containsKey(call)) {
            val observer = object : Call.Callback() {
                override fun onStateChanged(call: Call, state: Int) {
                    refreshCallNotification()
                }
            }
            observers[call] = observer
            call.registerCallback(observer)
        }
        refreshCallNotification()
        openCallScreen()
    }

    override fun onCallRemoved(call: Call) {
        observers.remove(call)?.let { callback ->
            try { call.unregisterCallback(callback) } catch (_: Exception) { }
        }
        OngoingCall.remove(call)
        refreshCallNotification()
        super.onCallRemoved(call)
    }

    /** Une notification ouvre l'appel par une action explicite de l'utilisateur.
     *  Elle est complétée par le bouton de reprise de l'écran d'accueil.
     */
    private fun refreshCallNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        val call = OngoingCall.call
        if (call == null) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        try {
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, "Appel en cours", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Retour aux commandes de communication" })
            val intent = Intent(this, InCallActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pending = PendingIntent.getActivity(this, NOTIFICATION_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val number = call.details?.handle?.schemeSpecificPart ?: "Numéro masqué"
            val notification = Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.sym_action_call)
                .setContentTitle("Appel en cours — revenir à l'appel")
                .setContentText(number)
                .setContentIntent(pending)
                .setCategory(Notification.CATEGORY_CALL)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .build()
            if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(
                    Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                manager.notify(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            // L'accès depuis l'accueil reste disponible si les notifications sont refusées.
        }
    }
}
