package com.example.mondialer

import android.app.Activity
import android.app.AlertDialog
import android.widget.Toast

/** Analyse explicative uniquement, jamais utilisée pour refuser automatiquement un appel. */
object NumberInsight {
    private val npv = listOf(
        "0162", "0163", "0270", "0271", "0377", "0378", "0424", "0425",
        "0568", "0569", "0948", "0949", "02688", "02689", "05987",
        "05988", "05989", "09475", "09476", "09477", "09478", "09479"
    )

    private fun frenchNumber(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return if (digits.startsWith("33") && digits.length == 11) "0" + digits.drop(2)
            else digits
    }

    fun officialCategory(number: String): String {
        val n = frenchNumber(number)
        return when {
            npv.any { n.startsWith(it) } ->
                "Préfixe de numéro polyvalent vérifié ARCEP, utilisable par des systèmes automatisés. Cela ne prouve pas que cet appel est du démarchage."
            n.startsWith("0937") || n.startsWith("0938") || n.startsWith("0939") ->
                "Numéro destiné aux échanges avec une plateforme technique (ARCEP), pas automatiquement frauduleux."
            n.startsWith("08") ->
                "Numéro de service à valeur ajoutée ; coût et service à vérifier avant tout rappel."
            n.startsWith("118") ->
                "Numéro court de renseignements téléphoniques ; tarif à vérifier."
            n.length == 10 && (n.startsWith("06") || n.startsWith("07")) ->
                "Numéro de type mobile : aucun verdict fiable à partir du préfixe."
            else -> "Catégorie non déterminante : le préfixe ne permet pas d'établir une fraude."
        }
    }

    /** Confirmation explicite : envoi du seul numéro et du motif affiché à Gemini. */
    fun ask(activity: Activity, number: String, reason: String) {
        if (number.isBlank() || number.contains("masqu", ignoreCase = true)) {
            Toast.makeText(activity, "Numéro non analysable", Toast.LENGTH_SHORT).show()
            return
        }
        if (BlockRulesStore.aiKey.isBlank()) {
            AlertDialog.Builder(activity)
                .setTitle("Clé Gemini absente")
                .setMessage("Ajoutez votre clé dans les paramètres IA d'Anarchie Phone avant d'analyser un numéro.")
                .setPositiveButton("OK", null).show()
            return
        }
        val official = officialCategory(number)
        AlertDialog.Builder(activity)
            .setTitle("Analyser le numéro avec Gemini ?")
            .setMessage("Numéro : $number\n\nARCEP : $official\n\n" +
                "En continuant, ce numéro et son motif de blocage seront envoyés à Google Gemini. " +
                "Les contacts, SMS et historique complet ne seront pas transmis. " +
                "L'IA ne vérifie pas l'identité de l'entreprise et ne bloque aucun appel.")
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Analyser") { _, _ ->
                val progress = AlertDialog.Builder(activity)
                    .setTitle("Analyse IA")
                    .setMessage("Analyse en cours…")
                    .setNegativeButton("Fermer", null)
                    .create()
                progress.show()
                Thread {
                    val outcome = try {
                        val system = """Tu es un assistant de prudence téléphonique en France. 
Tu ne disposes PAS d'un annuaire en temps réel ni d'une recherche Internet. 
N'invente jamais le nom d'une entreprise, un opérateur attributaire, des avis ou des signalements. 
La catégorie ARCEP décrit une catégorie de numéros, pas l'identité d'un appelant. 
Un numéro présenté peut être usurpé. Un préfixe ne prouve pas une fraude. 
Explique en français en 5 lignes max, avec les rubriques : CATÉGORIE, INDICES, 
IDENTITÉ, RISQUE, CONSEIL. Si aucune identité n'est vérifiée, écris 'Inconnue'.
Ne conseille pas de rappeler les numéros potentiellement surtaxés."""
                        val message = "Numéro : $number\nCatégorie ARCEP déduite du préfixe : $official\n" +
                            "Motif local du journal (non vérifié) : ${reason.take(180)}"
                        AiClient.ask(system, message).trim().take(2500)
                    } catch (e: Exception) { "Analyse indisponible : ${e.message.orEmpty().take(140)}" }
                    activity.runOnUiThread {
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            progress.dismiss()
                            AlertDialog.Builder(activity)
                                .setTitle("Analyse IA — $number")
                                .setMessage("$outcome\n\nRésultat indicatif, non vérifié. " +
                                    "Il ne modifie pas les règles de blocage.")
                                .setPositiveButton("Fermer", null).show()
                        }
                    }
                }.start()
            }.show()
    }
}
