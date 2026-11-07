package com.example.mondialer

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.EditText
import android.content.Intent
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BlockedLogActivity : Activity() {

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

        findViewById<EditText>(R.id.editSearch).visibility = android.view.View.GONE
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
                "title" to (BlockRulesStore.callerAlias(it.number)?.let { alias ->
                    "$alias (étiquette personnelle) — ${it.number}"
                } ?: it.number),
                "sub" to "⛔ ${it.reason}  •  ${fmt.format(Date(it.time))}",
                "number" to it.number
            )
        }
        val list = findViewById<ListView>(R.id.list)
        EmptyState.show(this, items.isEmpty(), "⛔", getString(R.string.no_blocked_calls))
        list.adapter = CardAdapter(this, items)
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
