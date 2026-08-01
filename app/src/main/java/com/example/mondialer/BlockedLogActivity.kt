package com.example.mondialer

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.EditText
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
        load()
    }

    private fun load() {
        val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE)
        val items = BlockRulesStore.blockedLog().map {
            mapOf(
                "title" to it.number,
                "sub" to "⛔ ${it.reason}  •  ${fmt.format(Date(it.time))}"
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
