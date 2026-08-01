package com.example.mondialer

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class EmailActivity : Activity() {

    private var attachData: ByteArray? = null
    private var attachName: String? = null
    private var attachMime: String? = null


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

        setupAccountPicker()

        // Gestion des comptes d'envoi
        findViewById<Button>(R.id.btnEmailSettings).setOnClickListener {
            startActivity(Intent(this, MailAccountsActivity::class.java))
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

        findViewById<Button>(R.id.btnEmailAi).setOnClickListener { suggestBody() }
        findViewById<Button>(R.id.btnEmailSend).setOnClickListener { send() }
    }

    private var accounts = mutableListOf<BlockRulesStore.MailAccount>()
    private var current: BlockRulesStore.MailAccount? = null

    /** Bandeau « De : » permettant de choisir l'adresse d'expédition. */
    private fun setupAccountPicker() {
        accounts = BlockRulesStore.mailAccounts()
        current = accounts.firstOrNull { it.id == BlockRulesStore.defaultMailAccount }
            ?: accounts.firstOrNull()
        updateAccountLabel()

        findViewById<TextView>(R.id.txtFrom).setOnClickListener {
            accounts = BlockRulesStore.mailAccounts()
            if (accounts.isEmpty()) {
                startActivity(Intent(this, MailAccountsActivity::class.java))
                return@setOnClickListener
            }
            val labels = accounts.map { it.label + "  —  " + it.user }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle(R.string.mail_pick_account)
                .setItems(labels) { _, which ->
                    current = accounts[which]
                    updateAccountLabel()
                }
                .setNeutralButton(R.string.mail_manage) { _, _ ->
                    startActivity(Intent(this, MailAccountsActivity::class.java))
                }
                .show()
        }
    }

    private fun updateAccountLabel() {
        val c = current
        findViewById<TextView>(R.id.txtFrom).text =
            if (c == null) getString(R.string.mail_no_account)
            else getString(R.string.mail_from, c.user)
    }

    private fun text(id: Int) = findViewById<EditText>(id).text.toString().trim()

    /** Propose trois rédactions à partir de l'objet et des notes déjà saisies. */
    private fun suggestBody() {
        if (BlockRulesStore.aiKey.isBlank()) {
            AlertDialog.Builder(this)
                .setMessage(R.string.ai_no_key)
                .setPositiveButton(R.string.ai_configure) { _, _ ->
                    startActivity(Intent(this, AiSettingsActivity::class.java))
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }
        val subject = text(R.id.emailSubject)
        val body = findViewById<EditText>(R.id.emailBody).text.toString().trim()
        if (subject.isEmpty() && body.isEmpty()) {
            Toast.makeText(this, R.string.ai_no_context_mail, Toast.LENGTH_LONG).show()
            return
        }
        val context = buildString {
            if (subject.isNotEmpty()) append("Objet : ").append(subject).append("\n")
            if (body.isNotEmpty()) append("Notes ou message reçu :\n").append(body)
        }

        val progress = AlertDialog.Builder(this)
            .setMessage(R.string.ai_thinking)
            .setCancelable(true)
            .show()

        Thread {
            val result = try {
                AiClient.suggestReplies(context, "mail")
            } catch (e: Exception) {
                runOnUiThread {
                    progress.dismiss()
                    Toast.makeText(this,
                        getString(R.string.ai_error, e.message ?: ""),
                        Toast.LENGTH_LONG).show()
                }
                return@Thread
            }
            runOnUiThread {
                progress.dismiss()
                if (result.isEmpty()) {
                    Toast.makeText(this, R.string.ai_empty, Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                // Aperçu tronqué dans la liste, texte complet une fois choisi
                val labels = result.map { it.take(90).replace("\n", " ") }.toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle(R.string.ai_pick)
                    .setItems(labels) { _, which ->
                        findViewById<EditText>(R.id.emailBody).setText(result[which])
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }.start()
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
        accounts = BlockRulesStore.mailAccounts()
        if (current == null) current = accounts.firstOrNull {
            it.id == BlockRulesStore.defaultMailAccount } ?: accounts.firstOrNull()
        val account = current
        if (account == null) {
            AlertDialog.Builder(this)
                .setMessage(R.string.mail_no_account_msg)
                .setPositiveButton(R.string.mail_manage) { _, _ ->
                    startActivity(Intent(this, MailAccountsActivity::class.java))
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }

        val to = text(R.id.emailTo)
        if (to.isBlank()) {
            Toast.makeText(this, R.string.email_to_missing, Toast.LENGTH_SHORT).show()
            return
        }
        val subject = text(R.id.emailSubject)
        val body = findViewById<EditText>(R.id.emailBody).text.toString()

        val btn = findViewById<Button>(R.id.btnEmailSend)
        btn.isEnabled = false
        Toast.makeText(this, R.string.email_sending, Toast.LENGTH_SHORT).show()

        val aData = attachData
        val aName = attachName
        val aMime = attachMime

        Thread {
            try {
                MailSender.send(account, to, subject, body, aData, aName, aMime)
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

    private fun suggestBody() {
        if (BlockRulesStore.aiKey.isBlank()) {
            AlertDialog.Builder(this)
                .setMessage(R.string.ai_no_key)
                .setPositiveButton(R.string.ai_configure) { _, _ ->
                    startActivity(Intent(this, AiSettingsActivity::class.java))
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }
        val subject = text(R.id.emailSubject)
        val body = findViewById<EditText>(R.id.emailBody).text.toString().trim()
        if (subject.isEmpty() && body.isEmpty()) {
            Toast.makeText(this, R.string.ai_no_context_mail, Toast.LENGTH_LONG).show()
            return
        }
        val context = buildString {
            if (subject.isNotEmpty()) append("Objet : ").append(subject).append("\n")
            if (body.isNotEmpty()) append("Notes ou message reçu :\n").append(body)
        }

        val progress = AlertDialog.Builder(this)
            .setMessage(R.string.ai_thinking)
            .setCancelable(true)
            .show()

        Thread {
            val result = try {
                AiClient.suggestReplies(context, "mail")
            } catch (e: Exception) {
                runOnUiThread {
                    progress.dismiss()
                    Toast.makeText(this,
                        getString(R.string.ai_error, e.message ?: ""),
                        Toast.LENGTH_LONG).show()
                }
                return@Thread
            }
            runOnUiThread {
                progress.dismiss()
                if (result.isEmpty()) {
                    Toast.makeText(this, R.string.ai_empty, Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                // Aperçu tronqué dans la liste, texte complet une fois choisi
                val labels = result.map { it.take(90).replace("\n", " ") }.toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle(R.string.ai_pick)
                    .setItems(labels) { _, which ->
                        findViewById<EditText>(R.id.emailBody).setText(result[which])
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }.start()
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

    override fun onResume() {
        super.onResume()
        ThemeUtil.refreshIfNeeded(this)
    }

    override fun onDestroy() {
        ThemeUtil.forget(this)
        super.onDestroy()
    }
}
