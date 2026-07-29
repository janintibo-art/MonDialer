package com.example.mondialer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ListView
import android.widget.SimpleAdapter
import android.widget.TextView
import android.widget.Toast

class ContactsActivity : Activity() {

    private var all = listOf<Map<String, String>>()
    private lateinit var list: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtil.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)

        findViewById<TextView>(R.id.txtTitle).text = getString(R.string.contacts)
        val search = findViewById<EditText>(R.id.editSearch)
        search.hint = getString(R.string.search_contact)
        list = findViewById(R.id.list)

        if (checkSelfPermission(Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED) load()
        else requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 1)

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = filter(s?.toString() ?: "")
        })

        // Appui simple : appeler directement
        list.setOnItemClickListener { _, _, pos, _ ->
            @Suppress("UNCHECKED_CAST")
            val item = list.adapter.getItem(pos) as Map<String, String>
            setResult(RESULT_OK, Intent()
                .putExtra("number", item["sub"] ?: "")
                .putExtra("call", true))
            finish()
        }
        // Appui long : mettre le numéro au clavier sans appeler
        list.setOnItemLongClickListener { _, _, pos, _ ->
            @Suppress("UNCHECKED_CAST")
            val item = list.adapter.getItem(pos) as Map<String, String>
            setResult(RESULT_OK, Intent().putExtra("number", item["sub"] ?: ""))
            finish()
            true
        }
    }

    private fun load() {
        val out = mutableListOf<Map<String, String>>()
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.STARRED
            ),
            null, null,
            ContactsContract.CommonDataKinds.Phone.STARRED + " DESC, " +
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val star = if (c.getInt(2) == 1) "★ " else ""
                out.add(mapOf(
                    "title" to star + (c.getString(0) ?: ""),
                    "sub" to (c.getString(1) ?: "")
                ))
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
            (it["sub"] ?: "").contains(query)
        })
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        if (requestCode == 1 && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) load()
        else Toast.makeText(this, R.string.perm_needed, Toast.LENGTH_LONG).show()
    }
}
