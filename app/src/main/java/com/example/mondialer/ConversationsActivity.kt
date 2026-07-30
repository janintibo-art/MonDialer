package com.example.mondialer

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Button
import android.widget.ListView
import android.widget.SimpleAdapter
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationsActivity : Activity() {

    private lateinit var list: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtil.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversations)

        list = findViewById(R.id.list)

        findViewById<Button>(R.id.btnNewMsg).setOnClickListener {
            startActivity(Intent(this, ThreadActivity::class.java))
        }
        findViewById<Button>(R.id.btnSmsDefault).setOnClickListener { requestSmsRole() }

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
                        items.add(mapOf(
                            "title" to (name ?: address),
                            "sub" to "${body.take(60)}  •  $date",
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
                list.adapter = SimpleAdapter(
                    this, items, R.layout.item_two_lines,
                    arrayOf("title", "sub"), intArrayOf(R.id.text1, R.id.text2)
                )
                list.setOnItemClickListener { _, _, pos, _ ->
                    @Suppress("UNCHECKED_CAST")
                    val item = list.adapter.getItem(pos) as Map<String, String>
                    startActivity(Intent(this, ThreadActivity::class.java)
                        .putExtra("address", item["address"])
                        .putExtra("thread_id", item["tid"]))
                }
                loading = false
            }
        }.start()
    }

    private fun requestSmsRole() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm.isRoleAvailable(RoleManager.ROLE_SMS) && !rm.isRoleHeld(RoleManager.ROLE_SMS)) {
            startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_SMS), 50)
        } else if (rm.isRoleHeld(RoleManager.ROLE_SMS)) {
            Toast.makeText(this, R.string.already_sms_default, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        if (requestCode == 1 &&
            checkSelfPermission(Manifest.permission.READ_SMS)
            == PackageManager.PERMISSION_GRANTED) load()
    }
}
