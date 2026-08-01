package com.example.mondialer

import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.util.ByteArrayDataSource

/** Envoi SMTP, avec le compte choisi par l'utilisateur. */
object MailSender {

    private fun session(host: String, port: String, user: String, pass: String): Session {
        val props = Properties()
        props["mail.smtp.host"] = host
        props["mail.smtp.port"] = port
        props["mail.smtp.auth"] = "true"
        // Le port détermine le mode de chiffrement attendu par le serveur
        if (port == "587" || port == "25") {
            props["mail.smtp.starttls.enable"] = "true"
        } else {
            props["mail.smtp.ssl.enable"] = "true"
        }
        props["mail.smtp.connectiontimeout"] = "20000"
        props["mail.smtp.timeout"] = "30000"
        return Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(user, pass)
        })
    }

    /** Vérifie les identifiants sans rien envoyer. */
    fun verify(host: String, port: String, user: String, pass: String) {
        val transport = session(host, port, user, pass).getTransport("smtp")
        transport.connect(host, port.toIntOrNull() ?: 465, user, pass)
        transport.close()
    }

    /** Envoie un message, éventuellement avec une pièce jointe. */
    fun send(
        account: BlockRulesStore.MailAccount,
        to: String, subject: String, body: String,
        attachData: ByteArray? = null,
        attachName: String? = null,
        attachMime: String? = null
    ) {
        val session = session(account.host, account.port, account.user, account.pass)
        val msg = MimeMessage(session)
        msg.setFrom(
            if (account.fromName.isBlank()) InternetAddress(account.user)
            else InternetAddress(account.user, account.fromName))
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
        msg.subject = subject

        if (attachData != null) {
            val multipart = MimeMultipart()
            val textPart = MimeBodyPart()
            textPart.setText(body, "UTF-8")
            multipart.addBodyPart(textPart)
            val filePart = MimeBodyPart()
            filePart.dataHandler = javax.activation.DataHandler(
                ByteArrayDataSource(attachData, attachMime ?: "application/octet-stream"))
            filePart.fileName = attachName ?: "fichier"
            multipart.addBodyPart(filePart)
            msg.setContent(multipart)
        } else {
            msg.setText(body, "UTF-8")
        }
        Transport.send(msg)
    }
}
