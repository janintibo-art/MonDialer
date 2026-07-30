package com.example.mondialer

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayOutputStream

class EditContactActivity : Activity() {

    private var contactId: String? = null
    private var rawContactId: String? = null
    private var newPhoto: ByteArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtil.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_contact)

        contactId = intent.getStringExtra("contact_id")
        findViewById<TextView>(R.id.txtEditTitle).text =
            if (contactId == null) getString(R.string.new_contact)
            else getString(R.string.edit_contact)

        findViewById<ImageView>(R.id.imgPhoto).setOnClickListener {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "image/*"
            startActivityForResult(i, 70)
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }

        val btnSys = findViewById<Button>(R.id.btnSystemEdit)
        if (contactId == null) btnSys.visibility = android.view.View.GONE
        else btnSys.setOnClickListener {
            try {
                val uri = ContentUris.withAppendedId(
                    ContactsContract.Contacts.CONTENT_URI, contactId!!.toLong())
                startActivity(Intent(Intent.ACTION_EDIT, uri))
            } catch (_: Exception) {}
        }

        val perms = arrayOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS)
        if (perms.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
            requestPermissions(perms, 1)
        } else if (contactId != null) loadContact()
    }

    private fun loadContact() {
        val id = contactId ?: return
        try {
            // Raw contact
            contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts._ID),
                ContactsContract.RawContacts.CONTACT_ID + "=?",
                arrayOf(id), null
            )?.use { c -> if (c.moveToFirst()) rawContactId = c.getString(0) }

            // Données existantes
            contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1),
                ContactsContract.Data.CONTACT_ID + "=?",
                arrayOf(id), null
            )?.use { c ->
                while (c.moveToNext()) {
                    val mime = c.getString(0) ?: continue
                    val value = c.getString(1) ?: continue
                    when (mime) {
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE ->
                            setIfEmpty(R.id.editName, value)
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE ->
                            setIfEmpty(R.id.editPhone, value)
                        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE ->
                            setIfEmpty(R.id.editEmail, value)
                        ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE ->
                            setIfEmpty(R.id.editAddress, value)
                    }
                }
            }

            // Photo existante
            try {
                val contactUri = ContentUris.withAppendedId(
                    ContactsContract.Contacts.CONTENT_URI, id.toLong())
                ContactsContract.Contacts.openContactPhotoInputStream(
                    contentResolver, contactUri)?.use { input ->
                    val bmp = BitmapFactory.decodeStream(input)
                    if (bmp != null)
                        findViewById<ImageView>(R.id.imgPhoto).setImageBitmap(bmp)
                }
            } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    private fun setIfEmpty(id: Int, value: String) {
        val e = findViewById<EditText>(id)
        if (e.text.isNullOrBlank()) e.setText(value)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 70 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                val bmp = contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)
                } ?: return
                // Redimensionner à 512 px max
                val scale = 512f / maxOf(bmp.width, bmp.height)
                val resized = if (scale < 1f)
                    Bitmap.createScaledBitmap(bmp,
                        (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
                else bmp
                val out = ByteArrayOutputStream()
                resized.compress(Bitmap.CompressFormat.JPEG, 90, out)
                newPhoto = out.toByteArray()
                findViewById<ImageView>(R.id.imgPhoto).setImageBitmap(resized)
            } catch (e: Exception) {
                Toast.makeText(this, R.string.attach_fail, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun save() {
        if (checkSelfPermission(Manifest.permission.WRITE_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_CONTACTS), 1)
            return
        }
        val name = findViewById<EditText>(R.id.editName).text.toString().trim()
        val phone = findViewById<EditText>(R.id.editPhone).text.toString().trim()
        val email = findViewById<EditText>(R.id.editEmail).text.toString().trim()
        val addr = findViewById<EditText>(R.id.editAddress).text.toString().trim()

        if (name.isBlank() && phone.isBlank()) {
            Toast.makeText(this, R.string.need_name_or_phone, Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Créer le contact si nécessaire
            if (rawContactId == null) {
                val rv = ContentValues()
                val rawUri = contentResolver.insert(
                    ContactsContract.RawContacts.CONTENT_URI, rv) ?: return
                rawContactId = rawUri.lastPathSegment
            }
            val rid = rawContactId ?: return

            if (name.isNotBlank()) upsert(rid,
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE, name)
            if (phone.isNotBlank()) upsert(rid,
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, phone)
            if (email.isNotBlank()) upsert(rid,
                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE, email)
            if (addr.isNotBlank()) upsert(rid,
                ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE, addr)

            // Photo
            val photo = newPhoto
            if (photo != null) {
                val cv = ContentValues().apply {
                    put(ContactsContract.CommonDataKinds.Photo.PHOTO, photo)
                }
                val updated = contentResolver.update(
                    ContactsContract.Data.CONTENT_URI, cv,
                    ContactsContract.Data.RAW_CONTACT_ID + "=? AND " +
                            ContactsContract.Data.MIMETYPE + "=?",
                    arrayOf(rid, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE))
                if (updated == 0) {
                    cv.put(ContactsContract.Data.RAW_CONTACT_ID, rid)
                    cv.put(ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                    contentResolver.insert(ContactsContract.Data.CONTENT_URI, cv)
                }
            }

            Toast.makeText(this, R.string.contact_saved, Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.contact_save_fail, Toast.LENGTH_SHORT).show()
        }
    }

    /** Met à jour la première donnée du type, ou la crée si absente. */
    private fun upsert(rid: String, mime: String, value: String) {
        val cv = ContentValues().apply { put(ContactsContract.Data.DATA1, value) }
        val updated = contentResolver.update(
            ContactsContract.Data.CONTENT_URI, cv,
            ContactsContract.Data.RAW_CONTACT_ID + "=? AND " +
                    ContactsContract.Data.MIMETYPE + "=?",
            arrayOf(rid, mime))
        if (updated == 0) {
            cv.put(ContactsContract.Data.RAW_CONTACT_ID, rid)
            cv.put(ContactsContract.Data.MIMETYPE, mime)
            contentResolver.insert(ContactsContract.Data.CONTENT_URI, cv)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        if (requestCode == 1 && grantResults.all {
                it == PackageManager.PERMISSION_GRANTED } && contactId != null) {
            loadContact()
        }
    }
}
