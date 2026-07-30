package com.example.mondialer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.text.Normalizer

class ContactsActivity : Activity() {

    data class Contact(val name: String, val number: String, val contactId: String)

    private var all = listOf<Contact>()
    private var shown = listOf<Contact>()
    private val letterPos = HashMap<Char, Int>()
    private lateinit var list: ListView
    private lateinit var adapter: ContactAdapter
    private val indexChars = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private var pickMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtil.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contacts)

        pickMode = intent.getBooleanExtra("pick", false)
        findViewById<TextView>(R.id.txtTitle).text = getString(R.string.contacts)
        val search = findViewById<EditText>(R.id.editSearch)
        search.hint = getString(R.string.search_contact)
        list = findViewById(R.id.list)
        adapter = ContactAdapter()
        list.adapter = adapter

        findViewById<Button>(R.id.btnNewContact).setOnClickListener {
            startActivity(Intent(this, EditContactActivity::class.java))
        }

        buildIndexBar()

        if (checkSelfPermission(Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED) load()
        else requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 1)

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = filter(s?.toString() ?: "")
        })

        list.setOnItemClickListener { _, _, pos, _ ->
            val c = shown[pos]
            if (pickMode) {
                setResult(RESULT_OK, Intent()
                    .putExtra("contact_id", c.contactId)
                    .putExtra("name", c.name)
                    .putExtra("number", c.number))
            } else {
                setResult(RESULT_OK, Intent()
                    .putExtra("number", c.number)
                    .putExtra("call", true))
            }
            finish()
        }
        list.setOnItemLongClickListener { _, _, pos, _ ->
            val c = shown[pos]
            setResult(RESULT_OK, Intent().putExtra("number", c.number))
            finish()
            true
        }
    }

    override fun onResume() {
        super.onResume()
        ThemeUtil.refreshIfNeeded(this)
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED) load()
    }

    private fun initialOf(name: String): Char {
        val clean = Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .uppercase()
        for (ch in clean) if (ch in 'A'..'Z') return ch
        return '#'
    }

    private fun load() {
        val out = mutableListOf<Contact>()
        try {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID
                ),
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    out.add(Contact(
                        c.getString(0) ?: "",
                        c.getString(1) ?: "",
                        c.getString(2) ?: ""
                    ))
                }
            }
        } catch (_: Exception) {}
        all = out
        show(out)
    }

    private fun show(items: List<Contact>) {
        shown = items
        letterPos.clear()
        for ((i, c) in items.withIndex()) {
            val l = initialOf(c.name)
            if (l !in letterPos) letterPos[l] = i
        }
        adapter.notifyDataSetChanged()
    }

    private fun filter(q: String) {
        val query = q.trim().lowercase()
        if (query.isEmpty()) { show(all); return }
        show(all.filter {
            it.name.lowercase().contains(query) || it.number.contains(query)
        })
    }

    // ---- Index alphabétique tactile ----
    private fun buildIndexBar() {
        val bar = findViewById<LinearLayout>(R.id.indexBar)
        val overlay = findViewById<TextView>(R.id.letterOverlay)
        bar.removeAllViews()
        for (ch in indexChars) {
            val tv = TextView(this)
            tv.text = ch.toString()
            tv.textSize = 11f
            tv.gravity = Gravity.CENTER
            tv.setTextColor(resolveNeon())
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            bar.addView(tv, lp)
        }
        bar.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val idx = ((event.y / v.height) * indexChars.length)
                        .toInt().coerceIn(0, indexChars.length - 1)
                    val ch = indexChars[idx]
                    overlay.text = ch.toString()
                    overlay.visibility = View.VISIBLE
                    jumpTo(ch)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    overlay.visibility = View.GONE
                    v.performClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun jumpTo(ch: Char) {
        letterPos[ch]?.let { list.setSelection(it); return }
        // Lettre absente : aller à la suivante disponible
        val start = indexChars.indexOf(ch)
        for (i in start until indexChars.length) {
            letterPos[indexChars[i]]?.let { list.setSelection(it); return }
        }
    }

    private fun resolveNeon(): Int {
        val tv = android.util.TypedValue()
        theme.resolveAttribute(R.attr.cNeon, tv, true)
        return tv.data
    }

    inner class ContactAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val v = convertView ?: layoutInflater.inflate(R.layout.item_contact, parent, false)
            val c = shown[position]
            v.findViewById<TextView>(R.id.text1).text = c.name
            v.findViewById<TextView>(R.id.text2).text = c.number
            v.findViewById<Button>(R.id.btnEdit).setOnClickListener {
                startActivity(Intent(this@ContactsActivity, EditContactActivity::class.java)
                    .putExtra("contact_id", c.contactId))
            }
            return v
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        if (requestCode == 1 && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) load()
        else Toast.makeText(this, R.string.perm_needed, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        ThemeUtil.forget(this)
        super.onDestroy()
    }
}
