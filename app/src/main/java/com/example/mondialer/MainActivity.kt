package com.example.mondialer

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var display: EditText
    private var contacts = listOf<Pair<String, String>>() // nom, numéro
    private var contactsLoaded = false
    private var pendingCall = false

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtil.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        display = findViewById(R.id.editNumber)

        intent?.data?.let { uri: Uri ->
            if (uri.scheme == "tel") display.setText(uri.schemeSpecificPart)
        }

        val digits = mapOf(
            R.id.btn1 to "1", R.id.btn2 to "2", R.id.btn3 to "3",
            R.id.btn4 to "4", R.id.btn5 to "5", R.id.btn6 to "6",
            R.id.btn7 to "7", R.id.btn8 to "8", R.id.btn9 to "9",
            R.id.btnStar to "*", R.id.btn0 to "0", R.id.btnHash to "#"
        )
        digits.forEach { (id, d) ->
            findViewById<Button>(id).setOnClickListener { v ->
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                display.append(d)
            }
        }

        findViewById<Button>(R.id.btnDelete).setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            val t = display.text.toString()
            if (t.isNotEmpty()) display.setText(t.dropLast(1))
            display.setSelection(display.text.length)
        }
        findViewById<Button>(R.id.btnDelete).setOnLongClickListener {
            display.setText(""); true
        }

        findViewById<Button>(R.id.btnCall).setOnClickListener { placeCall() }

        findViewById<Button>(R.id.btnFilters).setOnClickListener {
            startActivity(Intent(this, FiltersActivity::class.java))
        }
        findViewById<Button>(R.id.btnContacts).setOnClickListener {
            startActivityForResult(Intent(this, ContactsActivity::class.java), 20)
        }
        findViewById<Button>(R.id.btnLog).setOnClickListener {
            startActivityForResult(Intent(this, CallLogActivity::class.java), 30)
        }
        findViewById<Button>(R.id.btnDefault).setOnClickListener { requestDefaultDialer() }

        // T9 : suggestions de contacts pendant la frappe
        display.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = updateSuggestions(s?.toString() ?: "")
        })

        // Autorisation contacts (T9, favoris, nom en appel)
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED) {
            loadContacts(); loadFavorites()
        } else {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 2)
        }
    }

    // ---- Contacts en mémoire pour le T9 ----
    private fun loadContacts() {
        if (contactsLoaded) return
        val out = mutableListOf<Pair<String, String>>()
        try {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    out.add(Pair(c.getString(0) ?: "", c.getString(1) ?: ""))
                }
            }
        } catch (_: Exception) {}
        contacts = out
        contactsLoaded = true
    }

    private fun t9(s: String): String = s.lowercase().map { c ->
        when (c) {
            in 'a'..'c', 'à', 'â', 'ç' -> '2'
            in 'd'..'f', 'é', 'è', 'ê', 'ë' -> '3'
            in 'g'..'i', 'î', 'ï' -> '4'
            in 'j'..'l' -> '5'
            in 'm'..'o', 'ô', 'ö' -> '6'
            in 'p'..'s' -> '7'
            in 't'..'v', 'ù', 'û', 'ü' -> '8'
            in 'w'..'z' -> '9'
            in '0'..'9' -> c
            else -> ' '
        }
    }.joinToString("").replace(" ", "")

    private fun updateSuggestions(typed: String) {
        val row = findViewById<LinearLayout>(R.id.suggestRow)
        val q = typed.filter { it.isDigit() }
        if (q.length < 2 || contacts.isEmpty()) { row.visibility = View.GONE; return }

        val matches = contacts.filter {
            t9(it.first).contains(q) || it.second.filter { ch -> ch.isDigit() }.contains(q)
        }.distinctBy { it.first }.take(3)

        row.removeAllViews()
        if (matches.isEmpty()) { row.visibility = View.GONE; return }
        row.visibility = View.VISIBLE
        for ((name, number) in matches) {
            val tv = TextView(this)
            tv.text = name
            tv.setTextColor(resolveNeon())
            tv.textSize = 13f
            tv.setPadding(28, 16, 28, 16)
            tv.setBackgroundResource(R.drawable.btn_action)
            tv.setOnClickListener {
                display.setText(number.filter { ch -> ch.isDigit() || ch == '+' })
                display.setSelection(display.text.length)
                row.visibility = View.GONE
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(6, 0, 6, 0)
            row.addView(tv, lp)
        }
    }

    // ---- Favoris (contacts étoilés) ----
    private fun loadFavorites() {
        val row = findViewById<LinearLayout>(R.id.favRow)
        row.removeAllViews()
        val favs = mutableListOf<Pair<String, String>>()
        try {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                ContactsContract.CommonDataKinds.Phone.STARRED + "=1",
                null, null
            )?.use { c ->
                while (c.moveToNext() && favs.size < 6) {
                    val name = c.getString(0) ?: continue
                    if (favs.none { it.first == name })
                        favs.add(Pair(name, c.getString(1) ?: ""))
                }
            }
        } catch (_: Exception) {}

        if (favs.isEmpty()) { row.visibility = View.GONE; return }
        row.visibility = View.VISIBLE
        for ((name, number) in favs) {
            val tv = TextView(this)
            tv.text = "★ " + name.split(" ").first()
            tv.setTextColor(resolveNeon())
            tv.textSize = 13f
            tv.setPadding(28, 16, 28, 16)
            tv.setBackgroundResource(R.drawable.btn_action)
            tv.setOnClickListener {
                display.setText(number.filter { ch -> ch.isDigit() || ch == '+' })
                placeCall()
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(6, 0, 6, 0)
            row.addView(tv, lp)
        }
    }

    private fun resolveNeon(): Int {
        val tv = android.util.TypedValue()
        theme.resolveAttribute(R.attr.cNeon, tv, true)
        return tv.data
    }

    // ---- Appel ----
    private fun placeCall() {
        val number = display.text.toString().trim()
        if (number.isEmpty()) return
        if (checkSelfPermission(Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) {
            pendingCall = true
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), 1)
            return
        }
        startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        when (requestCode) {
            1 -> if (grantResults.isNotEmpty()
                && grantResults[0] == PackageManager.PERMISSION_GRANTED && pendingCall) {
                pendingCall = false
                placeCall()
            }
            2 -> if (grantResults.isNotEmpty()
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadContacts(); loadFavorites()
            }
        }
    }

    private fun requestDefaultDialer() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm.isRoleAvailable(RoleManager.ROLE_DIALER) && !rm.isRoleHeld(RoleManager.ROLE_DIALER)) {
            startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER), 10)
        } else if (rm.isRoleHeld(RoleManager.ROLE_DIALER)) {
            Toast.makeText(this, R.string.already_default, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            10 -> if (resultCode == RESULT_OK)
                Toast.makeText(this, R.string.now_default, Toast.LENGTH_SHORT).show()
            20, 30 -> if (resultCode == RESULT_OK) {
                val n = data?.getStringExtra("number") ?: return
                display.setText(n)
                display.setSelection(display.text.length)
                if (data.getBooleanExtra("call", false)) placeCall()
            }
        }
    }
}
