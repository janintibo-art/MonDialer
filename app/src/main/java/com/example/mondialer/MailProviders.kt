package com.example.mondialer

/** Réglages SMTP des principaux fournisseurs, pour éviter la saisie manuelle. */
object MailProviders {

    class Preset(
        val label: String,
        val host: String,
        val port: String,
        val domains: List<String>,
        val note: String
    )

    val ALL = listOf(
        Preset("Yahoo", "smtp.mail.yahoo.com", "465",
            listOf("yahoo.fr", "yahoo.com", "ymail.com", "rocketmail.com"),
            "Yahoo exige un mot de passe d’application : login.yahoo.com → Sécurité du compte → Générer un mot de passe d’application."),
        Preset("Gmail", "smtp.gmail.com", "465",
            listOf("gmail.com", "googlemail.com"),
            "Gmail exige un mot de passe d’application : myaccount.google.com/apppasswords (validation en 2 étapes requise)."),
        Preset("Orange / Sosh", "smtp.orange.fr", "465",
            listOf("orange.fr", "wanadoo.fr", "sosh.fr"),
            "Votre mot de passe habituel convient."),
        Preset("SFR / RED", "smtp.sfr.fr", "465",
            listOf("sfr.fr", "neuf.fr", "numericable.fr"),
            "Votre mot de passe habituel convient."),
        Preset("Free", "smtp.free.fr", "465",
            listOf("free.fr"),
            "Votre mot de passe habituel convient."),
        Preset("Outlook / Hotmail", "smtp.office365.com", "587",
            listOf("outlook.fr", "outlook.com", "hotmail.fr", "hotmail.com", "live.fr"),
            "Un mot de passe d’application peut être exigé si la double authentification est active."),
        Preset("La Poste", "smtp.laposte.net", "465",
            listOf("laposte.net"),
            "Votre mot de passe habituel convient."),
        Preset("AOL", "smtp.aol.com", "465",
            listOf("aol.com", "aol.fr"),
            "AOL exige un mot de passe d’application."),
        Preset("iCloud", "smtp.mail.me.com", "587",
            listOf("icloud.com", "me.com", "mac.com"),
            "iCloud exige un mot de passe pour application : appleid.apple.com."),
        Preset("Autre / manuel", "", "465", emptyList(),
            "Renseignez le serveur SMTP indiqué par votre fournisseur.")
    )

    /** Devine le fournisseur à partir du domaine de l'adresse saisie. */
    fun guess(email: String): Preset? {
        val domain = email.substringAfter("@", "").lowercase().trim()
        if (domain.isEmpty()) return null
        return ALL.firstOrNull { domain in it.domains }
    }
}
