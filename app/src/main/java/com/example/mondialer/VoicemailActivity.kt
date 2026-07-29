package com.example.mondialer

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.VoicemailContract
import android.telephony.TelephonyManager
import android.widget.Button
import android.widget.ListView
import android.widget.SimpleAdapter
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VoicemailActivity : Activity() {

    private var player: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtil.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voicemail)

        findViewById<Button>(R.id.btnCallVm).setOnClickListener { callVoicemail() }

        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE), 1)
        }
        loadVisualVoicemail()
    }

    private fun callVoicemail() {
        var number: String? = null
        try {
            if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED) {
                number = getSystemService(TelephonyManager::class.java).voiceMailNumber
            }
        } catch (_: Exception) {}

        if (!number.isNullOrBlank()) {
            dial(number)
        } else {
            // Numéros de répondeur des opérateurs français
            val carriers = arrayOf(
                "Orange / Sosh — 888",
                "SFR / RED — 147",
                "Bouygues / B&You — 660",
                "Free — 666"
            )
            val numbers = arrayOf("888", "147", "660", "666")
            AlertDialog.Builder(this)
                .setTitle(R.string.choose_carrier)
                .setItems(carriers) { _, which -> dial(numbers[which]) }
                .show()
        }
    }

    private fun dial(number: String) {
        if (checkSelfPermission(Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), 2)
            return
        }
        startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
    }

    /** Messagerie vocale visuelle : disponible seulement si l'opérateur la fournit
     *  et si l'app est Téléphone par défaut. */
    private fun loadVisualVoicemail() {
        val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE)
        val items = mutableListOf<Map<String, String>>()
        val ids = mutableListOf<Long>()
        try {
            contentResolver.query(
                VoicemailContract.Voicemails.CONTENT_URI,
                arrayOf(
                    VoicemailContract.Voicemails._ID,
                    VoicemailContract.Voicemails.NUMBER,
                    VoicemailContract.Voicemails.DATE,
                    VoicemailContract.Voicemails.DURATION,
                    VoicemailContract.Voicemails.IS_READ
                ),
                null, null,
                VoicemailContract.Voicemails.DATE + " DESC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val number = c.getString(1) ?: getString(R.string.hidden_number)
                    val date = fmt.format(Date(c.getLong(2)))
                    val dur = c.getLong(3)
                    val unread = if (c.getInt(4) == 0) "● " else ""
                    ids.add(id)
                    items.add(mapOf(
                        "title" to unread + number,
                        "sub" to "🎧 ${dur}s  •  $date"
                    ))
                }
            }
        } catch (e: Exception) {
            // Pas d'accès : opérateur sans messagerie visuelle ou app pas encore par défaut
        }

        val list = findViewById<ListView>(R.id.listVm)
        if (items.isEmpty()) {
            Toast.makeText(this, R.string.no_visual_vm, Toast.LENGTH_LONG).show()
            return
        }
        list.adapter = SimpleAdapter(
            this, items, R.layout.item_two_lines,
            arrayOf("title", "sub"), intArrayOf(R.id.text1, R.id.text2)
        )
        list.setOnItemClickListener { _, _, pos, _ ->
            playVoicemail(ids[pos])
        }
    }

    private fun playVoicemail(id: Long) {
        try {
            player?.release()
            player = MediaPlayer()
            val uri = ContentUris.withAppendedId(VoicemailContract.Voicemails.CONTENT_URI, id)
            player?.setDataSource(this, uri)
            player?.prepare()
            player?.start()
            Toast.makeText(this, R.string.playing_vm, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.vm_play_fail, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        player?.release()
        super.onDestroy()
    }
}
