package com.example.mondialer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.app.AlertDialog
import android.provider.ContactsContract
import android.widget.Button
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationsActivity : Activity() {

    private lateinit var list: ListView
    private var allItems = listOf<Map<String, String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtil.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversations)

        list = findViewById(R.id.list)

        findViewById<Button>(R.id.btnNewMsg).setOnClickListener {
            startActivityForResult(
                Intent(this, ContactsActivity::class.java).putExtra("pick", true), 70)
        }
        findViewById<Button>(R.id.btnMail).setOnClickListener {
            startActivity(Intent(this, EmailActivity::class.java))
        }

        // Recherche par nom, numéro ou contenu du message
        findViewById<EditText>(R.id.editSearch).addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) = filter(s?.toString() ?: "")
            })

        val perms = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS
        )
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = perms.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) load()
        else requestPermissions(missing.toTypedArray(), 1)
    }

    override fun onResume() {
        super.onResume()
        ThemeUtil.refreshIfNeeded(this)
        if (checkSelfPermission(Manifest.permission.READ_SMS)
            == PackageManager.PERMISSION_GRANTED) load()
    }

    private fun lookupName(number: String): String? {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) return null
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            contentResolver.query(uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) { null }
    }

    @Volatile private var loading = false

    private fun load() {
        if (loading) return
        loading = true
        Thread {
            val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE)
            val seen = HashSet<String>()
            val items = mutableListOf<Map<String, String>>()
            try {
                contentResolver.query(
                    Uri.parse("content://sms"),
                    arrayOf("thread_id", "address", "body", "date"),
                    null, null, "date DESC LIMIT 200"
                )?.use { c ->
                    while (c.moveToNext() && items.size < 100) {
                        val tid = c.getString(0) ?: continue
                        if (tid in seen) continue
                        seen.add(tid)
                        val address = c.getString(1) ?: ""
                        val body = c.getString(2) ?: ""
                        val date = fmt.format(Date(c.getLong(3)))
                        val name = lookupName(address)
                        val draft = BlockRulesStore.draft(address)
                        val preview = if (draft.isNotBlank())
                            "✎ " + draft.take(50) else body.take(60)
                        items.add(mapOf(
                            "title" to (name ?: address),
                            "sub" to "$preview  •  $date",
                            "address" to address,
                            "tid" to tid
                        ))
                    }
                }
            } catch (_: Exception) {}

            // Fils MMS absents de la liste SMS
            try {
                contentResolver.query(
                    Uri.parse("content://mms"),
                    arrayOf("_id", "thread_id", "date"),
                    null, null, "date DESC LIMIT 60"
                )?.use { c ->
                    while (c.moveToNext() && items.size < 120) {
                        val tid = c.getString(1) ?: continue
                        if (tid in seen) continue
                        seen.add(tid)
                        val mid = c.getString(0)
                        val date = fmt.format(Date(c.getLong(2) * 1000))
                        var addr = ""
                        contentResolver.query(
                            Uri.parse("content://mms/" + mid + "/addr"),
                            arrayOf("address"), "type=137", null, null
                        )?.use { a -> if (a.moveToFirst()) addr = a.getString(0) ?: "" }
                        if (addr.isBlank()) continue
                        val name = lookupName(addr)
                        items.add(mapOf(
                            "title" to (name ?: addr),
                            "sub" to "📷 MMS  •  " + date,
                            "address" to addr,
                            "tid" to tid
                        ))
                    }
                }
            } catch (_: Exception) {}

            runOnUiThread {
                allItems = items
                show(items)
                EmptyState.show(this, items.isEmpty(), "💬",
                    getString(R.string.empty_messages))
                loading = false
                val q = findViewById<EditText>(R.id.editSearch).text.toString()
                if (q.isNotBlank()) filter(q)
            }
        }.start()
    }

    private fun show(items: List<Map<String, String>>) {
        list.adapter = CardAdapter(this, items)
        list.setOnItemClickListener { _, _, pos, _ ->
            @Suppress("UNCHECKED_CAST")
            val item = list.adapter.getItem(pos) as Map<String, String>
            startActivity(Intent(this, ThreadActivity::class.java)
                .putExtra("address", item["address"])
                .putExtra("thread_id", item["tid"])
                .putExtra("search", item["match"]))
        }
        list.setOnItemLongClickListener { _, _, pos, _ ->
            @Suppress("UNCHECKED_CAST")
            val item = list.adapter.getItem(pos) as Map<String, String>
            showThreadActions(item)
            true
        }
    }

    /** Actions sur une conversation entière. */
    private fun showThreadActions(item: Map<String, String>) {
        val address = item["address"] ?: return
        val tid = item["tid"]
        AlertDialog.Builder(this)
            .setTitle(item["title"])
            .setItems(arrayOf(
                getString(R.string.thread_delete),
                getString(R.string.thread_mark_read),
                getString(R.string.action_block_number, address))) { _, which ->
                when (which) {
                    0 -> confirmDeleteThread(address, tid)
                    1 -> markRead(address, tid)
                    2 -> {
                        BlockRulesStore.addNumber(address)
                        Toast.makeText(this, R.string.number_blocked,
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun confirmDeleteThread(address: String, tid: String?) {
        AlertDialog.Builder(this)
            .setTitle(R.string.thread_delete)
            .setMessage(getString(R.string.thread_delete_confirm, address))
            .setPositiveButton(R.string.contact_delete_yes) { _, _ ->
                deleteThread(address, tid)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Supprime la conversation. La suppression n'est possible que si
     * l'application est celle par défaut pour les SMS : Android l'exige.
     */
    private fun deleteThread(address: String, tid: String?) {
        Thread {
            var removed = 0
            try {
                if (tid != null) {
                    removed += contentResolver.delete(
                        Uri.parse("content://sms"), "thread_id=?", arrayOf(tid))
                    removed += contentResolver.delete(
                        Uri.parse("content://mms"), "thread_id=?", arrayOf(tid))
                } else {
                    removed += contentResolver.delete(
                        Uri.parse("content://sms"), "address=?", arrayOf(address))
                }
                BlockRulesStore.setDraft(address, "")
            } catch (_: Exception) {}
            runOnUiThread {
                if (removed > 0) {
                    Toast.makeText(this, R.string.thread_deleted, Toast.LENGTH_SHORT).show()
                    load()
                } else {
                    Toast.makeText(this, R.string.thread_delete_fail,
                        Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun markRead(address: String, tid: String?) {
        Thread {
            try {
                val v = android.content.ContentValues().apply {
                    put("read", 1); put("seen", 1)
                }
                if (tid != null)
                    contentResolver.update(Uri.parse("content://sms"), v,
                        "thread_id=?", arrayOf(tid))
                else
                    contentResolver.update(Uri.parse("content://sms"), v,
                        "address=?", arrayOf(address))
            } catch (_: Exception) {}
            runOnUiThread { load() }
        }.start()
    }

    /**
     * Filtre sur le nom, le numéro et l'aperçu ; puis, si peu de résultats,
     * cherche aussi dans le corps de tous les messages.
     */
    private fun filter(query: String) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) { show(allItems); return }

        val direct = allItems.filter {
            (it["title"] ?: "").lowercase().contains(q) ||
            (it["address"] ?: "").contains(q) ||
            (it["sub"] ?: "").lowercase().contains(q)
        }
        show(direct)

        // Recherche approfondie dans le contenu des messages
        if (q.length >= 3) {
            Thread {
                val found = deepSearch(q, direct.mapNotNull { it["tid"] }.toSet())
                if (found.isNotEmpty()) {
                    runOnUiThread {
                        val current = findViewById<EditText>(R.id.editSearch)
                            .text.toString().trim().lowercase()
                        if (current == q) show(direct + found)
                    }
                }
            }.start()
        }
    }

    /** Parcourt le corps des SMS pour retrouver une conversation par son contenu. */
    private fun deepSearch(q: String, exclude: Set<String>): List<Map<String, String>> {
        val out = mutableListOf<Map<String, String>>()
        val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE)
        val seen = HashSet<String>()
        try {
            contentResolver.query(
                Uri.parse("content://sms"),
                arrayOf("thread_id", "address", "body", "date"),
                "body LIKE ?", arrayOf("%" + q + "%"), "date DESC LIMIT 60"
            )?.use { c ->
                while (c.moveToNext() && out.size < 30) {
                    val tid = c.getString(0) ?: continue
                    if (tid in exclude || tid in seen) continue
                    seen.add(tid)
                    val address = c.getString(1) ?: ""
                    val body = c.getString(2) ?: ""
                    val date = fmt.format(Date(c.getLong(3)))
                    val name = lookupName(address)
                    out.add(mapOf(
                        "title" to "🔎 " + (name ?: address),
                        "sub" to body.take(70) + "  •  " + date,
                        "address" to address,
                        "tid" to tid,
                        "match" to q
                    ))
                }
            }
        } catch (_: Exception) {}
        return out
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 70 && resultCode == RESULT_OK) {
            val contactId = data?.getStringExtra("contact_id") ?: return
            val name = data.getStringExtra("name") ?: ""
            showChannelChooser(contactId, name)
        }
        if (requestCode == 50) { /* rôle SMS */ }
    }

    /** Propose tous les numéros et emails du contact. */
    private fun showChannelChooser(contactId: String, name: String) {
        Thread {
            val labels = mutableListOf<String>()
            val actions = mutableListOf<Pair<String, String>>() // type, valeur
            try {
                contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?",
                    arrayOf(contactId), null
                )?.use { c ->
                    val seen = HashSet<String>()
                    while (c.moveToNext()) {
                        val num = c.getString(0) ?: continue
                        if (num in seen) continue
                        seen.add(num)
                        labels.add("📱 SMS — " + num)
                        actions.add(Pair("sms", num))
                    }
                }
                contentResolver.query(
                    ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Email.DATA1),
                    ContactsContract.CommonDataKinds.Email.CONTACT_ID + "=?",
                    arrayOf(contactId), null
                )?.use { c ->
                    val seen = HashSet<String>()
                    while (c.moveToNext()) {
                        val mail = c.getString(0) ?: continue
                        if (mail in seen) continue
                        seen.add(mail)
                        labels.add("✉ Email — " + mail)
                        actions.add(Pair("email", mail))
                    }
                }
            } catch (_: Exception) {}

            runOnUiThread {
                if (actions.isEmpty()) {
                    Toast.makeText(this, R.string.no_contact_channel, Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                if (actions.size == 1) {
                    openChannel(actions[0])
                    return@runOnUiThread
                }
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.choose_channel, name))
                    .setItems(labels.toTypedArray()) { _, which ->
                        openChannel(actions[which])
                    }
                    .show()
            }
        }.start()
    }

    private fun openChannel(action: Pair<String, String>) {
        when (action.first) {
            "sms" -> startActivity(Intent(this, ThreadActivity::class.java)
                .putExtra("address", action.second))
            "email" -> startActivity(Intent(this, EmailActivity::class.java)
                .putExtra("to", action.second))
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        if (requestCode == 1 &&
            checkSelfPermission(Manifest.permission.READ_SMS)
            == PackageManager.PERMISSION_GRANTED) load()
    }

    override fun onDestroy() {
        ThemeUtil.forget(this)
        super.onDestroy()
    }
}
