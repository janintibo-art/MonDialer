package com.example.mondialer

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.EditText
import android.text.Editable
import android.text.TextWatcher
import android.content.Intent
import android.net.Uri
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

        findViewById<TextView>(R.id.txtSpamStats).visibility = android.view.View.VISIBLE
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
        // Recherche en ligne exclusivement à la demande explicite de l'utilisateur.
        // Appui long sur une ligne : l'application ne contacte aucun annuaire en arrière-plan.
        findViewById<ListView>(R.id.list).setOnItemLongClickListener { parent, _, position, _ ->
            @Suppress("UNCHECKED_CAST")
            val item = parent.getItemAtPosition(position) as Map<String, String>
            val number = item["number"].orEmpty()
            val normalized = BlockRulesStore.normalize(number)
            if (normalized.length < 8) {
                Toast.makeText(this, "Numéro indisponible pour la recherche", Toast.LENGTH_SHORT).show()
            } else {
                val choices = arrayOf(
                    "PagesJaunes / PagesBlanches (recherche manuelle)",
                    "Moteur de recherche Web",
                    "Données officielles ARCEP (opérateur attributaire)"
                )
                AlertDialog.Builder(this)
                    .setTitle("Identifier un numéro")
                    .setMessage("Une recherche externe transmettra ce numéro au site choisi. L'opérateur ARCEP n'est pas nécessairement l'entreprise appelante. Aucun résultat n'est une preuve d'identité.")
                    .setItems(choices) { _, which ->
                        val uri = when (which) {
                            0 -> Uri.parse("https://www.pagesjaunes.fr/annuaireinverse")
                            1 -> Uri.parse("https://www.google.com/search?q=" +
                                Uri.encode("\"$normalized\" entreprise téléphone"))
                            else -> Uri.parse("https://www.data.gouv.fr/datasets/ressources-en-numerotation-telephonique")
                        }
                        try { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        catch (_: Exception) {
                            Toast.makeText(this, "Aucun navigateur disponible", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            true
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
        updateStats(BlockRulesStore.blockedLog())
        render(findViewById<EditText>(R.id.editSearch).text.toString())
    }

    // Compteurs calculés uniquement à partir de l'historique local, limité
    // aux 200 événements conservés par BlockRulesStore.
    private fun updateStats(entries: List<BlockRulesStore.BlockedEntry>) {
        val now = System.currentTimeMillis()
        val week = 7L * 24 * 60 * 60 * 1000
        val month = 30L * 24 * 60 * 60 * 1000
        val last7 = entries.count { it.time in (now - week)..now }
        val last30 = entries.count { it.time in (now - month)..now }
        val unique = entries.map { BlockRulesStore.normalize(it.number) }
            .filter { it.isNotBlank() }.distinct().size
        val top = entries.groupingBy { it.reason }.eachCount()
            .maxByOrNull { it.value }?.let { "\nMotif principal : ${it.key} (${it.value})" }.orEmpty()
        findViewById<TextView>(R.id.txtSpamStats)?.text =
            "⛨ Antispam  •  7 j : $last7  •  30 j : $last30  •  $unique numéros distincts$top" +
            "\nHistorique local : ${entries.size}/200 dernières entrées"
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
