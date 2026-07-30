package com.example.mondialer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
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

class EmailActivity : Activity() {

    private var attachData: ByteArray? = null
    private var attachName: String? = null
    private var attachMime: String? = null

    private fun prefs() = getSharedPreferences("email_cfg", Context.MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtil.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_email)

        // Pré-remplissage du destinataire
        intent.getStringExtra("to")?.let {
            findViewById<EditText>(R.id.emailTo).setText(it)
        }
        intent.data?.let { uri ->
            if (uri.scheme == "mailto") {
                findViewById<EditText>(R.id.emailTo).setText(uri.schemeSpecificPart)
            }
        }

        // Réglages SMTP
        val panel = findViewById<View>(R.id.settingsPanel)
        findViewById<Button>(R.id.btnEmailSettings).setOnClickListener {
            panel.visibility =
                if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        loadSettings()
        if (prefs().getString("host", "").isNullOrBlank()) {
            panel.visibility = View.VISIBLE
            Toast.makeText(this, R.string.email_cfg_missing, Toast.LENGTH_LONG).show()
        }

        val presets = mapOf(
            R.id.presetGmail to Pair("smtp.gmail.com", "465"),
            R.id.presetOrange to Pair("smtp.orange.fr", "465"),
            R.id.presetSfr to Pair("smtp.sfr.fr", "465"),
            R.id.presetFree to Pair("smtp.free.fr", "465"),
            R.id.presetOutlook to Pair("smtp.office365.com", "587"),
            R.id.presetLaposte to Pair("smtp.laposte.net", "465")
        )
        presets.forEach { (id, hp) ->
            findViewById<Button>(id).setOnClickListener {
                findViewById<EditText>(R.id.smtpHost).setText(hp.first)
                findViewById<EditText>(R.id.smtpPort).setText(hp.second)
            }
        }

        findViewById<Button>(R.id.btnSaveSmtp).setOnClickListener {
            prefs().edit()
                .putString("host", text(R.id.smtpHost))
                .putString("port", text(R.id.smtpPort))
                .putString("user", text(R.id.smtpUser))
                .putString("pass", text(R.id.smtpPass))
                .apply()
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
            panel.visibility = View.GONE
        }

        // Pièce jointe
        findViewById<Button>(R.id.btnEmailAttach).setOnClickListener {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "*/*"
            startActivityForResult(i, 80)
        }
        findViewById<TextView>(R.id.emailAttachChip).setOnClickListener {
            attachData = null; attachName = null; attachMime = null
            it.visibility = View.GONE
        }

        findViewById<Button>(R.id.btnEmailSend).setOnClickListener { send() }
    }

    private fun text(id: Int) = findViewById<EditText>(id).text.toString().trim()

    private fun loadSettings() {
        val p = prefs()
        findViewById<EditText>(R.id.smtpHost).setText(p.getString("host", ""))
        findViewById<EditText>(R.id.smtpPort).setText(p.getString("port", "465"))
        findViewById<EditText>(R.id.smtpUser).setText(p.getString("user", ""))
        findViewById<EditText>(R.id.smtpPass).setText(p.getString("pass", ""))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 80 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            Thread {
                try {
                    val bytes = contentResolver.openInputStream(uri)?.readBytes()
                        ?: return@Thread
                    val mime = contentResolver.getType(uri) ?: "application/octet-stream"
                    var name = "fichier"
                    contentResolver.query(uri, null, null, null, null)?.use { c ->
                        val idx = c.getColumnIndex(
                            android.provider.OpenableColumns.DISPLAY_NAME)
                        if (c.moveToFirst() && idx >= 0) name = c.getString(idx) ?: name
                    }
                    runOnUiThread {
                        attachData = bytes; attachMime = mime; attachName = name
                        val chip = findViewById<TextView>(R.id.emailAttachChip)
                        chip.text = "📎 $name (${bytes.size / 1024} Ko) ✕"
                        chip.visibility = View.VISIBLE
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this, R.string.attach_fail, Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }
    }

    private fun send() {
        val p = prefs()
        val host = p.getString("host", "") ?: ""
        val port = p.getString("port", "465") ?: "465"
        val user = p.getString("user", "") ?: ""
        val pass = p.getString("pass", "") ?: ""
        val to = text(R.id.emailTo)
        val subject = text(R.id.emailSubject)
        val body = findViewById<EditText>(R.id.emailBody).text.toString()

        if (host.isBlank() || user.isBlank() || pass.isBlank()) {
            findViewById<View>(R.id.settingsPanel).visibility = View.VISIBLE
            Toast.makeText(this, R.string.email_cfg_missing, Toast.LENGTH_LONG).show()
            return
        }
        if (to.isBlank()) {
            Toast.makeText(this, R.string.email_to_missing, Toast.LENGTH_SHORT).show()
            return
        }

        val btn = findViewById<Button>(R.id.btnEmailSend)
        btn.isEnabled = false
        Toast.makeText(this, R.string.email_sending, Toast.LENGTH_SHORT).show()

        val aData = attachData
        val aName = attachName
        val aMime = attachMime

        Thread {
            try {
                val props = Properties()
                props["mail.smtp.host"] = host
                props["mail.smtp.port"] = port
                props["mail.smtp.auth"] = "true"
                if (port == "587") {
                    props["mail.smtp.starttls.enable"] = "true"
                } else {
                    props["mail.smtp.ssl.enable"] = "true"
                }
                val session = Session.getInstance(props, object : Authenticator() {
                    override fun getPasswordAuthentication() =
                        PasswordAuthentication(user, pass)
                })

                val msg = MimeMessage(session)
                msg.setFrom(InternetAddress(user))
                msg.setRecipients(Message.RecipientType.TO,
                    InternetAddress.parse(to))
                msg.subject = subject

                if (aData != null) {
                    val multipart = MimeMultipart()
                    val textPart = MimeBodyPart()
                    textPart.setText(body)
                    multipart.addBodyPart(textPart)
                    val filePart = MimeBodyPart()
                    filePart.dataHandler = javax.activation.DataHandler(
                        ByteArrayDataSource(aData, aMime ?: "application/octet-stream"))
                    filePart.fileName = aName ?: "fichier"
                    multipart.addBodyPart(filePart)
                    msg.setContent(multipart)
                } else {
                    msg.setText(body)
                }

                Transport.send(msg)
                runOnUiThread {
                    Toast.makeText(this, R.string.email_sent, Toast.LENGTH_LONG).show()
                    finish()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    btn.isEnabled = true
                    Toast.makeText(this,
                        getString(R.string.email_fail, e.message ?: ""),
                        Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        ThemeUtil.refreshIfNeeded(this)
    }

    override fun onDestroy() {
        ThemeUtil.forget(this)
        super.onDestroy()
    }
}
