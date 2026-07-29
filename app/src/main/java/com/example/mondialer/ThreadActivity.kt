package com.example.mondialer

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telephony.SmsManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ThreadActivity : Activity() {

    data class Msg(val body: String, val date: Long, val outgoing: Boolean)

    private var address: String = ""
    private var threadId: String? = null
    private val msgs = mutableListOf<Msg>()
    private lateinit var adapter: MsgAdapter
    private lateinit var listView: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtil.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_thread)

        address = intent.getStringExtra("address") ?: ""
        threadId = intent.getStringExtra("thread_id")
        // Ouverture via une intention sms:/smsto:
        intent.data?.let { uri ->
            if (uri.scheme in listOf("sms", "smsto", "mms", "mmsto")) {
                address = uri.schemeSpecificPart ?: address
            }
        }

        val editTo = findViewById<EditText>(R.id.editTo)
        if (address.isNotBlank()) {
            editTo.setText(address)
            editTo.isEnabled = false
        }

        listView = findViewById(R.id.listMsgs)
        adapter = MsgAdapter()
        listView.adapter = adapter

        findViewById<Button>(R.id.btnSend).setOnClickListener {
            val to = editTo.text.toString().trim()
            val body = findViewById<EditText>(R.id.editBody).text.toString().trim()
            if (to.isBlank() || body.isBlank()) return@setOnClickListener
            sendSms(to, body)
        }

        if (checkSelfPermission(Manifest.permission.READ_SMS)
            == PackageManager.PERMISSION_GRANTED && address.isNotBlank()) load()
    }

    override fun onResume() {
        super.onResume()
        if (checkSelfPermission(Manifest.permission.READ_SMS)
            == PackageManager.PERMISSION_GRANTED && address.isNotBlank()) load()
    }

    private fun load() {
        msgs.clear()
        try {
            val (sel, args) = if (threadId != null)
                Pair("thread_id=?", arrayOf(threadId!!))
            else
                Pair("address=?", arrayOf(address))
            contentResolver.query(
                Uri.parse("content://sms"),
                arrayOf("body", "date", "type"),
                sel, args, "date ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val type = c.getInt(2)
                    msgs.add(Msg(
                        c.getString(0) ?: "",
                        c.getLong(1),
                        type == 2 || type == 4 || type == 6 // envoyé / en cours / file d'attente
                    ))
                }
            }
        } catch (_: Exception) {}
        adapter.notifyDataSetChanged()
        if (msgs.isNotEmpty()) listView.setSelection(msgs.size - 1)

        // Marquer comme lus
        try {
            val v = ContentValues().apply { put("read", 1); put("seen", 1) }
            if (threadId != null)
                contentResolver.update(Uri.parse("content://sms"), v, "thread_id=?", arrayOf(threadId))
            else
                contentResolver.update(Uri.parse("content://sms"), v, "address=?", arrayOf(address))
        } catch (_: Exception) {}
    }

    private fun sendSms(to: String, body: String) {
        if (checkSelfPermission(Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.SEND_SMS), 1)
            return
        }
        try {
            val sm = SmsManager.getDefault()
            val parts = sm.divideMessage(body)
            sm.sendMultipartTextMessage(to, null, parts, null, null)

            // Enregistrer dans les envoyés (pris en compte si app SMS par défaut)
            try {
                val values = ContentValues().apply {
                    put("address", to)
                    put("body", body)
                    put("date", System.currentTimeMillis())
                    put("read", 1)
                    put("type", 2)
                }
                contentResolver.insert(Uri.parse("content://sms/sent"), values)
            } catch (_: Exception) {}

            findViewById<EditText>(R.id.editBody).setText("")
            if (address.isBlank()) { address = to; }
            msgs.add(Msg(body, System.currentTimeMillis(), true))
            adapter.notifyDataSetChanged()
            listView.setSelection(msgs.size - 1)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.sms_fail, Toast.LENGTH_SHORT).show()
        }
    }

    inner class MsgAdapter : BaseAdapter() {
        private val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE)
        override fun getCount() = msgs.size
        override fun getItem(position: Int) = msgs[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val v = convertView ?: layoutInflater.inflate(R.layout.item_msg, parent, false)
            val m = msgs[position]
            val row = v.findViewById<LinearLayout>(R.id.msgRow)
            val bubble = v.findViewById<TextView>(R.id.msgBubble)
            val time = v.findViewById<TextView>(R.id.msgTime)
            bubble.text = m.body
            time.text = fmt.format(Date(m.date))
            if (m.outgoing) {
                row.gravity = Gravity.END
                bubble.setBackgroundResource(R.drawable.bubble_out)
            } else {
                row.gravity = Gravity.START
                bubble.setBackgroundResource(R.drawable.bubble_in)
            }
            return v
        }
    }
}
