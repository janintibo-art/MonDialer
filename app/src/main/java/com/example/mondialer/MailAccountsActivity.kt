package com.example.mondialer

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.SimpleAdapter
import android.widget.TextView
import android.widget.Toast

/** Gestion des comptes d'envoi : ajout, modification, compte par défaut. */
class MailAccountsActivity : Activity() {

    private lateinit var listView: ListView
    private var accounts = mutableListOf<BlockRulesStore.MailAccount>()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtil.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lists)

        findViewById<TextView>(R.id.txtTitle).text = getString(R.string.mail_accounts_title)
        listView = findViewById(R.id.list)

        findViewById<Button>(R.id.btnNewList).apply {
            text = getString(R.string.mail_account_new)
            setOnClickListener { editAccount(null) }
        }

        listView.setOnItemClickListener { _, _, pos, _ -> editAccount(accounts[pos]) }
        listView.setOnItemLongClickListener { _, _, pos, _ ->
            showActions(accounts[pos]); true
        }
    }

    override fun onResume() {
        super.onResume()
        ThemeUtil.refreshIfNeeded(this)
        reload()
    }

    private fun reload() {
        accounts = BlockRulesStore.mailAccounts()
        val def = BlockRulesStore.defaultMailAccount
        val items = accounts.map {
            mapOf(
                "title" to (if (it.id == def) "★ " else "") + it.label,
                "sub" to it.user + "  •  " + it.host
            )
        }
        listView.adapter = SimpleAdapter(
            this, items, R.layout.item_two_lines,
            arrayOf("title", "sub"), intArrayOf(R.id.text1, R.id.text2))
        findViewById<TextView>(R.id.txtHint).text =
            if (items.isEmpty()) getString(R.string.mail_accounts_empty)
            else getString(R.string.mail_accounts_hint)
    }

    /** Formulaire d'ajout ou de modification, avec détection du fournisseur. */
    private fun editAccount(existing: BlockRulesStore.MailAccount?) {
        val email = EditText(this).apply {
            hint = getString(R.string.mail_field_address)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setText(existing?.user ?: "")
        }
        val pass = EditText(this).apply {
            hint = getString(R.string.mail_field_password)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(existing?.pass ?: "")
        }
        val label = EditText(this).apply {
            hint = getString(R.string.mail_field_label)
            setText(existing?.label ?: "")
        }
        val host = EditText(this).apply {
            hint = getString(R.string.mail_field_host)
            setText(existing?.host ?: "")
        }
        val port = EditText(this).apply {
            hint = getString(R.string.mail_field_port)
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(existing?.port ?: "465")
        }
        val note = TextView(this).apply {
            textSize = 12f
            setTextColor(ThemeRes.color(this@MailAccountsActivity, R.attr.cTextDim))
        }

        // Le serveur se remplit tout seul dès que le domaine est reconnu
        email.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val preset = MailProviders.guess(s?.toString() ?: "") ?: return
                if (host.text.isBlank() || existing == null) {
                    host.setText(preset.host)
                    port.setText(preset.port)
                }
                if (label.text.isBlank()) label.setText(preset.label)
                note.text = preset.note
            }
        })
        MailProviders.guess(existing?.user ?: "")?.let { note.text = it.note }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (18 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, 0)
            addView(email); addView(pass); addView(label)
            addView(host); addView(port); addView(note)
        }
        val scroll = android.widget.ScrollView(this).apply { addView(box) }

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) R.string.mail_account_new
                      else R.string.mail_account_edit)
            .setView(scroll)
            .setPositiveButton(R.string.save_contact) { _, _ ->
                val u = email.text.toString().trim()
                if (u.isBlank() || host.text.isBlank()) {
                    Toast.makeText(this, R.string.mail_account_incomplete,
                        Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                if (existing == null) {
                    val a = BlockRulesStore.MailAccount(
                        BlockRulesStore.newAccountId(),
                        label.text.toString().trim().ifEmpty { u.substringAfter("@") },
                        host.text.toString().trim(), port.text.toString().trim(),
                        u, pass.text.toString())
                    accounts.add(a)
                    if (BlockRulesStore.defaultMailAccount.isBlank())
                        BlockRulesStore.defaultMailAccount = a.id
                } else {
                    existing.user = u
                    existing.pass = pass.text.toString()
                    existing.label = label.text.toString().trim()
                        .ifEmpty { u.substringAfter("@") }
                    existing.host = host.text.toString().trim()
                    existing.port = port.text.toString().trim()
                }
                BlockRulesStore.saveMailAccounts(accounts)
                reload()
            }
            .setNeutralButton(R.string.mail_account_test) { _, _ ->
                testAccount(email.text.toString().trim(), pass.text.toString(),
                    host.text.toString().trim(), port.text.toString().trim())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun testAccount(user: String, pass: String, host: String, port: String) {
        Toast.makeText(this, R.string.mail_account_testing, Toast.LENGTH_SHORT).show()
        Thread {
            val msg = try {
                MailSender.verify(host, port, user, pass)
                getString(R.string.mail_account_ok)
            } catch (e: Exception) {
                getString(R.string.mail_account_ko, e.message ?: "")
            }
            runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
        }.start()
    }

    private fun showActions(a: BlockRulesStore.MailAccount) {
        AlertDialog.Builder(this)
            .setTitle(a.label)
            .setItems(arrayOf(
                getString(R.string.mail_account_default),
                getString(R.string.mail_account_edit),
                getString(R.string.mail_account_delete))) { _, which ->
                when (which) {
                    0 -> {
                        BlockRulesStore.defaultMailAccount = a.id
                        reload()
                        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
                    }
                    1 -> editAccount(a)
                    2 -> {
                        accounts.remove(a)
                        if (BlockRulesStore.defaultMailAccount == a.id)
                            BlockRulesStore.defaultMailAccount =
                                accounts.firstOrNull()?.id ?: ""
                        BlockRulesStore.saveMailAccounts(accounts)
                        reload()
                    }
                }
            }
            .show()
    }

    override fun onDestroy() {
        ThemeUtil.forget(this)
        super.onDestroy()
    }
}
