package com.example.mondialer

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.widget.Toast

/**
 * Signalement à la plateforme nationale 33700 (gratuit, tous opérateurs).
 * SMS indésirable : on transfère le contenu, puis la plateforme répond en
 * demandant le numéro de l'expéditeur.
 * Appel indésirable : on envoie « spam vocal » suivi du numéro.
 */
object Report33700 {

    private const val SHORT_CODE = "33700"

    private fun canSend(a: Activity): Boolean {
        if (a.checkSelfPermission(android.Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            a.requestPermissions(arrayOf(android.Manifest.permission.SEND_SMS), 33)
            return false
        }
        return true
    }

    private fun send(a: Activity, body: String): Boolean = try {
        val sm = SmsManager.getDefault()
        val parts = sm.divideMessage(body)
        sm.sendMultipartTextMessage(SHORT_CODE, null, parts, null, null)
        true
    } catch (e: Exception) {
        Toast.makeText(a, R.string.report_fail, Toast.LENGTH_SHORT).show()
        false
    }

    /** Signale un SMS : envoi du contenu, puis proposition d'envoyer le numéro. */
    fun reportSms(a: Activity, content: String, sender: String) {
        if (!canSend(a)) return
        AlertDialog.Builder(a)
            .setTitle(R.string.report_title)
            .setMessage(a.getString(R.string.report_sms_confirm, content.take(120)))
            .setPositiveButton(R.string.report_send) { _, _ ->
                if (!send(a, content)) return@setPositiveButton
                // Étape 2 : la plateforme réclame ensuite le numéro émetteur
                AlertDialog.Builder(a)
                    .setTitle(R.string.report_step2_title)
                    .setMessage(a.getString(R.string.report_step2, sender))
                    .setPositiveButton(R.string.report_send_number) { _, _ ->
                        if (send(a, sender)) {
                            Toast.makeText(a, R.string.report_done, Toast.LENGTH_LONG).show()
                        }
                    }
                    .setNegativeButton(R.string.report_later, null)
                    .show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Signale un appel indésirable au format attendu par la plateforme. */
    fun reportCall(a: Activity, number: String) {
        if (!canSend(a)) return
        val body = "spam vocal $number"
        AlertDialog.Builder(a)
            .setTitle(R.string.report_title)
            .setMessage(a.getString(R.string.report_call_confirm, body))
            .setPositiveButton(R.string.report_send) { _, _ ->
                if (send(a, body)) {
                    Toast.makeText(a, R.string.report_done, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
