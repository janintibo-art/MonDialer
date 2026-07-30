package com.example.mondialer

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CallLog
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ListView
import android.widget.SimpleAdapter
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallLogActivity : Activity() {

    private var all = listOf<Map<String, String>>()
    private lateinit var list: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtil.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)

        findViewById<TextView>(R.id.txtTitle).text = getString(R.string.call_log)
        val search = findViewById<EditText>(R.id.editSearch)
        search.hint = getString(R.string.search_log)
        list = findViewById(R.id.list)

        if (checkSelfPermission(Manifest.permission.READ_CALL_LOG)
            == PackageManager.PERMISSION_GRANTED) load()
        else requestPermissions(arrayOf(Manifest.permission.READ_CALL_LOG), 1)

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = filter(s?.toString() ?: "")
        })

        // Appui simple : rappeler directement
        list.setOnItemClickListener { _, _, pos, _ ->
            @Suppress("UNCHECKED_CAST")
            val item = list.adapter.getItem(pos) as Map<String, String>
            val num = item["num"] ?: ""
            if (num.isBlank()) return@setOnItemClickListener
            setResult(RESULT_OK, Intent()
                .putExtra("number", num)
                .putExtra("call", true))
            finish()
        }

        // Appui long : menu d'actions
        list.setOnItemLongClickListener { _, _, pos, _ ->
            @Suppress("UNCHECKED_CAST")
            val item = list.adapter.getItem(pos) as Map<String, String>
            val num = item["num"] ?: ""
            if (num.isBlank()) return@setOnItemLongClickListener true

            val normalized = BlockRulesStore.normalize(num)
            val prefix = normalized.take(4)
            val options = arrayOf(
                getString(R.string.action_dial_pad),
                getString(R.string.action_block_number, num),
                getString(R.string.action_block_prefix, prefix)
            )
            AlertDialog.Builder(this)
                .setTitle(num)
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> {
                            setResult(RESULT_OK, Intent().putExtra("number", num))
                            finish()
                        }
                        1 -> {
                            BlockRulesStore.addNumber(num)
                            Toast.makeText(this, R.string.number_blocked, Toast.LENGTH_SHORT).show()
                        }
                        2 -> {
                            BlockRulesStore.addPrefix(prefix)
                            Toast.makeText(this, R.string.prefix_blocked, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .show()
            true
        }
    }

    private fun typeSymbol(t: Int): String = when (t) {
        CallLog.Calls.INCOMING_TYPE -> "↙ Entrant"
        CallLog.Calls.OUTGOING_TYPE -> "↗ Sortant"
        CallLog.Calls.MISSED_TYPE -> "✕ Manqué"
        CallLog.Calls.REJECTED_TYPE -> "⊘ Rejeté"
        CallLog.Calls.BLOCKED_TYPE -> "⛔ Bloqué"
        else -> "•"
    }

    private fun load() {
        val out = mutableListOf<Map<String, String>>()
        val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE)
        contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE
            ),
            null, null,
            CallLog.Calls.DATE + " DESC"
        )?.use { c ->
            var count = 0
            while (c.moveToNext() && count < 200) {
                val number = c.getString(0) ?: ""
                val name = c.getString(1)
                val type = c.getInt(2)
                val date = fmt.format(Date(c.getLong(3)))
                out.add(mapOf(
                    "title" to (if (name.isNullOrBlank()) number.ifBlank { getString(R.string.hidden_number) } else name),
                    "sub" to "${typeSymbol(type)}  •  $number  •  $date",
                    "num" to number
                ))
                count++
            }
        }
        all = out
        show(out)
    }

    private fun show(items: List<Map<String, String>>) {
        list.adapter = SimpleAdapter(
            this, items, R.layout.item_two_lines,
            arrayOf("title", "sub"), intArrayOf(R.id.text1, R.id.text2)
        )
    }

    private fun filter(q: String) {
        val query = q.trim().lowercase()
        if (query.isEmpty()) { show(all); return }
        show(all.filter {
            (it["title"] ?: "").lowercase().contains(query) ||
            (it["num"] ?: "").contains(query)
        })
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        if (requestCode == 1 && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) load()
        else Toast.makeText(this, R.string.perm_needed, Toast.LENGTH_LONG).show()
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
