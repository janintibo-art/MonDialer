package com.example.mondialer

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.telephony.SmsManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ThreadActivity : Activity() {

    data class Msg(
        val body: String?,
        val date: Long,
        val outgoing: Boolean,
        val imageUri: Uri? = null,
        val fileName: String? = null,
        val fileUri: Uri? = null,
        val fileMime: String? = null
    )

    private var address: String = ""
    private var threadId: String? = null
    private val msgs = mutableListOf<Msg>()
    private lateinit var adapter: MsgAdapter
    private lateinit var listView: ListView

    @Volatile private var loading = false
    private val bmpCache = HashMap<Uri, Bitmap?>()
    private val decoding = HashSet<Uri>()

    private var attachData: ByteArray? = null
    private var attachName: String? = null
    private var attachMime: String? = null

    private val emojis = ("😀😁😂🤣😃😄😅😆😉😊😋😎😍😘🥰😗😙😚🙂🤗🤩🤔🤨😐😑😶🙄😏😣" +
        "😥😮🤐😯😪😫🥱😴😌😛😜😝🤤😒😓😔😕🙃🤑😲☹️🙁😖😞😟😤😢😭😦😧😨😩🤯😬" +
        "😰😱🥵🥶😳🤪😵😡😠🤬😷🤒🤕🤢🤮🤧🥳🥺🤠🤡🤥🤫🤭🧐🤓😈👿👍👎👊✊🤛🤜🤞" +
        "✌️🤟🤘👌🤏👈👉👆👇☝️✋🤚🖐🖖👋🤙💪🙏❤️🧡💛💚💙💜🖤🤍💔❣️💕💞💓💗💖" +
        "💘💝🔥✨⭐🌟💫🎉🎊🎁🌹🌸💐🍀☀️🌙⚡❄️🍕🍔🍟🌭🍿🧁🎂🍾🥂☕🚀✈️🚗⚽🏀🎮🎵🎶")

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtil.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_thread)

        address = intent.getStringExtra("address") ?: ""
        threadId = intent.getStringExtra("thread_id")
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
            if (to.isBlank()) return@setOnClickListener
            if (attachData != null) sendMms(to, body)
            else if (body.isNotBlank()) sendSms(to, body)
        }

        val emojiPanel = findViewById<View>(R.id.emojiPanel)
        findViewById<Button>(R.id.btnEmoji).setOnClickListener {
            emojiPanel.visibility =
                if (emojiPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        val grid = findViewById<GridView>(R.id.emojiGrid)
        val list = splitEmojis(emojis)
        grid.adapter = object : BaseAdapter() {
            override fun getCount() = list.size
            override fun getItem(p: Int) = list[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, cv: View?, parent: ViewGroup?): View {
                val tv = (cv as? TextView) ?: TextView(this@ThreadActivity)
                tv.text = list[p]
                tv.textSize = 26f
                tv.gravity = Gravity.CENTER
                tv.setPadding(0, 14, 0, 14)
                return tv
            }
        }
        grid.setOnItemClickListener { _, _, pos, _ ->
            findViewById<EditText>(R.id.editBody).append(list[pos])
        }

        findViewById<Button>(R.id.btnGif).setOnClickListener {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "image/gif"
            startActivityForResult(i, 61)
        }
        findViewById<Button>(R.id.btnAttach).setOnClickListener {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "*/*"
            startActivityForResult(i, 60)
        }
        findViewById<TextView>(R.id.txtAttach).setOnClickListener { clearAttachment() }
    }

    private fun splitEmojis(s: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val len = Character.charCount(cp)
            var chunk = s.substring(i, i + len)
            i += len
            while (i < s.length) {
                val c2 = s.codePointAt(i)
                if (c2 == 0xFE0F || c2 in 0x1F3FB..0x1F3FF || c2 == 0x200D) {
                    val l2 = Character.charCount(c2)
                    chunk += s.substring(i, i + l2)
                    i += l2
                } else break
            }
            if (chunk.isNotBlank()) out.add(chunk)
        }
        return out
    }

    override fun onResume() {
        super.onResume()
        ThemeUtil.refreshIfNeeded(this)
        if (checkSelfPermission(Manifest.permission.READ_SMS)
            == PackageManager.PERMISSION_GRANTED && address.isNotBlank()) loadAsync()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if ((requestCode == 60 || requestCode == 61) && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            Thread {
                try {
                    val bytes = contentResolver.openInputStream(uri)?.readBytes()
                        ?: return@Thread
                    val mime = contentResolver.getType(uri) ?: "application/octet-stream"
                    val name = queryName(uri) ?: "fichier"
                    runOnUiThread {
                        if (bytes.size > 1_000_000) {
                            Toast.makeText(this, R.string.attach_too_big, Toast.LENGTH_LONG).show()
                        }
                        attachData = bytes
                        attachMime = mime
                        attachName = name
                        val chip = findViewById<TextView>(R.id.txtAttach)
                        chip.text = "📎 $name (${bytes.size / 1024} Ko) ✕"
                        chip.visibility = View.VISIBLE
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this, R.string.attach_fail, Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }
    }

    private fun queryName(uri: Uri): String? = try {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
        }
    } catch (_: Exception) { null }

    private fun clearAttachment() {
        attachData = null; attachName = null; attachMime = null
        findViewById<TextView>(R.id.txtAttach).visibility = View.GONE
    }

    // ---- Chargement ASYNCHRONE (SMS + MMS) ----
    private fun loadAsync() {
        if (loading) return
        loading = true
        Thread {
            val result = mutableListOf<Msg>()
            try {
                val (sel, args) = if (threadId != null)
                    Pair("thread_id=?", arrayOf(threadId!!))
                else Pair("address=?", arrayOf(address))
                contentResolver.query(
                    Uri.parse("content://sms"),
                    arrayOf("body", "date", "type"),
                    sel, args, "date DESC LIMIT 300"
                )?.use { c ->
                    while (c.moveToNext()) {
                        val type = c.getInt(2)
                        result.add(Msg(c.getString(0) ?: "", c.getLong(1),
                            type == 2 || type == 4 || type == 6))
                    }
                }
            } catch (_: Exception) {}

            try {
                val tid = threadId ?: findThreadId()
                if (tid != null) {
                    contentResolver.query(
                        Uri.parse("content://mms"),
                        arrayOf("_id", "date", "msg_box"),
                        "thread_id=?", arrayOf(tid), "date DESC LIMIT 50"
                    )?.use { c ->
                        while (c.moveToNext()) {
                            val mid = c.getString(0)
                            val date = c.getLong(1) * 1000
                            val outgoing = c.getInt(2) == 2
                            loadMmsParts(result, mid, date, outgoing)
                        }
                    }
                }
            } catch (_: Exception) {}

            result.sortBy { it.date }

            // Marquer comme lus (en arrière-plan)
            try {
                val v = ContentValues().apply { put("read", 1); put("seen", 1) }
                if (threadId != null)
                    contentResolver.update(Uri.parse("content://sms"), v,
                        "thread_id=?", arrayOf(threadId))
                else
                    contentResolver.update(Uri.parse("content://sms"), v,
                        "address=?", arrayOf(address))
            } catch (_: Exception) {}

            runOnUiThread {
                msgs.clear()
                msgs.addAll(result)
                adapter.notifyDataSetChanged()
                if (msgs.isNotEmpty()) listView.setSelection(msgs.size - 1)
                loading = false
            }
        }.start()
    }

    private fun findThreadId(): String? = try {
        var tid: String? = null
        contentResolver.query(
            Uri.parse("content://sms"),
            arrayOf("thread_id"), "address=?", arrayOf(address), null
        )?.use { c -> if (c.moveToFirst()) tid = c.getString(0) }
        tid
    } catch (_: Exception) { null }

    private fun loadMmsParts(result: MutableList<Msg>, mid: String, date: Long, outgoing: Boolean) {
        try {
            var text: String? = null
            val extra = mutableListOf<Msg>()
            contentResolver.query(
                Uri.parse("content://mms/part"),
                arrayOf("_id", "ct", "text", "name", "cl"),
                "mid=?", arrayOf(mid), null
            )?.use { c ->
                while (c.moveToNext()) {
                    val partId = c.getString(0)
                    val ct = c.getString(1) ?: ""
                    val partUri = Uri.parse("content://mms/part/$partId")
                    when {
                        ct == "text/plain" -> text = c.getString(2) ?: ""
                        ct.startsWith("image/") ->
                            extra.add(Msg(null, date, outgoing, imageUri = partUri))
                        ct == "application/smil" -> {}
                        else -> {
                            val name = c.getString(3) ?: c.getString(4) ?: "fichier"
                            extra.add(Msg(null, date, outgoing,
                                fileName = name, fileUri = partUri, fileMime = ct))
                        }
                    }
                }
            }
            if (!text.isNullOrBlank()) result.add(Msg(text, date, outgoing))
            result.addAll(extra)
        } catch (_: Exception) {}
    }

    // ---- Envoi ----
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
            Thread {
                try {
                    val values = ContentValues().apply {
                        put("address", to); put("body", body)
                        put("date", System.currentTimeMillis())
                        put("read", 1); put("type", 2)
                    }
                    contentResolver.insert(Uri.parse("content://sms/sent"), values)
                } catch (_: Exception) {}
            }.start()
            findViewById<EditText>(R.id.editBody).setText("")
            if (address.isBlank()) address = to
            msgs.add(Msg(body, System.currentTimeMillis(), true))
            adapter.notifyDataSetChanged()
            listView.setSelection(msgs.size - 1)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.sms_fail, Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendMms(to: String, body: String) {
        if (checkSelfPermission(Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.SEND_SMS), 1)
            return
        }
        val data = attachData ?: return
        val name = attachName ?: "fichier"
        val mime = attachMime ?: "application/octet-stream"
        findViewById<EditText>(R.id.editBody).setText("")
        clearAttachment()
        Toast.makeText(this, R.string.mms_sending, Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val parts = mutableListOf<MmsCodec.Part>()
                if (body.isNotBlank())
                    parts.add(MmsCodec.Part("text/plain", null, body.toByteArray()))
                parts.add(MmsCodec.Part(mime, name, data))

                val pdu = MmsCodec.composeSendReq(to, parts)
                val dir = File(cacheDir, "mms"); dir.mkdirs()
                val file = File(dir, "out_${System.currentTimeMillis()}.pdu")
                file.writeBytes(pdu)
                val uri = FileProvider.getUriForFile(
                    this, "com.example.mondialer.files", file)

                SmsManager.getDefault().sendMultimediaMessage(this, uri, null, null, null)
                MmsStore.insert(this, to, parts, outgoing = true)

                runOnUiThread {
                    if (address.isBlank()) address = to
                    loadAsync()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, R.string.sms_fail, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun saveToDownloads(uri: Uri, name: String, mime: String) {
        Thread {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                }
                val out = contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@Thread
                contentResolver.openInputStream(uri)?.use { input ->
                    contentResolver.openOutputStream(out)?.use { output ->
                        input.copyTo(output)
                    }
                }
                runOnUiThread {
                    Toast.makeText(this,
                        getString(R.string.saved_downloads, name), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, R.string.attach_fail, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
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
            val img = v.findViewById<ImageView>(R.id.msgImage)
            val time = v.findViewById<TextView>(R.id.msgTime)

            time.text = fmt.format(Date(m.date))
            row.gravity = if (m.outgoing) Gravity.END else Gravity.START
            val bg = if (m.outgoing) R.drawable.bubble_out else R.drawable.bubble_in

            bubble.visibility = View.GONE
            img.visibility = View.GONE
            bubble.setOnClickListener(null)
            img.setOnClickListener(null)

            when {
                m.imageUri != null -> {
                    img.visibility = View.VISIBLE
                    img.setBackgroundResource(bg)
                    val cached = bmpCache[m.imageUri]
                    if (cached != null) {
                        img.setImageBitmap(cached)
                    } else {
                        img.setImageResource(android.R.drawable.ic_menu_gallery)
                        decodeAsync(m.imageUri)
                    }
                    img.setOnClickListener {
                        saveToDownloads(m.imageUri, "mms_${m.date}.jpg", "image/jpeg")
                    }
                }
                m.fileName != null -> {
                    bubble.visibility = View.VISIBLE
                    bubble.setBackgroundResource(bg)
                    bubble.text = "📎 ${m.fileName}\n(${getString(R.string.tap_to_save)})"
                    bubble.setOnClickListener {
                        if (m.fileUri != null)
                            saveToDownloads(m.fileUri, m.fileName,
                                m.fileMime ?: "application/octet-stream")
                    }
                }
                else -> {
                    bubble.visibility = View.VISIBLE
                    bubble.setBackgroundResource(bg)
                    bubble.text = m.body ?: ""
                }
            }
            return v
        }

        /** Décode l'image en arrière-plan puis rafraîchit la liste. */
        private fun decodeAsync(uri: Uri) {
            synchronized(decoding) {
                if (uri in decoding || bmpCache.containsKey(uri)) return
                decoding.add(uri)
            }
            Thread {
                val bmp = try {
                    contentResolver.openInputStream(uri)?.use { input ->
                        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                        BitmapFactory.decodeStream(input, null, opts)
                    }
                } catch (_: Exception) { null }
                runOnUiThread {
                    bmpCache[uri] = bmp
                    synchronized(decoding) { decoding.remove(uri) }
                    notifyDataSetChanged()
                }
            }.start()
        }
    }

    override fun onDestroy() {
        ThemeUtil.forget(this)
        super.onDestroy()
    }
}
