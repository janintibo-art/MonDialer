package com.example.mondialer

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
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

        intent.getStringExtra("forward")?.let {
            findViewById<RichEditText>(R.id.editBody).setText(it)
        }

        // Contenu envoyé depuis une autre application (Spotify, galerie, navigateur…)
        if (intent.action == Intent.ACTION_SEND) {
            val shared = intent.getStringExtra(Intent.EXTRA_TEXT)
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
            if (!shared.isNullOrBlank()) {
                val text = if (!subject.isNullOrBlank() && !shared.contains(subject))
                    "$subject\n$shared" else shared
                findViewById<RichEditText>(R.id.editBody).setText(text)
            }
            val stream = if (android.os.Build.VERSION.SDK_INT >= 33)
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (stream != null) {
                attachFromUri(stream, intent.type, "partage")
            }
        }

        val editTo = findViewById<EditText>(R.id.editTo)
        if (address.isNotBlank()) {
            editTo.setText(address)
            editTo.isEnabled = false
        }

        // Reprise du texte laissé en plan dans cette conversation
        if (address.isNotBlank()) {
            val draft = BlockRulesStore.draft(address)
            val field = findViewById<RichEditText>(R.id.editBody)
            if (draft.isNotBlank() && field.text.isBlank()) {
                field.setText(draft)
                field.setSelection(field.text.length)
            }
        }

        listView = findViewById(R.id.listMsgs)
        adapter = MsgAdapter()
        listView.adapter = adapter

        // Appui long sur un message : copier, enregistrer, partager, signaler…
        listView.setOnItemLongClickListener { _, _, pos, _ ->
            val m = msgs.getOrNull(pos) ?: return@setOnItemLongClickListener false
            showMessageMenu(m)
            true
        }

        findViewById<Button>(R.id.btnSend).setOnClickListener {
            val to = editTo.text.toString().trim()
            val body = findViewById<RichEditText>(R.id.editBody).text.toString().trim()
            if (to.isBlank()) return@setOnClickListener
            if (attachData != null) sendMms(to, body)
            else if (body.isNotBlank()) sendSms(to, body)
        }

        // Le clavier (Gboard et consorts) transmet directement le GIF choisi
        findViewById<RichEditText>(R.id.editBody).onRichContent = { uri, mime ->
            attachFromUri(uri, mime, "gif")
        }

        findViewById<Button>(R.id.btnAttach).setOnClickListener {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "*/*"
            startActivityForResult(i, 60)
        }
        findViewById<TextView>(R.id.txtAttach).setOnClickListener { clearAttachment() }

        findViewById<Button>(R.id.btnAi).setOnClickListener { suggestReply() }
        findViewById<Button>(R.id.btnAi).setOnLongClickListener { analyzeScam(); true }
    }

    /**
     * Demande trois réponses possibles à partir des derniers messages du fil.
     * Rien n'est envoyé : la suggestion choisie atterrit dans la zone de saisie.
     */
    private fun suggestReply() {
        if (BlockRulesStore.aiKey.isBlank()) {
            AlertDialog.Builder(this)
                .setMessage(R.string.ai_no_key)
                .setPositiveButton(R.string.ai_configure) { _, _ ->
                    startActivity(Intent(this, AiSettingsActivity::class.java))
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }
        if (msgs.isEmpty()) {
            Toast.makeText(this, R.string.ai_no_context, Toast.LENGTH_SHORT).show()
            return
        }

        // Contexte : les derniers échanges, en indiquant qui parle
        val recent = msgs.takeLast(10).joinToString("\n") {
            (if (it.outgoing) "Moi : " else "Correspondant : ") + (it.body ?: "[pièce jointe]")
        }
        val draft = findViewById<RichEditText>(R.id.editBody).text.toString().trim()
        val context = if (draft.isEmpty()) recent
                      else recent + "\n\nIntention de réponse à développer : " + draft

        val progress = AlertDialog.Builder(this)
            .setMessage(R.string.ai_thinking)
            .setCancelable(true)
            .show()

        Thread {
            val result = try {
                AiClient.suggestReplies(context, "sms")
            } catch (e: Exception) {
                runOnUiThread {
                    progress.dismiss()
                    Toast.makeText(this,
                        getString(R.string.ai_error, e.message ?: ""),
                        Toast.LENGTH_LONG).show()
                }
                return@Thread
            }
            runOnUiThread {
                progress.dismiss()
                if (result.isEmpty()) {
                    Toast.makeText(this, R.string.ai_empty, Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                AlertDialog.Builder(this)
                    .setTitle(R.string.ai_pick)
                    .setItems(result.toTypedArray()) { _, which ->
                        findViewById<RichEditText>(R.id.editBody).setText(result[which])
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }.start()
    }

    /** Menu d'actions adapté au contenu du message sélectionné. */
    private fun showMessageMenu(m: Msg) {
        val labels = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        when {
            m.imageUri != null -> {
                val name = "image_${m.date}.jpg"
                labels.add(getString(R.string.msg_save_image))
                actions.add { saveToDownloads(m.imageUri, name, "image/jpeg") }
                labels.add(getString(R.string.msg_share))
                actions.add { shareAttachment(m.imageUri, name, "image/jpeg") }
            }
            m.fileUri != null -> {
                val name = m.fileName ?: "fichier"
                val mime = m.fileMime ?: "application/octet-stream"
                labels.add(getString(R.string.msg_save_file))
                actions.add { saveToDownloads(m.fileUri, name, mime) }
                labels.add(getString(R.string.msg_share))
                actions.add { shareAttachment(m.fileUri, name, mime) }
            }
            !m.body.isNullOrBlank() -> {
                labels.add(getString(R.string.msg_copy))
                actions.add { copyText(m.body) }
                labels.add(getString(R.string.msg_select))
                actions.add { selectPartial(m.body) }
                labels.add(getString(R.string.msg_forward))
                actions.add { forward(m.body) }
            }
        }

        // Actions réservées aux messages reçus
        if (!m.outgoing && !m.body.isNullOrBlank()) {
            labels.add(getString(R.string.report_menu))
            actions.add { Report33700.reportSms(this, m.body, address) }
            labels.add(getString(R.string.scan_menu))
            actions.add { analyzeScam() }
            labels.add(getString(R.string.action_block_number, address))
            actions.add {
                BlockRulesStore.addNumber(address)
                Toast.makeText(this, R.string.number_blocked, Toast.LENGTH_SHORT).show()
            }
        }

        // Suppression du message, toujours proposée en dernier
        labels.add(getString(R.string.msg_delete))
        actions.add { confirmDeleteMessage(m) }

        if (labels.isEmpty()) return
        AlertDialog.Builder(this)
            .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
            .show()
    }

    private fun confirmDeleteMessage(m: Msg) {
        AlertDialog.Builder(this)
            .setMessage(R.string.msg_delete_confirm)
            .setPositiveButton(R.string.contact_delete_yes) { _, _ -> deleteMessage(m) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Retire un message du fournisseur système, puis rafraîchit le fil. */
    private fun deleteMessage(m: Msg) {
        Thread {
            var removed = 0
            try {
                if (m.body != null) {
                    removed = contentResolver.delete(
                        Uri.parse("content://sms"),
                        "address=? AND body=? AND date=?",
                        arrayOf(address, m.body, m.date.toString()))
                }
                if (removed == 0) {
                    // Pièce jointe ou message MMS : on cible la date, en secondes
                    removed = contentResolver.delete(
                        Uri.parse("content://mms"),
                        "date=?", arrayOf((m.date / 1000).toString()))
                }
            } catch (_: Exception) {}
            runOnUiThread {
                if (removed > 0) {
                    Toast.makeText(this, R.string.msg_deleted, Toast.LENGTH_SHORT).show()
                    loadAsync()
                } else {
                    Toast.makeText(this, R.string.thread_delete_fail,
                        Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    /** Copie dans le presse-papiers du système. */
    private fun copyText(text: String) {
        try {
            val cm = getSystemService(android.content.ClipboardManager::class.java)
            cm.setPrimaryClip(android.content.ClipData.newPlainText("message", text))
            Toast.makeText(this, R.string.msg_copied, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.msg_copy_fail, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Ouvre le texte dans un champ où la sélection est libre : on peut copier
     * seulement le passage voulu, un numéro ou une adresse par exemple.
     */
    private fun selectPartial(text: String) {
        val field = EditText(this)
        field.setText(text)
        field.setTextIsSelectable(true)
        field.setPadding(48, 32, 48, 16)
        field.setTextColor(ThemeRes.color(this, R.attr.cText))
        field.setBackgroundResource(0)
        field.setSelection(0, text.length)

        AlertDialog.Builder(this)
            .setTitle(R.string.msg_select_title)
            .setView(field)
            .setPositiveButton(R.string.msg_copy_selection) { _, _ ->
                val start = field.selectionStart.coerceAtLeast(0)
                val end = field.selectionEnd.coerceAtLeast(0)
                val part = if (start != end)
                    field.text.substring(minOf(start, end), maxOf(start, end))
                else field.text.toString()
                copyText(part)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Prépare le message pour un autre destinataire. */
    private fun forward(text: String) {
        startActivity(Intent(this, ThreadActivity::class.java)
            .putExtra("forward", text))
    }

    /** Partage une pièce jointe vers une autre application. */
    private fun shareAttachment(uri: Uri, name: String, mime: String) {
        Thread {
            try {
                val dir = File(cacheDir, "share")
                dir.mkdirs()
                val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val file = File(dir, safe)
                contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { out -> input.copyTo(out) }
                }
                val shared = FileProvider.getUriForFile(
                    this, "com.example.mondialer.files", file)
                val send = Intent(Intent.ACTION_SEND)
                    .setType(mime)
                    .putExtra(Intent.EXTRA_STREAM, shared)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                runOnUiThread {
                    startActivity(Intent.createChooser(
                        send, getString(R.string.msg_share)))
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, R.string.attach_fail, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    /** Soumet le dernier message reçu à l'analyse anti-arnaque. */
    private fun analyzeScam() {
        if (BlockRulesStore.aiKey.isBlank()) {
            Toast.makeText(this, R.string.ai_no_key_short, Toast.LENGTH_LONG).show()
            return
        }
        val incoming = msgs.lastOrNull { !it.outgoing && !it.body.isNullOrBlank() }
        if (incoming == null) {
            Toast.makeText(this, R.string.scan_no_message, Toast.LENGTH_SHORT).show()
            return
        }
        val progress = AlertDialog.Builder(this)
            .setMessage(R.string.scan_running)
            .setCancelable(true)
            .show()

        Thread {
            val verdict = try {
                AiClient.analyzeScam(incoming.body ?: "")
            } catch (e: Exception) {
                runOnUiThread {
                    progress.dismiss()
                    Toast.makeText(this,
                        getString(R.string.ai_error, e.message ?: ""),
                        Toast.LENGTH_LONG).show()
                }
                return@Thread
            }
            runOnUiThread {
                progress.dismiss()
                AlertDialog.Builder(this)
                    .setTitle(R.string.scan_title)
                    .setMessage(verdict)
                    .setPositiveButton(android.R.string.ok, null)
                    .setNeutralButton(R.string.scan_block) { _, _ ->
                        BlockRulesStore.addNumber(address)
                        Toast.makeText(this, R.string.number_blocked, Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(R.string.report_menu) { _, _ ->
                        Report33700.reportSms(this, incoming.body ?: "", address)
                    }
                    .show()
            }
        }.start()
    }


    override fun onPause() {
        super.onPause()
        // Le texte non envoyé est conservé pour la prochaine ouverture
        if (address.isNotBlank()) {
            BlockRulesStore.setDraft(address,
                findViewById<RichEditText>(R.id.editBody).text.toString())
        }
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

    /** Charge un contenu (GIF du clavier ou fichier choisi) en pièce jointe. */
    private fun attachFromUri(uri: Uri, mimeHint: String?, nameHint: String) {
        Thread {
            try {
                val bytes = contentResolver.openInputStream(uri)?.readBytes() ?: return@Thread
                val mime = mimeHint ?: contentResolver.getType(uri) ?: "application/octet-stream"
                val ext = mime.substringAfterLast('/', "bin")
                val name = queryName(uri) ?: "$nameHint.$ext"
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
            // Sans identifiant de fil, on le déduit du numéro : la recherche
            // par adresse exacte manquerait les formats internationaux.
            if (threadId == null) threadId = findThreadId()
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
                val tid2 = threadId
                if (tid2 != null)
                    contentResolver.update(Uri.parse("content://sms"), v,
                        "thread_id=?", arrayOf(tid2))
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

    /**
     * Retrouve le fil correspondant au numéro. La comparaison exacte échoue
     * dès que le format diffère (+33645511828 contre 0645511828) : on compare
     * donc les derniers chiffres, seuls réellement discriminants.
     */
    private fun findThreadId(): String? {
        val mine = BlockRulesStore.normalize(address)
        if (mine.isEmpty()) return null
        val tail = mine.takeLast(9)
        var found: String? = null
        try {
            contentResolver.query(
                Uri.parse("content://sms"),
                arrayOf("thread_id", "address"),
                null, null, "date DESC LIMIT 500"
            )?.use { c ->
                while (c.moveToNext() && found == null) {
                    val a = BlockRulesStore.normalize(c.getString(1))
                    if (a.isNotEmpty() && (a.endsWith(tail) || tail.endsWith(a.takeLast(9)))) {
                        found = c.getString(0)
                    }
                }
            }
        } catch (_: Exception) {}
        return found
    }

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
            findViewById<RichEditText>(R.id.editBody).setText("")
            if (address.isBlank()) address = to
            BlockRulesStore.setDraft(to, "")
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
        findViewById<RichEditText>(R.id.editBody).setText("")
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
            val bg = if (m.outgoing) ThemeRes.res(this@ThreadActivity, R.attr.bubbleOutBg)
                     else ThemeRes.res(this@ThreadActivity, R.attr.bubbleInBg)

            bubble.visibility = View.GONE
            img.visibility = View.GONE

            // Le menu s'ouvre par appui long, posé sur chaque vue : un
            // écouteur de clic sur un enfant priverait la liste de l'événement.
            val longPress = View.OnLongClickListener {
                showMessageMenu(m)
                true
            }
            v.setOnLongClickListener(longPress)
            bubble.setOnLongClickListener(longPress)
            img.setOnLongClickListener(longPress)
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
                    img.setOnClickListener { showMessageMenu(m) }
                }
                m.fileName != null -> {
                    bubble.visibility = View.VISIBLE
                    bubble.setBackgroundResource(bg)
                    bubble.text = "📎 ${m.fileName}\n(${getString(R.string.tap_to_save)})"
                    bubble.setOnClickListener { showMessageMenu(m) }
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
