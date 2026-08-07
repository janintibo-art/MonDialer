package com.example.mondialer

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
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
import android.widget.ImageView
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
                finish()
            } else {
                showContactActions(c)
            }
        }
        list.setOnItemLongClickListener { _, _, pos, _ ->
            val c = shown[pos]
            setResult(RESULT_OK, Intent().putExtra("number", c.number))
            finish()
            true
        }
    }

    /**
     * Propose les moyens de contact réellement renseignés : chaque numéro
     * peut être appelé ou recevoir un SMS, chaque adresse un email.
     */
    private fun showContactActions(c: Contact) {
        Thread {
            val labels = mutableListOf<String>()
            val actions = mutableListOf<Pair<String, String>>()   // type, valeur

            val numbers = LinkedHashSet<String>()
            val emails = LinkedHashSet<String>()
            try {
                contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?",
                    arrayOf(c.contactId), null
                )?.use { cur ->
                    while (cur.moveToNext()) cur.getString(0)?.let { numbers.add(it) }
                }
                contentResolver.query(
                    ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Email.DATA1),
                    ContactsContract.CommonDataKinds.Email.CONTACT_ID + "=?",
                    arrayOf(c.contactId), null
                )?.use { cur ->
                    while (cur.moveToNext()) cur.getString(0)?.let { emails.add(it) }
                }
            } catch (_: Exception) {}

            if (numbers.isEmpty() && c.number.isNotBlank()) numbers.add(c.number)

            // Chaque numéro est proposé avec ses deux usages, à la suite :
            // plus lisible qu'une liste d'appels puis une liste de SMS.
            for (n in numbers) {
                labels.add(getString(R.string.act_call, n))
                actions.add(Pair("call", n))
                labels.add(getString(R.string.act_sms, n))
                actions.add(Pair("sms", n))
            }
            for (e in emails) {
                labels.add(getString(R.string.act_mail, e))
                actions.add(Pair("mail", e))
            }
            labels.add(getString(R.string.act_edit))
            actions.add(Pair("edit", c.contactId))

            runOnUiThread {
                if (actions.size == 1) {          // seulement « modifier »
                    Toast.makeText(this, R.string.act_nothing, Toast.LENGTH_SHORT).show()
                }
                AlertDialog.Builder(this)
                    .setTitle(c.name)
                    .setItems(labels.toTypedArray()) { _, which ->
                        val (type, value) = actions[which]
                        when (type) {
                            "call" -> {
                                setResult(RESULT_OK, Intent()
                                    .putExtra("number", value)
                                    .putExtra("call", true))
                                finish()
                            }
                            "sms" -> startActivity(
                                Intent(this, ThreadActivity::class.java)
                                    .putExtra("address", value))
                            "mail" -> startActivity(
                                Intent(this, EmailActivity::class.java)
                                    .putExtra("to", value))
                            "edit" -> startActivity(
                                Intent(this, EditContactActivity::class.java)
                                    .putExtra("contact_id", value))
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }.start()
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
        lastAnimated = -1
        EmptyState.show(this, items.isEmpty(), "👤", getString(R.string.empty_contacts))
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

    private val photoCache = HashMap<String, android.graphics.Bitmap?>()
    private val photoTried = HashSet<String>()
    private var lastAnimated = -1


    /**
     * Charge la photo en arrière-plan, une seule tentative par contact.
     * La vue est mise à jour directement : rafraîchir toute la liste
     * relancerait getView, donc de nouveaux chargements, sans fin.
     */
    private fun loadPhoto(contactId: String, target: ImageView) {
        photoTried.add(contactId)
        Thread {
            var bmp: android.graphics.Bitmap? = null
            try {
                val uri = android.content.ContentUris.withAppendedId(
                    ContactsContract.Contacts.CONTENT_URI, contactId.toLong())
                ContactsContract.Contacts.openContactPhotoInputStream(
                    contentResolver, uri, true)?.use {
                    bmp = android.graphics.BitmapFactory.decodeStream(it)
                }
            } catch (_: Exception) {}
            val loaded = bmp ?: return@Thread
            photoCache[contactId] = loaded
            runOnUiThread {
                // La vue a pu être recyclée entre-temps : on vérifie
                if (target.tag == contactId) target.setImageBitmap(loaded)
            }
        }.start()
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

            // Photo du carnet si elle existe, sinon pastille d'initiales
            val img = v.findViewById<ImageView>(R.id.avatar)
            img.tag = c.contactId
            val cached = photoCache[c.contactId]
            if (cached != null) {
                img.setImageBitmap(cached)
            } else {
                img.setImageDrawable(AvatarDrawable(c.name,
                    ThemeRes.color(this@ContactsActivity, R.attr.cNeon)))
                if (c.contactId !in photoTried) loadPhoto(c.contactId, img)
            }

            // Apparition décalée ; la vue finit toujours pleinement visible
            v.animate().cancel()
            if (position > lastAnimated) {
                lastAnimated = position
                v.alpha = 0f
                v.translationY = 26f * resources.displayMetrics.density
                v.animate().alpha(1f).translationY(0f)
                    .setStartDelay((position % 8) * 28L).setDuration(260)
                    .withEndAction { v.alpha = 1f; v.translationY = 0f }
                    .start()
            } else {
                v.alpha = 1f
                v.translationY = 0f
            }
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
