package com.example.mondialer

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.EditText
import android.text.Editable
import android.text.TextWatcher
import android.content.Intent
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BlockedLogActivity : Activity() {

    private var displayed = listOf<Map<String, String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtil.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)

        val title = findViewById<TextView>(R.id.txtTitle)
        title.text = getString(R.string.blocked_log_title)
        // Appui long sur le titre : vider le journal
        title.setOnLongClickListener {
            AlertDialog.Builder(this)
                .setMessage(R.string.clear_log_confirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    BlockRulesStore.clearBlockedLog()
                    load()
                    Toast.makeText(this, R.string.removed, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            true
        }

        findViewById<EditText>(R.id.editSearch).apply {
            hint = "Chercher un numero, un nom ou un motif..."
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) = render(s?.toString().orEmpty())
            })
        }
        findViewById<ListView>(R.id.list).setOnItemClickListener { parent, _, position, _ ->
            @Suppress("UNCHECKED_CAST")
            val item = parent.getItemAtPosition(position) as Map<String, String>
            val number = item["number"] ?: return@setOnItemClickListener
            val field = EditText(this).apply {
                hint = "Entreprise ou nom supposé (local, non vérifié)"
                setText(BlockRulesStore.callerAlias(number).orEmpty())
                setSingleLine(true)
            }
            AlertDialog.Builder(this)
                .setTitle("Étiquette personnelle — $number")
                .setMessage("Cette étiquette est enregistrée uniquement sur votre téléphone. Elle ne prouve pas l’identité de l’appelant.")
                .setView(field)
                .setPositiveButton("Enregistrer") { _, _ ->
                    BlockRulesStore.saveCallerAlias(number, field.text.toString())
                    load()
                }
                .setNeutralButton("Effacer") { _, _ ->
                    BlockRulesStore.saveCallerAlias(number, "")
                    load()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        load()
    }

    private fun load() {
        val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE)
        val items = BlockRulesStore.blockedLog().map {
            mapOf(
                "title" to (BlockRulesStore.callerAlias(it.number)
                    ?: BlockRulesStore.networkCallerName(it.number)
                    ?: it.number.ifBlank { "Numéro masqué" }),
                "sub" to buildString {
                    append("⛔ ").append(it.reason)
                    append("  •  ").append(fmt.format(Date(it.time)))
                    append("\n")
                    append(it.number.ifBlank { "Numéro masqué" })
                    if (BlockRulesStore.callerAlias(it.number) != null)
                        append("  •  Étiquette personnelle (non vérifiée)")
                    else if (BlockRulesStore.networkCallerName(it.number) != null)
                        append("  •  Nom réseau (non vérifié)")
                },
                "number" to it.number,
                "status" to "blocked"
            )
        }
        displayed = items
        render(findViewById<EditText>(R.id.editSearch).text.toString())
    }

    private fun render(query: String) {
        val q = query.trim().lowercase(Locale.ROOT)
        val items = if (q.isEmpty()) displayed else displayed.filter {
            listOf("title", "sub", "number").any { key ->
                it[key]?.lowercase(Locale.ROOT)?.contains(q) == true
            }
        }
        val list = findViewById<ListView>(R.id.list)
        EmptyState.show(this, items.isEmpty(), "⛔", getString(R.string.no_blocked_calls))
        list.adapter = CardAdapter(this, items)
    }

    override fun onResume() {
        super.onResume()
        ThemeUtil.refreshIfNeeded(this)
        load()
    }

    override fun onDestroy() {
        ThemeUtil.forget(this)
        super.onDestroy()
    }
}
